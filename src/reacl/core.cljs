(ns reacl.core)

(defn jsmap
  [clmp]
  (loop [clmp clmp
         args []]
    (if (not (seq clmp))
      (apply js-obj args)
      (recur (nnext clmp)
             (concat args [(name (first clmp)) (second clmp)])))))

(defn make-local-state
  [s]
  #js {:reacl_local_state s})

(defn extract-app-state
  [this]
  (.. this -props -reacl_app_state))

(defn extract-props
  [this]
  (.. this -props -reacl_props))

(defn extract-local-state
  [this]
  (.. this -state -reacl_local_state))

(defn- instantiate
  [clazz toplevel app-state & props]
  (clazz #js {:reacl_top_level toplevel :reacl_app_state app-state :reacl_props props}))

(defn instantiate-toplevel
  [clazz app-state & props]
  (let [placeholder (atom nil)
        component (apply instantiate clazz (fn [] @placeholder) app-state props)]
    (reset! placeholder component)
    component))

(defrecord State
    [app-state local-state])

(defn return
  [&{:keys [app-state local-state]}]
  (State. app-state local-state))

(def ^{:dynamic true} *component* nil)

(defn event-handler
  [f]
  (fn [& args]
    (let [top (.. *component* -props reacl_top_level)
          local-state (extract-local-state *component*)
          ps (apply f (concat args [local-state]))]
      (if (not (nil? (:local-state ps)))
        (.setState *component* (make-local-state (:local-state ps))))
      (if (not (nil? (:app-state ps)))
        (.setProps top #js {:reacl_app_state (:app-state ps)})))))
