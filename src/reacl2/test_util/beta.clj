(ns reacl2.test-util.beta)

(defmacro let-test-class [bindings & body]
  (assert (vector? bindings))
  (assert (= 1 (count bindings)))
  `(let [~@(mapcat (fn [[v c]]
                     (assert (list? c))
                     (assert (not-empty c))
                     `[~v (reacl2.test-util.beta/test-class ~(first c) ~@(rest c))])
                   bindings)]
     ~@body))
