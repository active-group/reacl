(ns reacl2.trace.console
  (:require [reacl2.trace.core :as trace]
            [reacl2.core :as reacl]))

(defn- log! [state label & args]
  (apply js/console.log (str "#" (:cycle-id state))
         label
         args)
  state)

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
  ;; show args maybe? call show-comp on args that are components, maybe?
  (apply list (str "#{" (trace/component-class-name comp)) (str "@" (trace/component-id comp)) (concat (show-comp-args (reacl/extract-args comp)) ["}"])))

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

(defn setup-console-tracer! []
  (trace/add-tracer! ::console
                     {:cycle-id 0}
                     {trace/send-message-trace
                      (fn [state component msg]
                        (apply log! state "sending message" msg "to" (show-comp component)))
                      
                      trace/handled-message-trace
                      (fn [state component app-state local-state msg returned]
                        (apply log! state "  handled message:"  msg "to" (concat (show-comp component) ["into" (show-ret returned) "given app-state" app-state "and local-state" local-state])))
                      
                      trace/reduced-action-trace
                      (fn [state component action returned]
                        (apply log! state "  reduced action" action "from" (concat (show-comp component) ["into" (show-ret returned)])))
                      
                      trace/commit-trace
                      (fn [state global-app-state local-state-map]
                        (-> state
                            (log! "commit global app-state" (global global-app-state) "and local states" (map-keys show-comp-short local-state-map))
                            (update :cycle-id inc)))}))

(defn shutdown-console-tracer! []
  (trace/remove-tracer! ::console))
