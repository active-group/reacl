(ns reacl2.trace.core)

(def ^:private tracers (atom {}))

(def ^{:doc "The given `component` was sent the given `message`"}
  send-message-trace ::send-message-trace)

(def ^{:doc "The given `component` returned `ret` from handling a
  message or the livecycle method `from`. This marks the beginning of an update
  cycle."}  returned-trace ::returned-trece)

(def ^{:doc "The given `class` was rendered as a toplevel with the
  given `app-state` and `args`."}
  render-component-trace ::render-component-trace)

(def ^{:doc "The `component` had an action reducer attached, which
  transformed the given `action` into the given `returned`. "}
  reduced-action-trace ::reduced-action-trace)

(def ^{:doc "The update cycle started with a send-message or
  render-component came to an end, resulting in the given new global
  `app-state` and a sequence of tuples of a `component` and its new
  `local-state`."}  commit-trace ::commit-trace)

(defn- trigger-trace! [trace & args]
  (let [mp @tracers]
    (doseq [[id [state t]] mp]
      (when-let [f (get t trace)]
        (try (let [nstate (apply f state args)]
               (swap! tracers assoc-in [id 0] nstate))
             (catch :default e
               (js/console.warn "Tracer failed" e)))))))

(defn tracer [initial-state tracer-map]
  {:initial-state initial-state
   :tracer-map tracer-map})

(defn add-tracer! [id tracer]
  (let [{:keys [initial-state tracer-map]} tracer]
    (assert (map? tracer-map))
    (assert (every? #{returned-trace send-message-trace render-component-trace reduced-action-trace commit-trace}
                    (keys tracer-map)))
    (assert (every? ifn? (vals tracer-map)))
    (swap! tracers assoc id [initial-state tracer-map])))

(defn remove-tracer! [id]
  (swap! tracers dissoc id))

(defn ^:no-doc trace-send-message!
  [component message]
  (trigger-trace! send-message-trace component message))

(defn ^:no-doc trace-returned!
  [component ret from]
  (trigger-trace! returned-trace component ret from))

(defn ^:no-doc trace-render-component!
  [class app-state args]
  (trigger-trace! render-component-trace class app-state args))

(defn ^:no-doc trace-reduced-action!
  [component action returned]
  (trigger-trace! reduced-action-trace component action returned))

(defn ^:no-doc trace-cycle-done!
  [global-app-state local-state-map]
  (trigger-trace! commit-trace global-app-state local-state-map))

;; utitlies for tracers

(defn component-class-name [comp]
  (.-displayName (.-constructor comp)))

(defonce ^:private comp-ids (atom nil))
(defonce ^:private comp-id-next (atom 0))
(defn- next-id [] (swap! comp-id-next inc))

(defn component-id [comp]
  (let [wmp (or @comp-ids
                (let [m (js/WeakMap.)]
                  (reset! comp-ids m)
                  m))]
    (if (.has wmp comp)
      (.get wmp comp)
      (let [id (next-id)]
        (.set wmp comp id)
        id))))

(defn- event-cycle-ids-initial-state [custom-state]
  {:cycle-id 0 :event-id 0 :custom custom-state})

(defn- with-next-event-id [state f]
  (-> (f state (:event-id state))
      (update :event-id inc)))

(defn map-event-cycle-ids [tracer]
  (-> tracer
      (update :initial-state event-cycle-ids-initial-state)
      (update :tracer-map
              (fn [tracer-map]
                (into {}
                      (map (fn [[t f]]
                             [t (cond 
                                  (= t send-message-trace)
                                  (fn [state component msg]
                                    (with-next-event-id state (fn [state ev-id]
                                                                (update state :custom f ev-id component msg))))
                                  (= t render-component-trace)
                                  (fn [state class app-state args]
                                    (with-next-event-id state (fn [state ev-id]
                                                                (update state :custom f ev-id class app-state args))))

                                  (= t returned-trace)
                                  (fn [state component returned from]
                                    (with-next-event-id state
                                      (fn [state ev-id]
                                        ;; and this starts a new cycle.
                                        (-> state
                                            (update :cycle-id inc)
                                            (update :custom f ev-id (inc (:cycle-id state)) component returned from)))))

                                  (= t reduced-action-trace)
                                  (fn [state component action returned]
                                    (-> state
                                        (update :custom f (:cycle-id state) component action returned)))

                                  (= t commit-trace)
                                  (fn [state global-app-state local-state-map]
                                    (-> state
                                        (update :custom f (:cycle-id state) global-app-state local-state-map)))

                                  :else (assert false t))])
                           tracer-map))))))
