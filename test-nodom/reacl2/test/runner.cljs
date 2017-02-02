(ns reacl2.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [reacl2.test.core-test]))

(doo-tests 'reacl2.test.core-test)
