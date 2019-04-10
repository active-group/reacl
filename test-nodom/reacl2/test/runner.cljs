(ns reacl2.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [reacl2.test.core-test]
            [reacl2.test.test-util.beta-test]))

(enable-console-print!) ; needed for Node

(doo-tests 'reacl2.test.core-test
           'reacl2.test.test-util.beta-test)
