(ns examples.todo.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]
            [reacl.lens :as lens]))

(enable-console-print!)


(def to-do-item
  (reacl/class todos [lens]
    render
    (fn [& {:keys [dom-node]}]
      (let [todo (lens/yank todos lens)]
        (dom/letdom
         [checkbox (dom/input
                    {:type "checkbox"
                     :value (:done? todo)
                     :onChange (fn [e]
                                 (on-check
                                  (.-checked (dom-node checkbox))))})]
         (dom/div checkbox
                  (:text todo)))))
    on-check
    (reacl/event-handler
     (fn [checked?]
       (reacl/return :app-state
                     (lens/shove todos
                                 (lens/in lens :done?)
                                 checked?))))))

(def to-do-app
  (reacl/class todos []
   render
   (fn [& {:keys [local-state instantiate]}]
     (dom/div
      (dom/h3 "TODO")
      (dom/div (map-indexed (fn [i todo]
                              (dom/keyed (str i) (instantiate to-do-item (lens/at-index i))))
                            todos))
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
    (fn [e _ text]
      (.preventDefault e)
      (reacl/return :app-state (concat todos [{:text text :done? false}])
                    :local-state "")))))

(js/React.renderComponent
 (reacl/instantiate-toplevel to-do-app [])
 (.getElementById js/document "content"))
