(ns reacl2.test.new-api-test
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
            [reacl2.test-util.beta :as tu]
            [reacl2.test-util.xpath :as xpath :include-macros true]
            [active.clojure.lens :as lens])
  (:require-macros [cljs.test :refer (is deftest testing async)]))

(reacl/defclass msg-to-state this state []
  render (dom/div)
  handle-message
  (fn [msg]
    (reacl/return :app-state msg)))

(deftest bind-test
  (let [c (tu/mount (reacl/class "class" this state []
                                 render (msg-to-state (reacl/bind this :sub)))
                    {:sub nil})]
    (is (= (tu/send-message! (xpath/select c (xpath/>> / msg-to-state))
                             ::state)
           (reacl/return :app-state {:sub ::state})))))

(deftest bind-locally-test
  (let [c (tu/mount (reacl/class "class" this []
                                 local-state [st {:sub nil}]
                                 render (msg-to-state (reacl/bind-locally this :sub)))
                    {:sub nil})]
    (is (= (tu/send-message! (xpath/select c (xpath/>> / msg-to-state))
                             ::state)
           (reacl/return)))
    (is (= (tu/inspect-local-state c)
           {:sub ::state}))))

(deftest static-test
  (let [c (tu/mount (reacl/class "class" this []
                                 render (msg-to-state (reacl/static ::fixed)))
                    {:sub nil})]
    (is (= (tu/send-message! (xpath/select c (xpath/>> / msg-to-state))
                             ::state)
           (reacl/return)))))

(deftest reactive-test
  (let [c (tu/mount (reacl/class "class" this state []
                                 render (msg-to-state (reacl/reactive (:sub state)
                                                                      (reacl/reaction this identity)))
                                 handle-message
                                 (fn [sub-state]
                                   (reacl/return :app-state (assoc state :sub sub-state))))
                    {:sub nil})]
    (is (= (tu/send-message! (xpath/select c (xpath/>> / msg-to-state))
                             ::state)
           (reacl/return :app-state {:sub ::state})))))

(deftest reactive-focus-test
  (let [c (tu/mount (reacl/class "class" this state []
                                 render (msg-to-state (-> (reacl/reactive (:sub state)
                                                                          (reacl/reaction this identity))
                                                          (reacl/focus :subsub)))
                                 handle-message
                                 (fn [sub-state]
                                   (reacl/return :app-state (assoc state :sub sub-state))))
                    {:sub {:subsub nil}})]
    (is (= (tu/send-message! (xpath/select c (xpath/>> / msg-to-state))
                             ::state)
           (reacl/return :app-state {:sub {:subsub ::state}})))))
