(ns ^{:author "Michael Sperber"
      :doc "Supporting macros for Reacl."}
  reacl.core
  (:refer-clojure :exclude [class]))

(def ^{:private true} lifecycle-name-map
  {'component-will-mount 'componentWillMount
   'component-did-mount 'componentDidMount
   'component-will-receive-args 'componentWillReceiveProps
   'should-component-update? 'shouldComponentUpdate
   'component-will-update 'componentWillUpdate
   'component-did-update 'componentDidUpdate
   'component-will-unmount 'componentWillUnmount})

;; Attention: duplicate definition in core.cljs
(def ^{:private true} special-tags
  (clojure.set/union (into #{} (map val lifecycle-name-map))
                     #{'render 'handle-message 'initial-state 'component-will-mount 'local}))

(defn- split-symbol [stuff dflt]
  (if (symbol? (first stuff))
    [(first stuff) (rest stuff)]
    [dflt stuff]))

(defmacro class
  "Create a Reacl class.

  This is a regular React class, with some convenience added - binding
  of `this`, application state, parameters etc - and implementing
  Reacl-specific protocols, particularly for event handling.

  The syntax is

      (reacl.core/class <name> [<this-name> [<app-state-name> [<local-state-name>]]] [<param> ...]
        render <renderer-exp>
        [initial-state <initial-state-exp>]
        [local [<local-name> <local-expr>]]
        [handle-message <messager-handler-exp>]
        [<lifecycle-method-name> <lifecycle-method-exp> ...])

  `<name>` is a name for the class, for debugging purposes.

  A number of names are bound in the various expressions in the body
  of reacl.core/class:

  - `<this-name>` is bound to the component object itself
  - `<app-state-name>` is bound to the global application state
  - `<local-state-name>` is bound to the component-local state
  - the `<param>` ... names are the explicit arguments of instantiations

  A `local` clause allows binding additional local variables upon
  instantiation.  The syntax is analogous to `let`.

  `<renderer-exp>` is an expression that renders the component, and
  hence must return a virtual dom node.

  The `handle-message` function accepts a message sent to the
  component via [[reacl.core/send-message!]].  It's expected to
  return a value specifying a new application state and/or
  component-local state, via [[reacl.core/return]].

  A lifecycle method can be one of:

    `component-will-mount` `component-did-mount`
    `component-will-receive-args` `should-component-update?`
    `component-will-update` `component-did-update` `component-will-unmount`

  These correspond to React's lifecycle methods, see
  here (component-will-receive-args is similar to
  componentWillReceiveProps):

  http://facebook.github.io/react/docs/component-specs.html

  Each right-hand-side `<lifecycle-method-exp>`s should evaluate to a
  function. The arguments, which slightly differ from the
  corresponding React methods, can be seen in the following list:

  `(component-will-mount)` The component can send itself messages in
  this method; if that changes the state, the component will only
  render once.

  `(component-did-mount)` The component can update it's DOM in this method.

  `(component-will-receive-args next-arg1 next-arg2 ...)` The component
  has the chance to update it's local state in this method.

  `(should-component-update? next-app-state next-local-state next-arg1
  next-arg2 ...)` This method should return if the given new values
  should cause an update of the component (if render should be
  evaluated again). If it's not specified, a default implementation
  will do a (=) comparison with the current values. Implement this, if
  you want to prevent an update on every app-state change for example.

  (component-will-update next-app-state next-local-state next-arg1 next-arg2 ...)
  Called immediately before an update.
  
  (component-did-update prev-app-state prev-local-state prev-arg1 prev-arg2 ...)
  Called immediately after an update. The component can update it's DOM here.
  
  (component-will-unmount)
  The component can cleanup it's DOM here for example.

  Example:

      (defrecord New-text [text])
      (defrecord Submit [])

      (reacl/defclass to-do-app
        this todos local-state []
        render
        (dom/div
         (dom/h3 \"TODO\")
         (dom/div (map-indexed (fn [i todo]
                                 (dom/keyed (str i) (to-do-item this (lens/at-index i))))
                               todos))
         (dom/form
          {:onSubmit (fn [e _]
                       (.preventDefault e)
                       (reacl/send-message! this (Submit.)))}
          (dom/input {:onChange (fn [e]
                                  (reacl/send-message! this
                                                       (New-text. (.. e -target -value))))
                      :value local-state})
          (dom/button
           (str \"Add #\" (+ (count todos) 1)))))

        initial-state \"\"

        handle-message
        (fn [msg]
          (cond
           (instance? New-text msg)
           (reacl/return :local-state (:text msg))

           (instance? Submit msg)
           (reacl/return :local-state \"\"
                         :app-state (concat todos [(Todo. local-state false)])))))"
  [?name & ?stuff]

  (let [[?component ?stuff] (split-symbol ?stuff `component#)
        [?app-state ?stuff] (split-symbol ?stuff `app-state#)
        [?local-state ?stuff] (split-symbol ?stuff `local-state#)

        [[& ?args] & ?clauses] ?stuff

        ?clause-map (apply hash-map ?clauses)
        ?locals-clauses (get ?clause-map 'local [])
        ?locals-ids (map first (partition 2 ?locals-clauses))

        ?wrap-expression (fn [?sym]
                           (let [?exists (contains? ?clause-map ?sym)
                                 ?expr (get ?clause-map ?sym)]
                             (when ?exists
                               `(fn [] ~?expr))))
        ?render-fn (?wrap-expression 'render)
        ?initial-state-fn (?wrap-expression 'initial-state)

        ?other-fns-map (dissoc ?clause-map 'local 'render 'initial-state)
        ?misc-fns-map (apply dissoc ?other-fns-map
                             special-tags)

        ?wrap-nlocal
        (fn [?f]
          (if ?f
            (let [?more `more#]
              `(fn [~?component ~?app-state [~@?locals-ids] [~@?args] & ~?more]
                 (apply ~?f ~?more)))
            'nil))
        ?wrap-std ;; reuse wrap-nlocal?!
        (fn [?f]
          (if ?f
            (let [?more `more#]
              `(fn [~?component ~?app-state ~?local-state [~@?locals-ids] [~@?args] & ~?more]
                 ;; everything user misc fn is also visible
                 (let [~@(mapcat (fn [[n f]] [n `(aget ~?component ~(str n))]) ?misc-fns-map)]
                   (apply ~?f ~?more))))
            'nil))

        ?std-fns-map (assoc ?other-fns-map
                       'render ?render-fn)

        ?wrapped-nlocals [['initial-state (?wrap-nlocal ?initial-state-fn)]]

        ?wrapped-std (map (fn [[?n ?f]] [?n (?wrap-std ?f)])
                          ?std-fns-map)

        ?fns
        (into {}
              (map (fn [[?n ?f]] [(keyword ?n) ?f])
                   (concat ?wrapped-nlocals ?wrapped-std)))

        ?compute-locals
        `(fn [~?app-state [~@?args]]
           (let ~?locals-clauses
             [~@?locals-ids]))
        ]
    `(reacl.core/create-class ~?name ~?compute-locals ~?fns)))

(defmacro defclass
  "Define a Reacl class, see [[class]] for documentation.

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
  [?name & ?stuff]
  `(def ~?name (reacl.core/class ~(str ?name) ~@?stuff)))

(defmacro view
  [?name & ?stuff]
  (let [[?component ?stuff] (split-symbol ?stuff `component#)
        ?app-state `app-state#
        [?local-state ?stuff] (split-symbol ?stuff `local-state#)

        [[& ?args] & ?clauses] ?stuff

        ?clause-map (apply hash-map ?clauses)
        ;; this adds app-state arg to component-will-update,
        ;; component-did-update, should-component-update?
        add-arg (fn [?current-fn]
                  (let [?ignore `ignore#
                        ?args `args#]
                    `(fn [~?ignore & ~?args]
                       (apply ~?current-fn ~?args))))
        ?component-will-update (update-in ?clause-map ['component-will-update] add-arg)
        ?component-did-update (update-in ?clause-map ['component-will-update] add-arg)
        ?should-component-update? (update-in ?clause-map ['should-component-update?] add-arg)
        ]
    `(reacl.core/class->view
      (reacl.core/class ~?name ~?component ~?app-state ~?local-state [~@?args]
                        ~@?clauses))))

(defmacro defview
  [?name & ?stuff]
  `(def ~?name (reacl.core/view ~(str ?name) ~@?stuff)))
