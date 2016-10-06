(ns ^{:doc "Reacl core functionality."}
  reacl.core
  (:require [cljsjs.react]))

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

(defn- ^:no-doc props-extract-initial-app-state
  "Extract initial applications state from props of a Reacl toplevel component.

   For internal use."
  [props]
  (aget props "reacl_initial_app_state"))

(defn extract-initial-app-state
  "Extract initial applications state from a Reacl component."
  [this]
  (props-extract-initial-app-state (.-props this)))

(defn- ^:no-doc tl-state-extract-app-state
  "Extract latest applications state from state of a toplevel Reacl component.

   For internal use."
  [state]
  (aget state "reacl_app_state"))

(defn- ^:no-doc props-extract-reaction
  [props]
  (aget props "reacl_reaction"))

(defn- ^:no-doc data-extract-app-state
  "Extract the latest applications state from a Reacl component data.

   For internal use."
  [props state]
  ;; before first render, it's the initial-app-state, afterwards
  ;; it's in the state
  (if (and (not (nil? state))
           (.hasOwnProperty state "reacl_app_state"))
    (tl-state-extract-app-state state)
    (props-extract-initial-app-state props)))

(defn- ^:no-doc extract-app-state
  "Extract the latest applications state from a Reacl component.

   For internal use."
  [this]
  (data-extract-app-state (.-props this) (.-state this)))

(def ^:private extract-current-app-state extract-app-state)

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

(defn ^:no-doc extract-locals
  "Get the local bindings for a component.

   For internal use."
  [this]
  (let [state (.-state this)
        props (.-props this)]
    (if (and (not (nil? state))
             (.hasOwnProperty state "reacl_locals"))
      (aget state "reacl_locals")
      (aget props "reacl_initial_locals"))))
  
(defn ^:no-doc set-locals!
  "Set the local bindings for a component.

  For internal use."
  [this locals]
  (aset (.-state this) "reacl_locals" locals))

(defn ^:no-doc compute-locals
  "Compute the locals.
  For internal use."
  [clazz app-state args]
  ((aget clazz "__computeLocals") app-state args))

(defn ^:no-doc make-props
  "Forge the props for a React element, for testing.

  For internal use."
  [cl app-state args]
  #js {:reacl_args args})

(declare react-class)

(defn ^:no-doc make-state
  "Forge the state for a React element, for testing.

  For internal use."
  [cl app-state local-state args]
  #js {:reacl_locals ((aget (react-class cl) "__computeLocals") app-state args)
       :reacl_app_state app-state
       :reacl_local_state local-state})

(declare invoke-reaction)

(defrecord ^{:doc "Type for a reaction, a restricted representation for callback."
             :no-doc true}
    Reaction 
  [component make-message args]
  Fn
  IFn
  (-invoke [this value]
    (invoke-reaction this value)))

(def no-reaction 
  "Use this as a reaction if you don't want to react to an app-state change."
  nil)

(defn pass-through-reaction
  "Use this if you want to pass the app-state as the message.

  `component` must be the component to send the message to"
  [component]
  (assert (not (nil? component)))
  (Reaction. component identity nil))

(defn reaction
  "A reaction that says how to deal with a new app state in a subcomponent.

  - `component` component to send a message to
  - `make-message` function to apply to the new app state and any additional `args`, to make the message.

  Common specialized reactions are [[no-reaction]] and [[pass-through-reaction]]."
  [component make-message & args]
  (assert (not (nil? component)))
  (assert (not (nil? make-message)))
  (Reaction. component make-message args))

(declare send-message!)

(defn invoke-reaction
  "Invokes the given reaction with the given message value (usually an app-state)."
  [reaction value]
  (send-message! (:component reaction) (apply (:make-message reaction) value (:args reaction))))

; On app state:
;
; It starts with the argument to the instantiation of toplevel or
; embedded components. We put that app-state into
;
;   props.reacl_initial_app_state
;
; In getInitialState of those top components, we take that over into
; their state, into
;
;  state.reacl_app_state

