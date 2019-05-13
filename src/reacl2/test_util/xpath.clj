(ns reacl2.test-util.xpath
  (:require [reacl2.test-util.xpath :as xpath]))

;; convenience macro

(defn- xpath-form [f]
  (cond
    (= '. f) `xpath/self
    (= '.. f) `xpath/parent
    (= '... f) `xpath/root
    (= '/ f) `xpath/children
    (= '** f) `xpath/all
    (vector? f) `(xpath/where (xpath/>> ~@f))
    :else  ;; else eval
    f))

(defmacro >>
  "Compose the given xpath selector forms to a combined selector, where from left to right, the selectors restrict the filter further. Special selector forms are: \n
   - `/` selects the the immediate children.
   - `.` selects/keeps the current node (will only rarely be needed).
   - `..` selects the parent node.
   - `...` selects the root node.
   - `**` selects the current node and all its children and grand children.\n
   - `[x y]` filters as with `(has? (>> x y))`\n
   Any other form should evaluate to a selector as with [[comp]].\n
   For example `(>> / **)` selects the children and all grand children from the current node.
"
  [& forms]
  `(xpath/comp ~@(map xpath-form forms)))
