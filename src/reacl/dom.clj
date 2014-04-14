(ns reacl.dom)

(defmacro defdom
  [n]
  `(def ~n (dom-function ~(symbol (str "js/React.DOM." (name n))))))

(defmacro letdom
  [clauses body0 & bodies]
  ;; FIXME: error check
  (let [pairs (partition 2 clauses)]
    `(let [~@(mapcat (fn [p]
                       (let [lhs (first p)]
                         [lhs `(reacl.dom/make-dom-binding ~(str lhs))]))
                     pairs)]
       ~@(map (fn [p]
                (let [lhs (first p)
                      rhs (second p)]
                  `(reacl.dom/set-dom-binding! ~(first p)
                                               ~(second p))))
              pairs)
       ~body0 ~@bodies)))

       
                             
        