(ns examples.todo.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]))

(enable-console-print!)

(defrecord TodosApp [next-id todos])

(defrecord Todo [id text done?])

(defrecord Delete [todo])

(reacl/defclass to-do-item
  this todo [parent]
  render
  (dom/letdom
   [checkbox (dom/input
              {:type "checkbox"
               :value (:done? todo)
               :onChange #(reacl/send-message! this
                                               (.-checked (dom/dom-node this checkbox)))})]
   (dom/div checkbox
            (dom/button {:onClick #(reacl/send-message! parent (Delete. todo))}
                        "Zap")
            (:text todo)))
  handle-message
  (fn [checked?]
    (reacl/return :app-state
                  (assoc todo :done? checked?))))

(defrecord New-text [text])
(defrecord Submit [])
(defrecord Change [todo])

(reacl/defview to-do-app
  this local-state [initial-todos]
  render
  (dom/div
   (dom/h3 "TODO")
   (dom/div (map (fn [todo]
                   (dom/keyed (str (:id todo))
                              (to-do-item todo
                                          (reacl/reaction this ->Change)
                                          this)))
                 (:todos local-state)))
   (dom/form
    {:onSubmit (fn [e _]
                 (.preventDefault e)
                 (reacl/send-message! this (Submit.)))}
    (dom/input {:onChange (fn [e]
                            (reacl/send-message! this
                                                 (New-text. (.. e -target -value))))
                :value (:input local-state)})
    (dom/button
     (str "Add #" (:next-id local-state)))))

  initial-state (assoc initial-todos
                  :input "")

  handle-message
  (fn [msg]
    (cond
     (instance? New-text msg)
     (reacl/return :local-state (assoc local-state :input (:text msg)))
     
     (instance? Submit msg)
     (let [next-id (:next-id local-state)]
       (reacl/return :local-state (assoc local-state
                                    :input ""
                                    :todos (concat (:todos local-state) [(Todo. next-id (:input local-state) false)])
                                    :next-id (+ 1 next-id))))

     (instance? Delete msg)
     (let [id (:id (:todo msg))]
       (reacl/return :local-state
                     (assoc local-state
                       :todos (remove (fn [todo] (= id (:id todo))) (:todos local-state)))))

     (instance? Change msg)
     (let [changed-todo (:todo msg)
           changed-id (:id changed-todo)]
       (reacl/return :local-state
                     (assoc local-state
                       :todos (mapv (fn [todo]
                                      (if (= changed-id (:id todo) )
                                        changed-todo
                                        todo))
                                    (:todos local-state))))))))

(reacl/render-component
 (.getElementById js/document "content")
 to-do-app (TodosApp. 0 []))
