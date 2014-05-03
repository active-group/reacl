(ns ^{:author "Michael Sperber"
      :doc "Supporting macros for Reacl."}
  reacl.core
  (:refer-clojure :exclude [class]))

(defmacro class
  "Create a Reacl class.

   This is a regular React class, with some convenience added, as well as:

   - implicit propagation of app state
   - a pure model for event handlers

   The syntax is

   (reacl/class <app-state> [<param> ...]
     render <renderer-exp>
     [initial-state <initial-state-exp>]

     <event-handler-name> <event-handler-exp> ...)

   <app-state> is name bound to the global application state (implicitly
   propagated through instantiation, see below), and <param> ... the names
   of explicit arguments of instantiations (see below).  These names are
   bound in <renderer-exp>, <initial-state-exp> as well as all
   <event-handler-exp>s.

   <renderer-exp> must evaluate to a function that renders the component,
   and hence must return a virtual dom node.  It gets passed several
   keyword arguments:

   - instantiate: an instantiation function for subcomponents; see below
   - local-state: component-local state
   - dom-node: a function for retrieving \"real\" dom nodes corresponding
     to virtual dom nodes

   The instantiate function is for instantiating Reacl subcomponents; it
   takes a Reacl class and arguments corresponding to the Reacl class's
   <param>s.  It implicitly passes the application state to the
   subcomponent.

   The component's local-state is an arbitrary object representing the
   component's local state.

   The dom-node function takes a named dom object bound via
   reacl.dom/letdom and yields its corresponding \"real\" dom object for
   extracting GUI state.

   Everything that's not a renderer or an initial-state is assumed to
   be a binding for a function, typically an event handler.  These
   functions will all be bound under their corresponding
   <event-handler-name>s in <renderer-exp>.

   Example:

   (def to-do-app
     (reacl.core/class todos []
      render
      (fn [& {:keys [local-state instantiate]}]
        (reacl.dom/div
         (reacl.dom/h3 \"TODO\")
         (reacl.dom/div (map-indexed (fn [i todo]
                                 (reacl.dom/keyed (str i) (instantiate to-do-item (reacl.lens/at-index i))))
                               todos))
         (reacl.dom/form
          {:onSubmit handle-submit}
          (reacl.dom/input {:onChange on-change :value local-state})
          (reacl.dom/button
           (str \"Add #\" (+ (count todos) 1))))))

      initial-state \"\"

      on-change
      (reacl.core/event-handler
       (fn [e state]
         (reacl.core/return :local-state (.. e -target -value))))

      handle-submit
      (reacl.core/event-handler
       (fn [e _ text]
         (.preventDefault e)
         (reacl.core/return :app-state (concat todos [{:text text :done? false}])
                            :local-state \"\")))))"
  [?app-state [& ?args] & ?clauses]
  (let [map (apply hash-map ?clauses)
        render (get map 'render)
        wrap-args
        (fn [?this & ?body]
          `(let [~?app-state (reacl.core/extract-app-state ~?this)
                 [~@?args] (reacl.core/extract-args ~?this)] ; FIXME: what if empty?
             ~@?body))
        initial-state (if-let [?expr (get map 'initial-state)]
                        (let [?this `this#]
                          `(fn [] 
                             (cljs.core/this-as
                              ~?this
                              ~(wrap-args ?this `(reacl.core/make-local-state ~?expr)))))
                        `(fn [] (reacl.core/make-local-state nil)))
        misc (filter (fn [e]
                       (not (contains? #{'render 'initial-state} (key e))))
                     map)
        renderfn
        (let [?this `this#  ; looks like a bug in ClojureScript, this# produces a warning but works
              ?state `state#]
          `(fn []
             (cljs.core/this-as 
              ~?this
              (let [~?state (reacl.core/extract-local-state ~?this)]
                ~(wrap-args
                  ?this
                  `(let [~@(mapcat (fn [p]
                                     [(first p) `(aget ~?this ~(str (first p)))])
                                    misc)]
                     (binding [reacl.core/*component* ~?this]
                       (~render :instantiate (fn [clazz# & props#] (apply reacl.core/instantiate clazz#
                                                                          (reacl.core/extract-toplevel ~?this)
                                                                          ~?app-state
                                                                          props#))
                                :local-state ~?state
                                :dom-node (fn [dn#] (reacl.dom/dom-node-ref ~?this dn#))
                                :this ~?this))))))))]
    `(js/React.createClass (cljs.core/js-obj "render" ~renderfn 
                                             "getInitialState" ~initial-state 
                                             ~@(mapcat (fn [[?name ?rhs]]
                                                         [(str ?name) 
                                                          (let [?args `args#
                                                                ?this `this#]
                                                            `(fn [& ~?args]
                                                               (cljs.core/this-as
                                                                ~?this
                                                                (binding [reacl.core/*component* ~?this]
                                                                  (apply ~(wrap-args ?this ?rhs) ~?args)))))])
                                                       misc)))))
