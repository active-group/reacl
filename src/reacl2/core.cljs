(ns reacl2.core
  "This namespace contains the Reacl core functionality.

Define classes with the macros [[defclass]] or [[class]].

Create return values for a message handler or livecycle method with [[return]] and [[merge-returned]].
The auxiliary functions for return values [[returned-actions]], [[returned-app-state]],
[[returned-local-state]], [[returned-messages]], [[returned?]] will usually only be needed in unit tests.

In event handlers you will usually need to call [[send-message!]].

To instantiate classes that have app-state you need to create bindings with [[bind]],
[[bind-local]], [[use-reaction]] or [[use-app-state]], and sometimes reactions with [[reaction]] or [[pass-through-reaction]].

Sometimes modifications of the created elements are needed via [[keyed]], [[refer]],
[[redirect-actions]], [[reduce-action]], [[action-to-message]] or [[map-action]].

To finally render a class to the DOM use [[render-component]] and [[handle-toplevel-action]].
"
  (:require [react :as react]
            [react-dom :as react-dom]
            [create-react-class :as createReactClass] ;; Note: function import, not a namespace.
            [prop-types :as ptypes]
            [reacl2.trace.core :as trace])
  (:refer-clojure :exclude [refer]))

(defn- warning [& args]
  (if (and js/console js/console.warn)
    (apply js/console.warn args)
    (apply println args)))

