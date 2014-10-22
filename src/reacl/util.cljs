(ns ^{:author "Marcus Crestani"
      :doc "Various utilities for Reacl."}
  reacl.util
  (:require [reacl.core :as core :include-macros true]
            [reacl.dom :as dom :include-macros true]))

(core/defclass 
  ^{:doc "A reacl class that delays `clazz`'s state change for `delay` milliseconds."}
  delayed this state local-state [clazz delay & args]
  render
  (apply core/embed clazz this
         state
         #(core/send-message! this [:update %1])
         args)

  initial-state 
  {:st nil
   :id nil}

  handle-message
  (fn [[msg data]]
    (case msg
     :update
     (do
       (when (:id local-state)
         (.clearTimeout js/window (:id local-state)))
       (let [id (.setTimeout js/window #(core/send-message! this [:publish]) delay)]
         (core/return :local-state (assoc local-state
                                      :st data
                                      :id id))))
     
     :publish
     (do
       (core/return :app-state
                     (:st local-state))))))

(defn values-of-child-nodes
  "Returns a list of values of all child nodes of a parent dom object."
  [this parent]
  (let [children (.-childNodes (dom/dom-node this parent))]
    (map #(.-value %)
         (map #(.item children %) (range (.-length children))))))

(defn make-datalist
  "Map a list of options to a datalist."
  [id & [options]]
  (apply dom/datalist 
         {:id id}
         (map #(dom/option %) options)))
