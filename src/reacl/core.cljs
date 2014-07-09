(ns ^{:author "Michael Sperber"
      :doc "Reacl core functionality."}
  reacl.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [put! chan <!]]))

(defn- jsmap
  "Convert a Clojure map to a JavaScript hashmap."
  [clmp]
  (loop [clmp clmp
         args []]
    (if (not (seq clmp))
      (apply js-obj args)
      (recur (nnext clmp)
             (concat args [(name (first clmp)) (second clmp)])))))

(defn set-local-state!
  "Set Reacl local state of a component.

   For internal use."
  [this local-state]
  (.setState this #js {:reacl_local_state local-state}))

(defn extract-local-state
  "Extract local state from a Reacl component.

   For internal use."
  [this]
  ; otherweise Closure :advanced screws it up
  (aget (.-state this) "reacl_local_state"))

(defn extract-toplevel
  "Extract toplevel component of a Reacl component.

   For internal use."
  [this]
  @(aget (.-props this) "reacl_toplevel_atom"))

(defn extract-app-state
  "Extract applications state from a Reacl component.

   For internal use."
  [this]
  @(aget (.-props this) "reacl_app_state_atom"))

(defn set-app-state!
  "Set the application state associated with a Reacl component.

   For internal use."
  [this app-state]
  (let [toplevel (extract-toplevel this)
        app-state-atom (aget (.-props toplevel) "reacl_app_state_atom")]
    (reset! app-state-atom app-state)
    (.setState toplevel #js {:reacl_app_state app-state})))

(defn extract-args
  "Get the component args for a component.

   For internal use."
  [this]
  (aget (.-props this) "reacl_args"))

(defn extract-locals
  "Get the local bindings for a component.

   For internal use."
  [this]
  (aget (.-state this) "reacl_locals"))

(defn extract-channel
  "Get the component channel for a component

  For internal use."
  [this]
  (aget (.-state this) "reacl_channel"))

(defn initialize-channel!
  "Set Reacl channel of a component, returning it.

   For internal use."
  [this]
  (let [c (chan)]
    (.setState this #js {:reacl_channel c})
    c))

(defprotocol IReaclClass
  (-instantiate [clazz component args])
  (-instantiate-toplevel [clazz app-state args])
  (-instantiate-embedded [clazz app-state app-state-callback args]))

(defn make-local-state
  "Make a React state containing Reacl local variables and local state.

   For internal use."
  [this locals local-state]
  (let [props (.-props this)
        toplevel-atom (aget props "reacl_toplevel_atom")
        state (.-state this)]
    #js {:reacl_locals locals
         :reacl_app_state @(aget props "reacl_app_state_atom")
         :reacl_local_state local-state}))

(defn instantiate-internal
  "Internal function to instantiate a Reacl component.

  `clazz' is the React class (not the Reacl class ...).
  `parent' is the component from which the Reacl component is instantiated.
  `args` are the arguments to the component."
  [clazz parent args]
  (let [props (.-props parent)]
    (clazz #js {:reacl_toplevel_atom (aget props "reacl_toplevel_atom")
                :reacl_app_state_atom (aget props "reacl_app_state_atom")
                :reacl_args args})))

(defn instantiate-toplevel-internal
  "Internal function to instantiate a Reacl component.

  `clazz' is the React class (not the Reacl class ...).
  `app-state' is the  application state.
  `args` are the arguments to the component."
  [clazz app-state args]
  (clazz #js {:reacl_toplevel_atom (atom nil) ;; NB: set by render-component
              :reacl_app_state_atom (atom app-state)
              :reacl_args args}))

(defn instantiate-embedded-internal
  "Internal function to instantiate an embedded Reacl component.

  `clazz' is the React class (not the Reacl class ...).
  `app-state' is the  application state.
  `app-state-callback' is a function called with a new app state on changes.
  `args` are the arguments to the component."
  [clazz app-state app-state-callback args]
  (let [toplevel-atom (atom nil)
        component (clazz #js {:reacl_toplevel_atom toplevel-atom
                              :reacl_app_state_atom (atom app-state)
                              :reacl_args args
                              :reacl_app_state_callback app-state-callback})]
    (reset! toplevel-atom component)
    component))

(defn instantiate-toplevel
  "Instantiate a Reacl component at the top level.

  `clazz' is the Reacl class.
  `app-state' is the application state
  `args` are the arguments to the component."

  [clazz app-state & args]
  (-instantiate-toplevel clazz app-state args))

(defn render-component
  "Instantiate and render a component into the DOM.

  - `element' is the DOM element
  - `clazz` is the Reacl clazz
  - `app-state' is the application state
  - `args' are the arguments of the component."
  [element clazz app-state & args]
  (let [instance
        (js/React.renderComponent
         (apply instantiate-toplevel clazz app-state args)
         element)]
    (reset! (aget (.-props instance) "reacl_toplevel_atom") instance)
    instance))

(defn embed
  "Embed a Reacl component.

  This creates a component with its own application state that can be
  embedded in a surrounding application.  Any changes to the app state 
  lead to the callback being invoked.

  `clazz' is the Reacl class.
  `app-state' is the application state.
  `app-state-callback' is a function called with a new app state on changes.
  `args` are the arguments to the component."
  [clazz app-state app-state-callback & args]
  (-instantiate-embedded clazz app-state app-state-callback args))

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

(defn set-state!
  "Set the app state and component state according to what return returned."
  [component ps]
  (if (not (nil? (:local-state ps)))
    (set-local-state! component (:local-state ps)))
  (if (not (nil? (:app-state ps)))
    (set-app-state! component (:app-state ps))))

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
      (set-state! component ps))))

(defn make-message-handler
  "Make a message handler for a Reacl component.

  For internal use.

  This returns a function that takes a function `f'.

  It returns a function with that applies `f' to its own arguments and
  sends the result value as a message to the component."
  [this]
  (let [ch (extract-channel this)]
    (fn [f]
      (fn [& args]
        (let [msg (apply f args)]
          (put! ch msg))))))

(defn send-message!
  "Send a message to a Reacl component."
  [comp msg]
  (put! (extract-channel comp) msg))

(defn- handle-message
  "Handle a message for a Reacl component.

  For internal use.

  This returns a State object."
  [comp msg]
  ((aget comp "__handleMessage") msg))

(defn handle-message->state
  "Handle a message for a Reacl component.

  For internal use.

  This returns application state and local state."
  [comp msg]
  (let [ps (handle-message comp msg)]
    [(or (:app-state ps) (extract-app-state comp))
     (or (:local-state ps) (extract-local-state comp))]))

(defn message-processor
  "Process messages for a Reacl component.

  For internal use.  This implements the `handle-message' clause in
  reacl.core/class.

  This accepts a component and a message handler, and creates an event
  handler suitable for sticking into the DOM."
  [comp c]
  (go
    (loop []
      (let [msg (<! c)]
        (let [st (handle-message comp msg)]
          (set-state! comp st)
          (recur))))))
