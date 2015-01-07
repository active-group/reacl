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

(defn- ^:no-doc props-extract-initial-app-state
  "Extract initial applications state from props of a Reacl toplevel component.

   For internal use."
  [props]
  (aget props "reacl_initial_app_state"))

(defn- ^:no-doc tl-state-extract-app-state
  "Extract latest applications state from state of a toplevel Reacl component.

   For internal use."
  [state]
  (aget state "reacl_app_state"))

(defn- ^:no-doc props-extract-app-state-callback
  [props]
  (aget props "reacl_app_state_callback"))

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
  ((aget clazz "__computeLocals") app-state args))

;; On the app-state integration:
;;
;; It starts with the argument to the instantiation of toplevel or
;; embedded components. We put that app-state into
;;
;;   toplevel/props.reacl_initial_app_state
;;
;; In getInitialState of those top components, we take that over into
;; their state, into
;;
;;  toplevel/state.reacl_app_state
;;
;; This is the place for app-state changed during the livetime of the
;; toplevel component. When creating/updating lower level components,
;; we 'snapshot' the app-state, which their current 'look' is based
;; on, into
;;
;;  lowlevel/props.reacl_last_app_state
;;
;; When a component does not need to be updated, that value will not
;; be the current app-state anymore; but for livecycle-methods like
;; componentWillUpdate, that is exactly what we want. But in a method
;; like handle-message, we need the most recent app-state, even if the
;; component's look is based on an older one. Therefor we look into
;; the toplevel's state in that case.
;;
;; Similar, for set-app-state!, we have to change the state of the
;; toplevel component (resp. embedded). Last but not least, a toplevel
;; component might be "reinstantiated" (very common in the embedded
;; case, but also possible on toplevel). It will then receive a new
;; reacl_initial_app_state in the props, which we take over into it's
;; state in componentWillReceiveProps.

(defn ^:no-doc set-app-state!
  "Set the application state associated with a Reacl component.
   May not be called before the first render.

   For internal use."
  [this app-state]
  (let [props (.-props this)]
    (assert (.hasOwnProperty (.-state this) "reacl_app_state"))

    ;; recompute locals if toplevel (in other cases they are computed
    ;; upon reinstantiation)
    (reset! (aget props "reacl_locals") 
            (compute-locals (.-constructor this) app-state (extract-args this)))

    ;; set state - must be after compute locals
    (.setState this #js {:reacl_app_state app-state})

    ;; embedded callback
    (if-let [callback (props-extract-app-state-callback props)]
      (callback app-state))))

(defprotocol ^:no-doc IReaclClass
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
  (clazz #js {:reacl_embedded_ref_count (atom nil)
              :reacl_initial_app_state app-state
              :reacl_args args
              :reacl_locals (atom locals)}))

(defn ^:no-doc instantiate-embedded-internal
  "Internal function to instantiate an embedded Reacl component.

  `clazz' is the React class (not the Reacl class ...).
  `parent' is the component from which the Reacl component is instantiated.
  `app-state' is the  application state.
  `app-state-callback' is a function called with a new app state on changes.
  `args' are the arguments to the component.
  `locals' are the local variables of the components."
  [clazz parent app-state app-state-callback args locals]
  (clazz #js {:reacl_initial_app_state app-state
              :reacl_args args
              :reacl_locals (atom locals)
              :reacl_app_state_callback app-state-callback}))

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
  (js/React.renderComponent
   (apply instantiate-toplevel clazz app-state args)
   element))

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

;; Attention: duplicate definition for macro in core.clj
(def ^:private specials #{:render :initial-state :handle-message
                          :component-will-mount :component-did-mount
                          :component-will-receive-args
                          :should-component-update?
                          :component-will-update :component-did-update
                          :component-will-unmount})
(defn- ^:no-doc is-special-fn? [[n f]]
  (not (nil? (specials n))))

(defn create-class [display-name compute-locals fns]
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
                                          (initial-state app-state locals args))
                            state (make-local-state this local-state)]
                        ;; app-state will be the initial_app_state here
                        (aset state "reacl_app_state" app-state)
                        state)))

            "render"
            (std render)

            ;; Note handle-message must always see the most recent
            ;; app-state, even if the component was not updated after
            ;; a change to it.
            "__handleMessage"
            (std-current handle-message)

            "componentWillMount"
            (std component-will-mount)

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
                           ;; must preserve 'this' here...!
                           (.call f this next-props)))))

            "shouldComponentUpdate"
            (let [f (with-state-and-args should-component-update?)]
              (fn [next-props next-state]
                ;; have to check the app-state-callback for embedded
                ;; components for changes - though this will mostly
                ;; force an update!
                (this-as this
                         (or (and (let [callback-now (props-extract-app-state-callback (.-props this))
                                        callback-next (props-extract-app-state-callback next-props)]
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
;; FIXME: replace by embed
;;        IFn
;;        (-invoke [this component & args]
;;          (-instantiate this component args))
        IReaclClass
        (-instantiate-toplevel [this app-state args]
          (instantiate-toplevel-internal react-class app-state args
                                         (compute-locals app-state args)))
        (-instantiate-embedded [this component app-state app-state-callback args]
          (instantiate-embedded-internal react-class component app-state
                                         app-state-callback args
                                         (compute-locals app-state args)))
        (-react-class [this] react-class)
        ))))
