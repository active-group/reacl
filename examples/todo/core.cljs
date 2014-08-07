(ns examples.todo.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]
            [reacl.lens :as lens]))

(enable-console-print!)

(defrecord TodosApp [next-id todos])

(defrecord Todo [id text done?])

(reacl/defclass to-do-item
  this app-state [lens]
  render
  (let [todo (lens/yank app-state lens)]
    (dom/letdom
     [checkbox (dom/input
                {:type "checkbox"
                 :value (:done? todo)
                 :onChange #(reacl/send-message! this
                                                 (.-checked (dom/dom-node this checkbox)))})]
     (dom/div checkbox
              (:text todo))))
  handle-message
  (fn [checked?]
    (reacl/return :app-state
                  (lens/shove app-state
                              (lens/in lens :done?)
                              checked?))))

(defrecord New-text [text])
(defrecord Submit [])

(reacl/defclass to-do-app
  this app-state local-state []
  render
  (dom/div
   (dom/h3 "TODO")
   (dom/div (lens/map-keyed :id
                            (fn [todo id lens]
                              (dom/keyed (str id) (to-do-item this (lens/in :todos lens))))
                            (:todos app-state)))
   (dom/form
    {:onSubmit (fn [e _]
                 (.preventDefault e)
                 (reacl/send-message! this (Submit.)))}
    (dom/input {:onChange (fn [e]
                            (reacl/send-message! this
                                                 (New-text. (.. e -target -value))))
                :value local-state})
    (dom/button
     (str "Add #" (:next-id app-state)))))

  initial-state ""

  handle-message
  (fn [msg]
    (cond
     (instance? New-text msg)
     (reacl/return :local-state (:text msg))
     
     (instance? Submit msg)
     (let [next-id (:next-id app-state)]
       (reacl/return :local-state ""
                     :app-state (assoc app-state
                                  :todos (concat (:todos app-state) [(Todo. next-id local-state false)])
                                  :next-id (+ 1 next-id)))))))

(reacl/render-component
 (.getElementById js/document "content")
 to-do-app (TodosApp. 0 []))