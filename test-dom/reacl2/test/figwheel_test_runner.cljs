(ns reacl2.test.figwheel-test-runner
  (:require [figwheel.main.testing :refer-macros [run-tests-async]]
            [reacl2.test.core]))


(defn -main [& args]
  (run-tests-async 10000))
