; python -m SimpleHTTPServer
; and then go to:
; http://localhost:8000/index.html

(ns examples.delayed.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]))

(enable-console-print!)

(reacl/defclass delayed this state local-state [clazz delay publish-callback & args]
  render
  (apply reacl/embed clazz this
         state
         #(reacl/send-message! this [:update %1])
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
       (let [id (.setTimeout js/window #(reacl/send-message! this [:publish]) delay)]
         (reacl/return :local-state (assoc local-state
                                      :st data
                                      :id id))))
     
     :publish
     (do
       (reacl/return :app-state
                     (publish-callback (:st local-state)))))))

(reacl/defclass filter-input this state []
 render
 (dom/letdom [filter-input (dom/input {:type "text"
                                       :id "filter"
                                       :className "form-control"
                                       :onChange #(reacl/send-message! this (.-value (dom/dom-node this filter-input)))})]
             (dom/div filter-input))

 handle-message
 (fn [data]
   (reacl/return :app-state data)))

(reacl/defclass filter-parent this state local-state []
  initial-state ""

  render 
  (do
    (dom/div
     (dom/p "What you enter in the text field below gets published immediately:"
            (reacl/embed filter-input this local-state #(reacl/send-message! this %)))

     (dom/p "What you enter in the text field below gets published after a delay of one second:"
            (delayed this filter-input 1000 #(reacl/send-message! this %)))

     (dom/p "Published value: " state)))

  
  handle-message
  (fn [data]
    (reacl/return :app-state data)))

(reacl/defclass root this state []
  render
  (do
    (dom/div 
     (filter-parent this))))

(reacl/render-component
 (.getElementById js/document "content")
 root "")
