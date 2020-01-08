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

(deftest fixed-test
  (let [c (tu/mount (reacl/class "class" this []
                                 render (msg-to-state (reacl/fixed ::fixed)))
                    {:sub nil})]
    (is (= (tu/send-message! (xpath/select c (xpath/>> / msg-to-state))
                             ::state)
           (reacl/return)))))

(deftest use-reaction-test
  (let [c (tu/mount (reacl/class "class" this state []
                                 render (msg-to-state (reacl/use-reaction (:sub state)
                                                                          (reacl/reaction this identity)))
                                 handle-message
                                 (fn [sub-state]
                                   (reacl/return :app-state (assoc state :sub sub-state))))
                    {:sub nil})]
    (is (= (tu/send-message! (xpath/select c (xpath/>> / msg-to-state))
                             ::state)
           (reacl/return :app-state {:sub ::state})))))

(deftest falsy-app-states
  (let [msg-to-state2 (reacl/class "msg-to-state2" this state [arg1 arg2]
                                   render (dom/div)
                                   handle-message
                                   (fn [msg]
                                     ;; additional check that args don't get messed up with the app-state:
                                     (reacl/return :app-state (if (= arg1 (* 2 arg2))
                                                                msg
                                                                ::fail))))
        
        c (tu/mount (reacl/class "class" this state []
                                 render (msg-to-state2 (reacl/use-reaction (:sub state)
                                                                           (reacl/reaction this identity))
                                                       42 21)
                                 handle-message
                                 (fn [sub-state]
                                   (reacl/return :app-state (assoc state :sub sub-state))))
                    {:sub nil})]
    (is (= (tu/send-message! (xpath/select c (xpath/>> / msg-to-state2))
                             false)
           (reacl/return :app-state {:sub false})))))

(deftest use-reaction-update-test
  ;; mount with a wrong 'sel' value:
  (let [c (tu/mount (reacl/class "class" this state [sel]
                                 render (msg-to-state (reacl/use-reaction 42 (reacl/reaction this vector sel)))
                                 handle-message
                                 (fn [[st sel]]
                                   (reacl/return :app-state (assoc state sel st))))
                    {:sub :init}
                    :dummy)]
    ;; update the 'sel' argument
    (tu/update! c {:sub nil} :sub)
    ;; then try the message to update something
    (is (= (tu/send-message! (xpath/select c (xpath/>> / msg-to-state))
                             ::state)
           (reacl/return :app-state {:sub ::state})))))

(deftest use-reaction-focus-test
  (let [c (tu/mount (reacl/class "class" this state []
                                 render (msg-to-state (-> (reacl/use-reaction (:sub state)
                                                                              (reacl/reaction this identity))
                                                          (reacl/focus :subsub)))
                                 handle-message
                                 (fn [sub-state]
                                   (reacl/return :app-state (assoc state :sub sub-state))))
                    {:sub {:subsub nil}})]
    (is (= (tu/send-message! (xpath/select c (xpath/>> / msg-to-state))
                             ::state)
           (reacl/return :app-state {:sub {:subsub ::state}})))))

;; not sure if this is well-defined at all, and can/should be possible:
#_(deftest double-state-change-test
  ;; covers a rather obscure edge case, where a two state changes in are triggered in one cycle:
  (let [msgs (reacl/class "msgs" this [parent gparent]
                          render (dom/div)
                          handle-message
                          (fn [msg]
                            (reacl/return :message [gparent ::msg1]
                                          :message [parent ::msg2]
                                          )))

        msg-to-state1 (reacl/class "msg-to-state1" this state [parent]
                                   render (msgs this parent)
                                   handle-message
                                   (fn [msg]
                                     (reacl/return :app-state (conj state msg))))
        sub (fn
              ([m] (first (:sub m)))
              ([m v] (update m :sub conj v)))
        
        c (tu/mount (reacl/class "class" this state []
                                 render (msg-to-state1 (reacl/bind this :sub) this)
                                 handle-message
                                 (fn [msg]
                                   (reacl/return :app-state (update state :sub conj msg))))
                    {:sub []})]
    (is (= (reacl/return :app-state {:sub [::msg1 ::msg2]})
           (tu/send-message! (xpath/select c (xpath/>> ** msgs))
                             ::dummy)))))
