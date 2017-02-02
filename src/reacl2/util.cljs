(ns ^{:author "Marcus Crestani"
      :doc "Various utilities for Reacl."}
  reacl2.util
  (:require [reacl2.core :as core :include-macros true]
            [reacl2.dom :as dom :include-macros true]))

(core/defclass 
  ^{:doc 
    "A reacl class that delays `clazz`'s state change for `delay`
     milliseconds.

     Example:

     This is a reacl class that implements a text input field that can
     be used as search field:

          (reacl/defclass search-input this state []
            render
            (dom/letdom [search-input 
                         (dom/input {:type \"text\"
                                     :id \"search\"
                                     :onChange #(reacl/send-message! this (.-value (dom/dom-node this search-input)))})]
              (dom/div search-input))
            handle-message
            (fn [data]
              (reacl/return :app-state data)))
   
     Often it is not efficient to actually perform a search and update the
     search results after every key the user types, but only after the user
     hasn't type anything in while.  The `delayed` reacl class provides a
     way to delay the propagatation of the `search-field`'s state change.
     Here, the search field's update to the application state is published
     after a delay of 200 milliseconds of inactivity:
     
          (delayed this search-input 200)

     Or, to keep the internal state of `delayed` and `search-input`
     separate from the surrounding application's state, you can embed
     `delayed`:

          (delayed this local-state (reacl/pass-through-reaction this) filter-input 200)"}
  delayed this state [clazz delay & args]
  render
  (apply clazz
         (core/opt :reaction (core/reaction this (fn [app-state] [:update app-state])))
         state
         args)

  local-state [local-state {:st nil :id nil}]

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