(defn- local-state-state
  "Set Reacl local state in the given state object.

   For internal use."
  [local-state]
  #js {:reacl_local_state local-state})

(defn- set-local-state!
  "Set Reacl local state of a component.

   For internal use."
  [this local-state]
  (.setState this (local-state-state local-state)))

(defn- state-extract-local-state
  "Extract local state from the state of a Reacl component.

   For internal use."
  [state]
  (when (some? state)
    (aget state "reacl_local_state")))

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

(defrecord ^:private EmbedState
    [app-state ; new app state from child
     embed ; function parent-app-state parent-local-state child-app-state |-> [parent-app-state parent-local-state]  (one or both can be keep-state)
     args ;; extra args for embed
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
  (-validate! [clazz app-state args])
  (-make-refs [clazz])
  (-compute-locals [clazz app-state args]))

(defn reacl-class?
  "Is an object a Reacl class?"
  [x]
  (satisfies? IReaclClass x))

(defn react-class
  "Extract the React class from a Reacl class."
  [clazz]
  (-react-class clazz))

(defn has-app-state?
  "Returns if the given class has an app-state class or not."
  [class]
  (-has-app-state? class))

(defn ^:no-doc component?
  "Returns if `v` is a value bound to the 'this' part in a class at runtime."
  [v]
  ;; Note: not public because there any many notions of what a 'component' might - used internally only to give earlier error.
  ;; Note: this is probably true for things returned by instantiating a class: (instance? react/Component v)
  ;; But not for the object bound to `this` - the
  (and (instance? js/Object v)
       (.hasOwnProperty v "props")
       (.hasOwnProperty v "state")))

(defn ^:no-doc component-class
  "Returns the class the given reacl component (a 'this') was created from."
  [v]
  (assert (component? v))
  (aget (.-props v) "reacl_class"))

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

(defn binding?
  "Returns true if v is a binding."
  [v]
  (opt? v))

(defn ^:no-doc opt-map
  "For a value created by [[opt]], returns a map with the arguments that were passed to it."
  [v]
  (assert (opt? v))
  (:map v))

(declare keep-state? keep-state)

(defn- embed-app-state-f [app-state local-state child-app-state f]
  [(f app-state child-app-state) keep-state])

(defn- embed-local-f [app-state local-state child-app-state f]
  [keep-state (f local-state child-app-state)])

;; TODO: use active-clojure lens utils?

(defn- id-lens
  ([v] v)
  ([_ v] v))

(defrecord ^:private LensComp [l1 l2]
  IFn
  (-invoke [_ d]
    (l2 (l1 d)))
  (-invoke [_ d v]
    (l1 d (l2 (l1 d) v))))

(defn- lens-comp [l1 l2]
  (cond
    (= l1 id-lens) l2
    (= l2 id-lens) l1
    :else
    (LensComp. l1 l2)))

(defrecord ^:private KeywordLens [k]
  IFn
  (-invoke [this v] (get v k))
  (-invoke [this v vv] (assoc v k vv)))

(defn- lift-lens [v]
  ;; Note: maybe we should use active.clojure/lens, but we don't have a dependency on that yet.
  (cond
    (keyword? v) (KeywordLens. v)
    :else v))

(defn- geti [k m] (get m k))

(defn- internal-opt
  "Translates the 'user facing api' of using [[opt]] into a simplified form."
  [opts]
  (assert (<= (count (select-keys opts [:reaction :embed-app-state :embed-local-state :bind :bind-local])) 1)
          "Only one of :reaction, :embed-app-state, :embed-local-state :bind and :bind-local may be specified.")
  (-> (condp geti opts
        :reaction :>>
        (fn [r]
          (do (assert (or (nil? r) (instance? Reaction r)) (str "Invalid reaction: " (pr-str r)))
              opts))
        :embed-app-state :>>
        (fn [f]
          (assoc opts
                 :reaction (reaction :parent ->EmbedState embed-app-state-f [(lift-lens f)])))
        :embed-local-state :>>
        (fn [f]
          (assoc opts
                 :reaction (reaction :parent ->EmbedState embed-local-f [(lift-lens f)])))
        ;; Note that extract-app-state and extract-local-state only work as expected during rendering (we could try to verify that?)
        ;; If someone should instantiate components during handle-message, he shall be doomed (or has to adjust :app-state manually).
        :bind :>>
        (fn [[comp lens]]
          (when-not (-has-app-state? (component-class comp))
            (throw (ex-info (str "Cannot bind to the app-state of the given component, as it does not have an app-state. Maybe use bind-local instead.") {:class (component-class comp)})))
          (assert (not (contains? opts :app-state)) "Do not use :bind together with an :app-state.")
          (let [l (lift-lens (or lens id-lens))]
            (assoc opts
                   :app-state (l (extract-app-state comp))
                   :reaction (reaction comp ->EmbedState embed-app-state-f [l]))))
        :bind-local :>>
        (fn [[comp lens]]
          (assert (not (contains? opts :app-state)) "Do not use :bind-local together with an :app-state.")
          (let [l (lift-lens (or lens id-lens))]
            (assoc opts
                   :app-state (l (extract-local-state comp))
                   :reaction (reaction comp ->EmbedState embed-local-f [l]))))
        ;; else do nothing
        opts)
      (dissoc :embed-app-state :embed-local-state :bind :bind-local)))

(defn opt
  "Create options for component instantiation.

  Takes the following keyword arguments:

  - `:reaction` must be a reaction to an app-state change, typically created via
    [[reaction]], [[no-reaction]], or [[pass-through-reaction]].  -
  - `:embed-app-state` can be specified as an alternative to `:reaction`
    and specifies, that the app state of this component is embedded in the
    parent component's app state.  This must be a function of two arguments, the
    parent app state and this component's app-state.  It must return a new parent
    app state.
  - `:embed-local-state` is similar to `:embed-app-state`, but changes of the
    app state are instead embedded into the local state of the parent component.
  - `:app-state` can be specified as an alternative to passing the app state as the
    next parameter of a class instantiation.
  - `:bind [parent lens]` specifies, that `lens` applied to the current app state of
    `parent` is used as the app state of the component, and that changes to the app state
    of the component are integrated into the parent's app state via
    `(lens parent-app-state new-child-app-state)`. `lens` can also be nil or a keyword.
  - `:bind-local [parent lens]` is similar to `:bind`, but the app state of the
    component is instead bound to the local state of the parent.
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

  Only one of `:reaction`, `:embed-app-state`, `:embed-local-state`,
  `:bind` and `:bind-local` should be specified, and when using `:bind`
  or `:bind-local` then do not specify an `:app-state` either.
  "
  [& {:as mp}]
  {:pre [(every? (fn [[k _]]
                   (contains? #{:reaction :embed-app-state :embed-local-state :bind :bind-local :app-state :reduce-action :parent :ref} k))
                 mp)]}
  (Options. (internal-opt mp)))


(defn use-app-state
  "Returns a binding that uses the given value as the app-state of a
  child component, ignoring all updates the child component returns."
  [app-state]
  (opt :app-state app-state
       :reaction no-reaction))

(defn- focus-make-message [inner-app-state prev-make-message lens original-app-state & args]
  (apply prev-make-message (lens original-app-state inner-app-state) args))

(defn- focused-app-state [prev lens]
  (lens prev))

(defn focus
  "Returns a binding that appends the given `lens` to the given
  `binding`, so that a child component only uses a smaller part of the
  bound value as its app-state."
  [binding lens]
  (assert (opt? binding))
  (if (= lens id-lens)
    binding
    (let [lens (lift-lens lens)]
      (update binding :map
              (fn [mp]
                (cond-> mp
                  (contains? mp :app-state)
                  (update :app-state focused-app-state lens)

                  (instance? Reaction (:reaction mp))
                  (update :reaction
                          (fn [prev-reaction]
                            (when-not (contains? mp :app-state)
                              (throw (new js/Error "To focus a reaction, it must include the app-state. Use 'bind', 'bind-local', 'use-app-state' or 'use-reaction'.")))
                            (cond
                              ;; simplified special case for embed (current states get passed in process-message anyway):
                              (= ->EmbedState (:make-message prev-reaction))
                              (update prev-reaction :args
                                      (fn [[embed [lens1]]]
                                        [embed [(lens-comp lens1 lens)]]))

                              :else
                              ;; it's a bit dangerous for general reactions (via [[use-reaction]]), because it then captures more of some state
                              ;; than is actually edited by a component. So other changes in the outer part will race with this.
                              (apply reaction
                                     (:component prev-reaction)
                                     focus-make-message
                                     (cons (:make-message prev-reaction) (cons lens (cons (:app-state mp) (:args prev-reaction))))))))

                  ;; Note: no-reaction = nil can be skipped here.
                  (not (or (nil? (:reaction mp))
                           (instance? Reaction (:reaction mp))))
                  ;; something 'manually' implemented? all built-in reactions should be covered.
                  (do (throw (new js/Error "Cannot focus over the reaction in these opts.")))))))))

(defn bind
  "Returns a binding that embeds a child's app-state into the
  app-state of the given `parent` component using the given `lens`,
  which defaults to the identity lens."
  ([parent] (bind parent id-lens))
  ([parent lens]
   (assert (component? parent))
   (opt :bind [parent lens])))

(defn bind-local
  "Returns a binding that embeds a child's app-state into the
  local-state of the given `parent` component using the given `lens`,
  which defaults to the identity lens."
  ([parent] (bind-local parent id-lens))
  ([parent lens]
   (assert (component? parent))
   (opt :bind-local [parent lens])))

(defn use-reaction
  "Returns a binding the uses the given value `app-state` as the
  child's app-state, and triggers the given `reaction` when the child
  wants to update it. See [[reaction]] for creating reactions."
  [app-state reaction]
  (opt :app-state app-state
       :reaction reaction))

(defn reveal
  "Returns the value provided by the given `binding` to be used as the
  app-state of a child component.

  Do not use this outside the evaluation of the `render` clause in
  which the binding was constructed (e.g. in an event handler); that
  may lead to hard to track bugs in your component."
  [binding]
  (assert (opt? binding))
  (let [mp (:map binding)]
    (if (contains? mp :app-state)
      (:app-state mp)
      (throw (new js/Error "Cannot reveal the app-state of this binding; must created via 'use-app-state', 'use-reaction', 'bind' or 'bind-local'.")))))

(defn- map-over-components [elem f]
  (assert (.hasOwnProperty elem "props"))
  (if (some? (aget (.-props elem) "reacl_class")) ;; aka is-reacl-component?
    (f elem)
    ;; for dom elements (or native React components), we map over the children, looking for components there.
    (do
      (let [cs (react/Children.map (.-children (.-props elem))
                                   (fn [e]
                                     ;; child can be a string - keep them as is
                                     (if (.hasOwnProperty e "props")
                                       (map-over-components e f)
                                       e)))]
        (react/cloneElement elem nil cs)))))

(defn set-parent
  "Clones the given element, but replaces the parent component used in
  the reaction and action processing, which is the first component
  higher in the rendering tree (its _rendering parent_) by default. If
  `target` is nil, the default behavior is restored."
  [elem target]
  (map-over-components
   elem
   (fn [comp]
     (react/cloneElement comp #js {:reacl_parent target}))))

;; TODO: currently not properly implementable, I think, as it also affects reactions, which it should not.
#_(defn redirect-actions
  "Clones the given element, but replacing the target of all actions
  flowing out of child components. By default, actions that are not
  _reduced_ or _handled_, are passed to the first component higher in
  the rendering tree (its _rendering parent_). In rare situations you
  may want to render a component at one place in the tree, while still
  redirect actions returned by it to some other component."
  [elem target]
  (map-over-components
   elem
   (fn [comp]
     (react/cloneElement comp #js {:reacl_parent target}))))

(defn refer
  "Returns an element identical to the given `elem`, but replacing its
  `ref` property, so that the given ref reflects the dom element
  created for it."
  [elem ref]
  (react/cloneElement elem #js {:ref ref}))

(defn keyed
  "Returns an element identical to the given `elem`, but replacing its
  `key` property."
  [elem key]
  ;; Note: some version of react has cloneAndReplaceKey
  (react/cloneElement elem #js {:key key}))

(def ^:private empty-opt (Options. {}))

(defn- extract-opt
  [rst]
  (if (empty? rst)
    [empty-opt rst]
    (let [frst (first rst)]
      (if (opt? frst)
        [frst (rest rst)]
        [empty-opt rst]))))

(defn ^:no-doc extract-binding
  [has-app-state? rst]
  (let [[opts rst] (extract-opt rst)
        [binding args] (if (or (not has-app-state?) (contains? (:map opts) :app-state))
                         [opts rst]
                         ;; legacy variant, where app-state is the first arg after the opts:
                         (let [app-state (first rst)]
                           [(Options. (assoc (:map opts)
                                             :app-state app-state))
                            (rest rst)]))]
    [binding args]))

(defn ^:no-doc extract-opt+app-state
  [has-app-state? rst]
  (let [[binding args] (extract-binding has-app-state? rst)]
    [binding (:app-state (:map binding)) args]))

(def ^:no-doc uber-class
  (let [cl
        (createReactClass
         #js {:getInitialState (fn []
                                 (this-as this
                                   (aset this "reacl_toplevel_ref" (react/createRef))
                                   (let [props (.-props this)]
                                     #js {:reacl_init_id (aget props "reacl_init_id")
                                          :reacl_uber_app_state (aget props "reacl_initial_app_state")})))

              :render
              (fn []
                (this-as this
                  (let [props (.-props this)
                        clazz (aget props "reacl_toplevel_class")
                        opts (aget props "reacl_toplevel_opts")
                        args (aget props "reacl_toplevel_args")
                        app-state (aget (.-state this) "reacl_uber_app_state")]
                    (apply clazz
                           (Options. (cond-> (assoc opts :ref (aget this "reacl_toplevel_ref"))
                                       (has-app-state? clazz) (assoc :app-state app-state)))
                           args))))

              :displayName (str `toplevel)

              :getChildContext (fn []
                                 (this-as this 
                                   #js {:reacl_uber this}))})]
         (aset cl "childContextTypes" #js {:reacl_uber ptypes/PropTypes.object})
         (aset cl "contextTypes" #js {:reacl_uber ptypes/PropTypes.object})
         (aset cl "getDerivedStateFromProps"
               (fn [new-props state]
                 ;; take a new initial app-state from outside if render-component was called again;
                 ;; note this is called before every 'render' call,
                 ;; which is why we have to remember how the class was
                 ;; instantiated.
                 (let [new-init-id (aget new-props "reacl_init_id")]
                   (if (not (identical? new-init-id (aget state "reacl_init_id")))
                     (let [app-state (aget new-props "reacl_initial_app_state")]
                       #js {:reacl_init_id new-init-id
                            :reacl_uber_app_state app-state})
                     #js {}))))
         cl))

(defn- resolve-uber
  [comp]
  (aget (.-context comp) "reacl_uber"))

(defn- make-uber-component
  [clazz opts args app-state]
  (react/createElement uber-class
                       #js {:reacl_toplevel_class clazz
                            :reacl_toplevel_opts (cond-> opts
                                                   (and (has-app-state? clazz) (not (contains? opts :reaction))) (assoc :reaction no-reaction))
                            :reacl_toplevel_args args
                            :reacl_init_id #js {} ;; unique obj for each call.
                            :reacl_initial_app_state app-state}))

(def ^{:arglists '([clazz opts app-state & args]
                   [clazz app-state & args]
                   [clazz opts & args]
                   [clazz & args])
       :private true
       :doc "Internal function to instantiate a Reacl component.

  - `clazz` is the Reacl class
  - `opts` is an object created with [[opt]]
  - `app-state` is the application state
  - `args` is a seq of class arguments"}
  instantiate-toplevel-internal
  (fn [clazz has-app-state? rst]
    (when-not (reacl-class? clazz)
      (throw (ex-info (str "Expected a Reacl class as the first argument, but got: " clazz) {:value clazz})))
    (let [[{opts :map} app-state args] (extract-opt+app-state has-app-state? rst)]
      (make-uber-component clazz opts args app-state))))

(def ^{:arglists '([clazz opts app-state & args]
                   [clazz app-state & args]
                   [clazz opts & args]
                   [clazz & args])
       :doc "Creates an instance (a React component) of the given class. For testing purposes mostly."}
  instantiate-toplevel
  (fn [clazz & rst]
    (instantiate-toplevel-internal clazz (has-app-state? clazz) rst)))

(defn- action-reducer
  [this]
  (aget (.-props this) "reacl_reduce_action"))

(def ^{:arglists '([clazz opts app-state & args]
                   [clazz app-state & args]
                   [clazz opts & args]
                   [& args])
       :private true
       :doc "Internal function to instantiate an embedded Reacl component.

  - `clazz` is the Reacl class
  - `opts` is an object created with [[opt]]
  - `app-state` is the application state
  - `args` is a seq of class arguments"}
  instantiate-embedded-internal
  (fn [clazz has-app-state? rst]
    (let [[{opts :map} app-state args] (extract-opt+app-state has-app-state? rst)
          rclazz (react-class clazz)]
      (when (and (-has-app-state? clazz)
                 (not (contains? opts :reaction)))
        (warning "Instantiating class" (class-name clazz) "without reacting to its app-state changes. Use 'use-app-state' if you intended to do this."))
      (when (and (not (-has-app-state? clazz))
                 (contains? opts :reaction))
        (warning "Instantiating class" (class-name clazz) "with reacting to app-state changes, but it does not have an app-state."))
      (-validate! clazz app-state args)
      (react/createElement rclazz
                           #js {:reacl_app_state app-state
                                :ref (:ref opts)
                                :reacl_locals (-compute-locals clazz app-state args)
                                :reacl_args args
                                :reacl_refs (-make-refs clazz)
                                :reacl_class clazz
                                :reacl_reaction (or (:reaction opts) no-reaction)
                                :reacl_parent (:parent opts)
                                :reacl_reduce_action (or (:reduce-action opts)
                                                         default-reduce-action)}))))

(defn- instantiate-embedded-internal-v1
  [clazz app-state reaction args]
  (let [rclazz (react-class clazz)]
    (react/createElement rclazz
                         #js {:reacl_app_state app-state
                              :reacl_locals (-compute-locals clazz app-state args)
                              :reacl_args args
                              :reacl_class clazz
                              :reacl_reaction reaction
                              :reacl_reduce_action default-reduce-action})))

(defn handle-toplevel-action
  "Returns a value to be passed to [[render-component]], which
  specifies how to handle toplevel actions, which will usually be
  actions interacting with some global entity like the browser or a server.

  All actions reaching the toplevel, are passed to `(f app-state action)`:
 
- with the current state of the application, where
- `f` may have a side effect on the browser or initiate some Ajax request, and 
- must use [[return]] to return a modified application state, or send some message to a component below.

Note that you must only use [[return]] so send a message
_immediately_ to a component (like the id of an Ajax request), and
only [[send-message!]] to send a message later, from an _asynchronous_ context, to a
component (like the result of an Ajax request).
"
  [f]
  (opt :reduce-action f))

(def ^{:arglists '([element clazz opts app-state & args]
                   [element clazz app-state & args]
                   [element clazz opts & args]
                   [element clazz & args])
       :doc "Instantiate and render a component into the DOM.

  - `element` is the DOM element
  - `clazz` is the Reacl class
  - `opts` is an object created with [[handle-toplevel-action]] or [[opt]]
  - `app-state` is the initial application state
  - `args` is a seq of class arguments"}
  render-component
  (fn [element clazz & rst]
    (react-dom/render
     (-instantiate-toplevel-internal clazz rst)
     element)))

(defrecord ^{:doc "Type of a unique value to distinguish nil from no change of state.
            For internal use in [[return]]."
             :private true} 
    KeepState
  [])

(def ^{:doc "Single value of type KeepState.
             Can be used in [[return]] to indicate no (application or local)
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


(def ^:private returned-nil (Returned. keep-state keep-state [] #queue []))

(defn- as-map [v]
  (or v {}))

(extend-protocol IPrintWithWriter
  Returned
  (-pr-writer [ret writer _]
    (-write writer (str "#" (namespace ::x) ".Returned "))
    (-write writer
            (->> (clojure.data.diff ret returned-nil)
                 (first)
                 (as-map)))))


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
             (vec (concat (:actions ret) actions))
             (reduce conj
                     (:messages ret)
                     messages)))

(defn merge-returned
  "Merge the given return values from left to right. Actions and
  messages are appended, states are replaced unless they
  are [[keep-state]]."
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
  "Return state from a Reacl message handler or livecycle methods.

   Has optional keyword arguments:

   - `:app-state` is for a new app state (only once).
   - `:local-state` is for a new component-local state (only once).
   - `:action` is for an action (may be present multiple times)
   - `:message` is for a tuple `[target message]` to be queued (may be present multiple times)

   A state can be set to nil. To keep a state unchanged, do not specify
  that option, or specify the value [[keep-state]]."
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
          (do (assert (contains? #{:app-state :local-state :action :message} (first args)) (str "Invalid argument " (first args) " to reacl/return."))
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
          (when (not= reduce-action default-reduce-action)
            (trace/trace-reduced-action! comp action action-ret))
          (recur (rest actions)
                 (right-state app-state
                              action-app-state)
                 local-state
                 (reduce conj! reduced-actions new-actions)
                 (reduce conj messages new-messages)))))))

(declare get-app-state)

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
  [comp app-state local-state recompute-locals? msg]
  (if (instance? EmbedState msg)
    (let [[as ls] (apply (:embed msg) app-state local-state (:app-state msg) (:args msg))]
      (return :app-state as :local-state ls))
    (let [handle-message (aget comp "__handleMessage")]
      (assert handle-message (if-let [class (.-constructor comp)]
                               (str "Message target does not have a handle-message method: " (str "instance of " (.-displayName class)))
                               (str "Message target is not a component: " comp)))
      (let [args (extract-args comp)
            ret (handle-message comp
                                app-state local-state
                                (if recompute-locals?
                                  (-compute-locals (component-class comp) app-state args)
                                  (extract-locals comp))
                                args (extract-refs comp)
                                msg)]
        (if (returned? ret)
          ret
          (do (assert (= false true) (str "A 'reacl/return' value was expected, but handle-message of " (.-displayName (.-constructor comp)) " returned: " (pr-str ret)))
              returned-nil))))))

(defn ^:no-doc handle-message
  "Handle a message for a Reacl component.

  For internal use.

  This returns a `Returned` object."
  [comp msg]
  ;; Note: the locals in the props are uptodate here, so we can safely pass recompute-locals? = false here.
  (process-message comp (extract-app-state comp) (extract-local-state comp) false msg))

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
                                     true  ;; FIXME: can we avoid recomputing when nothing has changed? (maybe use a locals-map?)
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

(defn- state-map-get* [comp state-map f]
  (let [res (get state-map comp ::no-state)]
    (case res
      (::no-state) [false (f comp)]
      [true res])))

(defn- get-local-state*
  [comp local-state-map]
  (state-map-get* comp local-state-map extract-local-state))

(defn- get-app-state*
  [comp app-state-map]
  (state-map-get* comp app-state-map extract-app-state))

(defn- get-local-state [comp local-state-map]
  (second (get-local-state* comp local-state-map)))

(defn- get-app-state [comp app-state-map]
  (second (get-app-state* comp app-state-map)))

(defn- handle-returned-1
  "Handle a single subcycle in a [[Returned]] object.

  Assumes the actions in `ret` are for comp.

  Returns `UpdateInfo` value."
  [comp ^Returned ret pending-messages app-state-map local-state-map]
  (let [[app-state local-state actions-for-parent queued-messages] (reduce-returned-actions comp (get-app-state comp app-state-map) ret)
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
       (do
         ;; little tricks here to remove this when asserts are elided:
         (assert (do (when-let [messages (not-empty pending-messages)]
                       (doseq [[target msg] messages]
                         (warning "Reaction message " msg " with target" target " cannot not be delivered. Maybe you screwed up the reaction targets; those should always point 'upwards'.")))
                     true))
         (assert (do (when-let [actions (not-empty actions-for-parent)]
                       (doseq [a actions]
                         (warning "Action not handled:" a "- Add an action reducer to your call to render-component.")))
                     true))
         (UpdateInfo. comp
                      (right-state (get app-state-map comp keep-state) app-state)
                      app-state-map local-state-map
                      queued-messages)))))

(defn- handle-returned
  "Execute a complete supercycle.

  Returns `UpdateInfo` object."
  [comp ^Returned ret from]
  (loop [comp comp
         ^Returned ret ret
         app-state-map {}
         local-state-map {}
         queued-messages #queue []
         from from]
    (trace/trace-returned! comp ret from)
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
              [virt-app-state? app-state] (get-app-state* dest app-state-map)
              local-state (get-local-state dest local-state-map)
              ;; if app-state is 'virtual', i.e. from the state-map,
              ;; then we have to recompute
              ;; locals for a call to handle-message.
              recompute-locals? virt-app-state?
              ^Returned ret (process-message dest
                                             app-state
                                             local-state
                                             recompute-locals?
                                             msg)]
          (recur dest ret
                 app-state-map
                 local-state-map
                 queued-messages
                 'handle-message))))))

(defn ^:no-doc handle-returned!
  "Handle all effects described and caused by a [[Returned]] object. This is the entry point into a Reacl update cycle.

  Assumes the actions in `ret` are for comp."
  [comp ^Returned ret from]
  (let [ui (handle-returned comp ret from)
        app-state (:toplevel-app-state ui)]
    ;; after handle-returned, all messages must have been processed:
    (assert (empty? (:queued-messages ui)) "Internal invariant violation.")
    (trace/trace-commit! app-state (:local-state-map ui))
    (doseq [[comp local-state] (:local-state-map ui)]
      (set-local-state! comp local-state))
    (when-not (keep-state? app-state)
      (let [comp (:toplevel-component ui)
            uber (resolve-uber comp)]
        (.setState uber #js {:reacl_uber_app_state app-state})))))


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
  (trace/trace-send-message! comp msg)
  (binding [*send-message-forbidden* true]
    (let [comp (resolve-component comp)
          ^Returned ret (handle-message comp msg)]
      (react-dom/unstable_batchedUpdates #(handle-returned! comp ret 'handle-message))
      ret)))

(defn send-message-allowed?
  "Returns if calling `send-message!` is allowed at this point; it's
  basically only allowed in event handlers, outside a Reacl update
  cycle."
  []
  (not *send-message-forbidden*))

(defn- opt-handle-returned! [component v from]
  (when (some? v)
    (if (returned? v)
      (handle-returned! component v from)
      (assert false (str "A 'reacl/return' value was expected, but " from " returned:" (pr-str v))))))

(defn ^:no-doc compose-reducers [r1 r2]
  ;; Note: the default reducer just passes the action as is, so we can optimize that
  (cond
    (= r1 default-reduce-action) r2
    (= r2 default-reduce-action) r1
    :else
    (fn [app-state action]
      (let [ret1 (r1 app-state action)
            ;; get and remove actions from ret1 (in which action was translated to)
            actions (returned-actions ret1)
            ret1 (assoc ret1 :actions (:actions returned-nil))]
        ;; reduce over all actions r1 translated action into;
        ;; need to keep track of the app-state (once it is set keep that, but als keep-state if not changed at all.
        (reduce (fn [ret action]
                  (let [interm-app-state (returned-app-state ret)
                        current-app-state (right-state app-state interm-app-state)
                        ret2 (merge-returned ret
                                             (r2 current-app-state action))]
                    (if (keep-state? (returned-app-state ret2))
                      (merge-returned ret2 (return :app-state interm-app-state))
                      ret2)))
                ret1
                actions)))))

(defn reduce-action
  "Clone the given element, wrapping (composing) its action reducer
  with the given action reducer `f`."
  [elem f]
  (assert f)
  (map-over-components
   elem
   (fn [comp]
     (react/cloneElement comp #js {:reacl_reduce_action (if-let [prev (action-reducer comp)] ;; Note: will usually have one: the default-action-reducer.
                                                          (compose-reducers prev f)
                                                          f)}))))

(defn map-action
  "Clones the given element, applying a transformation function `f` to
  all actions 'coming out' of components below it."
  [elem f]
  (reduce-action elem (fn [app-state action]
                        ;; TODO: allow a 'ignore-action'/nil?
                        (return :action (f action)))))

(def ^:private const-true (constantly true))

(defn- pred-or-type [action pt]
  (and (if (ifn? pt)
         (pt action)
         (instance? pt action))
       true))

(defn action-to-message
  "Clones the given element, but 'redirects' all actions matching the
  `pred` function (or all actions if not specified), as a message sent
  to `target`. Alternatively `pred` and all remaining arguments can be
  record types, which are then used as in an instance check against
  the action."
  ([elem target] (action-to-message elem target const-true))
  ([elem target pred & types]
   (reduce-action elem (fn [_ action]
                         (if (some (partial pred-or-type action) (cons pred types))
                           (return :message [target action])
                           (return :action action))))))


;; Attention: duplicate definition for macro in core.clj
(def ^:private specials #{:render :initial-state :handle-message
                          :component-will-mount :component-did-mount
                          :component-will-receive-args
                          :component-did-catch
                          :should-component-update?
                          :component-will-update :component-did-update
                          :component-will-unmount
                          :mixins})

(defn- is-special-fn? [[n f]]
  (not (nil? (specials n))))

(defn- optional-ifn? [v]
  (or (nil? v) (ifn? v)))

;; FIXME: just pass all the lifecycle etc. as separate arguments

(defn ^:no-doc create-class [display-name compat-v1? mixins has-app-state? compute-locals validate num-refs fns]
  ;; split special functions and miscs
  (let [{specials true misc false} (group-by is-special-fn? fns)
        {:keys [render
                initial-state
                handle-message
                component-will-mount
                component-did-mount
                component-will-receive-args
                component-did-catch
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
    (assert (optional-ifn? component-did-catch))
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
          (fn [f method-name]
            (and f
                 (fn [& react-args]
                   (this-as this
                     (opt-handle-returned! this (apply f
                                                       this
                                                       (extract-app-state this) (extract-local-state this)
                                                       (extract-locals this) (extract-args this) (extract-refs this)
                                                       react-args)
                                           method-name)))))

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
            (when initial-state
              (fn []
                (this-as this
                         (let [app-state (extract-app-state this)
                               locals (extract-locals this)
                               args (extract-args this)]
                           (make-local-state this (initial-state this app-state locals args))))))

            "mixins"
            (when mixins
              (object-array mixins))

            "render"
            (when render
              (std (fn [this app-state local-state locals args refs]
                     (trace/trace-render-component! this)
                     (render this app-state local-state locals args refs))))

            ;; Note handle-message must always see the most recent
            ;; app-state, even if the component was not updated after
            
            ;; a change to it.
            "__handleMessage" handle-message

            "UNSAFE_componentWillMount"
            (std+state component-will-mount 'component-will-mount)

            "UNSAFE_componentWillReceiveProps"
            (when component-will-receive-args
              (fn [next-props]
                (this-as this
                         (opt-handle-returned! this
                                               (apply component-will-receive-args this
                                                      (extract-app-state this) (extract-local-state this) (extract-locals this) (extract-args this) (extract-refs this)
                                                      (props-extract-args next-props))
                                               'component-will-receive-args))))

            "componentDidCatch"
            (std+state component-did-catch 'component-did-catch)

            "getChildContext" (fn []
                                (this-as this 
                                  #js {:reacl_parent this}))

            "componentDidMount"
            (std+state component-did-mount 'component-did-mount)

            "shouldComponentUpdate"
            (with-state-and-args should-component-update?)

            "componentWillUpdate"
            (with-state-and-args component-will-update)

            "componentDidUpdate"
            (when component-did-update
              (let [f (with-state-and-args component-did-update)]
                (fn [prev-props prev-state]
                  (this-as this
                           (opt-handle-returned! this (.call f this prev-props prev-state) 'component-did-update)))))

            "componentWillUnmount"
            (std+state component-will-unmount 'component-will-unmount)
            }
           )

          react-class (createReactClass
                       (apply js-obj (apply concat
                                            (filter #(not (nil? (second %)))
                                                    react-method-map))))
          ]
      (aset react-class "childContextTypes" #js {:reacl_parent ptypes/PropTypes.object
                                                 :reacl_uber ptypes/PropTypes.object})
      (aset react-class "contextTypes" #js {:reacl_parent ptypes/PropTypes.object
                                            :reacl_uber ptypes/PropTypes.object})
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
          (-validate! [this app-state args]
            (when validate (apply validate app-state args)))
          (-make-refs [this]
            (make-refs))
          (-compute-locals [this app-state args]
            (when compute-locals (compute-locals app-state args)))
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
          (-validate! [this app-state args]
            (when validate (apply validate app-state args)))
          (-make-refs [this]
            (make-refs))
          (-compute-locals [this app-state args]
            (when compute-locals (compute-locals app-state args)))
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
                         (entry :component-did-mount "componentDidMount" (fn [this res] (opt-handle-returned! this res 'component-did-mount)))
                         (entry :component-will-mount "componentWillMount" (fn [this res] (opt-handle-returned! this res 'component-will-mount)))
                         (entry :component-will-unmount "componentWillUnmount" (fn [this res] (opt-handle-returned! this res 'component-will-unmount)))
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

