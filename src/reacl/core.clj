(ns reacl.core
  (:refer-clojure :exclude [class]))

(defmacro class
  [?app-state [& ?args] & ?clauses]
  (let [map (apply hash-map ?clauses)
        render (get map 'render)
        wrap-args
        (fn [?this & ?body]
          `(let [~?app-state (reacl.core/extract-app-state ~?this)
                 [~@?args] (reacl.core/extract-props ~?this)] ; FIXME: what if empty?
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
                       (~render :instantiate (fn [clazz# & props#] (apply instantiate clazz#
                                                                          (.. ~?this -props -reacl_top_level)
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
