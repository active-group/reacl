(ns reacl2.test-util.xpath
  (:require [reacl2.test-util.xpath :as xpath]))

;; convenience macro

(defn- xpath-form [f]
  (cond
    (= '. f) `xpath/self
    (= '.. f) `xpath/parent
    (= '... f) `xpath/root
    (= '/ f) `xpath/children
    (= '** f) `xpath/all  ;; // is not a valid symbol
    :else  ;; else eval
    f))

(defmacro >>
  "Compose the given xpath selector forms to a combined selector, where from left to right, the selectors restrict the query further. Special selector forms are: \n
   - `/` selects the the immediate children.
   - `.` selects/keeps the current node (will only rarely be needed).
   - `..` selects the parent node.
   - `...` selects the root node.
   - `**` selects the current node and all its children and grand children.\n
   Any other form should evaluate to a selector as with [[comp]].\n
   For example `(>> / **)` selects the children and all grand children from the current node.
"
  [& forms]
  (if-not (empty? forms)
    `(xpath/comp ~@(if-not (empty? forms)
                     (cons (xpath-form (first forms))
                           (map xpath-form (rest forms)))
                     (map xpath-form forms)))))
