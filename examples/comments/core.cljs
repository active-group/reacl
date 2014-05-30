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
  app-state [lens]
  render
  (fn []
    (let [comment (lens/yank app-state lens)
          author (:author comment)
          text (:text comment)]
      (dom/div {:className "comment"}
               (dom/h2 {:className "commentAuthor"} author)
               text))))

(reacl/defclass comment-list
  app-state [lens]
  render
  (fn [& {:keys [instantiate]}]
    (let [comments (lens/yank app-state lens)
          nodes (map-indexed (fn [i _]
                               (dom/keyed (str i) (instantiate comment (lens/in lens (lens/at-index i)))))
                             comments)]
      (dom/div {:className "commentList"}
               nodes))))

(reacl/defclass comment-box
  app-state [lens]
  render
  (fn [& {:keys [instantiate]}]
    (dom/div {:className "commentBox"}
             (dom/h1 "Comments")
             (instantiate comment-list lens)))
  handle-message
  (fn [msg]
    (let [new-comments
          (map (fn [e]
                 (Comment. (:author e) (:text e)))
               msg)]
      (reacl/return :app-state (lens/shove app-state lens new-comments))))
  component-will-mount
  (fn [comp]
    (let [refresh
          (fn []
            (edn-xhr
             {:method :get
              :url "comments.edn"
              :on-complete #(reacl/send-message! comp %)}))]
      (refresh)
      (js/setInterval refresh 2000))))

(js/React.renderComponent
 (reacl/instantiate-toplevel comment-box [] lens/id)
 (.getElementById js/document "content"))
