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
 (dom/div
  (dom/input {:type "text"
              :id "filter"
              :class "form-control"
              :onchange (fn [e]
                          (reacl/send-message! this (.. e -target -value)))}))
 
 handle-message
 (fn [data]
   (reacl/return :app-state data)))

(reacl/defclass filter-parent this state []
  local-state [local-state ""]

  render 
  (do
    (dom/div
     (dom/p "What you enter in the text field below gets published immediately (via embed and local-state):")
     (filter-input (reacl/opt :reaction (reacl/pass-through-reaction this)) local-state)
     
     (dom/hr)
     
     (dom/p "What you enter in the text filed below gets published after a delay of one second (via embed and local-state):")
     (util/delayed (reacl/opt :reaction (reacl/pass-through-reaction this)) local-state filter-input 1000)

     (dom/hr)

     (dom/p "Published value: " state)))

  
  handle-message
  (fn [data]
    (reacl/return :app-state data)))

(reacl/defclass root this state []
  render
  (do
    (dom/div 
     (filter-parent state))))

(reacl/render-component
 (.getElementById js/document "content")
 root "")
