(ns ^{:author "Michael Sperber"
      :doc "Supporting macros for Reacl."}
  reacl.core
  (:refer-clojure :exclude [class]))

(def ^{:private true} lifecycle-name-map
  { ;; 'component-will-mount is in special-tags
   'component-did-mount 'componentDidMount
   'component-will-receive-props 'componentWillReceiveProps
   'should-component-update? 'shouldComponentUpdate
   'component-will-update 'componentWillUpdate
   'component-did-update 'componentDidUpdate
   'component-will-unmount 'componentWillUnmount})

(def ^{:private true} special-tags
  (clojure.set/union (into #{} (map val lifecycle-name-map))
                     #{'render 'handle-message 'initial-state 'component-will-mount 'local}))

(defmacro class
  "Create a Reacl class.

   This is a regular React class, with some convenience added, as well as:

   - implicit propagation of app state
   - a pure model for event handlers

   The syntax is

   (reacl.core/class <name> <app-state> [<param> ...]
     render <renderer-exp>
     [initial-state <initial-state-exp>]
     [local [<local-name> <local-expr>]]
     [<lifecycle-method-name> <lifecycle-method-exp> ...]
     [handle-message <messager-handler-exp>]

     <event-handler-name> <event-handler-exp> ...)

   <name> is a name for the class, for debugging purposes.

   <app-state> is name bound to the global application state (implicitly
   propagated through instantiation, see below), and <param> ... the names
   of explicit arguments of instantiations (see below).  These names are
   bound in <renderer-exp>, <initial-state-exp> as well as all
   <event-handler-exp>s.

   A `local` clause allows binding local variables that are computed
   from <app-state> and <param> ...  The syntax is analogous to `let`.

   <renderer-exp> must evaluate to a function that renders the component,
   and hence must return a virtual dom node.  It gets passed several
   keyword arguments:

   - instantiate: an instantiation function for subcomponents; see below
   - local-state: component-local state
   - dom-node: a function for retrieving \"real\" dom nodes corresponding
     to virtual dom nodes
   - message-handler: a function for creating event handlers that
     return messages; see below

   The instantiate function is for instantiating Reacl subcomponents; it
   takes a Reacl class and arguments corresponding to the Reacl class's
   <param>s.  It implicitly passes the application state to the
   subcomponent.

   The component's local-state is an arbitrary object representing the
   component's local state.

   The dom-node function takes a named dom object bound via
   reacl.dom/letdom and yields its corresponding \"real\" dom object for
   extracting GUI state.

   The message-handler function creates an event handler for use in
   the DOM from a function.  That function receives the same arguments as
   the event handler, and is expected to return a message.  That message
   is then passed to the component's `handle-message' function.

   The `handle-message' function accepts a message sent to the
   component as well as the component's local state.  It's expected to
   return a value specifying a new application state and/or
   component-local state, via reacl.core/return.

   A lifecycle method can be one of:

     component-will-mount component-did-mount
     component-will-receive-props should-component-update? 
     component-will-update component-did-update component-will-unmount

   These correspond to React's lifecycle methods, see here:

   http://facebook.github.io/react/docs/component-specs.html

   Each right-hand-side <lifecycle-method-exp>s should evaluate to a
   function.  This function's argument is always the component.  The
   remaining arguments are as for React.

   Everything that's not a renderer, an initial-state, a local clause, a
   handle-message method, or lifecycle method is assumed to be a binding
   for a function, typically an event handler.  These functions will all
   be bound under their corresponding <event-handler-name>s in
   <renderer-exp>.

   Example:

  (defrecord New-text [text])
  (defrecord Submit [])

  (reacl/defclass to-do-app
    todos []
    render
    (fn [& {:keys [local-state instantiate message-handler]}]
      (dom/div
       (dom/h3 \"TODO\")
       (dom/div (map-indexed (fn [i todo]
                               (dom/keyed (str i) (instantiate to-do-item (lens/at-index i))))
                             todos))
       (dom/form
        {:onSubmit (message-handler
                    (fn [e _]
                      (.preventDefault e)
                      (Submit.)))}
        (dom/input {:onChange (message-handler
                               (fn [e]
                                 (New-text. (.. e -target -value))))
                    :value local-state})
        (dom/button
         (str \"Add #\" (+ (count todos) 1))))))

    initial-state \"\"

    handle-message
    (fn [msg local-state]
      (cond
       (instance? New-text msg)
       (reacl/return :local-state (:text msg))

       (instance? Submit msg)
       (reacl/return :local-state \"\"
                     :app-state (concat todos [(Todo. local-state false)])))))"
  [?name ?app-state [& ?args] & ?clauses]
  (let [clause-map (apply hash-map ?clauses)
        render (get clause-map 'render)
        wrap-args
        (fn [?this & ?body]
          `(let [~?app-state (reacl.core/extract-app-state ~?this)
                 [~@?args] (reacl.core/extract-args ~?this)] ; FIXME: what if empty?
             ~@?body))
        ?locals-clauses (partition 2 (get clause-map 'local []))
        ?initial-state (let [?state-expr (get clause-map 'initial-state)]
                          (if (or ?state-expr (not (empty? ?locals-clauses)))
                            (let [?this `this#]
                              `(fn [] 
                                 (cljs.core/this-as
                                  ~?this
                                  ~(wrap-args 
                                    ?this
                                    `(reacl.core/make-local-state [~@(map second ?locals-clauses)]
                                                                  ~(or ?state-expr `nil))))))
                            `(fn [] (reacl.core/make-local-state nil nil))))

        wrap-args&locals
        (fn [?this & ?body]
          (wrap-args ?this
                     `(let [[~@(map first ?locals-clauses)] (reacl.core/extract-locals ~?this)]
                        ~@?body)))

        misc (filter (fn [e]
                       (not (contains? special-tags (key e))))
                     clause-map)
        lifecycle (filter (fn [e]
                            (contains? lifecycle-name-map (key e)))
                          clause-map)
        ?renderfn
        (let [?this `this#  ; looks like a bug in ClojureScript, this# produces a warning but works
              ?state `state#]
          `(fn []
             (cljs.core/this-as 
              ~?this
              (let [~?state (reacl.core/extract-local-state ~?this)]
                ~(wrap-args&locals
                  ?this
                  `(let [~@(mapcat (fn [p]
                                     [(first p) `(aget ~?this ~(str (first p)))])
                                    misc)]
                     (~render ~?this
                              :instantiate (fn [clazz# & props#] (apply reacl.core/instantiate clazz# ~?this props#))
                              :local-state ~?state
                              :dom-node (fn [dn#] (reacl.dom/dom-node-ref ~?this dn#))
                              :message-handler (reacl.core/make-message-handler ~?this)
                              :this ~?this)))))))
        
        ?handle `handle# ; name of the handler
        bind-handler
        (if-let [?handler (get clause-map 'handle-message)]
          (fn [?body]
            (let [?this `this#]
              `(let [~?handle
                     (fn [msg# ~?this]
                       (~(wrap-args&locals ?this ?handler)
                        msg#
                        (reacl.core/extract-local-state ~?this)))]
               ~?body)))
          identity)]
    (bind-handler
     `(js/React.createClass (cljs.core/js-obj "render" ~?renderfn 
                                              "getInitialState" ~?initial-state 
                                              "displayName" ~(str ?name)
                                              ~@(mapcat (fn [[?name ?rhs]]
                                                          (let [?args `args#
                                                                ?this `this#]
                                                            [(str (get lifecycle-name-map ?name))
                                                             `(fn [& ~?args]
                                                                (cljs.core/this-as
                                                                 ;; FIXME: should really bind ?rhs outside
                                                                 ~?this
                                                                 (apply ~(wrap-args&locals ?this ?rhs) ~?this ~?args)))]))
                                                        lifecycle)
                                              ~@(mapcat (fn [[?name ?rhs]]
                                                          [(str ?name) 
                                                           (let [?args `args#
                                                                 ?this `this#]
                                                             `(fn [& ~?args]
                                                                (cljs.core/this-as
                                                                 ~?this
                                                                 (apply ~(wrap-args&locals ?this ?rhs) ~?this ~?args))))])
                                                        misc)
                                              ;; event handler, if there's a handle-message clause
                                              ~@(if (contains? clause-map 'handle-message)
                                                  ["componentWillMount"
                                                   (let [?this `this#]
                                                     `(fn []
                                                        (cljs.core/this-as
                                                         ~?this
                                                         (do
                                                           (reacl.core/message-processor ~?this ~?handle)
                                                           ;; if there is a component-will-mount clause, tack it on
                                                           ~@(if-let [?will-mount (get clause-map 'component-will-mount)]
                                                               [`(~(wrap-args&locals ?this ?will-mount) ~?this)]
                                                               [])))))]
                                                  []))))))

(defmacro defclass
  "Define a Reacl class.

   The syntax is

   (reacl.core/defclass <name> <app-state> [<param> ...]
     render <renderer-exp>
     [initial-state <initial-state-exp>]
     [<lifecycle-method-name> <lifecycle-method-exp> ...]
     [handle-message <messager-handler-exp>]

     <event-handler-name> <event-handler-exp> ...)

   This expands to this:

   (def <name>
     (reacl.core/class <name> <app-state> [<param> ...]
       render <renderer-exp>
       [initial-state <initial-state-exp>]
       [<lifecycle-method-name> <lifecycle-method-exp> ...]
       [handle-message <messager-handler-exp>]

       <event-handler-name> <event-handler-exp> ...))"
  [?name ?app-state [& ?args] & ?clauses]
  `(def ~?name (reacl.core/class ~?name ~?app-state [~@?args] ~@?clauses)))

