(ns examples.todo.core
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]))

(enable-console-print!)

;; reusable utilities:

(reacl/defclass checkbox this checked? []
  render
  (dom/input {:type "checkbox"
              :checked checked?
              :onchange (fn [e]
                          (reacl/send-message! this
                                               (.. e -target -checked)))})
  handle-message
  (fn [checked?]
    (reacl/return :app-state checked?)))

(reacl/defclass textbox this value []
  render
  (dom/input {:type "text"
              :value value
              :onchange (fn [e]
                          (reacl/send-message! this
                                               (.. e -target -value)))})
  handle-message
  (fn [text]
    (reacl/return :app-state text)))

(reacl/defclass button this [label action]
  render
  (dom/button {:onclick
               (fn [_]
                 (reacl/send-message! this ::click))}
              label)
  handle-message
  (fn [_]
    (reacl/return :action action)))

(reacl/defclass form this [submit-action & content]
  render
  (apply dom/form
         {:onsubmit (fn [e]
                      (.preventDefault e)
                      (reacl/send-message! this ::submit))}
         content)
  handle-message
  (fn [_]
    (reacl/return :action submit-action)))

;; Specific for this app:

(reacl/defclass to-do-item this todo [delete-action]
  render
  (dom/div (checkbox (reacl/bind this :done?))
           (button "Zap" delete-action)
           (:text todo)))

(defrecord ItemById [id]
  IFn
  (-invoke [_ todos]
    (some #(when (= (:id %) id) %) todos))
  (-invoke [_ todos v]
    (map #(if (= (:id %) id) v %) todos)))

(defn item-by-id [id] (ItemById. id))


(reacl/defclass to-do-item-list this todos [make-delete-item-action]
  render
  (dom/div 
   (map (fn [id]
          (-> (to-do-item (reacl/bind this (item-by-id id))
                          (make-delete-item-action id))
              (reacl/keyed (str id))))
        (map :id todos))))

(defrecord Submit [])

(reacl/defclass add-item-form this [add-action]
  local-state [text ""]

  render
  (-> (form (->Submit)
            (textbox (reacl/bind-locally this))
            (dom/button {:type "submit"} "Add"))
      (reacl/handle-actions this))

  handle-message
  (fn [msg]
    (cond
      (instance? Submit msg) (reacl/return :action (add-action text)
                                           :local-state ""))))

(defrecord TodosApp [next-id todos])

(defrecord Todo [id text done?])

(defrecord AddItem [text])
(defrecord DeleteItem [id])

(reacl/defclass to-do-app this app-state []

  render
  (-> (dom/div {}
               (dom/h3 "TODO")
               (to-do-item-list (reacl/bind this :todos) ->DeleteItem)
               (add-item-form ->AddItem))
      (reacl/handle-actions this))

  handle-message
  (fn [msg]
    (cond
      (instance? AddItem msg)
      (let [next-id (:next-id app-state)
            next-text (:text msg)]
        (reacl/return :app-state
                      (-> app-state
                          (update :todos concat [(->Todo next-id next-text false)])
                          (update :next-id inc))))

      (instance? DeleteItem msg)
      (let [id (:id msg)]
        (reacl/return :app-state
                      (-> app-state
                          (update :todos #(remove (fn [todo] (= id (:id todo)))
                                                  %))))))))

(reacl/render-component
 (.getElementById js/document "app-todo")
 to-do-app
 (TodosApp. 0 []))


          
