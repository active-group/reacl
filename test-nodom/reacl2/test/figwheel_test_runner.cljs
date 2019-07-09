(ns reacl2.test.figwheel-test-runner
  (:require [figwheel.main.testing :refer-macros [run-tests-async]]
            [reacl2.test.core-test]))


(defn -main [& args]
  (run-tests-async 10000))
