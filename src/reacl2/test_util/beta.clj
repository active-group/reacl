(ns reacl2.test-util.beta)

#_(defmacro provided [bindings & body]
  ;; Note: with-redefs-fn is not implemented in clojurescript.
  `(with-redefs-fn (hash-map ~@(mapcat (fn [[s v]]
                                         [`(var ~s) v])
                                       (partition 2 bindings)))
     (fn [] ~@body)))

(defmacro provided
  "This uses replaces the values bound to the given vars during the
  evaluation of `body`, and sets them back to the previous values
  afterwards. Example:

  ```
  (def x 42)
  (provided [x 11]
    (is (= (* x 2) 22)))
  (is (= x 42))
  ```

  You can use this to isolate a test of one class from the
  implementation of another class used in it, by replacing it with a mock.
  Do this only if isolating the test via [[inject-return!]] is not enough."
  [bindings & body]
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
