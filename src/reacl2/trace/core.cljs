(ns reacl2.trace.core)

(def ^:private tracers (atom {}))

(def ^{:doc "The given `component` was sent the given `message`,
  marking the beginning of an update cycle."}
  send-message-trace ::send-message-trace)

(def ^{:doc "The given `class` was rendered as a toplevel with the
  given `app-state` and `args`, marking the beginning of an update
  cycle."}  render-component-trace ::render-component-trace)

(def ^{:doc "The given `component` received and handled a `message`,
  under the given `app-state` and `local-state`, resulting in a
  `returned` value."} handled-message-trace ::handled-message-trace)

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

(defn add-tracer! [id initial-state tracer-map]
  (assert (map? tracer-map))
  (assert (every? #{send-message-trace render-component-trace handled-message-trace reduced-action-trace commit-trace}
                  (keys tracer-map)))
  (assert (every? ifn? (vals tracer-map)))
  (swap! tracers assoc id [initial-state tracer-map]))

(defn remove-tracer! [id]
  (swap! tracers dissoc id))

(defn ^:no-doc trace-send-message!
  [component message]
  (trigger-trace! send-message-trace component message))

(defn ^:no-doc trace-render-component!
  [class app-state args]
  (trigger-trace! render-component-trace class app-state args))

(defn ^:no-doc trace-handled-message!
  [component app-state local-state message returned]
  (trigger-trace! handled-message-trace component app-state local-state message returned))

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
