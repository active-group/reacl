(ns reacl.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [reacl.test.core-test]))

(doo-tests 'reacl.test.core-test)
