(ns ^{:doc "Supporting macros for Reacl."}
  reacl.core
  (:require [clojure.set :as set])
  (:refer-clojure :exclude [class]))

(defn- split-symbol [stuff dflt]
  (if (symbol? (first stuff))
    [(first stuff) (rest stuff)]
    [dflt stuff]))

(defmacro class
  [?name & ?stuff]
  `(reacl2.core/class ~?name ~@?stuff))

(defmacro defclass
  [?name & ?stuff]
  `(def ~?name (reacl.core/class ~(str ?name) ~@?stuff)))

(defmacro mixin
  [& ?stuff]
  `(reacl2.core/mixin ~@?stuff))

