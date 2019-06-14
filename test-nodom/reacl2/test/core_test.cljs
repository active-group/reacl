(ns reacl2.test.core-test
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
            [reacl2.test-util.alpha :as reacl-test]
            [cljs.test :as t]
            [clojure.string :as string])
  (:require-macros [cljs.test :refer (is deftest testing)]))

;; Code under test

(reacl/defclass string-display
  this s []
  render
  (dom/h1 s)
  handle-message
  (fn [new]
    (reacl/return :app-state new)))

(defn stripe
  [bgc text]
  (dom/li {:style {:background-color bgc}} text))

(reacl/defclass list-display
  this [lis]
  render
  (dom/ul {:class "animals"}
          (map (fn [bgc keyn text]
                 (dom/keyed (str keyn) (stripe bgc text)))
               (cycle ["#ff0" "#fff"]) (range 0) lis)))

(def contacts
  [{:first "Ben" :last "Bitdiddle" :email "benb@mit.edu"}
   {:first "Alyssa" :middle-initial "P" :last "Hacker" :email "aphacker@mit.edu"}
   {:first "Eva" :middle "Lu" :last "Ator" :email "eval@mit.edu"}
   {:first "Louis" :last "Reasoner" :email "prolog@mit.edu"}
   {:first "Cy" :middle-initial "D" :last "Effect" :email "bugs@mit.edu"}
   {:first "Lem" :middle-initial "E" :last "Tweakit" :email "morebugs@mit.edu"}])

(defn middle-name [{:keys [middle middle-initial]}]
  (cond
    middle (str " " middle)
    middle-initial (str " " middle-initial ".")))

(defn display-name
  [{:keys [first last] :as contact}]
  (str last ", " first (middle-name contact)))

