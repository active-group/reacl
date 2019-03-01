(ns reacl2.test.core
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
            [reacl2.test-util.alpha :as test-util]
            ;; include 'reacl1' to see they compile at least:
            [reacl.core :as reacl1 :include-macros true]
            [reacl.dom :as dom1 :include-macros true]
            [active.clojure.lens :as lens]
            [cljsjs.react]
            [cljsjs.react.test-renderer]
            [cljsjs.react.dom.test-utils]
            [cljs.test :as t])
  (:require-macros [cljs.test
                    :refer (is deftest run-tests testing async)]))

(enable-console-print!)

(deftest dom
  (let [d (dom/h1 "Hello, world!")]
    (is (= "<h1>Hello, world!</h1>"
           (test-util/render-to-text d)))))

(deftest elements
  (let [d (dom/h1 "Hello, world!")]
    (is (= "h1" (.-type d)))
    (is (= {"children" "Hello, world!"} (js->clj (.-props d))))
    (is (= nil (.-key d)))
    (is (= nil (.-ref d)))))

(defrecord Todo [id text done?])

(reacl/defclass to-do-item
  this todo []
  render
  (dom/letdom
   [checkbox (dom/input
              {:type "checkbox"
               :value (:done? todo)
               :onchange (fn [e]
                           (test-util/send-message! this (.-checked e)))})]
   (dom/div checkbox
            (:text todo)))
  handle-message
  (fn [checked?]
    (reacl/return :app-state
                  (assoc todo :done? checked?))))

(deftest simple
  (let [item (reacl/instantiate-toplevel to-do-item (Todo. 42 "foo" false))]
    (is (= (test-util/extract-app-state item)
           (Todo. 42 "foo" false)))
    (is (= "<div><input type=\"checkbox\" value=\"false\"/>foo</div>"
           (test-util/render-to-text item)))))

(deftest handle-message-simple
  (let [item (test-util/instantiate&mount to-do-item (Todo. 42 "foo" false))]
    (let [[app-state _] (test-util/handle-message->state item true)]
      (is (= app-state (Todo. 42 "foo" true))))))

(deftest to-do-elements
  (let [e (to-do-item (Todo. 42 "foo" true))]
    (is (test-util/hiccup-matches? [:div [:input {:type "checkbox", :value true, :onchange fn?}] "foo"]
                                   (test-util/render->hiccup e)))))

