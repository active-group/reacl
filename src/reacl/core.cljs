(ns ^{:author "Michael Sperber"
      :doc "Reacl core functionality."}
  reacl.core)

(defn- jsmap
  "Convert a Clojure map to a JavaScript hashmap."
  [clmp]
  (loop [clmp clmp
         args []]
    (if (not (seq clmp))
      (apply js-obj args)
      (recur (nnext clmp)
             (concat args [(name (first clmp)) (second clmp)])))))

(defn make-local-state
  "Make a React state containing Reacl local state.

   For internal use."
  [s]
  #js {:reacl_local_state s})

(defn set-local-state!
  "Make a React state hashmap containing Reacl local state s.

   For internal use."   
  [this local-state]
  (.setState this #js {:reacl_local_state local-state}))

(defn extract-local-state
  "Extract local state from a Reacl component.

   For internal use."
  [this]
  (.. this -state -reacl_local_state))

(defn extract-app-state
  "Extract applications state from a Reacl component.

   For internal use."
  [this]
  (.. this -props -reacl_app_state))

(defn extract-toplevel
  "Extract toplevel component of a Reacl component.

   For internal use."
  [this]
  ((.. this -props -reacl_top_level)))

(defn set-app-state!
  "Set the application state associated with a Reacl component.

   For internal use."
  [this app-state]
  (.setProps (extract-toplevel this)  #js {:reacl_app_state app-state}))

(defn extract-args
  "Get the component args for a component.

   For internal use."
  [this]
  (.. this -props -reacl_args))

(defn instantiate
  "Internal function to instantiate a Reacl component.

   `clazz' is the Reacl class.
   `toplevel' is function that yields the toplevel component.
   `app-state' is the app-state.
   `args` are the arguments to the component."
  [clazz toplevel app-state & args]
  (clazz #js {:reacl_top_level (constantly toplevel) :reacl_app_state app-state :reacl_args args}))

(defn instantiate-toplevel
  "Instantiate a Reacl component at the top level.

   `clazz' is the Reacl class.
   `app-state' is the app-state.
   `args` are the arguments to the component."
  [clazz app-state & args]
  (let [placeholder (atom nil)
        component (clazz #js {:reacl_top_level (fn [] @placeholder) :reacl_app_state app-state :reacl_args args})]
    (reset! placeholder component)
    component))

(defrecord State
    ^{:doc "Composite object for app state and local state.
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
  (fn [component & args]
    (let [local-state (extract-local-state component)
          ps (apply f (concat args [local-state]))]
      (if (not (nil? (:local-state ps)))
        (set-local-state! component (:local-state ps)))
      (if (not (nil? (:app-state ps)))
        (set-app-state! component (:app-state ps))))))