(defn ^:no-doc set-app-state!
  "Set the application state associated with a Reacl component.
   May not be called before the first render.

   For internal use."
  [this app-state]
  (let [props (.-props this)]
    (assert (.hasOwnProperty (.-state this) "reacl_app_state"))

    (set-locals! this (compute-locals (.-constructor this) app-state (extract-args this)))
    (.setState this #js {:reacl_app_state app-state})

    (if-let [reaction (props-extract-reaction props)]
      (invoke-reaction reaction app-state))))

(defprotocol ^:no-doc HasReactClass
  (-react-class [clazz]))

(defprotocol ^:no-doc IReaclClass
  (-instantiate-toplevel [clazz app-state args])
  (-instantiate-embedded [clazz app-state reaction args]))

(defprotocol ^:no-doc IReaclView
  (-instantiate [clazz args]))

(defn react-class
  "Extract the React class from a Reacl class."
  [clazz]
  (-react-class clazz))

(defn has-class?
  "Find out if an element was generated from a certain Reacl class."
  [clazz element]
  (identical? (.-type element) (-react-class clazz)))

(defn ^:no-doc make-local-state
  "Make a React state containing Reacl local variables and local state.

   For internal use."
  [this local-state]
  #js {:reacl_local_state local-state})

(defn ^:no-doc default-should-component-update?
  "Implements [[should-component-update?]] for React.

  For internal use only."
  [this app-state local-state locals args next-app-state next-local-state & next-args]
  (or (not= app-state
            next-app-state)
      (not= local-state
            next-local-state)
      (not= args
            next-args)))

(defn ^:no-doc instantiate-toplevel-internal
  "Internal function to instantiate a Reacl component.

  - `clazz` is the React class (not the Reacl class ...).
  - `app-state` is the  application state.
  - `args` are the arguments to the component.
  - `locals` are the local variables of the components."
  [clazz app-state args locals]
  (js/React.createElement clazz 
                          #js {:reacl_initial_app_state app-state
                               :reacl_initial_locals locals
                               :reacl_args args}))

(defn ^:no-doc instantiate-embedded-internal
  "Internal function to instantiate an embedded Reacl component.

  - `clazz` is the React class (not the Reacl class ...).
  - `app-state` is the  application state.
  - `reaction` is a reaction invoked with a new app state on changes; see [[reaction]].
  - `args` are the arguments to the component.
  - `locals` are the local variables of the components."
  [clazz app-state reaction args locals]
  (js/React.createElement clazz
                          #js {:reacl_initial_app_state app-state
                               :reacl_initial_locals locals
                               :reacl_args args
                               :reacl_reaction reaction}))

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
  - `clazz` is the Reacl clazz or view
  - `args` are the arguments of the component,
    which must start with the application state if `clazz` is a class."
  [element clazz & args]
  (js/React.render
   ;; TODO remove this hack, after introducing an initial-app-state
   ;; clause (or drop support for classes here)
   (if (satisfies? IReaclView clazz)
     (-instantiate clazz args)
     (apply instantiate-toplevel clazz args))
   element))

(defn embed
  "Embed a Reacl component.

  This creates a component with its own application state that can be
  embedded in a surrounding application.  Any changes to the app state 
  lead to the callback being invoked.

  - `clazz` is the Reacl class.
  - `app-state` is the application state.
  - `reaction` is a reaction invoked with a new app state on changes; see [[reaction]].
  - `args` are the arguments to the component."
  [clazz app-state reaction & args]
  (-instantiate-embedded clazz app-state reaction args))

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
  ([st0 v0]
   (State. (if (= st0 :app-state)
             v0
             keep-state)
           (if (= st0 :local-state)
             v0
             keep-state)))
  ([st0 v0 st1 v1]
   (State. (if (= st1 :app-state)
             v1
             (if (= st0 :app-state)
               v0
               keep-state))
           (if (= st1 :local-state)
             v1
             (if (= st0 :local-state)
               v0
               keep-state)))))

(defn set-state!
  "Set the app state and component state according to what return returned."
  [component ^State ps]
  (let [v (:local-state ps)]
    (when (not= keep-state v)
      (set-local-state! component v)))
  (let [v (:app-state ps)]
    (when (not= keep-state v)
      (set-app-state! component v))))

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
    [(if (not= keep-state (:app-state ps)) (:app-state ps) (extract-app-state comp))
     (if (not= keep-state (:local-state ps)) (:local-state ps) (extract-local-state comp))]))

