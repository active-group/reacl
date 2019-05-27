(ns reacl2.test.test-util.beta-test
  (:require [reacl2.test-util.beta :as tu :include-macros true]
            [reacl2.test-util.xpath :as xpath :include-macros true]
            [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
            )
  (:require-macros [cljs.test :refer (is deftest testing)]))

;; Note: this should be in clojurescript, but isn't as of "1.10.238"
(extend-protocol IPrintWithWriter
    js/Symbol
    (-pr-writer [sym writer _]
      (-write writer (str "\"" (.toString sym) "\""))))

(deftest basics-test
  (let [c (tu/env
           (reacl/class "test" this state []
                        component-did-mount
                        (fn []
                          (reacl/return :action [:mounted state]
                                        :app-state :mounted))
                                      
                        component-did-update
                        (fn []
                          (reacl/return :action [:updated state]))

                        component-will-unmount
                        (fn []
                          (reacl/return :action [:unmounted state]
                                        :app-state :unmounted))

                        render (dom/div)))]
    (is (= (tu/mount! c :initial) (reacl/return :action [:mounted :initial]
                                                :app-state :mounted)))
    ;; same if mounted twice
    (is (= (tu/mount! c :initial) (reacl/return :action [:mounted :initial]
                                                :app-state :mounted)))

    (is (= (tu/update! c :update-1) (reacl/return :action [:updated :update-1])))
    (is (= (tu/update! c :update-2) (reacl/return :action [:updated :update-2])))

    (is (= (tu/unmount! c) (reacl/return :action [:unmounted :update-2]
                                         :app-state :unmounted)))

    ;; throws if unmounted again.
    (is (thrown-with-msg? js/Error #"Something must be mounted to be unmounted. Call mount! first."
                          (tu/unmount! c)))))

(deftest messages-test
  (let [c (tu/env
           (reacl/class "test" this state []
                        handle-message
                        (fn [msg]
                          (reacl/return :app-state (conj state msg)))

                        render (dom/div)))]
    
    (is (thrown-with-msg? js/Error #"Nothing mounted into the test environment. Call mount! first."
                          (tu/send-message! c :msg-0)))
    
    (tu/mount! c [])

    (is (= (tu/send-message! c :msg-1) (reacl/return :app-state [:msg-1])))

    ;; Note that the app-state does not change, unless we call update!
    (is (not= (tu/send-message! c :msg-2) (reacl/return :app-state [:msg-1 :msg-2])))
    
    (tu/update! c [:msg-1])
    (is (= (tu/send-message! c :msg-2) (reacl/return :app-state [:msg-1 :msg-2])))

    (tu/unmount! c)
    (is (thrown-with-msg? js/Error #"Nothing mounted into the test environment. Call mount! first."
                          (tu/send-message! c :msg-3)))))

(deftest localstate-test
  (let [c (tu/env
           (reacl/class "test" this [x]
                        local-state [state x]
                                      
                        component-did-update
                        (fn []
                          (if (and x (not= x state))
                            (reacl/return :local-state x)
                            (reacl/return)))

                        render (dom/div)))]
    (is (thrown-with-msg? js/Error #"Nothing mounted into the test environment. Call mount! first."
                          (tu/inspect-local-state c)))
    (is (thrown-with-msg? js/Error #"Nothing mounted into the test environment. Call mount! first."
                          (tu/inject-local-state! c 7)))

    (tu/mount! c 4)
    (is (= (tu/inspect-local-state c) 4))

    (tu/update! c 6)
    (is (= (tu/inspect-local-state c) 6))

    (tu/update! c nil) ;; turn off 'did-update' first
    (tu/inject-local-state! c 17)
    (is (= (tu/inspect-local-state c) 17))

    (tu/unmount! c)
    (is (thrown-with-msg? js/Error #"Nothing mounted into the test environment. Call mount! first."
                          (tu/inspect-local-state c)))))

(deftest function-test
  ;; can test 'ordinary' functions as as if they were non-app-state classes for the actions 'generated'
  (let [class1 (reacl/class "class1" this [arg]
                            render (dom/span)
                            component-will-mount
                            (fn []
                              (reacl/return :action [:mounted arg]))
                            component-did-update
                            (fn []
                              (reacl/return :action [:updated arg])))

        c (tu/fn-env (fn [foo]
                       (dom/div {} (class1 foo))))]

    (is (= (tu/mount! c :act1) (reacl/return :action [:mounted :act1])))
    
    (is (= (tu/update! c :act2) (reacl/return :action [:updated :act2])))))

(deftest invoke-callback-test
  (let [c (tu/mount
           (reacl/class "test" this state [x]

                        handle-message
                        (fn [msg]
                          (reacl/return :app-state msg))

                        render (dom/div (dom/button {:onClick (fn [ev] (reacl/send-message! this x))})))
           nil :msg-1)]

    (is (= (tu/invoke-callback! (xpath/select-one c (xpath/>> ** "button"))
                                :onclick :my-event)
           (reacl/return :app-state :msg-1)))))

(deftest injection-test
  (let [c2 (reacl/class "test2" this state []
                        render (dom/div))
        
        c (tu/mount
           (reacl/class "test" this state []

                        handle-message
                        (fn [msg]
                          (reacl/return :app-state (inc msg)))

                        render (c2 (reacl/opt :reaction (reacl/pass-through-reaction this))
                                   nil))
           0)]

    (let [inner (xpath/select-one c (xpath/>> ** c2))
          act ::act]
      (is (tu/inject-return! inner (reacl/return :app-state 42 :action act))
          (reacl/return :app-state 42 :action act)))))

(def prov-x 11)

(deftest provided-test
  (tu/provided [prov-x 42]
               (is (= prov-x 42)))

  (tu/provided [reacl/send-message! (constantly 42)]
               ;; Note: clojurescript will still complain about the arity, because that is in the metadata of the var (I guess) - but that's ok.
               (is (= (reacl2.core/send-message! :a :b) 42))))
