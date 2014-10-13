(ns ^{:author "Michael Sperber"
      :doc "Reacl core functionality."}
  reacl.core)

(defn- ^:no-doc jsmap
  "Convert a Clojure map to a JavaScript hashmap."
  [clmp]
  (loop [clmp clmp
         args []]
    (if (not (seq clmp))
      (apply js-obj args)
      (recur (nnext clmp)
             (concat args [(name (first clmp)) (second clmp)])))))

(defn ^:no-doc set-local-state!
  "Set Reacl local state of a component.

   For internal use."
  [this local-state]
  (.setState this #js {:reacl_local_state local-state}))

(defn- ^:no-doc state-extract-local-state
  "Extract local state from the state of a Reacl component.

   For internal use."
  [state]
  ; otherweise Closure :advanced screws it up
  (aget state "reacl_local_state"))

(defn ^:no-doc extract-local-state
  "Extract local state from a Reacl component.

   For internal use."
  [this]
  (state-extract-local-state (.-state this)))

(defn ^:no-doc extract-toplevel
  "Extract toplevel component of a Reacl component.

   For internal use."
  [this]
  ((aget (.-props this) "reacl_get_toplevel")))

(defn ^:no-doc extract-app-state
  "Extract applications state from a Reacl component.

   For internal use."
  [this]
  @(aget (.-props this) "reacl_app_state_atom"))

(defn- ^:no-doc props-extract-args
  "Get the component args for a component from its props.

   For internal use."
  [props]
  (aget props "reacl_args"))

(defn ^:no-doc extract-args
  "Get the component args for a component.

   For internal use."
  [this]
  (props-extract-args (.-props this)))

; The locals are difficult: We want them to be like props on the one
; hand, but they should have new bindings when the app state changes.

; The latter means we cannot generally put them in the component
; state, as the component state survives app-state changes.

; So we need to put them in the props and make them mutable, by
; sticking an atom in the props.

; For updating the locals, we use a two-pronged strategy:

; - For non-top-level, non-embedded components there's no problem, as
;   the components get re-instantiated as a matter of the usual routine.
; - For top-level and embedded components, we reset the atom to update
;   the locals.

(defn ^:no-doc extract-locals
  "Get the local bindings for a component.

   For internal use."
  [this]
  @(aget (.-props this) "reacl_locals"))

(defn ^:no-doc compute-locals
  "Compute the locals.
  For internal use."
  [clazz app-state args]
  (apply (aget clazz "__computeLocals") app-state args))

(defn ^:no-doc set-app-state!
  "Set the application state associated with a Reacl component.

   For internal use."
  [this app-state]
  (let [toplevel (extract-toplevel this)
        toplevel-props (.-props toplevel)
        app-state-atom (aget toplevel-props "reacl_app_state_atom")]
    (reset! app-state-atom app-state)
    (when (identical? this toplevel)
      (reset! (aget toplevel-props "reacl_locals") 
              (compute-locals (.-constructor this) app-state (extract-args this))))
    (.setState toplevel #js {:reacl_app_state app-state})
    (if-let [callback (aget toplevel-props "reacl_app_state_callback")]
      (callback app-state))))

(defprotocol ^:no-doc IReaclClass
  (-instantiate [clazz component args])
  (-instantiate-toplevel [clazz app-state args])
  (-instantiate-embedded [clazz component app-state app-state-callback args])
  (-react-class [clazz]))

(defn react-class
  "Extract the React class from a Reacl class."
  [clazz]
  (-react-class clazz))

(defn ^:no-doc make-local-state
  "Make a React state containing Reacl local variables and local state.

   For internal use."
  [this local-state]
  #js {:reacl_local_state local-state})

(declare toplevel? embedded?)

(defn ^:no-doc should-component-update?
  "Implements [[shouldComponentUpdate]] for React.

  For internal use only."
  [this next-props next-state]
  (let [state (.-state this)]
    (or (and (not (toplevel? this))
             (not (embedded? this)))
        (and (not (.hasOwnProperty state "reacl_app_state"))
             (.hasOwnProperty next-state "reacl_app_state")) ; it was not set before, now it's set
        (not= (aget state "reacl_app_state") (aget next-state "reacl_app_state"))
        (not= (extract-args this)
              (props-extract-args next-props))
        (not= (extract-local-state this)
              (state-extract-local-state next-state)))))

(defn ^:no-doc instantiate-internal
  "Internal function to instantiate a Reacl component.

  - `clazz` is the React class (not the Reacl class ...).
  - `parent` is the component from which the Reacl component is instantiated.
  - `args` are the arguments to the component.
  - `locals` are the local variables of the components."
  [clazz parent args locals]
  (let [props (.-props parent)]
    (clazz #js {:reacl_get_toplevel (aget props "reacl_get_toplevel")
                :reacl_app_state_atom (aget props "reacl_app_state_atom")
                :reacl_embedded_ref_count (atom nil)
                :reacl_args args
                :reacl_locals (atom locals)})))

(defn ^:no-doc instantiate-toplevel-internal
  "Internal function to instantiate a Reacl component.

  - `clazz` is the React class (not the Reacl class ...).
  - `app-state` is the  application state.
  - `args` are the arguments to the component.
  - `locals` are the local variables of the components."
  [clazz app-state args locals]
  (let [toplevel-atom (atom nil)] ;; NB: set by render-component
    (clazz #js {:reacl_toplevel_atom toplevel-atom
                :reacl_get_toplevel (fn [] @toplevel-atom)
                :reacl_embedded_ref_count (atom nil)
                :reacl_app_state_atom (atom app-state)
                :reacl_args args
                :reacl_locals (atom locals)})))

(defn- ^:no-doc toplevel?
  "Is this component toplevel?"
  [this]
  (aget (.-props this) "reacl_toplevel_atom"))

(defn ^:no-doc instantiate-embedded-internal
  "Internal function to instantiate an embedded Reacl component.

  `clazz' is the React class (not the Reacl class ...).
  `parent' is the component from which the Reacl component is instantiated.
  `app-state' is the  application state.
  `app-state-callback' is a function called with a new app state on changes.
  `args' are the arguments to the component.
  `locals' are the local variables of the components."
  [clazz parent app-state app-state-callback args locals]
  (let [toplevel-atom (atom nil)
        ;; React will replace whatever is returned by (clazz ...) on mounting.
        ;; This is the only way to get at the mounted component, it seems.

        ;; That's not all, though: The ref-count atom will sometimes
        ;; be from the old object from the previous render cycle; it's
        ;; initialized in the render method, off the class macro.
        ref-count (aget (.-props parent) "reacl_embedded_ref_count")
        ref (str "__reacl_embedded__" @ref-count)]
    (swap! ref-count inc)
    (clazz #js {:reacl_get_toplevel (fn [] (aget (.-refs parent) ref))
                :reacl_app_state_atom (atom app-state)
                :reacl_embedded_ref_count (atom nil)
                :reacl_args args
                :reacl_locals (atom locals)
                :reacl_app_state_callback app-state-callback
                :ref ref})))

(defn- ^:no-doc embedded?
  "Check if a Reacl component is embedded."
  [comp]
  (some? (aget (.-props comp ) "reacl_app_state_callback")))

(defn instantiate-toplevel
  "Instantiate a Reacl component at the top level.

  - `clazz` is the Reacl class.
  - `app-state` is the application state
  - `args` are the arguments to the component."
  [clazz app-state & args]
  (-instantiate-toplevel clazz app-state args))

(defn render-component
  "Instantiate and render a component into the DOM.

  - `element` is the DOM element
  - `clazz` is the Reacl clazz
  - `app-state` is the application state
  - `args` are the arguments of the component."
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

  - `clazz` is the Reacl class.
  - `parent` is the component from which the Reacl component is instantiated.
  - `app-state` is the application state.
  - `app-state-callback` is a function called with a new app state on changes.
  - `args` are the arguments to the component."
  [clazz parent app-state app-state-callback & args]
  (-instantiate-embedded clazz parent app-state app-state-callback args))

(defrecord ^{:doc "Type of a unique value to distinguish nil from no change of state.
            For internal use in [[reacl.core/return]] and [[reacl.core/set-state!]]."
             :private true
             :no-doc true} 
    KeepState
  [])

(def ^{:doc "Single value of type KeepState.
             Can be used in reacl.core/return to indicate no (application or local)
             state change, which is different from setting it to nil."
       :no-doc true}
  keep-state (KeepState.))

(defrecord ^{:doc "Composite object for app state and local state.
            For internal use in reacl.core/return."
             :private true
             :no-doc true}
    State
    [app-state local-state])

(defn return
  "Return state from a Reacl event handler.

   Has two optional keyword arguments:

   - `:app-state` is for a new app state.
   - `:local-state` is for a new component-local state.

   A state can be set to nil. To keep a state unchanged, do not specify
   that option, or specify the value [[reacl.core/keep-state]]."
  [& args]
  (let [args-map (apply hash-map args)
        app-state (if (contains? args-map :app-state)
                    (get args-map :app-state)
                    keep-state)
        local-state (if (contains? args-map :local-state)
                      (get args-map :local-state)
                      keep-state)]
    (State. app-state local-state)))

(defn set-state!
  "Set the app state and component state according to what return returned."
  [component ps]
  (if (not (= keep-state (:local-state ps)))
    (set-local-state! component (:local-state ps)))
  (if (not (= keep-state (:app-state ps)))
    (set-app-state! component (:app-state ps))))

(defn event-handler
  "Create a Reacl event handler from a function.

   `f` must be a function that returns a return value created by
   [[reacl.core/return]], with a new application state and/or component-local
   state.

   [[reacl.core/event-handler]] turns that function `f` into an event
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

(defn- ^:no-doc handle-message
  "Handle a message for a Reacl component.

  For internal use.

  This returns a State object."
  [comp msg]
  ((aget comp "__handleMessage") msg))

(defn ^:no-doc handle-message->state
  "Handle a message for a Reacl component.

  For internal use.

  This returns application state and local state."
  [comp msg]
  (let [ps (handle-message comp msg)]
    [(or (:app-state ps) (extract-app-state comp))
     (or (:local-state ps) (extract-local-state comp))]))

(defn send-message!
  "Send a message to a Reacl component."
  [comp msg]
  (let [st (handle-message comp msg)]
    (set-state! comp st)))

