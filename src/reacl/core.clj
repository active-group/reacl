(ns ^{:no-doc true
      :doc "Supporting macros for Reacl."}
  reacl.core
  (:require [clojure.set :as set]
            [reacl2.core])
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
                        ~'compat-v1? true
                        ~@(apply concat ?clause-map))))

(defmacro defclass
  [?name & ?stuff]
  `(def ~?name (reacl.core/class ~(str ?name) ~@?stuff)))

(defmacro mixin
  [& ?stuff]
  `(reacl2.core/mixin ~@?stuff))

(defn- split-symbol-dflt [stuff dflt]
  (if (symbol? (first stuff))
    [(first stuff) (rest stuff)]
    [dflt stuff]))

(defmacro view
  [?name & ?stuff]
  (let [[?component ?stuff] (split-symbol-dflt ?stuff `component#)
        ?app-state `app-state#
        [?local-state ?stuff] (split-symbol-dflt ?stuff `local-state#)

        [?args & ?clauses] ?stuff

        ;; this adds app-state arg to component-will-update,
        ;; component-did-update, should-component-update?
        add-arg (fn [?current-fn]
                  (when ?current-fn
                    (let [?ignore `ignore#
                          ?args `args#]
                      `(fn [~?ignore & ~?args]
                         (apply ~?current-fn ~?args)))))
        ?clause-map (-> (apply hash-map ?clauses)
                        (update-in ['component-will-update] add-arg)
                        (update-in ['component-did-update] add-arg)
                        (update-in ['should-component-update?] add-arg))
        ?clauses (apply concat ?clause-map)
        ]
    `(reacl.core/class->view
      (reacl.core/class ~?name ~?component ~?app-state ~?local-state [~@?args]
                        ~@?clauses))))

(defmacro defview
  [?name & ?stuff]
  `(def ~?name (reacl.core/view ~(str ?name) ~@?stuff)))