(defn parse-contact [contact-str]
  (let [[first middle last :as parts] (string/split contact-str #"\s+")
        [first last middle] (if (nil? last) [first middle] [first last middle])
        middle (when middle (string/replace middle "." ""))
        c (if middle (count middle) 0)]
    (when (>= (count parts) 2)
      (cond-> {:first first :last last}
        (== c 1) (assoc :middle-initial middle)
        (>= c 2) (assoc :middle middle)))))

(defrecord Delete [contact])

(reacl/defclass contact-display
  this contact [parent] ; parent later
  render
  (dom/li
   (dom/span (display-name contact))
   (dom/button {:onclick (fn [e] (reacl/send-message! parent (->Delete contact)))} "Delete")))

(defrecord NewText [text])
(defrecord Add [contact])

(reacl/defclass contacts-display
  this data []
  local-state [new-text ""]
  render
  (dom/div
   (dom/h2 "Contact list")
   (dom/ul
    (map (fn [c n] (dom/keyed (str n) (contact-display c reacl/no-reaction this)))
         data (range 0)))
   (dom/div
    (dom/input {:type "text" :value new-text
                :onchange (fn [e] (reacl/send-message! this
                                                       (->NewText (.. e -target -value))))})
    (dom/button {:onclick (fn [e] (reacl/send-message! this (->Add (parse-contact new-text))))} "Add contact")))
  handle-message
  (fn [msg]
    (cond
      (instance? Delete msg)
      (reacl/return :app-state
                    (vec (remove (fn [c] (= c (:contact msg))) data)))

      (instance? NewText msg)
      (reacl/return :local-state (:text msg))

      (instance? Add msg)
      (reacl/return :app-state (conj data (:contact msg))
                    :local-state ""))))

;; messages
(defrecord NewComments [comments])
(defrecord Refresh [])

;; action
(defrecord RefreshMeEvery [component interval])
(defrecord EdnXhr [component url make-message])

(reacl/defclass comment-box
  this comments []
  render
  (dom/div {:class "commentBox"}
           (dom/h1 "Comments")
           (dom/div {:class "commentList"}
                    (map-indexed (fn [i comment]
                                   (dom/keyed (str i) comment))
                                 comments)))
  handle-message
  (fn [msg]
    (cond
      (instance? NewComments msg)
      (reacl/return :app-state msg)

      (instance? Refresh msg)
      (reacl/return :action (EdnXhr. this "comments.edn" ->NewComments))))

  component-did-mount
  (fn []
    (reacl/return :action (RefreshMeEvery. this 2000))))

;; Tests

(deftest string-display-test
  (let [e (string-display (reacl/opt :reaction reacl/no-reaction) "Hello, Mike")
        renderer (reacl-test/create-renderer e)]
    (let [t (reacl-test/render-output renderer)]
      (is (reacl-test/render-output=dom? t (dom/h1 "Hello, Mike")))
      (is (reacl-test/element-has-type? t string-display))
      (is (reacl-test/element-has-type? (first (reacl-test/element-children t)) :h1))
      (is (= ["Hello, Mike"] (reacl-test/element-children (first (reacl-test/element-children t))))))))

(deftest list-display-test
  (let [e (list-display ["Lion" "Zebra" "Buffalo" "Antelope"])
        renderer (reacl-test/create-renderer)]
    (reacl-test/render! renderer e)
    (let [t (reacl-test/render-output renderer)]
      (is (reacl-test/element-has-type? t list-display))
      (let [ul (first (reacl-test/element-children t))]
        (is (reacl-test/element-has-type? ul :ul))
        ;; FIXME: the children are empty - Mike doesn't know why
        (doseq [c (reacl-test/element-children ul)]
          (is (reacl-test/element-has-type? c :li)))))))

(deftest hiccup-test
  (let [e (string-display (reacl/opt :reaction reacl/no-reaction) "Hello, Mike")]
    (is (= [:h1 "Hello, Mike"]
           (reacl-test/render->hiccup e)))))

(deftest contacts-display-handle-message-test
  (let [st (reacl-test/handle-message contacts-display [{:first "David" :last "Frese"}] [] "Foo"
                                      (->Add {:first "Mike" :last "Sperber"}))]
    (is (= [{:first "David", :last "Frese"} {:first "Mike", :last "Sperber"}]
           (:app-state st))))
  (let [st (reacl-test/handle-message contacts-display [{:first "David" :last "Frese"}] [] "Foo"
                                      (->NewText "David Frese"))]
    (is (= "David Frese"
           (:local-state st)))))

(deftest contacts-display-test
  (let [e (reacl/instantiate-toplevel contacts-display contacts)
        renderer (reacl-test/create-renderer)]
    (reacl-test/render! renderer e)
    (let [t (reacl-test/render-output renderer)
          input (reacl-test/descend-into-element t [:div :div :input])
          st (reacl-test/invoke-callback input :onchange #js {:target #js {:value "Mike Sperber"}})]
      (is (= "Mike Sperber") (:local-state st)))
    (let [t (reacl-test/render-output renderer)
          button (reacl-test/descend-into-element t [:div :div :button])
          st (reacl-test/invoke-callback button :onclick #js {})]
      (is (= (conj contacts {:first "Mike", :last "Sperber"})
             (:app-state st))))
    (let [t (reacl-test/render-output renderer)]
      (is (reacl-test/element-has-type? t contacts-display))
      (is (reacl-test/element-has-type? (first (reacl-test/element-children (first (reacl-test/element-children t)))) :div)))))

(deftest dom-f-perf-test
  (let [mp {:style {:background-color "white"
                    :padding "0px 1px 2px 4px"}
            :onClick (fn [x] nil)
            :width 100}]
    ;; 1.5:   ~6500ms
    ;; 1.5.1: ~2200ms
    (time (dotimes [n 10000]
            (dom/div mp (dom/br))))
    (assert true)))

(deftest comments-action-test
  (let [st (reacl-test/handle-message comment-box ["foo" "bar" "baz"] [] nil (Refresh.))]
    (is (= [(EdnXhr. nil "comments.edn" ->NewComments)]
           (:actions st)))))

(deftest display-name-test
  (reacl/defclass display-name-test1 this [] render (dom/div))
  (is (= (.-displayName (reacl/react-class display-name-test1))
         "reacl2.test.core-test/display-name-test1")))

(deftest return-test
  ;; tests properties of 'reacl/return'
  (let [comp (reacl/instantiate-toplevel contacts-display contacts)] ;; just any component.
    (testing "keep-state by default"
      (is (= (reacl/return)
             (reacl/return :app-state reacl/keep-state)))
      (is (= (reacl/return)
             (reacl/return :local-state reacl/keep-state))))
    (testing "nil state is not keep-state"
      (is (not= (reacl/return)
                (reacl/return :app-state nil)))
      (is (not= (reacl/return)
                (reacl/return :local-state nil))))
    (testing "multiple messages and actions possible"
      (is (not= (reacl/return :action 1)
                (reacl/return :action 1 :action 2)))
      (is (not= (reacl/return :message [comp 1])
                (reacl/return :message [comp 1] :message [comp 2]))))
    (testing "messages and action have an order"
      (is (not= (reacl/return :action 2 :action 1)
                (reacl/return :action 1 :action 2)))
      (is (not= (reacl/return :message [comp 1] :message [comp 2])
                (reacl/return :message [comp 2] :message [comp 1]))))))

(deftest merge-returned-test
  (let [comp (reacl/instantiate-toplevel contacts-display contacts)] ;; just any component
    (is (= (reacl/merge-returned (reacl/return :app-state 1)
                                 (reacl/return :local-state 2)
                                 (reacl/return :message [comp 3])
                                 (reacl/return :action 4))
           (reacl/return :app-state 1
                         :local-state 2
                         :message [comp 3]
                         :action 4)))
    (is (= (reacl/merge-returned (reacl/return :app-state 0 :local-state 0 :message [comp 3] :action 4)
                                 (reacl/return :app-state 1 :local-state 2)
                                 (reacl/return :message [comp 30])
                                 (reacl/return :action 40))
           (reacl/return :app-state 1
                         :local-state 2
                         :message [comp 3]
                         :message [comp 30]
                         :action 4
                         :action 40)))))

(def ^:dynamic *parent-atom* nil)
  
(reacl/defclass parent-a
  this app-state []
  render
  (dom/button {:onclick (fn [e] (reacl/send-message! this :new-app-state))})
  handle-message
  (fn [msg]
    (reacl/return :app-state msg)))

(defn constant-parent-b
  [_]
  :parent-b)

(reacl/defclass parent-c
  this app-state [sub-comp]
  render
  (dom/div sub-comp)
  handle-message
  (fn [msg]
    (reacl/return)))

(reacl/defclass parent-b
  this app-state []
  render
  (dom/div
   (parent-c (reacl/opt :reaction ; must not be called
                        (reacl/reaction this constant-parent-b))
             :app-state-c
             (parent-a (reacl/opt :parent this
                                  :reaction (reacl/pass-through-reaction :parent))
                       :app-state-a)))

  handle-message
  (fn [msg]
    (reset! *parent-atom* msg)
    (reacl/return :app-state msg)))

(deftest parent-test
  (binding [*parent-atom* (atom nil)]
    (let [comp (reacl/instantiate-toplevel parent-b :init)
          renderer (reacl-test/create-renderer comp)
          out (reacl-test/render-output renderer)
          button (reacl-test/descend-into-element out [:div :div :button])
          st (reacl-test/invoke-callback button :onclick #js {})]
      (is (= :new-app-state @*parent-atom* )))))
      
    
(deftest keyed-test
  (is (= (str ::test)
         (.-key (dom/keyed ::test
                           (dom/div))))))
