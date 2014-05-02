(ns reacl.core)

(defn- jsmap
  "Convert a Clojure map to a JavaScript hashmap."
  [clmp]
  (loop [clmp clmp
         args []]
    (if (not (seq clmp))
      (apply js-obj args)
      (recur (nnext clmp)
             (concat args [(name (first clmp)) (second clmp)])))))

(defn- make-local-state
  "Make a React state hashmap containing Reacl local state s."
  [s]
  #js {:reacl_local_state s})

(defn- instantiate
  "Internal function to instantiate a Reacl component.

   `clazz' is the Reacl class.
   `toplevel' is function that yields the toplevel component.
   `app-state' is the app-state.
   `args` are the arguments to the component."
  [clazz toplevel app-state & args]
  (clazz #js {:reacl_top_level toplevel :reacl_app_state app-state :reacl_args args}))

(defn instantiate-toplevel
  "Instantiate a Reacl component at the top level.

   `clazz' is the Reacl class.
   `app-state' is the app-state.
   `args` are the arguments to the component."
  [clazz app-state & args]
  (let [placeholder (atom nil)
        component (apply instantiate clazz (fn [] @placeholder) app-state args)]
    (reset! placeholder component)
    component))

(defrecord State
    ^{:doc "Composite objet for app state and local state.
            For internal use in reacl.core/return."
      :private true}
    [app-state local-state])

(defn return
  "Return state from a Reacl event handler.

   Has two optional keyword arguments:

   `app-state` is for a new app state.
   `local-state` is for a new component-local state."
  [&{:keys [app-state local-state]}]
  (State. app-state local-state))

(def ^{:dynamic true
       :doc "Internal dynamic variable for holding the current component."}
  *component* nil)

(defn event-handler
  "Create a Reacl event handler from a function.

   `f` must be a function that returns a return value created by
   reacl.core/return, with a new application state and/or component-local
   state.

   reacl.core/event-handler turns that function `f` into an event
   handler, that is, a function that sets the new application state
   and/or component-local state.  `f` gets passed any argument the event
   handler gets passed, plus, as its last argument, the component-local
   state.

   This example event handler can be used with onSubmit, for example:

   (reacl/event-handler
    (fn [e _ text]
      (.preventDefault e)
      (reacl/return :app-state (concat todos [{:text text :done? false}])
                    :local-state \"\")))

   Note that `text` is the component-local state."
  [f]
  (fn [& args]
    (let [top (.. *component* -props reacl_top_level)
          local-state (.. *component* -state -reacl_local_state)
          ps (apply f (concat args [local-state]))]
      (if (not (nil? (:local-state ps)))
        (.setState *component* (make-local-state (:local-state ps))))
      (if (not (nil? (:app-state ps)))
        (.setProps top #js {:reacl_app_state (:app-state ps)})))))
