(ns reacl2.trace.console
  (:require [reacl2.trace.core :as trace]
            [reacl2.core :as reacl]))

(defn- log! [state label & args]
  (apply js/console.log
         label
         args)
  state)

(defn- logc! [state label & args]
  (apply log! state (str "#" (:cycle-id state)) label args))

(defn- loge! [state label & args]
  (as-> state $
    (apply log! $ (str "event #" (:event-id state)) label args)
    (update $ :event-id inc)))

(declare show-comp)

(defn- show-comp-args [args]
  (mapcat (fn [a]
            (cond
              (reacl/component? a)
              [(show-comp a)]

              (or (vector? a) (list? a))
              (concat ["["] (show-comp-args a) "]")

              :else [a]))
          args))

(defn- show-comp [comp]
  (apply list (str "#{" (trace/component-class-name comp)) (str "@" (trace/component-id comp))
         ;; this shows args, and call show-comp on args that are component
         #_(concat (show-comp-args (reacl/extract-args comp)) ["}"])
         ;; but with this, one can grab the comp from the log into a global variable in Chrome.
         [comp "}"]))

(defn-  show-comp-short [comp]
  (str (str "#{" (trace/component-class-name comp)) (str " @" (trace/component-id comp)) "}"))

(defn- show-ret [ret]
  (let [ast (reacl/returned-app-state ret)
        lst (reacl/returned-local-state ret)
        msgs (reacl/returned-messages ret)
        acts (reacl/returned-actions ret)]
    `(~'return ~@(when-not (reacl/keep-state? ast)
                   [:app-state ast])
               ~@(when-not (reacl/keep-state? lst)
                   [:local-state lst])
               ~@(mapcat #(list :message %) msgs)
               ~@(mapcat #(list :action %) acts))))

(defn- map-keys [f mp]
  (zipmap (map f (keys mp)) (vals mp)))

(defn- global [v]
  (if (reacl/keep-state? v)
    "<unchanged>"
    v))

(defn- end-log-cycle [state]
  (when (:group-open state)
    (js/console.groupEnd (:cycle-id state)))
  (assoc state :group-open false))

(defn- start-log-cycle [state]
  (let [nstate (cond-> state
                 (:group-open state) (end-log-cycle)
                 true (update :cycle-id inc))]
    (js/console.group (str "event #" (:event-id nstate) " cycle #" (:cycle-id nstate)))
    (-> nstate
        (assoc :group-open true
               :event-id (inc (:event-id nstate))))))

(defn setup-console-tracer! []
  (trace/add-tracer! ::console
                     {:cycle-id 0 :event-id 0}
                     {trace/send-message-trace
                      (fn [state component msg] ;; may start a cycle (but must not)
                        (as-> state $
                          (apply loge! $ "sending message" msg "to" (show-comp component))))

                      trace/render-component-trace
                      (fn [state class app-state args]
                        (as-> state $
                          (apply loge! $ "rendering component" [(.-displayName (reacl/react-class class)) app-state args])))

                      trace/returned-trace
                      (fn [state component returned]
                        ;; Note: one must look at the previous event (another cycle, a message or a rendering)
                        ;; to see why this cycle triggered (but hardly no other way...?)
                        (as-> state $
                          (start-log-cycle $)
                          (apply logc! $ "returned from component" (concat (show-comp component) [(show-ret returned)]))))
                      
                      trace/reduced-action-trace
                      (fn [state component action returned]
                        (when-not (:group-open state) (js/console.warn "Action ouside cycle?"))
                        (apply logc! state "  reduced action" action "from" (concat (show-comp component) ["into" (show-ret returned)])))
                      
                      trace/commit-trace
                      (fn [state global-app-state local-state-map]
                        (when-not (:group-open state) (js/console.warn "Commit ouside cycle?"))
                        (-> state
                            (logc! "commit global app-state" (global global-app-state) "and local states" (map-keys show-comp-short local-state-map))
                            (end-log-cycle))
                        
                        )}))

(defn shutdown-console-tracer! []
  (trace/remove-tracer! ::console))
