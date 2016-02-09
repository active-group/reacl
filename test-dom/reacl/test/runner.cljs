(ns reacl.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [reacl.test.core]))

(doo-tests 'reacl.test.core)
