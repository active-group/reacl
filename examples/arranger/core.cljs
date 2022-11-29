(ns examples.arranger.core
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
            [active.clojure.cljs.record :refer-macros [define-record-type]]
            [active.clojure.lens :as lens]))

(enable-console-print!)

(define-record-type Select
  select
  select?
  [selection select-selection])

(reacl/defclass arranger this arranged
  [available render-fn]

  ;local-state []

  render
  (dom/div
    (apply dom/select
           {:onchange #(reacl/send-message! this
                                            (select (-> % .-target .-value)))}
           (dom/option {:value ::not-selected} "Select...")
           (mapv (fn [a] (dom/option {:value a} a))
                 available))

    (apply dom/div
           (mapv render-fn arranged)))

  handle-message
  (fn [msg]
    (cond
      (select? msg)
      (reacl/return :app-state (concat arranged [(select-selection msg)])))))

(reacl/defclass root this state []
  render
  (dom/div
   (arranger (reacl/bind this) ["A" "B" "C"] #(dom/h1 %))))

(reacl/render-component
 (.getElementById js/document "app-arranger")
 root [])
