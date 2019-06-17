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

(defrecord Delete [])

(reacl/defclass to-do-item-list this todos [make-delete-item-action]
  render
  (dom/div 
   (map (fn [todo]
          (-> (to-do-item (reacl/bind this (item-by-id (:id todo)))
                          (->Delete))
              (reacl/keyed (str (:id todo)))
              (reacl/map-action 
               (fn [act]
                 (cond
                   (instance? Delete act) (make-delete-item-action (:id todo))
                   :else act)))))
        todos)))

(reacl/defclass add-item-form this text [submit-action next-id]
  render
  (form submit-action
        (textbox (reacl/bind this))
        (dom/button {:type "submit"}
                    (str "Add #" next-id))))

(defrecord TodosApp [next-id todos])

(defrecord Todo [id text done?])

(defrecord Submit [])
(defrecord DeleteItem [id])

(reacl/defclass to-do-app this app-state []

  local-state [next-text ""]

  render
  (-> (dom/div {}
               (dom/h3 "TODO")
               (to-do-item-list (reacl/bind this :todos) ->DeleteItem)
               (add-item-form (reacl/bind-locally this)
                              (->Submit) (:next-id app-state)))
      (reacl/reduce-action (fn [_ act]
                             (cond
                               (instance? Submit act) (reacl/return :message [this act])
                               (instance? DeleteItem act) (reacl/return :message [this act])
                               :else (reacl/return :action act)))))

  handle-message
  (fn [msg]
    (cond
      (instance? Submit msg)
      (let [next-id (:next-id app-state)]
        (reacl/return :local-state ""
                      :app-state
                      (assoc app-state
                             :todos
                             (concat (:todos app-state)
                                     [(->Todo next-id next-text false)])
                             :next-id (+ 1 next-id))))

      (instance? DeleteItem msg)
      (let [id (:id msg)]
        (reacl/return :app-state
                      (assoc app-state
                             :todos 
                             (remove (fn [todo] (= id (:id todo)))
                                     (:todos app-state))))))))

(reacl/render-component
 (.getElementById js/document "app-todo")
 to-do-app
 (TodosApp. 0 []))


          