(defn send-message!
  "Send a message to a Reacl component.

  Returns the `State` object returned by the message handler."
  [comp msg]
  (let [st (handle-message comp msg)]
    (set-state! comp st)
    st))

(defn opt-set-state! [component v]
  (when v
    (assert (instance? State v))
    (set-state! component v)))

;; Attention: duplicate definition for macro in core.clj
(def ^:private specials #{:render :initial-state :handle-message
                          :component-will-mount :component-did-mount
                          :component-will-receive-args
                          :should-component-update?
                          :component-will-update :component-did-update
                          :component-will-unmount
                          :mixins})

(defn- ^:no-doc is-special-fn? [[n f]]
  (not (nil? (specials n))))


;; FIXME: just pass all the lifecycle etc. as separate arguments

(defn create-class [display-name mixins compute-locals fns]
  ;; split special functions and miscs
  (let [{specials true misc false} (group-by is-special-fn? fns)
        {:keys [render
                initial-state
                handle-message
                component-will-mount
                component-did-mount
                component-will-receive-args
                should-component-update?
                component-will-update
                component-did-update
                component-will-unmount]
         :or {:should-component-update? default-should-component-update?}
         }
        (into {} specials)
        ]
    ;; Note that it's args is not & args down there
    (let [ ;; base: prepend this app-state and args
          base
          (fn [f & flags]
            (when f
              (let [get-app-state (if ((apply hash-set flags) :force-current-app-state)
                                    ;; this is uptodate at any time
                                    extract-current-app-state
                                    ;; during the livecylcle/updates this might be older
                                    extract-app-state)]
                (fn [& react-args]
                  (this-as this
                           (apply f this (get-app-state this) (extract-args this)
                                  react-args))))))
          ;; with locals, but without local-state
          nlocal
          (fn [f & flags]
            (apply
             base (when f
                    (fn [this app-state args & react-args]
                      (apply f this app-state (extract-locals this) args
                             react-args)))
             flags))
          ;; also with local-state (most reacl methods)
          std
          (fn [f & flags]
            (apply
             nlocal (when f
                      (fn [this app-state locals args & react-args]
                        (apply f this app-state (extract-local-state this)
                               locals args
                               react-args)))
             flags))
          std-current
          (fn [f]
            (std f :force-current-app-state))
          std+state
          (fn [f & flags]
            (apply std
                   (when f
                     (fn [this & args]
                       (opt-set-state! this (apply f this args))))
                   flags))
          ;; and one arg with next/prev-props
          with-props-and-args
          (fn [f]
            (std (when f
                   (fn [this app-state local-state locals args other-props & more-react-args]
                     (apply f this app-state local-state locals args
                            (props-extract-args other-props)
                            other-props
                            more-react-args)))))
          with-args
          (fn [f]
            (with-props-and-args
              (when f
                (fn [this app-state local-state locals args other-args other-props]
                  ;; 'roll out' args
                  (apply f this app-state local-state locals args other-args)))))
          ;; and one arg with next/prev-state
          with-state-and-args
          (fn [f]
            (with-props-and-args
              (when f
                (fn [this app-state local-state locals args other-args other-props other-state]
                  (apply f this app-state local-state locals args
                         (data-extract-app-state other-props other-state)
                         (state-extract-local-state other-state)
                         other-args)))))

          react-method-map
          (merge
           (into {} (map (fn [[n f]] [n (std f)]) misc))
           {"displayName"
            display-name

            "getInitialState"
            (nlocal (fn [this app-state locals args]
                      (let [local-state (when initial-state
                                          (initial-state this app-state locals args))
                            state (make-local-state this local-state)]
                        ;; app-state will be the initial_app_state here
                        (aset state "reacl_app_state" app-state)
                        state)))

            "mixins"
            (when mixins
              (object-array mixins))

            "render"
            (std render)

            ;; Note handle-message must always see the most recent
            ;; app-state, even if the component was not updated after
            ;; a change to it.
            "__handleMessage"
            (std-current handle-message)

            "componentWillMount"
            (std+state component-will-mount)

            "componentDidMount"
            (std component-did-mount)

            "componentWillReceiveProps"
            (let [f (with-args component-will-receive-args)]
              ;; this might also be called when the args have not
              ;; changed (prevent that?)
              (fn [next-props]
                (this-as this
                         ;; embedded/toplevel has been
                         ;; 'reinstantiated', so take over new
                         ;; initial app-state
                         (.setState this #js {:reacl_app_state (props-extract-initial-app-state next-props)})
                         (when f
                           ;; must preserve 'this' here via .call!
                           (opt-set-state! this (.call f this next-props))))))

            "shouldComponentUpdate"
            (let [f (with-state-and-args should-component-update?)]
              (fn [next-props next-state]
                ;; have to check the reaction for embedded
                ;; components for changes - though this will mostly
                ;; force an update!
                (this-as this
                         (or (and (let [callback-now (props-extract-reaction (.-props this))
                                        callback-next (props-extract-reaction next-props)]
                                    (and (or callback-now callback-next)
                                         (not= callback-now callback-next))))
                             (if f
                               (.call f this next-props next-state)
                               true)))))

            "componentWillUpdate"
            (with-state-and-args component-will-update)

            "componentDidUpdate"
            (with-state-and-args component-did-update) ;; here it's
            ;; the previous state&args

            "componentWillUnmount"
            (std component-will-unmount)

            "statics"
            (js-obj "__computeLocals"
                    compute-locals ;; [app-state & args]
                    )
            }
           )

          react-class (js/React.createClass
                       (apply js-obj (apply concat
                                            (filter #(not (nil? (second %)))
                                                    react-method-map))))
          ]
      (reify
        IFn
        (-invoke [this app-state reaction & args]
          (-instantiate-embedded this app-state reaction args))
        IReaclClass
        (-instantiate-toplevel [this app-state args]
          (instantiate-toplevel-internal react-class app-state args
                                         (compute-locals app-state args)))
        (-instantiate-embedded [this app-state reaction args]
          (instantiate-embedded-internal react-class app-state
                                         reaction args
                                         (compute-locals app-state args)))
        HasReactClass
        (-react-class [this] react-class)
        ))))

(def ^:private mixin-methods #{:component-will-mount :component-did-mount
                               :component-will-update :component-did-update
                               :component-will-receive-args
                               :component-will-unmount})

