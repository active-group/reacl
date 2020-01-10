(ns ^{:doc "Supporting macros for Reacl."}
  reacl2.core
  (:require [clojure.set :as set]
            [clojure.string :as string]
            cljs.analyzer)
  (:refer-clojure :exclude [class]))

(def ^{:private true} special-tags
  (concat ['handle-message
           'component-will-mount
           'component-did-mount
           'component-will-receive-args
           'component-did-catch
           'should-component-update?
           'component-will-update
           'component-did-update
           'component-will-unmount]))

(defn- split-symbol [stuff dflt]
  (if (symbol? (first stuff))
    [(first stuff) (rest stuff)]
    [dflt stuff]))

;; compile an argument to a mixin to a function of this
(defn- compile-mixin-argument [?args thing]
  (if-let [index (first (filter identity (map-indexed (fn [i x] (if (= x thing) i nil)) ?args)))]
    `(fn [this#]
       (nth (reacl2.core/extract-args this#) ~index))
    (throw (Error. (str "Illegal mixin argument: " thing)))))

(defn- translate-mixins [mixins ?args]
  (map (fn [mix]
         `(~(first mix) ~@(map (partial compile-mixin-argument ?args) (rest mix))))
       mixins))

(defn- compilation-error [env msg]
  ;; empty in cljs? (:line (meta form)) ":" (:column (meta form))
  #_(Error. msg)
  ;; this does not change much, but maybe suitable:
  (cljs.analyzer/error env msg))

(defn- clause-map [env ?clauses]
  ;; check for even number of args.
  (when-not (even? (count ?clauses))
    (throw (compilation-error
            env
            (str "Invalid class definition. Must have an even number of arguments."
                 ;; if there is a symbol followed by a symbol, report the first as the potential problem
                 (if-let [p (reduce (fn [[r rem] v]
                                      (if r
                                        [r (rest rem)] ;; stop
                                        (if (and (symbol? v)
                                                 (symbol? (first rem)))
                                          [v (rest rem)]
                                          [nil (rest rem)])))
                                    [nil (rest ?clauses)]
                                    ?clauses)]
                   (str "Possibly missing def for " p ".")
                   "")))))
  (let [?clause-map (apply hash-map ?clauses)]
    ;; check for duplicate defs.
    (when-not (= (count ?clause-map) (/ (count ?clauses) 2))
      (throw (compilation-error
              env
              (str "Duplicate class clauses: " (let [keys (map second (filter #(even? (first %))
                                                                              (map-indexed vector ?clauses)))
                                                     dups (filter (fn [v]
                                                                    (> (count (filter #(= v %) keys)) 1))
                                                                  keys)]
                                                 (set dups))))))

    ?clause-map))

(defn- analyze-stuff [env ?stuff]
  (let [[?component ?stuff] (split-symbol ?stuff `component#)
        [has-app-state? ?app-state ?stuff] (if (symbol? (first ?stuff))
                                             [true (first ?stuff) (rest ?stuff)]
                                             [false `app-state# ?stuff])

        [?args & ?clauses] ?stuff

        ?clause-map (clause-map env ?clauses)]
    
    [?component ?app-state has-app-state? ?args ?clause-map]))

(defmacro class
  "Create a Reacl class.

  The syntax is

      (class <name> <this-name> [<app-state-name>] [<param> ...]
        <clause> ...)

  `<name>` is a name for the class, for debugging purposes.

  This is equivalent to [[defclass]] but without binding the new class to a name."
  [?name & ?stuff]

  (let [[?component ?app-state has-app-state? ?args ?clause-map] (analyze-stuff &env ?stuff)
        
        ?locals-clauses (get ?clause-map 'local [])
        ?locals-ids (map first (partition 2 ?locals-clauses))

        ?ref-ids (get ?clause-map 'refs [])
        
        [has-local-state? [?local-state ?initial-local-state-expr]]
        (if-let [local-state-clauses (get ?clause-map 'local-state)]
          (do (when-not (and (vector? local-state-clauses) (= (count local-state-clauses) 2))
                (throw (compilation-error &env "Invalid local-state clause. Must have the form: local-state [<name> <value>].")))
              [true local-state-clauses])
          [false [`local-state# nil]])

        ?render-fn (when-let [?expr (get ?clause-map 'render)]
                     `(fn [] ~?expr))

        compat-v1? (get ?clause-map 'compat-v1?)

        ;; handle-message, lifecycle methods, and user-defined functions (v1 only)
        ?other-fns-map (dissoc ?clause-map 'local 'render 'mixins 'local-state 'compat-v1? 'refs)
        ;; user-defined functions
        ?misc-fns-map (apply dissoc ?other-fns-map special-tags)

        _ (when (and compat-v1? (not-empty ?misc-fns-map))
            (throw (compilation-error &env (str "Invalid clauses in class definition: " (keys ?misc-fns-map)))))
        

        wrap-misc (fn [body]
                    `(let [~@(mapcat (fn [[n f]] [n `(aget ~?component ~(str n))]) ?misc-fns-map)]
                       ~body))
        wrap-locals (fn [body locals]
                      ;; locals are supposed to shadow parameters
                      `(let [[~@?locals-ids] ~locals]
                         ~body))
        
        ?wrap-std
        (fn [?f]
          (if ?f
            (let [?more `more#
                  ?locals `locals#]
              `(fn [~?component ~?app-state ~?local-state ~?locals [~@?args] [~@?ref-ids] & ~?more]
                 ;; every user misc fn is also visible; for v1 compat
                 ~(-> `(apply ~?f ~?more)
                      (wrap-misc)
                      (wrap-locals ?locals))))
            'nil))

        ?std-fns-map (assoc ?other-fns-map
                            'render ?render-fn)

        ?wrapped-nlocal-state [['initial-state
                                (when has-local-state?
                                  (let [?locals `locals#]
                                    `(fn [~?component ~?app-state ~?locals [~@?args]]
                                       ;; every user misc fn is also visible; for v1 compat
                                       ~(-> ?initial-local-state-expr
                                            (wrap-misc)
                                            (wrap-locals ?locals)))))]]

        ?wrapped-std (map (fn [[?n ?f]] [?n (?wrap-std ?f)])
                          ?std-fns-map)

        ?fns
        (into {}
              (map (fn [[?n ?f]] [(keyword ?n) ?f])
                   (concat ?wrapped-nlocal-state ?wrapped-std)))

        ?mixins (some-> (get ?clause-map 'mixins)
                        (translate-mixins ?args))

        ?compute-locals
        (when-not (empty? ?locals-ids)
          `(fn [~?app-state [~@?args]]
             (let ~?locals-clauses
               [~@?locals-ids])))

        ?validate (when-let [?validate-expr (get ?clause-map 'validate)]
                    `(fn [~?app-state & ~?args]
                       ~?validate-expr))]
    (when (nil? ?render-fn)
      (throw (compilation-error &env "All classes must have a render clause.")))
    `(reacl2.core/create-class ~?name ~compat-v1? ~(if ?mixins `[~@?mixins] `nil) ~has-app-state? ~?compute-locals ~?validate ~(count ?ref-ids) ~?fns)))

(defmacro defclass
  "Defines a Reacl class.

  The syntax is

      (defclass <name> <this-name> [<app-state-name>] [<param> ...]
        <clause> ...)

  `<name>` is the symbol the class is bound to, and is used together with the
  current namespace as the _display name_ of the class for debugging purposes.

  The presence of `app-state-name` determines if the class has an app-state or not.

  Each `<clause>` has the format `<clause-name> <clause-arg>`.  More specifically,
  it can be one of the following:

       render <renderer-exp>
       local [<local-name> <local-expr>] ...]
       local-state [<local-state-name> <initial-state-exp>]
       refs [<ref-name> ...]
       validate <validation-expr>
       handle-message <messager-handler-exp>
       <lifecycle-method-name> <lifecycle-method-exp>

  Of these, only the `render` clause is mandatory, all the others are optional.
  
  A number of names are bound in the various clauses:

  - `<this-name>` is bound to the component object itself
  - `<app-state-name>` is bound to the app-state of the component
  - the `<param>` ... names are the explicit arguments of instantiations
  - the `<local-name>` names are bound to the values of the
    `<local-expr>` expressions, which can refer to the variables above
  - `<local-state-name>` is bound to the component-local state
    if there is a `local-state` clause

  A `local` clause allows binding additional local variables upon
  instantiation.  The syntax is analogous to `let`.

  A `local-state` clause allows specifying a variable for the
  component's local state, along with an expression for the value of
  its initial value.

  A `refs` clause specifies names for references to sub-components,
  which can be associated with components via `ref` attributes or [[refer]]
  in the `render` expression.
  
  `<renderer-exp>` is an expression that renders the component, and
  hence must return a virtual dom node.

  `<validation-exp>` is evaluated on each instantiation of the class
  for its side-effects, which may be raising an error or an assertion,
  if the app-state or the arguments violate some invariant.

  The `handle-message` function accepts a message sent to the
  component via [[send-message!]] or [[return]].  It's expected to
  return a value specifying a new application state, a new
  component-local state, actions or new messages, or a combination there of, via [[return]].

  A class can be invoked as a function to yield a component. If the
  class does not have an app-state, it just takes values for the
  paramters, like ordinary function applications:

  `(<class> <arg> ...)`

  If the class has an app-state, then a _binding_ has to be specified
  as the first argument, followed by values for the paramters:

  `(<class> <binding> <arg> ...)`

  The binding specifies what the app-state of the component will be,
  as well as how to handle changes to the app-state by that component.
  Bindings are created with [[bind]], [[bind-locally]]
  and [[use-reaction]], and rarely with [[use-app-state]].

  A lifecycle method can be one of:

    `component-will-mount` `component-did-mount`
    `component-will-receive-args` `should-component-update?`
    `component-will-update` `component-did-update` `component-will-unmount`

  These correspond to React's lifecycle methods, see
  here:

  http://facebook.github.io/react/docs/component-specs.html

  (`component-will-receive-args` is similar to `componentWillReceiveProps`.)

  Each right-hand-side `<lifecycle-method-exp>`s should evaluate to a
  function. The arguments, which slightly differ from the
  corresponding React methods, can be seen in the following list:

  `(component-will-mount)` The component can send itself messages in
  this method, or optionally return a new state
  via [[return]]. If that changes the state, the component
  will only render once.

  `(component-did-mount)` The component can update its DOM in this
  method.  It can also return a new state via [[return]],
  but you should take extra care to not create an endless loop of
  updates here.

  `(component-will-receive-args next-arg1 next-arg2 ...)` The
  component has the chance to update its local state in this method
  by sending itself a message or optionally return a new state
  via [[return]].

  `(should-component-update? next-app-state next-local-state next-arg1
  next-arg2 ...)` This method should return if the given new values
  should cause an update of the component (if render should be
  evaluated again). If it's not specified, a default implementation
  will do a (=) comparison with the current values. Implement this, if
  you want to prevent an update on every app-state change for example.

  `(component-will-update next-app-state next-local-state next-arg1 next-arg2 ...)`
  Called immediately before an update.
  
  `(component-did-update prev-app-state prev-local-state prev-arg1 prev-arg2 ...)`
  Called immediately after an update. The component can update its DOM here.
  
  `(component-will-unmount)`
  The component can cleanup it's DOM here for example.

  Note that for classes that don't have an app-state or local-state,
  the corresponding arguments to these livecycle methods will simply be
  `nil`.

  Example:

  ```
    (defrecord New-text [text])
    (defrecord Submit [])
    (defrecord Change [todo])

    (reacl/defclass to-do-app
      this app-state []

      local-state [local-state \"\"]

      render
      (dom/div
       (dom/h3 \"TODO\")
       (dom/div (map (fn [todo]
                       (-> (to-do-item (reacl/reactive todo (reacl/reaction this ->Change) this)
                           (reacl/keyed (str (:id todo)))))
                     (:todos app-state)))
       (dom/form
        {:onsubmit (fn [e _]
                     (.preventDefault e)
                     (reacl/send-message! this (Submit.)))}
        (dom/input {:onchange 
                    (fn [e]
                      (reacl/send-message!
                       this
                       (New-text. (.. e -target -value))))
                    :value local-state})
        (dom/button
         (str \"Add #\" (:next-id app-state)))))

      handle-message
      (fn [msg]
        (cond
         (instance? New-text msg)
         (reacl/return :local-state (:text msg))

         (instance? Submit msg)
         (let [next-id (:next-id app-state)]
           (reacl/return :local-state \"\"
                         :app-state
                         (assoc app-state
                           :todos
                           (concat (:todos app-state)
                                   [(Todo. next-id local-state false)])
                           :next-id (+ 1 next-id))))

         (instance? Delete msg)
         (let [id (:id (:todo msg))]
           (reacl/return :app-state
                         (assoc app-state
                           :todos 
                           (remove (fn [todo] (= id (:id todo)))
                                   (:todos app-state)))))

         (instance? Change msg)
         (let [changed-todo (:todo msg)
               changed-id (:id changed-todo)]
           (reacl/return :app-state
                         (assoc app-state
                           :todos (mapv (fn [todo]
                                          (if (= changed-id (:id todo) )
                                            changed-todo
                                            todo))
                                        (:todos app-state))))))))
  ```"
  [?name & ?stuff]
  `(def ~?name (reacl2.core/class ~(str *ns* "/" ?name) ~@?stuff)))

;; (mixin [<this-name> [<app-state-name> [<local-state-name>]]] [<param> ...])

;; FIXME: should really be restricted to lifecycle methods we know we support

(defmacro mixin
  "Define a mixin. Mixins let you provide additional lifecycle method expressions that
  you can mix into your components.
  
  The syntax is
  
      (mixin [<this> [<app-state> [<local-state>]]] [<param> ...]
        [<lifecycle-method-name> <lifecycle-method-exp> ...])
  
  In order to use the mixin you can use the `mixins` clause in `defclass`

      (defclass foo ...
        mixins [(<your-mixin-var> [<param> ...])]
        ...)

  The lifecycle method expressions in the mixins will be called in order. Only after all
  mixin lifecycle methods have been handled the component's own lifecycle method will be
  called."
  [& ?stuff]
  (let [[?component ?stuff] (split-symbol ?stuff `component#)
        [?app-state ?stuff] (split-symbol ?stuff `app-state#)
        [?local-state ?stuff] (split-symbol ?stuff `local-state#)
          
        [?args & ?clauses] ?stuff

        ?clause-map (apply hash-map ?clauses)
        ?wrap (fn [?f]
                (if ?f
                  (let [?more `more#]
                    `(fn [~?component ~?app-state ~?local-state [~@?args] & ~?more]
                       (apply ~?f ~?more)))
                  'nil))
        ?wrapped (into {}
                       (map (fn [[?n ?f]] [(keyword ?n) (?wrap ?f)])
                            ?clause-map))]
    `(reacl2.core/create-mixin ~?wrapped)))