(deftest to-do-message
  (let [e (reacl/instantiate-toplevel to-do-item (Todo. 42 "foo" true))
        renderer (js/ReactTestRenderer.create e)]
    (let [t (.-root renderer)]
      (let [input (test-util/descend-into-element t [:div :input])]
        (.onChange (.-props input) #js {:checked false})))
    (let [t (.-root renderer)]
      (let [input (test-util/descend-into-element t [:div :input])]
        (is (not (.-value (.-props input))))))))

(reacl/defclass foo
  this bam [bar]
  local [baz (+ bam 15)
         bla (+ baz bar 7)]
  render
  (dom/span (dom/div (str baz)) (dom/div (str bla))))

(defn dom-with-tag
  [comp tag-name]
  (js/ReactTestUtils.findRenderedDOMComponentWithTag comp tag-name))

(defn doms-with-tag
  [comp tag-name]
  (into []
        (js/ReactTestUtils.scryRenderedDOMComponentsWithTag comp tag-name)))

(defn dom-with-class
  [comp clazz]
  (js/ReactTestUtils.findRenderedComponentWithType comp (reacl/react-class clazz)))

(defn doms-with-dom-class
  "Returns a list of all dom nodes with the given `:class? attributes rendered by `comp`."
  [comp clazz]
  (js/ReactTestUtils.scryRenderedDOMComponentsWithClass comp clazz))

(defn dom-content
  [comp]
  (.-textContent comp))

(deftest initial-state-test
  (testing "initial-state-test sees app-state, locals and args"
    (let [item (test-util/instantiate&mount
                (reacl/class "initial-state-test" this app-state [arg1]
                             local [local1 "local1"]
                             local-state [local-state (do (is (= "app-state" app-state))
                                                          (is (= "arg1" arg1))
                                                          (is (= "local1" local1))
                                                          "local-state")]
                             render
                             (do
                               (is (= local-state "local-state"))
                               (dom/div "test")))
                "app-state" "arg1")
          divs (doms-with-tag item "div")]
      (is (= ["test"]
             (map dom-content divs))))))

(deftest locals-sequential
  (let [item (test-util/instantiate&mount foo 42 12)
        divs (doms-with-tag item "div")]
    (is (= ["57" "76"]
           (map dom-content divs)))))

(deftest foo-element
  (let [e (foo 42 12)]
    (is (reacl/has-class? foo e))
    (is (= [12] (test-util/extract-args e)))
    (is (= 42 (test-util/extract-app-state e)))))

(deftest foo-render
  (let [e (foo 42 12)]
    (is (= [:span [:div "57"] [:div "76"]] (test-util/render->hiccup e)))))

(reacl/defclass bar
  this [app-state]
  local-state [local-state 1]
  render
  (dom/span (foo app-state local-state))
  handle-message
  (fn [new]
    (reacl/return :local-state new)))

(deftest local-change
  (let [item (test-util/instantiate&mount bar 5)]
    (test-util/send-message! item 2)
    (is (= ["20" "29"]
           (map dom-content (doms-with-tag item "div"))))))

(reacl/defclass blam
  this app-state []
  local [braf (+ app-state 7)]
  render
  (dom/div (str braf))
  handle-message
  (fn [new]
    (reacl/return :app-state new)))

(deftest local-app-state-change
  (let [item (test-util/instantiate&mount blam 5)]
    (test-util/send-message! item 6)
    (is (= ["13"]
           (map dom-content (doms-with-tag item "div"))))))

(reacl/defclass blaz
  this app-state []
  render
  (dom/span (blam (reacl/opt :embed-app-state (fn [old new] new)) app-state)))

(deftest local-app-state-change-embed
  (let [item (test-util/instantiate&mount blaz 5)
        embedded (dom-with-class item blam)]
    (test-util/send-message! embedded 6)
    (is (= ["13"]
           (map dom-content (doms-with-tag item "div"))))))

(reacl/defclass blaz2
  this app-state []
  render
  (blam (* 2 app-state))
  handle-message
  (fn [new]
    (reacl/return :app-state new)))
  
(deftest embedded-app-state-change
  (let [item (test-util/instantiate&mount blaz2 5)
        embedded (dom-with-class item blam)]
    (test-util/send-message! item 6)
    (is (= ["19"] ;; 2*6+7
           (map dom-content (doms-with-tag item "div"))))))

(reacl/defclass action-class1
  this app-state []
  render (dom/div "foo")

  component-will-mount
  (fn []
    (reacl/return :action :action)))

(reacl/defclass action-class2
  this [register-app-state!]
  render (dom/div (action-class1 (reacl/opt :reduce-action
                                            (fn [app-state action]
                                              (case action
                                                (:action) (reacl/return :action :this-action
                                                                        :app-state :app-state1)
                                                (reacl/return :action :another-action)))
                                            :reaction (reacl/reaction this
                                                                      (fn [app-state]
                                                                        (register-app-state! app-state))))
                                 :app-state0))
  handle-message ; the reaction sends a message
  (fn [msg]
    (reacl/return)))


(deftest transform-action-test
  (let [msga (atom [])
        app-statea (atom [])
        item (test-util/instantiate&mount action-class2
                                          (reacl/opt :reduce-action
                                                     (fn [app-state action]
                                                       (swap! msga conj action)
                                                       (reacl/return)))
                                          (fn [app-state]
                                            (swap! app-statea conj app-state)))]
    (is (= [:this-action] @msga))
    (is (= [:app-state1] @app-statea))))


(deftest app-state-n-action-test
  (let [action-class3
        (reacl/class "action-class3"
                     this app-state []
                     render (dom/div "foo")

                     component-did-mount
                     (fn []
                       (test-util/send-message! this nil)
                       nil)

                     handle-message
                     (fn [msg]
                       (reacl/return :app-state :app-state1
                                     :action :action)))
        
        action-class4
        (reacl/class "action-class4" this [register-app-state!]
                     render (dom/div (action-class3 (reacl/opt :reduce-action
                                                               (fn [app-state action]
                                                                 (case action
                                                                   (:action)
                                                                   (case app-state
                                                                     (:app-state0) (reacl/return :action :this-action
                                                                                                 :app-state :app-state0a)
                                                                     (:app-state1) (reacl/return :action :this-action
                                                                                                 :app-state :app-state1a))
                                                                   (reacl/return :action :another-action)))
                                                               :reaction (reacl/reaction this
                                                                                         (fn [app-state]
                                                                                           (register-app-state! app-state))))
                                                    :app-state0))
                     handle-message     ; the reaction sends a message
                     (fn [msg]
                       (reacl/return)))
        msga (atom [])
        app-statea (atom [])]
    (test-util/instantiate&mount action-class4
                                 (reacl/opt :reduce-action
                                            (fn [app-state action]
                                              (swap! msga conj action)
                                              (reacl/return)))
                                 (fn [app-state]
                                   (swap! app-statea conj app-state)))
    (is (= [:this-action] @msga))
    (is (= [:app-state1a] @app-statea))))

(reacl/defclass app-state-change-with-reaction-class this state []
                render
                (dom/div (str state))

                handle-message
                (fn [msg]
                  (reacl/return :app-state msg
                                :action :doAction)))


(deftest app-state-change-with-reaction-test
  (let [item (test-util/instantiate&mount app-state-change-with-reaction-class 1)]
    (is (= ["1"]
           (map dom-content (doms-with-tag item "div"))))
    (test-util/send-message! item 2)
    (is (= ["2"]
           (map dom-content (doms-with-tag item "div"))))))


(reacl/defclass local-state-boolean-value-class this []
  local-state [foo? false]
  render
  (dom/div))

(reacl/defclass local-state-nil-value-class this []
  local-state [foo? nil]
  render
  (dom/div))

(deftest local-state-boolean-value-test
  (let [item (test-util/instantiate&mount local-state-boolean-value-class)]
    (is (false? (test-util/extract-local-state item))))
  (let [item (test-util/instantiate&mount local-state-nil-value-class)]
    (is (nil? (test-util/extract-local-state item)))))

(reacl/defclass text-refs
  this content []
  refs [text-input]
  render
  (dom/input {:ref text-input
              :type "text"
              :value content
              :onchange (fn [e]
                          (let [v (.-value (reacl/get-dom text-input))]
                            (test-util/send-message! this (str v v))))})
  handle-message
  (fn [new-content]
    (reacl/return :app-state
                  (apply str new-content (reverse (.-value (reacl/get-dom text-input)))))))

(deftest text-refs-message
  (let [item (test-util/instantiate&mount text-refs "foo")
        dom (dom-with-tag item "input")]
    (js/ReactTestUtils.Simulate.change dom)
    (is (= "foofoooof" (.-value dom)))))


(deftest queued-test
  (let [queued-sub (reacl/class "queued-sub"
                                this app-state [super]
                                render
                                (dom/div
                                 (dom/button "Button")
                                 (dom/div {:class "app-state"} app-state))
                                handle-message
                                (fn [msg]
                                  (reacl/return :message [super "->"]
                                                :message [super msg])))
        queued-super (reacl/class "queued-super"
                                  this app-state []
                                  render
                                  (dom/div (queued-sub app-state this))
                                  handle-message
                                  (fn [msg]
                                    (reacl/return :app-state (str app-state msg))))
        item (test-util/instantiate&mount queued-super "old")]
    (is (= ["old"]
           (map dom-content (doms-with-dom-class item "app-state"))))
    (let [sub (dom-with-class item queued-sub)]
      (test-util/send-message! sub "new")
      (is (= ["old->new"]
             (map dom-content (doms-with-dom-class item "app-state")))))))

(deftest container-class-action-reaction-test
  (let [mount-called (atom false)
        inner-class (reacl/class "inner" inner app-state []
                                 component-did-mount
                                 (fn []
                                   (reset! mount-called true)
                                   (reacl/return :action :start))
                                 render (dom/div))

        container-class (reacl/class "container" container [& elements]
                                     render (apply dom/div elements))

        inner-reduce-called (atom false)
        inner-state-change-called (atom false)
        container-state-change-called (atom false)

        outer-class (reacl/class "outer" outer []
                                 local-state [outer-state :initial-outer-state]
                                 render (container-class (inner-class (reacl/opt :reduce-action (fn [inner-app-state action]
                                                                                                  (reset! inner-reduce-called true)
                                                                                                  (reacl/return :app-state [:inner-action action]))
                                                                                 :reaction (reacl/reaction outer
                                                                                                           (fn [v]
                                                                                                             (reset! inner-state-change-called true)
                                                                                                             (vector :inner-state v))))
                                                                      :the-inner-state))
                                 handle-message
                                 (fn [v]
                                   (reacl/return :local-state v)))
        ]
    (testing "container classes swallow actions per default"
      ;; Note: questionable what this feature is good for; we might as
      ;; well prohibit reacl/return :app-state (and :local-state) inside
      ;; a :reduce-action (and allow only :action and :message)
      ;; This merely covers the current behaviour.
    
      (let [it (test-util/instantiate&mount outer-class)
            outer-state (test-util/extract-local-state it)]
        (is @mount-called)
        (is @inner-reduce-called)
        
        ;; the action/app-state change does not bubble:
        (is (not= [:inner-action :start] outer-state))

        ;; but handled as a message to outer via the reaction of inner:
        (is @inner-state-change-called)
        (is (= [:inner-state [:inner-action :start]] outer-state))))
    ))

(deftest return-in-did-update-test
  ;; testing that all 'returned' values from component-did-update work (issue #23)
  (let [did-update-called (atom false)
        test1 (reacl/class "test1" this app-state [counter2 rval]
                           local-state [counter 1]
                           component-did-update (fn [prev-app-state prev-local-state prev-counter2 prev-rval]
                                                  ;; must use a condition, 
                                                  (if (not @did-update-called)
                                                    (do
                                                      (reset! did-update-called true)
                                                      rval)
                                                    (do
                                                      (reacl/return))))
                           render (dom/div)
                           handle-message
                           (fn [msg]
                             (assert (= :force-update msg))
                             (reacl/return :local-state (inc counter))))
        test2 (reacl/class "test2" this last-ret [rval]
                           local-state [counter 1]
                           render (test1 (reacl/opt :reaction (reacl/reaction this vector :app-state)
                                                    :reduce-action (fn [_ action]
                                                                     (reacl/return :message [this [action :action]])))
                                         nil
                                         counter rval)
                           handle-message
                           (fn [msg]
                             (if (= :force-update msg)
                               (reacl/return :local-state (inc counter))
                               ;; otherwise, 'expose' the app-state change or action returned of test1, with [v :app-state] => [:app-state v]
                               (reacl/return :app-state (reverse msg)))))]

    (testing "local-state works"
      (let [c (test-util/instantiate&mount test1 nil 0 (reacl/return :local-state -1))]
        ;; before
        (is (not= (test-util/extract-local-state c)
                  -1))
        (is (not @did-update-called))
        ;; trigger update
        (reset! did-update-called false)
        (test-util/send-message! c :force-update)
        ;; afterwards:
        (is @did-update-called)
        (is (= (test-util/extract-local-state c)
               -1))))

    (testing "app-state works"
      (let [c (test-util/instantiate&mount test2 nil (reacl/return :app-state :foo))]
        (reset! did-update-called false)
        (test-util/send-message! c :force-update)
        (is @did-update-called)
        (is (= (test-util/extract-app-state c)
               [:app-state :foo]))))

    (testing "actions work"
      (let [c (test-util/instantiate&mount test2 nil (reacl/return :action :foo))]
        (reset! did-update-called false)
        (test-util/send-message! c :force-update)
        (is @did-update-called)
        (is (= (test-util/extract-app-state c)
               [:action :foo]))))
    ))

(deftest action-to-message-test
  ;; Basic test that covers (return :message) as an option to handle actions
  (let [button (reacl/class "button" this [action]
                 render (dom/div)
                 handle-message
                 (fn [msg]
                   (reacl/return :app-state :action-sent-app
                                 :action action)))

        form (reacl/class "form" this state []
               render (button (reacl/opt :reaction (reacl/reaction this identity)
                                         :reduce-action
                                         (fn [_ action]
                                           (assert (= action :action1))
                                           (reacl/return :message [this :msg1])))
                              :action1)
               handle-message
               (fn [msg]
                 (reacl/return :app-state (cons msg state))))]
    (let [c (test-util/instantiate&mount form nil)]
      (test-util/send-message! (dom-with-class c button) nil)
      (is (= (test-util/extract-app-state c)
             [:msg1 :action-sent-app])))))

(deftest action-async-message-test
  (let [button (reacl/class "button" this [action]
                            render (dom/div)
                            handle-message
                            (fn [msg]
                              (reacl/return ;;:local-state :action-sent-loc
                               :app-state :action-sent-app
                               :action this)))

        message-sent (atom false)
        form (reacl/class "form" this state []
                          render (button (reacl/opt :reaction (reacl/reaction this identity)
                                                    :reduce-action
                                                    (fn [_ action]
                                                      (js/window.setTimeout #(do (reacl/send-message! this :msg1)
                                                                                 (reset! message-sent true))
                                                                            10)
                                                      (reacl/return)))
                                         :action1)
                          handle-message
                          (fn [msg]
                            (reacl/return :app-state (cons msg state))))]
    (async done
           (let [c (test-util/instantiate&mount form nil)]
             (test-util/send-message! (dom-with-class c button) nil)
             (js/window.setTimeout (fn []
                                     (is @message-sent)
                                     (is (= (test-util/extract-app-state c)
                                            [:msg1 :action-sent-app]))
                                     (done))
                                   20)))))

(deftest local-state-mapping-test
  ;; regression test; the container local-state was set to the parent's local-state.
  (let [child (reacl/class "child" this state []
                           render (dom/div))
        container (reacl/class "container" this state [f]
                               render (f state)
                               handle-message (fn [msg]
                                                (reacl/return :local-state (:local msg)
                                                              :app-state [this (:app msg)])))
        parent (reacl/class "parent" this []
                            render (container (reacl/opt :reaction (reacl/reaction this #(vector :container-app-state %)))
                                              nil
                                              (fn [state]
                                                (child state)))
                            handle-message (fn [msg]
                                             (reacl/return :local-state msg
                                                           :app-state :baz)))
        
        ]
    (let [c (test-util/instantiate&mount parent)]
      (test-util/send-message! (dom-with-class c container)
                               {:local 42 :app :foo})
      ;; parent's local-state:
      (is (= (first (test-util/extract-local-state c))
             :container-app-state))
      (is (= (second (second (test-util/extract-local-state c)))
             :foo))
      ;; the app-state contains the container instance, so we can look at it's local-state:
      (is (= (test-util/extract-local-state (first (second (test-util/extract-local-state c))))
             42)))))


