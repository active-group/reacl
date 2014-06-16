; python -m SimpleHTTPServer
; and then go to:
; http://localhost:8000/index.html

(ns examples.comments.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]
            [reacl.lens :as lens]
            [cljs.reader :as reader]
            [goog.events :as events]
            [goog.dom :as gdom])
  (:import [goog.net XhrIo]
           goog.net.EventType
           [goog.events EventType]))

(enable-console-print!)

(def ^:private meths
  {:get "GET"
   :put "PUT"
   :post "POST"
   :delete "DELETE"})

(defn edn-xhr [{:keys [method url data on-complete]}]
  (let [xhr (XhrIo.)]
    (events/listen xhr goog.net.EventType.COMPLETE
      (fn [e]
        (on-complete (reader/read-string (.getResponseText xhr)))))
    (. xhr
      (send url (meths method) (when data (pr-str data))
        #js {"Content-Type" "application/edn"}))))

(defrecord Comment [author text])

(reacl/defclass comment
  this app-state [lens]
  local [comment (lens/yank app-state lens)]
  render
  (let [author (:author comment)
        text (:text comment)]
    (dom/div {:className "comment"}
             (dom/h2 {:className "commentAuthor"} author)
             text)))

(reacl/defclass comment-list
  this app-state [lens]
  render
  (let [comments (lens/yank app-state lens)
        nodes (map-indexed (fn [i _]
                             (dom/keyed (str i) (comment this (lens/in lens (lens/at-index i)))))
                           comments)]
    (dom/div {:className "commentList"}
             nodes)))

(reacl/defclass comment-box
  this app-state [lens]
  render
  (dom/div {:className "commentBox"}
           (dom/h1 "Comments")
           (comment-list this lens))
  handle-message
  (fn [msg]
    (let [new-comments
          (map (fn [e]
                 (Comment. (:author e) (:text e)))
               msg)]
      (reacl/return :app-state (lens/shove app-state lens new-comments))))
  component-will-mount
  (fn []
    (let [refresh
          (fn []
            (edn-xhr
             {:method :get
              :url "comments.edn"
              :on-complete #(reacl/send-message! this %)}))]
      (refresh)
      (js/setInterval refresh 2000))))

(reacl/render-component
 (.getElementById js/document "content")
 comment-box [] lens/id)
