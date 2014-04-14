(ns examples.todo.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]))

(enable-console-print!)

(def to-do-list
  (fn [todos]
    (dom/ul (to-array
             (map (fn [itemText]
                    (dom/li {:key itemText} itemText))
                  todos)))))

(def to-do-app
  (reacl/class todos []
   render
   (fn [& {:keys [local-state]}]
     (dom/div
      (dom/h3 "TODO")
      (to-do-list todos)
      (dom/form
       {:onSubmit handle-submit}
       (dom/input {:onChange on-change :value local-state})
       (dom/button
        (str "Add #" (+ (count todos) 1))))))

   initial-state ""

   on-change
   (reacl/event-handler
    (fn [e state]
      (reacl/return :local-state (.. e -target -value))))

   handle-submit
   (reacl/event-handler
    (fn [e _ state]
      (.preventDefault e)
      (reacl/return :app-state (concat todos [state])
                    :local-state "")))))

(js/React.renderComponent
 (reacl/instantiate-toplevel to-do-app [])
 (.getElementById js/document "content"))
