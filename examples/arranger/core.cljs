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

(define-record-type Unselect
  unselect
  unselect?
  [selection unselect-selection])

(defn remove-selection
  [selected selection]
  (remove #(= selection %) selected))

(defn add-selection
  [selected selection]
  (concat selected [selection]))

(defn selection-
  [s1 s2]
  (vec (reduce (fn [result selection]
                 (remove-selection result selection))
               s1
               s2)))

(reacl/defclass arranger this arranged
  [available render-fn]

  render
  (letfn [(render-selection
            [thing]
            (dom/div
              (dom/button {:onclick #(reacl/send-message! this
                                                          (unselect thing))}
                          "X")
              (dom/div (render-fn thing))))]
    (dom/div
      (boot-strap-menu-bar
      (apply dragabble

             dom/li
             (mapv #(render-selection %) arranged))

      (let [selectable (selection- available arranged)]
        (when-not (empty? selectable)
          (apply dom/select
                 {:value ::not-selected
                  :onchange #(reacl/send-message! this
                                                  (select (-> % .-target .-value)))}
                 (dom/option {:value ::not-selected} "Select...")
                 (mapv (fn [a] (dom/option {:value a} a)) selectable))))))

  handle-message
  (fn [msg]
    (cond
      (select? msg)
      (reacl/return :app-state (add-selection arranged (select-selection msg)))

      (unselect? msg)
      (reacl/return :app-state (remove-selection arranged (unselect-selection msg)))))))

(reacl/defclass root this state []
  render
  (dom/div
   (arranger (reacl/bind this) ["A" "B" "C"] #(dom/h1 %))))

(reacl/render-component
 (.getElementById js/document "app-arranger")
 root [])
