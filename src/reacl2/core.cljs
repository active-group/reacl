(ns ^{:doc "Reacl core functionality."}
  reacl2.core
  (:require [cljsjs.react]
            [cljsjs.react.dom]
            [cljsjs.create-react-class]
            [cljsjs.prop-types]))

(defn- local-state-state
  "Set Reacl local state in the given state object.

   For internal use."
  [local-state]
  #js {:reacl_local_state local-state})

(defn ^{:no-doc true} set-local-state!
  "Set Reacl local state of a component.

   For internal use."
  [this local-state]
  (.setState this (local-state-state local-state)))

(defn- state-extract-local-state
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

(defn- props-extract-app-state
  "Extract initial applications state from props of a Reacl 2toplevel component.

   For internal use."
  [props]
  (aget props "reacl_app_state"))

(defn- props-extract-reaction
  [props]
  (aget props "reacl_reaction"))

(defn ^:no-doc extract-app-state
  "Extract the latest applications state from a Reacl component.

   For internal use."
  [this]
  (props-extract-app-state (.-props this)))

(defn- app-state-state
  "Set Reacl app state in the given state object.

  For internal use."
  [st app-state]
  (aset st "reacl_app_state" app-state)
  st)

(defn- props-extract-args
  "Get the component args for a component from its props.

   For internal use."
  [props]
  (aget props "reacl_args"))

(defn ^:no-doc extract-args
  "Get the component args for a component.

   For internal use."
  [this]
  (props-extract-args (.-props this)))

(defn- props-extract-refs
  "Get the refs for a component from its props.

   For internal use."
  [props]
  (aget props "reacl_refs"))

(defn- extract-refs
  "Get the refs for a component.

   For internal use."
  [this]
  (props-extract-refs (.-props this)))

(defn- props-extract-locals
  "Get the local bindings for a component.

   For internal use."
  [props]
  (aget props "reacl_locals"))

(defn- extract-locals
  "Get the local bindings for a component.

   For internal use."
  [this]
  (props-extract-locals (.-props this)))

(defn- locals-state
  "Set Reacl locals in the given state object.

  For internal use."
  [st locals]
  (aset st "reacl_locals" locals)
  st)
  
(defn ^:no-doc compute-locals
  "Compute the locals.
  For internal use."
  [clazz app-state args]
  ((aget clazz "__computeLocals") app-state args))

(declare return)

(defn- default-reduce-action [app-state action]
  ;; FIXME: can probably greatly optimize cases where this is used.
  (return :action action))

(declare react-class)

(declare invoke-reaction
         component?)

(defrecord ^{:doc "Type for a reaction, a restricted representation for callback."
             :private true}
    Reaction 
    [component make-message args]
  Fn
  IFn
  (-invoke [this value]
    (invoke-reaction component this value)))

(def ^{:doc "Use this as a reaction if you don't want to react to an app-state change."}
  no-reaction 
  nil)

(defn reaction
  "A reaction that says how to deal with a new app state in a subcomponent.

  - `component` component to send a message to
  - `make-message` function to apply to the new app state and any additional `args`, to make the message.

  Common specialized reactions are [[no-reaction]] and [[pass-through-reaction]]."
  [component make-message & args]
  (when-not (or (component? component) (= :parent component))
    (js/console.log component)
    (throw (ex-info (str "Expected a component, not: " component) {:value component})))
  (when-not (ifn? make-message)
    (throw (ex-info (str "Expected a function, not: " make-message) {:value make-message})))
  (Reaction. component make-message args))

(defn pass-through-reaction
  "Use this if you want to pass the app-state as the message.

  `component` must be the component to send the message to"
  [component]
  (reaction component identity nil))

(declare send-message! component-parent)

(defn ^:no-doc invoke-reaction
  "Invokes the given reaction with the given message value (usually an app-state).

  DEPRECATED."
  [this reaction value]
  (let [target (:component reaction)
        real-target
        (case target
          :parent
          (component-parent this)
          target)]
    (send-message! real-target (apply (:make-message reaction) value (:args reaction)))))

(defn- component-parent
  [comp]
  (or (aget (.-props comp) "reacl_parent")
      (aget (.-context comp) "reacl_parent")))

(defrecord ^:private EmbedAppState
    [app-state ; new app state from child
     embed ; function parent-app-state child-app-state |-> parent-app-state
     ])

(defprotocol ^:no-doc IHasDom
  "General protocol for objects that contain or map to a virtual DOM object."  
  (-get-dom [thing]))

(defn get-dom
  "Get a (real) DOM node from an object that contains one, typically a reference."
  [thing]
  (-get-dom thing))

;; wrapper for React refs
;; the field has to be named "current" to pass for a React ref
(defrecord ^{:private true} Ref [current]
  IHasDom
  (-get-dom [_] current))

;; for internal use by class macro.
(defn- new-ref []
  (->Ref nil))

(defn- new-refs [n]
  (vec (repeatedly n new-ref)))

(defprotocol ^:no-doc IReaclClass
  (-react-class [clazz])
  (-instantiate-toplevel-internal [clazz rst])
  (-has-app-state? [clazz])
  (-make-refs [clazz]))

(defn reacl-class?
  "Is an object a Reacl class?"
  [x]
  (satisfies? IReaclClass x))

(defn react-class
  "Extract the React class from a Reacl class."
  [clazz]
  (-react-class clazz))

(defn has-app-state?
  "Returns if the given class if an app-state class or not."
  [c]
  (-has-app-state? c))

(defn ^:no-doc component?
  "Returns if `v` is a value bound to the 'this' part in a class at runtime."
  [v]
  ;; Note: not public because there any many notions of what a 'component' might - used internally only to give earlier error.
  ;; Note: this is probably true for things returned by instantiating a class: (instance? js/React.Component v)
  ;; But not for the object bound to `this` - the
  (and (instance? js/Object v)
       (.hasOwnProperty v "props")
       (.hasOwnProperty v "state")))

(defn class-name
  "Returns the display name of the given Reacl class"
  [class]
  (assert (reacl-class? class))
  (.-displayName (react-class class)))

(defn has-class?
  "Find out if an element was generated from a certain Reacl class."
  [clazz element]
  (identical? (.-type element) (react-class clazz)))

(defn- make-local-state
  "Make a React state containing Reacl local variables and local state.

   For internal use."
  [this local-state]
  #js {:reacl_local_state local-state})

(defn ^:no-doc default-should-component-update?
  "Implements [[should-component-update?]] for React.

  For internal use only."
  [this app-state local-state locals args refs next-app-state next-local-state & next-args]
  (or (not= app-state
            next-app-state)
      (not= local-state
            next-local-state)
      (not= args
            (vec next-args))))

(defrecord ^{:doc "Optional arguments for instantiation." :private true}
    Options
  [map])

(defn opt?
  "Returns true if v is the result of [[opt]]."
  [v]
  (instance? Options v))

(defn opt-map
  "For a value created by [[opt]], returns a map with the arguments that were passed to it."
  [v]
  (assert (opt? v))
  (:map v))

(defn opt
  "Create options for component instantiation.

  Takes keyword arguments `:reaction`, `:embed-app-state`,
  `:reduce-action`.

  - `:reaction` must be a reaction to an app-state change, typically created via
    [[reaction]], [[no-reaction]], or [[pass-through-reaction]].  -
  - `:embed-app-state` can be specified as an alternative to `:reaction`
    and specifies, that the app state of this component is embedded in the
    parent component's app state.  This must be a function of two arguments, the
    parent app state and this component's app-state.  It must return a new parent
    app state.
  - `:reduce-action` takes arguments `[app-state action]` where `app-state` is the app state
    of the component being instantiated, and `action` is an action.  This
    should call [[return]] to handle the action.  By default, it is a function
    with body `(return :action action)` returning the action unchanged.
    This is called on every action generated by the child component.
    Local-state changes through this function are ignored.
  - `:parent` takes a component as an argument, which is used as the parent for
    the flow of actions and reactions.
  - `:ref` take a ref declared in the `refs` clause of a class, so that the
    instance of this class is bound to the ref at any time. Note that this cannot be specified on the toplevel.
  "
  [& {:as mp}]
  {:pre [(every? (fn [[k _]]
                   (contains? #{:reaction :embed-app-state :reduce-action :parent :ref} k))
                 mp)]}
  (Options. mp))

(defn- deconstruct-opt
  [rst]
  (if (empty? rst)
    [{} rst]
    (let [frst (first rst)]
      (if (opt? frst)
        [(:map frst) (rest rst)]
        [{} rst]))))

(defn- deconstruct-opt+app-state
  [has-app-state? rst]
  (let [[opts rst] (deconstruct-opt rst)
        [app-state args] (if has-app-state?
                           [(first rst) (rest rst)]
                           [nil rst])]
    [opts app-state args]))

(defn- internal-reaction [opts]
  (or (:reaction opts) ; FIXME: what if we have both?
      (if-let [embed-app-state (:embed-app-state opts)]
        (reaction :parent ->EmbedAppState embed-app-state)
        no-reaction)))

(declare keep-state?)

(def ^:no-doc uber-class
  (let [cl
        (js/createReactClass
         #js {:getInitialState (fn []
                                 (this-as this
                                   (aset this "reacl_toplevel_ref" (js/React.createRef))
                                   (let [app-state (aget (.-props this)
                                                         "reacl_app_state")]
                                     #js {:reacl_initial_app_state app-state
                                          :reacl_uber_app_state app-state})))

              :render
              (fn []
                (this-as this
                  (let [props (.-props this)
                        clazz (aget props "reacl_toplevel_class")
                        opts (aget props "reacl_toplevel_opts")
                        args (aget props "reacl_toplevel_args")
                        state (.-state this)
                        app-state (aget state "reacl_uber_app_state")]
                    (js/React.createElement (react-class clazz)
                                            #js {:ref (aget this "reacl_toplevel_ref")
                                                 :reacl_app_state app-state
                                                 :reacl_locals (compute-locals (react-class clazz) app-state args)
                                                 :reacl_args (vec args)
                                                 :reacl_refs (-make-refs clazz)
                                                 :reacl_reaction (internal-reaction opts)
                                                 :reacl_reduce_action (or (:reduce-action opts)
                                                                          default-reduce-action)}))))

              :displayName (str `toplevel)

              :getChildContext (fn []
                                 (this-as this 
                                   #js {:reacl_uber this}))})]
         (aset cl "childContextTypes" #js {:reacl_uber js/PropTypes.object})
         (aset cl "contextTypes" #js {:reacl_uber js/PropTypes.object})
         (aset cl "getDerivedStateFromProps"
               (fn [new-props state]
                 ;; take a new app-state from outside;
                 ;; note this is called before every 'render' call,
                 ;; which is why we have to remember how the class was
                 ;; instantiated.
                 (let [app-state (aget new-props
                                       "reacl_app_state")]
                   (if (not (identical? (aget state "reacl_initial_app_state")
                                        app-state))
                     #js {:reacl_initial_app_state app-state
                          :reacl_uber_app_state app-state}
                     #js {}))))
         cl))

(defn- resolve-uber
  [comp]
  (aget (.-context comp) "reacl_uber"))

(defn- make-uber-component
  [clazz opts args app-state]
  (js/React.createElement uber-class
                          #js {:reacl_toplevel_class clazz
                               :reacl_toplevel_opts opts
                               :reacl_toplevel_args args
                               :reacl_app_state app-state}))

(defn- instantiate-toplevel-internal
  "Internal function to instantiate a Reacl component.

  - `clazz` is the Reacl class
  - `opts` is an object created with [[opt]]
  - `app-state` is the application state
  - `args` is a seq of class arguments"
  {:arglists '([clazz opts app-state & args]
               [clazz app-state & args]
               [clazz opts & args]
               [clazz & args])}
  [clazz has-app-state? rst]
  (when-not (reacl-class? clazz)
    (throw (ex-info (str "Expected a Reacl class as the first argument, but got: " clazz) {:value clazz})))
  (let [[opts app-state args] (deconstruct-opt+app-state has-app-state? rst)]
    (assert (not (and (:reaction opts) (:embed-app-state opts)))) ; FIXME: assertion to catch FIXME below
    (make-uber-component clazz opts args app-state)))

(defn instantiate-toplevel
  "Creates an instance (a React component) of the given class. For testing purposes mostly."
  {:arglists '([clazz opts app-state & args]
               [clazz app-state & args]
               [clazz opts & args]
               [clazz & args])}
  [clazz & rst]
  (instantiate-toplevel-internal clazz (has-app-state? clazz) rst))

(defn- action-reducer
  [this]
  (aget (.-props this) "reacl_reduce_action"))

(defn- instantiate-embedded-internal
  "Internal function to instantiate an embedded Reacl component.

  - `clazz` is the Reacl class
  - `opts` is an object created with [[opt]]
  - `app-state` is the application state
  - `args` is a seq of class arguments"
  {:arglists '([clazz opts app-state & args]
               [clazz app-state & args]
               [clazz opts & args]
               [& args])}
  [clazz has-app-state? rst]
  (let [[opts app-state args] (deconstruct-opt+app-state has-app-state? rst)]
    (assert (not (and (:reaction opts) (:embed-app-state opts)))) ; FIXME: assertion to catch FIXME in internal-reaction
    (js/React.createElement (react-class clazz)
                            #js {:reacl_app_state app-state
                                 :ref (:ref opts)
                                 :reacl_locals (compute-locals (react-class clazz) app-state args)
                                 :reacl_args args
                                 :reacl_refs (-make-refs clazz)
                                 :reacl_reaction (internal-reaction opts)
                                 :reacl_parent (:parent opts)
                                 :reacl_reduce_action (or (:reduce-action opts)
                                                          default-reduce-action)})))

(defn- instantiate-embedded-internal-v1
  [clazz app-state reaction args]
  (js/React.createElement (react-class clazz)
                          #js {:reacl_app_state app-state
                               :reacl_locals (compute-locals (react-class clazz) app-state args)
                               :reacl_args args
                               :reacl_reaction reaction
                               :reacl_reduce_action default-reduce-action}))

(defn render-component
  "Instantiate and render a component into the DOM.

  - `element` is the DOM element
  - `clazz` is the Reacl class
  - `opts` is an object created with [[opt]]
  - `app-state` is the application state
  - `args` is a seq of class arguments"
  {:arglists '([element clazz opts app-state & args]
               [element clazz app-state & args]
               [element clazz opts & args]
               [element clazz & args])}
  [element clazz & rst]
  (js/ReactDOM.render
   (-instantiate-toplevel-internal clazz rst)
   element))

(defrecord ^{:doc "Type of a unique value to distinguish nil from no change of state.
            For internal use in [[reacl.core/return]]."
             :private true} 
    KeepState
  [])

(def ^{:doc "Single value of type KeepState.
             Can be used in reacl.core/return to indicate no (application or local)
             state change, which is different from setting it to nil."}
  keep-state (KeepState.))

(defn keep-state?
  "Check if an object is the keep-state object."
  [x]
  (instance? KeepState x))

(defn- right-state
  "Of two state objects, keep the one on the right, unless it's keep-state."
  [ls rs]
  (if (keep-state? rs)
    ls
    rs))

(defrecord ^{:doc "Object for the effects denoted by [[return]] and compositions of them.
  For internal use."
             :private true
             :no-doc true}
    Returned
    [app-state local-state actions
     ;; queue
     messages])

(def ^:private returned-nil (Returned. keep-state keep-state nil #queue []))

(defn returned-app-state
  "Returns the app-state from the given [[return]] value."
  [ret]
  (:app-state ret))

(defn returned-local-state
  "Returns the local-state from the given [[return]] value."
  [ret]
  (:local-state ret))

(defn returned-actions
  "Returns the actions from the given [[return]] value."
  [ret]
  (:actions ret))

(defn returned-messages
  "Returns the messages from the given [[return]] value."
  [ret]
  (:messages ret))

(defn returned?
  [x]
  (instance? Returned x))

(defn- add-to-returned  ;; Note: private to allow future extension to return. Use merge-returned for composition.
  "Adds the given messages and action to the given [[return]] value, and
  replaces app-state and local-state with the given values, unless
  they are [[keep-state]]."
  [^Returned ret app-state local-state actions messages]
  (Returned. (if (keep-state? app-state)
               (:app-state ret)
               app-state)
             (if (keep-state? local-state)
               (:local-state ret)
               local-state)
             (concat (:actions ret) actions)
             (reduce conj
                     (:messages ret)
                     messages)))

(defn merge-returned
  "Merge the given return values from left to right. Actions and messages are appended, states are replaced unless they are [[keep-state]."
  [& rets]
  (assert (every? returned? rets) "All arguments must be [[return]] values.")
  (reduce (fn [r1 r2]
            (add-to-returned r1
                             (returned-app-state r2)
                             (returned-local-state r2)
                             (returned-actions r2)
                             (returned-messages r2)))
          returned-nil
          rets))

(defn return
  "Return state from a Reacl event handler.

   Has optional keyword arguments:

   - `:app-state` is for a new app state (only once).
   - `:local-state` is for a new component-local state (only once).
   - `:action` is for an action (may be present multiple times)
   - `:message` is for a tuple `[target message]` to be queued (may be present multiple times)

   A state can be set to nil. To keep a state unchanged, do not specify
  that option, or specify the value [[reacl.core/keep-state]]."
  [& args]
  (assert (even? (count args)) "Expected an even number of arguments.")
  (loop [args (seq args)
         app-state keep-state
         local-state keep-state
         actions (transient [])
         messages #queue []] ;; no transient for queues
    (if (empty? args)
      (Returned. app-state local-state
                 (persistent! actions) messages)
      (let [arg (second args)
            nxt (nnext args)]
        (case (first args)
          (:app-state) (do (when-not (= app-state keep-state)
                             (assert false (str "An :app-state argument to reacl/return must be specified only once.")))
                           (recur nxt arg local-state actions messages))
          (:local-state) (do (when-not (= local-state keep-state)
                               (assert false (str "A :local-state argument to reacl/return must be specified only once.")))
                             (recur nxt app-state arg actions messages))
          (:action) (recur nxt app-state local-state (conj! actions arg) messages)
          (:message) (recur nxt app-state local-state actions (conj messages arg))
          (do (assert (= false true) (str "Invalid argument " (first args) " to reacl/return."))
              (recur nxt app-state local-state actions messages)))))))

(defn- action-effect
  [reduce-action app-state action]
  (let [ret (reduce-action app-state action)] ; prep for optimization
    (if (returned? ret)
      ret
      (do (assert (= false true) (str "A 'reacl/return' value was expected, but an action-reducer returned: " (pr-str ret)))
          ;; for backwards-compatibility we pass the action when ret if falsey, and don't if truthy.
          (if ret
            (return)
            (return :action action))))))

(defn ^:no-doc reduce-returned-actions
  "Returns app-state, local-state for this, actions reduced here, to be sent to parent."
  [comp app-state0 ^Returned ret]
  (let [reduce-action (action-reducer comp)]
    (loop [actions (:actions ret)
           app-state (:app-state ret)
           local-state (:local-state ret)
           reduced-actions (transient [])
           messages (or (:messages ret) #queue [])] ; no transients for queues
      (if (empty? actions)
        ;; FIXME: why not Returned?
        [app-state local-state (persistent! reduced-actions) messages]
        (let [action (first actions)
              action-ret (action-effect reduce-action
                                        (right-state app-state0
                                                     app-state)
                                        action)
              action-app-state (:app-state action-ret)
              ;; we ignore local-state here - no point
              new-actions (:actions action-ret)
              new-messages (:messages action-ret)]
          (recur (rest actions)
                 (right-state app-state
                              action-app-state)
                 local-state
                 (reduce conj! reduced-actions new-actions)
                 (reduce conj messages new-messages)))))))

(defn- reaction->pending-message
  [component as ^Reaction reaction]
  (let [target (:component reaction)
        real-target (case target
                      :parent
                      (component-parent component)
                      target)
        msg (apply (:make-message reaction) as (:args reaction))]
    [real-target msg]))

(defn- process-message
  "Process a message for a Reacl component."
  [comp app-state local-state msg]
  (if (instance? EmbedAppState msg)
    (return :app-state ((:embed msg) app-state (:app-state msg)))
    (let [handle-message (aget comp "__handleMessage")]
      (let [args (extract-args comp)
            ret (handle-message comp
                                app-state local-state
                                ;; FIXME: can we avoid recomputing when nothing has changed?
                                (compute-locals (.-constructor comp) app-state args)
                                args (extract-refs comp)
                                msg)]
        (if (returned? ret)
          ret
          (do (assert (= false true) (str "A 'reacl/return' value was expected, but a handle-message returned: " (pr-str ret)))
              returned-nil))))))

(defn ^:no-doc handle-message
  "Handle a message for a Reacl component.

  For internal use.

  This returns a `Returned` object."
  [comp msg]
  (process-message comp (extract-app-state comp) (extract-local-state comp) msg))

;; FIXME: thread all the things properly

(defn- process-reactions
  "Process all reactions that apply to a certain component and propagate upwards."
  [this app-state0 local-state0 actions pending-messages queued-messages]
  (loop [msgs pending-messages
         remaining (transient [])
         app-state keep-state
         local-state keep-state
         actions (transient actions)
         queued-messages (or queued-messages #queue [])]
    (if (empty? msgs)
      
      [(persistent! remaining)
       (Returned. app-state local-state (persistent! actions) queued-messages)]
      
      (let [[target msg] (first msgs)]
        (if (identical? target this)
          (let [ret (process-message this
                                     (right-state app-state0 app-state)
                                     (right-state local-state0 local-state)
                                     msg)]
            (recur (rest msgs)
                   remaining
                   (right-state app-state (:app-state ret))
                   (right-state local-state (:local-state ret))
                   (reduce conj! actions (:actions ret))
                   (reduce conj queued-messages (:messages ret))))
          (recur (rest msgs)
                 (conj! remaining (first msgs))
                 app-state local-state actions queued-messages))))))

(defrecord ^:private UpdateInfo [toplevel-component toplevel-app-state app-state-map local-state-map queued-messages])

(defn- update-state-map
  [state-map comp state]
  (if (keep-state? state)
    state-map
    (assoc state-map comp state)))

(defn- get-local-state
  [comp local-state-map]
  (let [res (get local-state-map comp ::no-state)]
    (case res
      (::no-state) (extract-local-state comp)
      res)))

(defn- get-app-state
  [comp app-state-map]
  (let [res (get app-state-map comp ::no-state)]
    (case res
      (::no-state) (extract-app-state comp)
      res)))

(defn- handle-returned-1
  "Handle a single subcycle in a [[Returned]] object.

  Assumes the actions in `ret` are for comp.

  Returns `UpdateInfo` value."
  [comp ^Returned ret pending-messages app-state-map local-state-map]
  (let [app-state (right-state
                   (get-app-state comp app-state-map)
                   (:app-state ret))
        [app-state local-state actions-for-parent queued-messages] (reduce-returned-actions comp app-state ret)
        app-state-map (update-state-map app-state-map comp app-state)
        local-state-map (update-state-map local-state-map comp local-state)]

    (if-let [parent (component-parent comp)]
       (let [pending-messages
             (if-let [reaction (and (not (keep-state? app-state))
                                    (props-extract-reaction (.-props comp)))]
               (cons (reaction->pending-message comp app-state reaction) pending-messages)
               pending-messages)
             [pending-messages returned] (process-reactions parent
                                                            (get-app-state parent app-state-map)
                                                            (get-local-state parent local-state-map)
                                                            actions-for-parent pending-messages queued-messages)]

         (recur parent returned pending-messages
                (update-state-map app-state-map parent (:app-state returned))
                (update-state-map local-state-map parent (:local-state returned))))
       (UpdateInfo. comp
                    app-state
                    app-state-map local-state-map
                    queued-messages))))

(defn- handle-returned
  "Execute a complete supercycle.

  Returns `UpdateInfo` object."
  [comp ^Returned ret]
  (loop [comp comp
         ^Returned ret ret
         app-state-map {}
         local-state-map {}
         queued-messages #queue []]
    ;; process this Returned, resulting in updated states, and maybe more messages.
    (let [ui (handle-returned-1 comp ret nil app-state-map local-state-map)
          app-state-map (:app-state-map ui)
          local-state-map (:local-state-map ui)
          queued-messages (reduce conj queued-messages (:queued-messages ui))]
      (if (empty? queued-messages)
        ui
        ;; process the next message, resulting in a new 'Returned', then recur.
        (let [[dest msg] (peek queued-messages)
              queued-messages (pop queued-messages)
              ^Returned ret (process-message dest
                                             (get-app-state dest app-state-map)
                                             (get-local-state dest local-state-map)
                                             msg)]
          (recur dest ret
                 app-state-map
                 local-state-map
                 queued-messages))))))

(defn- handle-returned!
  "Handle all effects described and caused by a [[Returned]] object. This is the entry point into a Reacl update cycle.

  Assumes the actions in `ret` are for comp."
  [comp ^Returned ret]
  (let [ui (handle-returned comp ret)
        comp (:toplevel-component ui)
        app-state (:toplevel-app-state ui)
        uber (resolve-uber comp)]
    ;; after handle-returned, all messages must have been processed:
    (assert (empty? (:queued-messages ui)) "Internal invariant violation.")
    (doseq [[comp local-state] (:local-state-map ui)]
      (set-local-state! comp local-state))
    (when-not (keep-state? app-state)
      (.setState uber #js {:reacl_uber_app_state app-state}))))


(defn ^:no-doc resolve-component
  "Resolves a component to its \"true\" Reacl component.

  You need to use this when trying to do things directly to a top-level component."
  [comp]
  (assert (some? (.-props comp)) (str "Expected a Reacl component, but was: " comp))
  (if (aget (.-props comp) "reacl_toplevel_class")
    (.-current (aget comp "reacl_toplevel_ref"))
    comp))

(def ^{:dynamic true :private true} *send-message-forbidden* false)

(defn send-message!
  "Send a message to a Reacl component.

  Returns the `Returned` object returned by the message handler."
  [comp msg]
  (when *send-message-forbidden*
    (assert false "The function send-message! must never be called during an update cycle. Use (reacl/return :message ...) instead."))
  ;; resolve-component is mainly for automated tests that send a message to the top-level component directly
  (binding [*send-message-forbidden* true]
    (let [comp (resolve-component comp)
          ^Returned ret (handle-message comp msg)]
      (handle-returned! comp ret)
      ret)))

(defn send-message-allowed?
  "Returns if calling `send-message!` is allowed at this point; it's
  basically only allowed in event handlers, outside a Reacl update
  cycle."
  []
  (not *send-message-forbidden*))

(defn- opt-handle-returned! [component v]
  (when (some? v)
    (if (returned? v)
      (handle-returned! component v)
      (assert false (str "A 'reacl/return' value was expected, but a method returned: " (pr-str v))))))

;; Attention: duplicate definition for macro in core.clj
(def ^:private specials #{:render :initial-state :handle-message
                          :component-will-mount :component-did-mount
                          :component-will-receive-args
                          :should-component-update?
                          :component-will-update :component-did-update
                          :component-will-unmount
                          :mixins})

(defn- is-special-fn? [[n f]]
  (not (nil? (specials n))))

(defn- optional-ifn? [v]
  (or (nil? v) (ifn? v)))

;; FIXME: just pass all the lifecycle etc. as separate arguments

(defn ^:no-doc create-class [display-name compat-v1? mixins has-app-state? compute-locals num-refs fns]
  ;; split special functions and miscs
  (let [{specials true misc false} (group-by is-special-fn? fns)
        {:keys [render
                initial-state
                handle-message
                component-will-mount
                component-did-mount
                component-will-receive-args
                component-will-update
                component-did-update
                component-will-unmount
                should-component-update?]
         :or {should-component-update? default-should-component-update?}
         }
        (into {} specials)
        ]
    (assert (ifn? render) "All classes must provide a `render` clause.")
    (assert (optional-ifn? handle-message))
    (assert (optional-ifn? component-will-mount))
    (assert (optional-ifn? component-did-mount))
    (assert (optional-ifn? component-will-receive-args))
    (assert (optional-ifn? component-will-update))
    (assert (optional-ifn? component-did-update))
    (assert (optional-ifn? component-will-unmount))
    (assert (optional-ifn? should-component-update?))
    
    ;; Note that it's args is not & args down there
    (let [;; with locals, but without local-state
          std
          (fn [f]
            (and f
                 (fn [& react-args]
                   (this-as this
                     (apply f this (extract-app-state this) (extract-local-state this)
                            (extract-locals this) (extract-args this) (extract-refs this)
                            react-args)))))

          std+state
          (fn [f]
            (and f
                 (fn [& react-args]
                   (this-as this
                     (opt-handle-returned! this (apply f
                                                       this
                                                       (extract-app-state this) (extract-local-state this)
                                                       (extract-locals this) (extract-args this) (extract-refs this)
                                                       react-args))))))

          ;; and one arg with next/prev-state
          with-state-and-args
          (fn [f]
            (and f
                 (fn [other-props other-state]
                   (this-as this
                     (apply f this
                            (extract-app-state this) (extract-local-state this) (extract-locals this) (extract-args this) (extract-refs this)
                            (props-extract-app-state other-props)
                            (state-extract-local-state other-state)
                            (props-extract-args other-props))))))

          make-refs (fn [] (new-refs num-refs))

          react-method-map
          (merge
           (into {} (map (fn [[n f]] [n (std f)]) misc))
           {"displayName"
            display-name

            "getInitialState"
            (fn []
              (this-as this
                (let [app-state (extract-app-state this)
                      locals (extract-locals this)
                      args (extract-args this)]
                  (make-local-state this (if initial-state
                                           (initial-state this app-state locals args)
                                           nil)))))

            "mixins"
            (when mixins
              (object-array mixins))

            "render"
            (std render)

            ;; Note handle-message must always see the most recent
            ;; app-state, even if the component was not updated after
            
            ;; a change to it.
            "__handleMessage" handle-message

            "UNSAFE_componentWillMount"
            (std+state component-will-mount)

            "UNSAFE_componentWillReceiveProps"
            (when component-will-receive-args
              (fn [next-props]
                (this-as this
                         (->> (apply component-will-receive-args this
                                     (extract-app-state this) (extract-local-state this) (extract-locals this) (extract-args this) (extract-refs this)
                                     (props-extract-args next-props))
                              (opt-handle-returned! this)))))

            "getChildContext" (fn []
                                (this-as this 
                                  #js {:reacl_parent this}))

            "componentDidMount"
            (std+state component-did-mount)

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
            (when component-did-update
              (let [f (with-state-and-args component-did-update)]
                (fn [prev-props prev-state]
                  (this-as this
                           (opt-handle-returned! this (.call f this prev-props prev-state))))))

            "componentWillUnmount"
            (std+state component-will-unmount)

            "statics"
            #js {"__computeLocals" compute-locals ;; [app-state & args]}
                 }
            }
           )

          react-class (js/createReactClass
                       (apply js-obj (apply concat
                                            (filter #(not (nil? (second %)))
                                                    react-method-map))))
          ]
      (aset react-class "childContextTypes" #js {:reacl_parent js/PropTypes.object
                                                 :reacl_uber js/PropTypes.object})
      (aset react-class "contextTypes" #js {:reacl_parent js/PropTypes.object
                                            :reacl_uber js/PropTypes.object})
      (if compat-v1?
        (reify
          IFn ; only this is different between v1 and v2
          ;; this + 20 regular args, then rest, so a1..a18
          (-invoke [this app-state reaction]
            (instantiate-embedded-internal-v1 this app-state reaction []))
          (-invoke [this app-state reaction a1]
            (instantiate-embedded-internal-v1 this app-state reaction [a1]))
          (-invoke [this app-state reaction a1 a2]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2]))
          (-invoke [this app-state reaction a1 a2 a3]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3]))
          (-invoke [this app-state reaction a1 a2 a3 a4]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6 a7]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7 a8]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6 a7 a8]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7 a8 a9]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6 a7 a8 a9]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18]
            (instantiate-embedded-internal-v1 this app-state reaction [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18]))
          (-invoke [this app-state reaction a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 rest]
            (instantiate-embedded-internal-v1 this app-state reaction (concat [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18] rest)))
          IReaclClass
          (-instantiate-toplevel-internal [this rst]
            (instantiate-toplevel-internal this has-app-state? rst))
          (-has-app-state? [this] has-app-state?)
          (-make-refs [this]
            (make-refs))
          (-react-class [this] react-class))
        (reify
          IFn
          (-invoke [this]
            (instantiate-embedded-internal this has-app-state? []))
          (-invoke [this a1]
            (instantiate-embedded-internal this has-app-state? [a1]))
          (-invoke [this a1 a2]
            (instantiate-embedded-internal this has-app-state? [a1 a2]))
          (-invoke [this a1 a2 a3]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3]))
          (-invoke [this a1 a2 a3 a4]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4]))
          (-invoke [this a1 a2 a3 a4 a5]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5]))
          (-invoke [this a1 a2 a3 a4 a5 a6]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8 a9]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20]
            (instantiate-embedded-internal this has-app-state? [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20]))
          (-invoke [this a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20 rest]
            (instantiate-embedded-internal this has-app-state? (concat [a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11 a12 a13 a14 a15 a16 a17 a18 a19 a20] rest)))
          IReaclClass
          (-instantiate-toplevel-internal [this rst]
            (instantiate-toplevel-internal this has-app-state? rst))
          (-has-app-state? [this] has-app-state?)
          (-make-refs [this]
            (make-refs))
          (-react-class [this] react-class))))))

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
                                             (props-extract-app-state other-props)
                                             (state-extract-local-state other-state)))))]
                            nil))
        pass-through (fn [this res] res)
        entries (filter identity
                        (vector 
                         (entry :component-did-mount "componentDidMount" (fn [this res] (opt-handle-returned! this res)))
                         (entry :component-will-mount "componentWillMount" (fn [this res] (opt-handle-returned! this res)))
                         (entry :component-will-unmount "componentWillUnmount" (fn [this res] (opt-handle-returned! this res)))
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

