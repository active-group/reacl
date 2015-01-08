; python -m SimpleHTTPServer
; and then go to:
; http://localhost:8000/index.html

(ns examples.delayed.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]
            [reacl.util :as util]))

(enable-console-print!)

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
     (dom/p "What you enter in the text field below gets published immediately (via embed and local-state):"
            (filter-input local-state #(reacl/send-message! this %)))
     
     (dom/hr)
     
     (dom/p "What you enter in the text filed below gets published after a delay of one second (via embed and local-state):"
            (util/delayed local-state #(reacl/send-message! this %) filter-input 1000))

     (dom/hr)

     (dom/p "Published value: " state)))

  
  handle-message
  (fn [data]
    (reacl/return :app-state data)))

(reacl/defclass root this state []
  render
  (do
    (dom/div 
     (filter-parent state (constantly nil)))))

(reacl/render-component
 (.getElementById js/document "content")
 root "")
