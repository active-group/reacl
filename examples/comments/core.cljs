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
    (dom/div {:class "comment"}
             (dom/h2 {:class "commentAuthor"} author)
             text)))

(reacl/defclass comment-list
  this comments []
  render
  (dom/div {:class "commentList"}
           (map-indexed (fn [i comment]
                          (dom/keyed (str i) (comment-entry comment)))
                        comments)))

; messages
(defrecord NewComments [comments])
(defrecord Refresh [])

; action
(defrecord RefreshMeEvery [component interval])
(defrecord EdnXhr [component url make-message])

(reacl/defclass comment-box
  this comments []
  render
  (dom/div {:class "commentBox"}
           (dom/h1 "Comments")
           (comment-list comments))
  handle-message
  (fn [msg]
    (cond
      (instance? NewComments msg)
      (reacl/return :app-state 
                    (map (fn [e]
                           (Comment. (:author e) (:text e)))
                         (:comments msg)))

      (instance? Refresh msg)
      (reacl/return :action (EdnXhr. this "comments.edn" ->NewComments))))

  component-did-mount
  (fn []
    (reacl/return :action (RefreshMeEvery. this 2000))))

(defn handle-action
  [action]
  (cond
    (instance? RefreshMeEvery action)
    (let [refresh (fn []
                    (reacl/send-message! (:component action) (Refresh.)))]
      (refresh)
      (js/setInterval refresh 2000))
    
    (instance? EdnXhr action)
    (edn-xhr {:method :get
              :url (str (:url action) "?") ; prevent caching
              :on-complete (fn [edn]
                             (reacl/send-message! (:component action) (NewComments. edn)))})))

(reacl/render-component
 (.getElementById js/document "content")
 comment-box
 (reacl/opt :handle-action handle-action)
 [])
