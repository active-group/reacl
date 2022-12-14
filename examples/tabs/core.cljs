(ns examples.tabs.core
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
            [active.clojure.cljs.record :refer-macros [define-record-type]]
            [active.clojure.lens :as lens]))

(enable-console-print!)

;; User actions

(define-record-type SelectTab
  select-tab
  select-tab?
  [tab select-tab-tab])

(define-record-type HideTab
  hide-tab
  hide-tab?
  [tab hide-tab-tab])

(define-record-type AddTab
  add-tab
  add-tab?
  [tab add-tab-tab])

(define-record-type DragStart
  drag-start
  drag-start?
  [tab drag-start-tab])

(define-record-type DragOver
  drag-over
  drag-over?
  [tab drag-over-tab])

(define-record-type DragStop
  drag-stop
  drag-stop?
  [tab drag-stop-tab])

(define-record-type DragEnter
  drag-enter
  drag-enter?
  [tab drag-enter-tab])

(define-record-type DragLeave
  drag-leave
  drag-leave?
  [tab drag-leave-tab])

(define-record-type DragEnd
  drag-end
  drag-end?
  [tab drag-end-tab])

;; Internal actions

(def render-tab-name identity)

(defn remove-tab
  [tabs tab]
  (remove #(= % tab) tabs))

(defn remove-tabs
  [all-tabs tabs-to-remove]
  (reduce remove-tab all-tabs tabs-to-remove))

(defn split-tabs-before-tab
  [tabs tab]
  (split-with (complement #{tab}) tabs))

(defn insert-tab-before
  [tabs tab-to-insert tab-to-insert-before]
  (if (= tab-to-insert tab-to-insert-before)
    tabs
    (let [[before after] (split-tabs-before-tab (remove-tab tabs tab-to-insert)
                                                tab-to-insert-before)]
      (concat before [tab-to-insert] after))))

(defn mapcat-indexed
  [f & args]
  (apply mapcat f (range) args))

;; ?

(define-record-type Tabbed
  make-tabbed
  tabbed?
  [selected-tab tabbed-selected-tab
   tabs tabbed-tabs])

(define-record-type TabState
  make-tab-state
  tab-state?
  [previous-tab tab-state-previous-tab
   dragging-tab tab-state-dragging-tab
   dragging-over tab-state-dragging-over])

(def initial-tab-state (make-tab-state nil nil nil))

(reacl/defclass tabbed-navigation this tabbed
  [all-tabs & [render-tab-name]]

  local-state [local-state initial-tab-state]

  ;; TAB-AREA
  render
  (let [render-tab-name (or render-tab-name identity)
        hidden-tabs (remove-tabs all-tabs (tabbed-tabs tabbed))]
    (apply dom/ul {:class "nav nav-tabs"}
           ;; DEF DRAG-AND-DROP-SPACER
           (let [render-drag-and-drop-spacer
                 (fn [idx tab tab-before]
                   (dom/keyed
                    (str "drag-and-drop-spacer-" idx "-" (render-tab-name tab))
                    (dom/li
                     {:style {:transition "width 200ms ease"
                              :width (if (and (not= (tab-state-dragging-tab local-state) tab)
                                              (not= (tab-state-dragging-tab local-state) tab-before)
                                              (= (tab-state-dragging-over local-state) tab))
                                       "30px"
                                       "0px")}
                      :ondragover (fn [ev]
                                    (.preventDefault ev)
                                    (reacl/send-message! this (drag-over tab)))
                      :ondrop (fn [ev]
                                (.preventDefault ev)
                                (reacl/send-message! this (drag-stop tab)))})))
                 ;; DEF TAB-ELEMENT (draggable-setting, name and close-button)
                 render-tab
                 (fn [idx tab]
                   (dom/keyed
                    (str "nav-item-" idx "-" (render-tab-name tab))
                    (dom/li
                     ;; DEF TAB-ELEMENT: draggable-setting
                     {:class "nav-item"
                      ;; FIXME: enabling this causes the :x: button to stop working
                      ;; :onclick (fn [ev]
                      ;;            (.preventDefault ev)
                      ;;            (reacl/send-message! this (select-tab tab)))
                      :draggable true
                      :ondragstart (fn [_ev]
                                     (reacl/send-message! this (drag-start tab)))
                      :ondragend (fn [ev]
                                   (.preventDefault ev)
                                   (reacl/send-message! this (drag-end tab)))
                      :ondragover (fn [ev]
                                    (.preventDefault ev)
                                    (reacl/send-message! this (drag-over tab)))
                      :ondragenter (fn [ev]
                                     (.preventDefault ev)
                                     (reacl/send-message! this (drag-enter tab)))
                      ;; FIXME: https://www.w3schools.com/jsref/event_ondragleave.asp
                      ;; :ondragleave (fn [ev]
                      ;;                (.preventDefault ev)
                      ;;                (reacl/send-message! this (drag-leave tab)))
                      :ondrop (fn [ev]
                                (.preventDefault ev)
                                (reacl/send-message! this (drag-stop tab)))}
                     ;; DEF TAB-ELEMENT name and close-button
                     (dom/span
                      {:class   (str "nav-link" (when (= (tabbed-selected-tab tabbed) tab) " active"))}
                      ;; DEF TAB-ELEMENT name
                      (dom/a
                       {:onclick (fn [ev]
                                   (.preventDefault ev)
                                   (reacl/send-message! this (select-tab tab)))
                        :draggable false
                        :href    "#"}
                       (render-tab-name tab))
                      ;; DEF TAB-ELEMENT close-button
                      (dom/button
                       {:class "btn btn-sm btn-close ms-3"
                        :aria-label "Close"
                        :onclick (fn [ev]
                                   (.preventDefault ev)
                                   (reacl/send-message! this (hide-tab tab)))
                        :draggable false
                        :href    "#"})))))]
             (concat
              ;; DRAW spacer tab, spacer tab, spacer tab, ...
              (mapcat-indexed (fn [idx tab tab-before] [(render-drag-and-drop-spacer idx tab tab-before)
                                                        (render-tab idx tab)])
                              (tabbed-tabs tabbed) (concat [nil] (tabbed-tabs tabbed)))
              ;; DRAW last spacer before dropdown-menu
              [(render-drag-and-drop-spacer -1 ::last (last (tabbed-tabs tabbed)))
               ;; DRAW DROPDOWN
               (dom/li
                {:class "nav-item"}
                ;; DEF DROPDOWN-ICON
                (dom/a
                 {:class   (str "nav-link" (when (and (nil? (tab-state-dragging-tab local-state))
                                                      (empty? hidden-tabs))
                                             " disabled"))
                  :data-bs-toggle "dropdown"
                  :role "button"
                  :aria-expanded "false"
                  :href    (when-not (empty? hidden-tabs) "#")
                  :ondragover (fn [ev]
                                (.preventDefault ev)
                                (reacl/send-message! this (drag-over ::last)))
                  :ondrop (fn [ev]
                            (.preventDefault ev)
                            (reacl/send-message! this (drag-stop ::last)))}
                 "+")
                ;; DEF DROPDOWN-MENU
                (apply dom/ul {:class "dropdown-menu"}
                       (map-indexed (fn [idx tab]
                                      (dom/keyed
                                       (str "nav-item-dropdown-item-" idx "-" (render-tab-name tab))
                                       (dom/li
                                        (dom/a
                                         {:class   "dropdown-item"
                                          :onclick #(reacl/send-message! this (add-tab tab))
                                          :href    "#"}
                                         (render-tab-name tab)))))
                                    hidden-tabs)))
               ;; DRAW some space after dropdown-menu
               (dom/li
                {:style {:width "30px"}
                 :ondragover (fn [ev]
                               (.preventDefault ev)
                               (reacl/send-message! this (drag-enter nil)))})]))))

  handle-message
  (fn [msg]
    (cond
      (select-tab? msg)
      (reacl/return :app-state (tabbed-selected-tab tabbed (select-tab-tab msg))
                    :local-state (tab-state-previous-tab local-state (tabbed-selected-tab tabbed)))

      (hide-tab? msg)
      (reacl/return :app-state (let [tab-to-hide (hide-tab-tab msg)
                                     selected-tab (tabbed-selected-tab tabbed)
                                     previous-selected-tab (tab-state-previous-tab local-state)
                                     removed (remove-tab (tabbed-tabs tabbed) tab-to-hide)]
                                 (-> tabbed
                                     (tabbed-selected-tab (if (= selected-tab tab-to-hide)
                                                            (or previous-selected-tab (first removed))
                                                            selected-tab))
                                     (tabbed-tabs removed))))

      (add-tab? msg)
      (reacl/return :app-state (-> tabbed
                                   (tabbed-selected-tab (add-tab-tab msg))
                                   (lens/overhaul tabbed-tabs concat [(add-tab-tab msg)])))

      (drag-start? msg)
      (reacl/return :local-state (tab-state-dragging-tab local-state (drag-start-tab msg)))

      (drag-end? msg)
      (reacl/return :local-state (-> local-state
                                     (tab-state-dragging-tab nil)
                                     (tab-state-dragging-over nil)))

      (drag-over? msg)
      (reacl/return :local-state (tab-state-dragging-over local-state (drag-over-tab msg)))

      (drag-enter? msg)
      (reacl/return :local-state (tab-state-dragging-over local-state (drag-enter-tab msg)))

      (drag-leave? msg)
      (reacl/return :local-state (tab-state-dragging-over local-state nil))

      (drag-stop? msg)
      (reacl/return :app-state (lens/overhaul tabbed tabbed-tabs insert-tab-before
                                              (tab-state-dragging-tab local-state) (drag-stop-tab msg))
                    :local-state (-> local-state
                                     (tab-state-dragging-tab nil)
                                     (tab-state-dragging-over nil))))))

(reacl/defclass root this state []
  render
  (dom/div
   ;; TAB-AREA
   (tabbed-navigation (reacl/bind this) ["A" "B" "C" "D"] render-tab-name)
   ;; TAB-CONTENT
   (tabbed-selected-tab state)))

(reacl/render-component
 (.getElementById js/document "app-tabs")
 root (make-tabbed "A" ["A" "B" "C" "D"]))
