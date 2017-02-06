(ns ^{:doc "Supporting macros for Reacl."}
  reacl.core
  (:require [clojure.set :as set])
  (:refer-clojure :exclude [class]))

(defn- split-symbol [stuff]
  (if (symbol? (first stuff))
    [(first stuff) (rest stuff)]
    [nil stuff]))

(defmacro class
  [?name & ?stuff]
  (let [[?component ?stuff] (split-symbol ?stuff)
        [?app-state ?stuff] (split-symbol ?stuff)
        [?local-state ?stuff] (split-symbol ?stuff)
        ?special-args (filter identity [?component ?app-state])
        
        [?args & ?clauses] ?stuff
        ?clause-map (apply hash-map ?clauses)
        ?initial-state (get ?clause-map 'initial-state)
        ?clause-map (dissoc ?clause-map 'initial-state)]
    `(reacl2.core/class ~?name ~@?special-args ~?args
                        ~@(if ?local-state
                            `(~'local-state [~?local-state ~?initial-state])
                            '())
                        ~@(apply concat ?clause-map))))

(defmacro defclass
  [?name & ?stuff]
  `(def ~?name (reacl.core/class ~(str ?name) ~@?stuff)))

(defmacro mixin
  [& ?stuff]
  `(reacl2.core/mixin ~@?stuff))

