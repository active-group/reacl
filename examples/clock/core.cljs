(ns examples.clock.core
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]))

(defn bold [text]
  (dom/span {:style {:font-weight "bold"}}
            text))

(defn clock [^js/Date date timezone]
  (bold (.toLocaleTimeString date "en-US" #js {"timeZone" timezone})))

(reacl/defclass select-timezone this value []
  render
  (apply dom/select
         {:value value
          :onchange (fn [ev]
                      (reacl/send-message! this (.-value (.-target ev))))}
         (map (fn [[k v]]
                (dom/option {:value k} v))
              {"America/New_York" "New York"
               "Europe/Berlin" "Berlin"
               "Asia/Shanghai" "Shanghai"}))
  handle-message
  (fn [new-value]
    (reacl/return :app-state new-value)))

(reacl/defclass clock-select this timezone [date]
  render
  (dom/div (select-timezone (reacl/bind this)) " "
           (clock date timezone)))

(reacl/defclass button this [label action]
  render
  (dom/button {:onclick (fn [ev] (reacl/send-message! this :click))}
              label)

  handle-message
  (fn [_]
    (reacl/return :action action)))

(defrecord StartInterval [ms target id-message tick-message])
(defrecord StopInterval [id])

(defn toplevel-action [app-state action]
  (cond
    (instance? StartInterval action)
    (let [target (:target action)
          ms (:ms action)
          tick-msg (:tick-message action)
          id-msg (:id-message action)
          id (.setInterval js/window
                           #(reacl/send-message! target (tick-msg (js/Date.)))
                           ms)]
      (reacl/return :message [target (id-msg id)]))

    (instance? StopInterval action)
    (let [id (:id action)]
      (.clearInterval js/window (:id action))
      (reacl/return))))

(defrecord TimerId [id])
(defrecord Tick [date])

(defrecord Flip [])

(reacl/defclass my-app this state [greeting]
  local-state [timer-id nil]
  
  render
  (dom/div (dom/h1 greeting)
           (clock-select (reacl/bind this :timezone-1) (:date state))
           (clock-select (reacl/bind this :timezone-2) (:date state))
           (-> (button "Flip" (Flip.))
               (reacl/action-to-message this)))

  component-did-mount
  (fn []
    (reacl/return :action (StartInterval. 1000 this ->TimerId ->Tick)))

  component-will-unmount
  (fn []
    (if timer-id
      (reacl/return :action (StopInterval. timer-id)
                    :local-state nil)
      (reacl/return)))

  handle-message
  (fn [msg]
    (cond
      (instance? TimerId msg) (reacl/return :local-state (:id msg))

      (instance? Flip msg)
      (reacl/return :app-state (-> state
                                   (assoc :timezone-1 (:timezone-2 state))
                                   (assoc :timezone-2 (:timezone-1 state))))
      (instance? Tick msg)
      (reacl/return :app-state (assoc state :date (:date msg))))))

(reacl/render-component (.getElementById js/document "app-clock")
                        my-app
                        (reacl/handle-toplevel-action toplevel-action)
                        {:date (js/Date.)
                         :timezone-1 "Europe/Berlin"
                         :timezone-2 "America/New_York"}
                        "Hello")
