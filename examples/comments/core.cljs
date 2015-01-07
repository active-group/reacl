; python -m SimpleHTTPServer
; and then go to:
; http://localhost:8000/index.html

(ns examples.comments.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]
            [cljs.reader :as reader]
            [goog.events :as events]
            [goog.dom :as gdom])
  (:import [goog.net XhrIo EventType]))

(enable-console-print!)

(def ^:private meths
  {:get "GET"
   :put "PUT"
   :post "POST"
   :delete "DELETE"})

(defn edn-xhr [{:keys [method url data on-complete]}]
  (let [xhr (XhrIo.)]
    (events/listen xhr EventType.COMPLETE
      (fn [e]
        (on-complete (reader/read-string (.getResponseText xhr)))))
    (. xhr
      (send url (meths method) (when data (pr-str data))
        #js {"Content-Type" "application/edn"}))))

(defrecord Comment [author text])

(reacl/defclass comment-entry
  this comment []
  render
  (let [author (:author comment)
        text (:text comment)]
    (dom/div {:className "comment"}
             (dom/h2 {:className "commentAuthor"} author)
             text)))

(reacl/defclass comment-list
  this comments []
  render
  (dom/div {:className "commentList"}
           (map-indexed (fn [i comment]
                          (dom/keyed (str i) (reacl/embed comment-entry this comment (constantly nil))))
                        comments)))

(reacl/defclass comment-box
  this comments []
  render
  (dom/div {:className "commentBox"}
           (dom/h1 "Comments")
           (reacl/embed comment-list this comments (constantly nil)))
  handle-message
  (fn [msg]
    (reacl/return :app-state 
                  (map (fn [e]
                         (Comment. (:author e) (:text e)))
                       msg)))
  component-will-mount
  (fn []
    (let [refresh
          (fn []
            (println "refresh")
            (edn-xhr
             {:method :get
              :url "comments.edn"
              :on-complete #(reacl/send-message! this %)}))]
      (refresh)
      (js/setInterval refresh 2000))))

(reacl/render-component
 (.getElementById js/document "content")
 comment-box [])
