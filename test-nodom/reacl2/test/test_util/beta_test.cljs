(ns reacl2.test.test-util.beta-test
  (:require [reacl2.test-util.beta :as tu :include-macros true]
            [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
            )
  (:require-macros [cljs.test :refer (is deftest testing)]))

;; Note: this should be in clojurescript, but isn't as of "1.10.238"
(extend-protocol IPrintWithWriter
    js/Symbol
    (-pr-writer [sym writer _]
      (-write writer (str "\"" (.toString sym) "\""))))

(deftest mount-update-unmount-test
  (let [c (tu/test-class (reacl/class "test" this state []
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

                                      render (dom/div))
                         :initial)]
    (is (= (tu/mount! c) (reacl/return :action [:mounted :initial]
                                       :app-state :mounted)))
    ;; TODO: what to expect if mounted/unmounted multiple times?

    (is (= (tu/update! c :update-1) (reacl/return :action [:updated :update-1])))
    (is (= (tu/update! c :update-2) (reacl/return :action [:updated :update-2])))

    (is (= (tu/unmount! c) (reacl/return :action [:unmounted :update-2]
                                         :app-state :unmounted)))))

(deftest messages-test
  (let [c (tu/test-class (reacl/class "test" this state []
                                      handle-message
                                      (fn [msg]
                                        (reacl/return :app-state (conj state msg)))

                                      render (dom/div))
                         [])]
    
    (is (thrown-with-msg? js/Error #"Test component must be mounted to send a message to it."
                          (tu/send-message! c :msg-0)))
    
    (tu/mount! c)

    (is (= (tu/send-message! c :msg-1) (reacl/return :app-state [:msg-1])))

    ;; Note that the app-state does not change, unless we call update!
    (is (not= (tu/send-message! c :msg-2) (reacl/return :app-state [:msg-1 :msg-2])))
    
    (tu/update! c [:msg-1])
    (is (= (tu/send-message! c :msg-2) (reacl/return :app-state [:msg-1 :msg-2])))

    (tu/unmount! c)
    (is (thrown-with-msg? js/Error #"Test component must be mounted to send a message to it."
                          (tu/send-message! c :msg-3)))))

(deftest localstate-test
  (let [c (tu/test-class (reacl/class "test" this [x]
                                      local-state [state x]
                                      
                                      component-did-update
                                      (fn []
                                        (if (and x (not= x state))
                                          (reacl/return :local-state x)
                                          (reacl/return)))

                                      render (dom/div))
                         4)]
    (is (thrown-with-msg? js/Error #"Test component must be mounted to inspect the local-state."
                          (tu/inspect-local-state c)))
    (is (thrown-with-msg? js/Error #"Test component must be mounted to inject a local-state."
                          (tu/inject-local-state! c 7)))

    (tu/mount! c)
    (is (= (tu/inspect-local-state c) 4))

    (tu/update! c 6)
    (is (= (tu/inspect-local-state c) 6))

    (tu/update! c nil) ;; turn off 'did-update' first
    (tu/inject-local-state! c 17)
    (is (= (tu/inspect-local-state c) 17))

    (tu/unmount! c)
    (is (thrown-with-msg? js/Error #"Test component must be mounted to inspect the local-state."
                          (tu/inspect-local-state c)))))

;; TODO: inspect vdom of the component. Call event-handlers/trigger events. get the return from that.
