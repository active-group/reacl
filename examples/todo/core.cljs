(ns examples.todo.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]))

(enable-console-print!)

(defrecord TodosApp [next-id todos])

(defrecord Todo [id text done?])

(defrecord Delete [todo])

(def mix
  (reacl/mixin foo app-state local-state 
    [parent]
    component-did-mount (fn []
                          (println "DID MOUNT!" parent foo app-state local-state))
    component-will-update (fn [next-app-state next-local-state]
                            (println "WILL UPDATE!" next-app-state next-local-state))
    component-did-update (fn [previous-app-state previous-local-state]
                           (println "DID UPDATE!" previous-app-state previous-local-state))))
               
               

(reacl/defclass to-do-item
  this todo [parent]
  mixins [(mix parent)]
  render
  (dom/div (dom/input
            {:type "checkbox"
             :value (:done? todo)
             :onchange (fn [e]
                         (reacl/send-message! this
                                              (.. e -target -checked)))})
           (dom/button
            {:onclick
             (fn [_]
               (reacl/send-message! parent (->Delete todo)))}
            "Zap")
           (:text todo))
  handle-message
  (fn [checked?]
    (reacl/return :app-state
                  (assoc todo :done? checked?))))

(defrecord NewText [text])
(defrecord Submit [])
(defrecord Change [todo])

(reacl/defclass to-do-app
  this app-state local-state []

  initial-state ""

  render
  (dom/div
   (dom/h3 "TODO")
   (dom/div 
    (map (fn [todo]
           (dom/keyed (str (:id todo))
                      (to-do-item
                       todo
                       :reaction (reacl/reaction this ->Change)
                       this)))
         (:todos app-state)))
   (dom/form
    {:onsubmit (fn [e]
                  (.preventDefault e)
                  (reacl/send-message! this (->Submit)))}
    (dom/input {:onchange 
                (fn [e]
                  (reacl/send-message!
                   this
                   (->NewText (.. e -target -value))))
                :value local-state})
    (dom/button
     (str "Add #" (:next-id app-state)))))


  handle-message
  (fn [msg]
    (cond
     (instance? NewText msg)
     (reacl/return :local-state (:text msg))
     
     (instance? Submit msg)
     (let [next-id (:next-id app-state)]
       (reacl/return
        :local-state ""
        :app-state
        (assoc app-state
               :todos
               (concat (:todos app-state)
                       [(->Todo next-id local-state false)])
               :next-id (+ 1 next-id))))

     (instance? Delete msg)
     (let [id (:id (:todo msg))]
       (reacl/return :app-state
                     (assoc app-state
                       :todos 
                       (remove (fn [todo] (= id (:id todo)))
                               (:todos app-state)))))

     (instance? Change msg)
     (let [changed-todo (:todo msg)
           changed-id (:id changed-todo)]
       (reacl/return :app-state
                     (assoc app-state
                       :todos (map (fn [todo]
                                     (if (= changed-id (:id todo) )
                                       changed-todo
                                       todo))
                                   (:todos app-state))))))))

(reacl/render-component
 (.getElementById js/document "content")
 to-do-app
 (TodosApp. 0 []))


          
