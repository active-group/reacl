(ns reacl2.trace.console
  "Tracers to be used with [[reacl2.trace.core/add-tracer!]] that issue log entries via `js/console.log`."
  (:require [reacl2.trace.core :as trace]
            [reacl2.core :as reacl]))

(defn- log! [label & args]
  (apply js/console.log
         label
         args))

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
         "args:" (reacl/extract-args comp)
         ;; but with this, one can grab the comp from the log into a global variable in Chrome.
         ["raw:" comp "}"]))

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

(defn- end-group [state]
  (when (:group-open state)
    (js/console.groupEnd (:cycle-id state)))
  (assoc state :group-open false))

(defn- start-group [state label]
  (let [state (if (:group-open state)
                (end-group state)
                state)]
    (js/console.group label)
    (assoc state :group-open true)))

(defonce ^{:doc "A tracer that logs everything that causes and happens during a Reacl rendering cycle."} console-tracer
  (trace/map-event-cycle-ids
   (trace/tracer
    {:group-open false}
    {trace/send-message-trace
     (fn [state event-id component msg]
       (apply log! (str "event #" event-id) (concat (show-comp component) ["received" msg]) )
       state)

     trace/render-component-trace
     (fn [state event-id class app-state args]
       (let [name (.-displayName (reacl/react-class class))]
         (log! (str "event #" event-id) "rendering component" (if (reacl/has-app-state? class)
                                                                (apply list name app-state args)
                                                                (apply list name args))))
       state)

     trace/returned-trace
     (fn [state event-id cycle-id component returned from]
       ;; Note: one must look at the previous event (another cycle, a message or a rendering)
       ;; to see why this cycle triggered (but hardly no other way...?)
       (let [state (start-group state (str "event #" event-id " cycle #" cycle-id))]
         (apply log! (str "#" cycle-id) (concat (show-comp component) [from "returned" (show-ret returned)]))
         state))
                      
     trace/reduced-action-trace
     (fn [state cycle-id component action returned]
       #_(when-not (:group-open state) (js/console.warn "Action outside cycle?"))
       (apply log! (str "#" cycle-id) (concat (show-comp component) ["reduced" action "into" (show-ret returned)]))
       state)
                      
     trace/commit-trace
     (fn [state cycle-id global-app-state local-state-map]
       #_(when-not (:group-open state) (js/console.warn "Commit ouside cycle?"))
       (log! (str "#" cycle-id) "commit global app-state" (global global-app-state) "and local states" (map-keys show-comp-short local-state-map))
       (end-group state))})))

(defn component-tracer
  "A tracer that logs the parts of Reacl rendering cycles that involve
  all components matching the given predicate `(pred comp &
  args)`. The log entries are prefixed with the given `label`."
  [label pred & args]
  (trace/tracer
   {}
   {trace/send-message-trace
    (fn [state component msg]
      (when (apply pred component args)
        (apply log! label (concat (show-comp component) ["received" msg])))
      state)
    trace/returned-trace
    (fn [state component returned from]
      (when (apply pred component args)
        (apply log! label (concat (show-comp component) [from "returned" (show-ret returned)])))
      state)
    trace/reduced-action-trace
    (fn [state component action returned]
      (when (apply pred component args)
        (apply log! label (concat (show-comp component) ["reduced" action "into" (show-ret returned)])))
      state)
                      
    trace/commit-trace
    (fn [state global-app-state local-state-map]
      (doseq [[cmp st] (filter (fn [[cmp st]]
                                 (apply pred cmp args))
                               local-state-map)]
        (apply log! label (concat (show-comp cmp) ["commit local-state" st])))
      state)}))

(defn class-tracer
  "A tracer that logs the parts of Reacl rendering cycles that involve
  all components instantiated from the given Reacl class. The log
  entries are prefixed with the given `label`."
  [label clazz]
  (component-tracer label (fn [component]
                            (= clazz (reacl/component-class component)))))
