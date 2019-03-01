(ns ^{:doc "Supporting macros for Reacl's DOM library   ."}
  reacl2.dom)

(defmacro ^:no-doc defdom
  "Internal macro for constructing DOM-construction wrappers."
  [n]
  `(def ~(vary-meta n assoc
                    :doc (str "Returns a dom element corresponding to a `" n "` tag. The `attrs` argument is an optional map of attributes. The remaining `children` arguments must be other elements or strings.")
                    :arglists '([attrs & children] [& children]))
     (dom-function ~(name n))))

(defmacro letdom
  "Bind DOM nodes to names for use in event handlers.

  This should be used together with [[reacl.core/class]] or [[reacl.core/defclass]].

  Its syntax is like `let`, but all right-hand sides must evaluate to
  virtual DOM nodes - typically input elements.

  The objects can be used with the [[dom-node]] function,
  which returns the corresponding real DOM node.

  Example:

      (reacl.core/defclass search-bar
        app-state [filter-text in-stock-only on-user-input]
        render
        (fn [& {:keys [dom-node]}]
          (dom/letdom
           [textbox (dom/input
                     {:type \"text\"
                      :placeholder \"Search...\"
                      :value filter-text
                      :onChange (fn [e]
                                  (on-user-input
                                   (.-value (dom-node textbox))
                                   (.-checked (dom-node checkbox))))})
            checkbox (dom/input
                      {:type \"checkbox\"
                       :value in-stock-only
                       :onChange (fn [e]
                                   (on-user-input
                                    (.-value (dom-node textbox))
                                    (.-checked (dom-node checkbox))))})]
           (dom/form
            textbox
            (dom/p
             checkbox
             \"Only show products in stock\")))))

  Note that the resulting DOM-node objects need to be used together
  with the other DOM wrappers in `reacl2.dom`."
  [clauses body0 & bodies]
  ;; FIXME: error check
  (let [pairs (partition 2 clauses)]
    `(let [~@(mapcat (fn [p]
                       (let [lhs (first p)]
                         (cond
                          (symbol? lhs)
                          `[~lhs (reacl2.dom/make-dom-binding '~lhs false)]
                          
                          (and (list? lhs)
                               (= (count lhs) 2)
                               (= :literally (first lhs))
                               (symbol? (second lhs)))
                          `[~(second lhs) (reacl2.dom/make-dom-binding '~(second lhs) true)]
                          
                          :else
                          (throw (Exception. (str "Not a valid letdom lhs " lhs))))))
                     pairs)]
       ~@(map (fn [p]
                (let [lhs (first p)
                      rhs (second p)]
                  `(reacl2.dom/set-dom-binding! ~(first p)
                                                ~(second p))))
              pairs)
       ~body0 ~@bodies)))
