(ns reacl2.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [reacl2.test.core]))

(doo-tests 'reacl2.test.core)
