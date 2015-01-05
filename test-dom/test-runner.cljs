(ns test-runner
  (:require
   [cljs.test :refer-macros [run-tests]]
   [reacl.test.core]))


(enable-console-print!)

(defn runner []
  (if (cljs.test/successful?
        (run-tests
          'reacl.test.core))
    0
    1))
