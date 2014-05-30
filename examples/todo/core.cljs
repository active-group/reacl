(ns examples.todo.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]
            [reacl.lens :as lens]))

(enable-console-print!)

(defrecord Todo [text done?])

(reacl/defclass to-do-item
  todos [lens]
  render
  (fn [& {:keys [dom-node message-handler]}]
    (let [todo (lens/yank todos lens)]
      (dom/letdom
       [checkbox (dom/input
                  {:type "checkbox"
                   :value (:done? todo)
                   :onChange (message-handler
                              (fn [_]
                                (.-checked (dom-node checkbox))))})]
       (dom/div checkbox
                (:text todo)))))
  handle-message
  (fn [checked?]
    (reacl/return :app-state
                  (lens/shove todos
                              (lens/in lens :done?)
                              checked?))))

(defrecord New-text [text])
(defrecord Submit [])

(reacl/defclass to-do-app
  todos []
  render
  (fn [& {:keys [local-state instantiate message-handler]}]
    (dom/div
     (dom/h3 "TODO")
     (dom/div (map-indexed (fn [i todo]
                             (dom/keyed (str i) (instantiate to-do-item (lens/at-index i))))
                           todos))
     (dom/form
      {:onSubmit (message-handler
                  (fn [e _]
                    (.preventDefault e)
                    (Submit.)))}
      (dom/input {:onChange (message-handler
                             (fn [e]
                               (New-text. (.. e -target -value))))
                  :value local-state})
      (dom/button
       (str "Add #" (+ (count todos) 1))))))

  initial-state ""

  handle-message
  (fn [msg local-state]
    (cond
     (instance? New-text msg)
     (reacl/return :local-state (:text msg))
     
     (instance? Submit msg)
     (reacl/return :local-state ""
                   :app-state (concat todos [(Todo. local-state false)])))))

(js/React.renderComponent
 (reacl/instantiate-toplevel to-do-app [])
 (.getElementById js/document "content"))
