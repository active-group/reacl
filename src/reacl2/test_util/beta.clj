(ns reacl2.test-util.beta)

#_(defmacro provided [bindings & body]
  ;; Note: with-redefs-fn is not implemented in clojurescript.
  `(with-redefs-fn (hash-map ~@(mapcat (fn [[s v]]
                                         [`(var ~s) v])
                                       (partition 2 bindings)))
     (fn [] ~@body)))

(defmacro provided [bindings & body]
  ;; Note: this will not work in async tests - can do something similar then?
  (let [pairs (partition 2 bindings)
        olds (gensym "olds")]
    `(let [~olds [~@(map first pairs)]]
       (do ~@(map (fn [[s v]]
                    `(set! ~s ~v))
                  pairs)
           (try (do ~@body)
                (finally
                  (do ~@(map-indexed (fn [i [s v]]
                               `(set! ~s (get ~olds ~i)))
                             pairs))))))))
