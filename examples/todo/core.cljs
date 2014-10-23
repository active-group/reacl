(ns examples.todo.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]
            [active.clojure.lens :as lens]))

(enable-console-print!)

(defn at-key
  [extract-key key]
  (lens/lens (fn [coll]
               (some (fn [el]
                       (and (= key (extract-key el))
                            el))
                     coll))
             (fn [coll v]
               (map (fn [el]
                      (if (= key (extract-key el))
                        v
                        el))
                    coll))))

(defn map-keyed
  [extract-key f coll]
  (map (fn [el]
         (let [key (extract-key el)]
           (f el key (at-key extract-key key))))
       coll))

(defrecord TodosApp [next-id todos])

(defrecord Todo [id text done?])

(defrecord Delete [todo])

(reacl/defclass to-do-item
  this app-state [parent lens]
  render
  (let [todo (lens/yank app-state lens)]
    (dom/letdom
     [checkbox (dom/input
                {:type "checkbox"
                 :value (:done? todo)
                 :onChange #(reacl/send-message! this
                                                 (.-checked (dom/dom-node this checkbox)))})]
     (dom/div checkbox
              (dom/button {:onClick #(reacl/send-message! parent
                                                           (Delete. todo))}
                          "Zap")
              (:text todo))))
  handle-message
  (fn [checked?]
    (reacl/return :app-state
                  (lens/shove app-state
                              (lens/>> lens :done?)
                              checked?))))

(defrecord New-text [text])
(defrecord Submit [])

(reacl/defclass to-do-app
  this app-state local-state []
  render
  (dom/div
   (dom/h3 "TODO")
   (dom/div (map-keyed :id
                       (fn [todo id lens]
                         (dom/keyed (str id) (to-do-item this this (lens/>> :todos lens))))
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
                                  :next-id (+ 1 next-id))))

     (instance? Delete msg)
     (let [id (:id (:todo msg))]
       (reacl/return :app-state
                     (assoc app-state
                       :todos (remove (fn [todo] (= id (:id todo))) (:todos app-state))))))))

(reacl/render-component
 (.getElementById js/document "content")
 to-do-app (TodosApp. 0 []))