(defn ^:no-doc create-mixin
  [fn-map]
  (let [entry (fn [tag name post]
                (if-let [f (get fn-map tag)]
                  [name
                   (fn [arg-fns]
                     (fn []
                       (this-as this
                                (post
                                 this
                                 (f this (extract-app-state this) (extract-local-state this)
                                    (map (fn [arg-fn] (arg-fn this)) arg-fns))))))]
                  nil))
        app+local-entry (fn [tag name]
                          (if-let [f (get fn-map tag)]
                            [name
                             (fn [arg-fns]
                               (fn [other-props other-state]
                                 (this-as this
                                          (f this (extract-app-state this) (extract-local-state this)
                                             (map (fn [arg-fn] (arg-fn this)) arg-fns)
                                             (data-extract-app-state other-props other-state)
                                             (state-extract-local-state other-state)))))]
                            nil))
        pass-through (fn [this res] res)
        entries (filter identity
                        (vector 
                         (entry :component-did-mount "componentDidMount" pass-through)
                         (entry :component-will-mount "componentWillMount" (fn [this res] (opt-set-state! this res)))
                         (entry :component-will-unmount "componentWillUnmount" pass-through)
                         (app+local-entry :component-will-update "componentWillUpdate")
                         (app+local-entry :component-did-update "componentDidUpdate")
                         ;; FIXME: :component-will-receive-args 
                         ))]
    (fn [& arg-fns]
      (apply js-obj 
             (apply concat
                    (map (fn [[name fnfn]]
                           [name (fnfn arg-fns)])
                         entries))))))

(defn ^:no-doc class->view
  [clazz]
  (let [react-class (-react-class clazz)
        className (.-displayName react-class)
        error-reaction
        (fn [v]
          (throw (str "Error: " className " tried to return an app-state, but it is a view. Use defclass for programm elements with an app-state.")))]
    (reify
      IFn
      (-invoke [this & args]
        (-instantiate this args))
      IReaclView
      (-instantiate [this args]
        (-instantiate-embedded clazz nil error-reaction args))
      HasReactClass
      (-react-class [this] react-class)
      )))
