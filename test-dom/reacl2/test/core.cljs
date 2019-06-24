(ns reacl2.test.core
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
            [reacl2.test-util.alpha :as test-util]
            ;; include 'reacl1' to see they compile at least:
            [reacl.core :as reacl1 :include-macros true]
            [reacl.dom :as dom1 :include-macros true]
            [active.clojure.lens :as lens]
            [react-test-renderer :as react-test-renderer]
            ["react-dom/test-utils" :as react-tu]
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
  (is (= (test-util/extract-app-state (test-util/instantiate&mount to-do-item (Todo. 42 "foo" false)))
         (Todo. 42 "foo" false)))
  (is (= (test-util/render-to-text (reacl/instantiate-toplevel to-do-item (Todo. 42 "foo" false)))
         "<div><input type=\"checkbox\" value=\"false\"/>foo</div>")))

(deftest handle-message-simple
  (let [item (test-util/instantiate&mount to-do-item (Todo. 42 "foo" false))]
    (let [[app-state _] (test-util/handle-message->state item true)]
      (is (= app-state (Todo. 42 "foo" true))))))

(deftest to-do-elements
  (let [e (to-do-item (reacl/opt :reaction reacl/no-reaction) (Todo. 42 "foo" true))]
    (is (test-util/hiccup-matches? [:div [:input {:type "checkbox", :value true, :onchange fn?}] "foo"]
                                   (test-util/render->hiccup e)))))

(deftest to-do-message
  (let [e (reacl/instantiate-toplevel to-do-item (Todo. 42 "foo" true))
        renderer (react-test-renderer/create e)]
    (let [t (.-root renderer)]
      (let [input (test-util/descend-into-element t [:div :input])]
        (.onChange (.-props input) #js {:checked false})))
    (let [t (.-root renderer)]
      (let [input (test-util/descend-into-element t [:div :input])]
        (is (not (.-value (.-props input))))))))

(defn dom-with-tag
  [comp tag-name]
  (react-tu/findRenderedDOMComponentWithTag comp tag-name))

(defn doms-with-tag
  [comp tag-name]
  (into []
        (react-tu/scryRenderedDOMComponentsWithTag comp tag-name)))

(defn dom-with-class
  [comp clazz]
  (react-tu/findRenderedComponentWithType comp (reacl/react-class clazz)))

(defn doms-with-dom-class
  "Returns a list of all dom nodes with the given `:class? attributes rendered by `comp`."
  [comp clazz]
  (react-tu/scryRenderedDOMComponentsWithClass comp clazz))

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

(deftest locals-sequential-test
  (let [foo (reacl/class "foo"
                         this bam [bar]
                         local [baz (+ bam 15)
                                bla (+ baz bar 7)]
                         render
                         (dom/span (dom/div (str baz)) (dom/div (str bla))))]
    (let [item (test-util/instantiate&mount foo 42 12)
          divs (doms-with-tag item "div")]
      (is (= ["57" "76"]
             (map dom-content divs))))))

(deftest locals-shadowing-test
  ;; locals shall shadow args:
  (let [foo (reacl/class "foo"
                         this [& [baz]]
                         local [baz 42]
                         render (dom/div (str baz)))]
    (let [item (test-util/instantiate&mount foo 12)
          divs (doms-with-tag item "div")]
      (is (= ["42"]
             (map dom-content divs))))))

(deftest has-class?-test
  (let [foo (reacl/class "foo"
                         this []
                         render (dom/span))]
    (is (reacl/has-class? foo (foo)))))

(deftest extract-app-state-test
  (let [foo (reacl/class "foo"
                         this bam []
                         render (dom/span))
        e (foo (reacl/opt :reaction reacl/no-reaction) 42)]
    (is (= 42 (test-util/extract-app-state e)))))

(deftest extract-args-test
  (let [foo (reacl/class "foo"
                         this [bar]
                         render (dom/span))
        e (foo 12)]
    (is (= [12] (test-util/extract-args e)))))


(deftest local-state-test
  (let [bar (reacl/class "bar"
                         this []
                         local-state [local-state 1]
                         render
                         (dom/span (dom/div local-state))
                         handle-message
                         (fn [new]
                           (reacl/return :local-state new)))
        item (test-util/instantiate&mount bar)]
    (is (= ["1"]
           (map dom-content (doms-with-tag item "div"))))
    (test-util/send-message! item 2)
    (is (= ["2"]
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

(deftest local-app-state-change-embed
  (let [blaz (reacl/class "blaz"
                          this app-state []
                          render
                          (dom/span (blam (reacl/opt :embed-app-state (fn [old new] new)) app-state)))]
    (let [item (test-util/instantiate&mount blaz 5)
          embedded (dom-with-class item blam)]
      (test-util/send-message! embedded 6)
      (is (= ["13"]
             (map dom-content (doms-with-tag item "div")))))))

(reacl/defclass blaz2
  this app-state []
  render
  (blam (reacl/opt :reaction reacl/no-reaction) (* 2 app-state))
  handle-message
  (fn [new]
    (reacl/return :app-state new)))
  
(deftest embedded-app-state-change
  (let [item (test-util/instantiate&mount blaz2 5)
        embedded (dom-with-class item blam)]
    (test-util/send-message! item 6)
    (is (= ["19"] ;; 2*6+7
           (map dom-content (doms-with-tag item "div"))))))

(deftest transform-action-test
  (let [action-class1 (reacl/class "action-class1"
                                   this app-state []
                                   render (dom/div "foo")

                                   component-will-mount
                                   (fn []
                                     (reacl/return :action :action)))

        action-class2 (reacl/class "action-class2"
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
                        handle-message  ; the reaction sends a message
                        (fn [msg]
                          (reacl/return)))

        msga (atom [])
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


(deftest app-state-change-with-message-test
  (let [item (test-util/instantiate&mount (reacl/class "app-state-change-with-reaction-class" this state []
                                                       render
                                                       (dom/div (str state))

                                                       handle-message
                                                       (fn [msg]
                                                         (reacl/return :app-state msg)))
                                          1)]
    (is (= ["1"]
           (map dom-content (doms-with-tag item "div"))))
    (test-util/send-message! item 2)
    (is (= ["2"]
           (map dom-content (doms-with-tag item "div"))))))


(deftest local-state-boolean-value-test
  (let [item (test-util/instantiate&mount (reacl/class "local-state-boolean-value-class" this []
                                                       local-state [foo? false]
                                                       render
                                                       (dom/div)))]
    (is (false? (test-util/extract-local-state item))))
  (let [item (test-util/instantiate&mount (reacl/class "local-state-nil-value-class" this []
                                                       local-state [foo? nil]
                                                       render
                                                       (dom/div)))]
    (is (nil? (test-util/extract-local-state item)))))


(deftest text-refs-message
  (let [item (test-util/instantiate&mount (reacl/class "text-refs"
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
                                          "foo")
        dom (dom-with-tag item "input")]
    (react-tu/Simulate.change dom)
    (is (= "foofoooof" (.-value dom)))))

(deftest queued-messages-test
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
                                  (dom/div (queued-sub (reacl/opt :reaction reacl/no-reaction) app-state this))
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
  (let [button (reacl/class "button" this st [action]
                 render (dom/div)
                 handle-message
                 (fn [msg]
                   (reacl/return :app-state :action-sent-app
                                 :action action)))

        form (reacl/class "form" this state []
               render (button (reacl/opt :reaction (reacl/pass-through-reaction this)
                                         :reduce-action
                                         (fn [_ action]
                                           (assert (= action :action1) (pr-str action))
                                           (reacl/return :message [this :msg1])))
                              nil :action1)
               handle-message
               (fn [msg]
                 (reacl/return :app-state (cons msg state))))]
    (let [c (test-util/instantiate&mount form nil)]
      (test-util/send-message! (dom-with-class c button) nil)
      (is (= (test-util/extract-app-state c)
             [:msg1 :action-sent-app])))))

(deftest action-async-message-test
  (let [button (reacl/class "button" this st [action]
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
                                                (child (reacl/opt :reaction reacl/no-reaction) state)))
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


(deftest livecylce-calls-test
  ;; tests all arguments to the livecycle methods, and when they are called.

  (let [calls (atom {})

        c (reacl/class "class"
                       this app-state [arg1 arg2]
                       local-state [state :state1]

                       render (dom/div)

                       handle-message
                       (fn [msg] msg)

                       component-will-mount
                       (fn [& args] (swap! calls assoc :component-will-mount args) nil)
                       
                       component-did-mount
                       (fn [& args] (swap! calls assoc :component-did-mount args) nil)
                       
                       component-will-receive-args
                       (fn [& args] (swap! calls assoc :component-will-receive-args args) nil)
                       
                       component-will-update
                       (fn [& args] (swap! calls assoc :component-will-update args) nil)
                       
                       component-did-update
                       (fn [& args] (swap! calls assoc :component-did-update args) nil)
                       
                       component-will-unmount
                       (fn [& args] (swap! calls assoc :component-will-unmount args) nil)
                       
                       should-component-update?
                       (fn [& args]
                         (swap! calls assoc :should-component-update? args)
                         true))

        div (js/document.createElement "div")]

    (let [comp (reacl/render-component div c :app-state :arg1 :arg2)]
    
      (testing "the first render/mount"
        (is (= @calls
               {:component-will-mount nil
                :component-did-mount nil})))
      
      (testing "a non-update"
        (reset! calls {})
        (test-util/send-message! comp (reacl/return))
        (is (= @calls {})))

      (testing "a local-state update"
        (reset! calls {})
        (test-util/send-message! comp (reacl/return :local-state :state2))
        (is (= @calls
               {:component-will-update '(:app-state :state2 :arg1 :arg2) ;; args = new
                :component-did-update '(:app-state :state1 :arg1 :arg2)
                :should-component-update? '(:app-state :state2 :arg1 :arg2)})))

      (testing "an app-state update from inside"
        (reset! calls {})
        (test-util/send-message! comp (reacl/return :app-state :new-app-state))
        (is (= @calls
               {:component-will-receive-args '(:arg1 :arg2) ;; <- would be nice if this did not fire.
                :component-will-update '(:new-app-state :state2 :arg1 :arg2) ;; args = new
                :component-did-update '(:app-state :state2 :arg1 :arg2) ;; args = old
                :should-component-update? '(:new-app-state :state2 :arg1 :arg2)})))) 
    
    (testing "an update of the args"
      (reset! calls {})
      (reacl/render-component div c :new-app-state :new-arg1 :new-arg2)
      (is (= @calls
             {:component-will-receive-args '(:new-arg1 :new-arg2)
              :component-will-update '(:new-app-state :state2 :new-arg1 :new-arg2)
              :component-did-update '(:new-app-state :state2 :arg1 :arg2)
              :should-component-update? '(:new-app-state :state2 :new-arg1 :new-arg2)})))

    (testing "an app-state update from outside"
      (reset! calls {})
      (reacl/render-component div c :new-app-state2 :new-arg1 :new-arg2)
      (is (= @calls
             {:component-will-receive-args '(:new-arg1 :new-arg2)
              :component-will-update '(:new-app-state2 :state2 :new-arg1 :new-arg2) ;; args = new
              :component-did-update '(:new-app-state :state2 :new-arg1 :new-arg2) ;; args = old
              :should-component-update? '(:new-app-state2 :state2 :new-arg1 :new-arg2)})))

    (testing "an unmount"
      (reset! calls {})
      (reacl/render-component div (reacl/class "foo" this [] render (dom/span)))
    
      (is (= @calls
             {:component-will-unmount nil}))))
  
  )

(deftest re-render-toplevel
  (let [div (js/document.createElement "div")
        clazz (reacl/class "top" this state [arg1]
                           render (dom/div state "-" arg1))]
    (let [item (reacl/render-component div clazz "foo" "bar")]
      (is (= (map dom-content (doms-with-tag item "div"))
             ["foo-bar"])))

    (let [item (reacl/render-component div clazz "bam" "baz")]
      (is (= (map dom-content (doms-with-tag item "div"))
             ["bam-baz"])))))

(deftest class-name-and-component-test
  (is (= (reacl/class-name (reacl/class "foobar" this []
                                        render (dom/div)))
         "foobar"))
  (let [c (test-util/instantiate&mount
           (reacl/class "foobar" this []
                        render (dom/div (str (boolean (reacl/component? this))))))]
    (is (= (map dom-content (doms-with-tag c "div"))
           ["true"]))))

(deftest downward-message-test
  ;; see issue #31
  (let [child (reacl/class "child" this []

                           local-state [local-state 0]

                           handle-message
                           (fn [msg]
                             (case msg
                               :click
                               (reacl/return :action [:click-action this])

                               :from-above
                               (reacl/return :local-state (inc local-state))))

                           render
                           (dom/button))
        parent (reacl/class "parent" this [] 

                            handle-message
                            (fn [msg]
                              (case (first msg)
                                :send-down (reacl/return :message [(second msg) :from-above])))

                            render
                            (dom/div
                             (child (reacl/opt
                                     :reduce-action
                                     (fn [_ action]
                                       (reacl/return :message [this [:send-down (second action)]]))))))
        
        ]
    (let [root (test-util/instantiate&mount parent)
          embedded (dom-with-class root child)]
      (test-util/send-message! embedded :click)
      (is (= 1 (test-util/extract-local-state embedded))))))

(deftest default-should-component-update?-test
  (let [render-called (atom 0)
        cl (reacl/class "class" this state [arg]
                        local-state [x :locst]
                        
                        #_should-component-update?
                        #_(fn [nstate _ narg]
                          (or (not= state nstate)
                              (not= arg narg)))
                        
                        render
                        (do (swap! render-called inc)
                            (dom/div)))]
    (let [div (js/document.createElement "div")]
      (is (= @render-called 0))
      (reacl/render-component div cl :a :b)
      (is (= @render-called 1))
      
      (reacl/render-component div cl :a :b)
      (is (= @render-called 1)))))

(deftest component-did-catch-test
  (let [render-called (atom 0)
        c1 (reacl/class "class1" this [arg]

                        render (if arg
                                 (throw (js/Error. "intentional test error"))
                                 (dom/div)))
        catched (atom nil)
        c2 (reacl/class "class2" this state []

                        local-state [local-state nil]

                        component-did-catch
                        (fn [error info]
                          (reset! catched [error info])
                          (reacl/return :local-state :error))
                        
                        render
                        (if (= :error local-state)
                          (c1 false)
                          (c1 true)))]
    (let [d (do
              ;; React does some fancy things with the browsers error
              ;; handler in DEV, and respects 'default prevented' in
              ;; that it does not log the error then (or is it the browser?)
              (let [eh (fn [ev]
                         (.preventDefault ev))]
                (js/window.addEventListener "error" eh)
                ;; and this suppressed the 'The above error occurred' log msg from React.
                (let [pre js/console.error]
                  (set! js/console.error (fn [& args] nil))
                  (try (test-util/instantiate&mount c2 nil)
                       (finally
                         (set! js/console.error pre)
                         (js/window.removeEventListener "error" eh)))))
              )]
      (is (some? @catched))
      (is (instance? js/Error (first @catched)))
      (is (= (test-util/extract-local-state d)
             :error)))))

(deftest update-consistency-test
  (let [class1 (reacl/class "class1" this app-state []
                            local-state [local-state false]

                            handle-message
                            (fn [msg]
                              (reacl/return :local-state true
                                            :app-state (inc app-state)))

                            component-did-update
                            (fn []
                              ;; at this point it used to be that local-state=true and app-state=0 (not increased yet)
                              ;; so it was increased from 0 to 1 again.
                              (if local-state
                                (reacl/return :local-state false
                                              :app-state (inc app-state))
                                (reacl/return)))

                            render (dom/div))

        cont (reacl/class "container" this state []
                          
                          handle-message (fn [msg]
                                           (reacl/return :app-state msg))
                          
                          render (class1 (reacl/opt :reaction (reacl/pass-through-reaction this))
                                         state))]

    ;; with one message causing two updates, the 'did-update' should
    ;; not see them separately, but consistently together:
    (let [p (test-util/instantiate&mount cont 0)]
      (test-util/send-message! (dom-with-class p class1) :foo)

      (is (= (test-util/extract-app-state p)
             2)))))

(deftest update-consistency2-test
  ;; similar to update-consistency-test but testing when the update
  ;; cascade is caused by rerendering the toplevel comp.
  (let [class1 (reacl/class "class1" this app-state []
                            local-state [local-state false]

                            handle-message
                            (fn [msg]
                              (reacl/return :local-state true
                                            :app-state (inc app-state)))

                            component-did-update
                            (fn []
                              ;; at this point it used to be that local-state=true and app-state=0 (not increased yet)
                              ;; so it was increased from 0 to 1 again.
                              (if local-state
                                (reacl/return :local-state false
                                              :app-state (inc app-state))
                                (reacl/return)))

                            render (dom/div))

        cont (reacl/class "container" this state []
                          
                          handle-message (fn [msg]
                                           (reacl/return :app-state msg))

                          refs [c1-ref]

                          component-did-update
                          (fn []
                            (if (= state 0)
                              (reacl/return :message [(reacl/get-dom c1-ref) :foo])
                              (reacl/return)))
                          
                          render (class1 (reacl/opt :ref c1-ref :reaction (reacl/pass-through-reaction this))
                                         state))]

    (let [div (js/document.createElement "div")]
      ;; render once with app-state -1
      (reacl/render-component div cont -1)
      ;; update with app-state 0 should start the other updates
      (let [p (reacl/render-component div cont 0)]

        (is (= (test-util/extract-app-state p)
               2))))))

(deftest validate-test
  (let [error? (atom nil)
        c (reacl/class "class" this state [arg]

                       validate (reset! error? (not= state arg))
                       
                       render (dom/div))]

    (is (some? (test-util/instantiate&mount c 42 42)))
    (is (not @error?))
    ;; Note: an actual assert or throw in validate could be catched here, but React still logs an error then which spams the test logs.
    (test-util/instantiate&mount c 42 21)
    (is @error?)))

(deftest compose-reducers-test
  (let [r1 (fn [app-state action]
             (reacl/return :app-state (inc app-state)
                           :action action
                           :action :act2))
        r2 (fn [app-state action]
             (reacl/return :app-state (inc app-state)
                           :action [action]))
        r3 (fn [app-state action]
             (reacl/return :action (first action)))
        c (reacl/compose-reducers (reacl/compose-reducers r1 r2)
                                  r3)]
    ;; r1 called once, r2 twice, and packing in r2 and unpacking in r2 or the actions:
    (is (= (c 0 :testact)
           (reacl/return :app-state 3
                         :action :testact
                         :action :act2)))
    
    ;; keep-state is preserved:
    (is (= ((reacl/compose-reducers r3 r3)
            :my-state [[:testact]]))
        (reacl/return :action :testact))))

(deftest change-reducer-test
  (let [c1 (reacl/class "class1" this []

                        handle-message
                        (fn [act]
                          (reacl/return :action act))

                        render (dom/div))
        c2 (reacl/class "class2" this state []

                        handle-message
                        (fn [st]
                          (reacl/return :app-state st))

                        render
                        ;; Note: the component may even be nested in a div:
                        (reacl/reduce-action (dom/div (c1))
                                             (fn [app-state action]
                                               (reacl/return :message [this [:transformed action]]))))

        e (test-util/instantiate&mount c2 :init)
        inner (dom-with-class e c1)]
    (test-util/send-message! inner :my-act)
    (is (= (test-util/extract-app-state e)
           [:transformed :my-act]))))

(deftest focus-and-virtual-app-state-test
  ;; focus bindings should work, even if a app-state is changed twice during a single cycle.
  (let [c1 (reacl/class "class1" this state []

                        handle-message
                        (fn [act]
                          (if (= act :act1)
                            (reacl/return :app-state (inc state)
                                          :message [this :act2])
                            (reacl/return :app-state (inc state))))

                        render (dom/div))
        outer-messages (atom 0)
        c2 (reacl/class "class2" this state []

                        handle-message
                        (fn [new-state]
                          (swap! outer-messages inc)
                          (reacl/return :app-state (-> state
                                                       (update :outer inc)
                                                       (assoc :inner (:inner (:middle new-state))))))

                        render
                        ;; testing with two focus calls... Note that 'reactive' can build an arbitrary app-state.
                        (c1 (-> (reacl/reactive {:middle {:inner (:inner state)}} (reacl/pass-through-reaction this))
                                (reacl/focus :middle)
                                (reacl/focus :inner))))

        e (test-util/instantiate&mount c2 {:inner 0 :outer 0})
        inner (dom-with-class e c1)]
    (test-util/send-message! inner :act1)
    
    (is (= @outer-messages 2))
    (is (= (test-util/extract-app-state e)
           {:inner 2 :outer 2})))
  )

(deftest screwed-reactions-test
  ;; with a lot of trickery, one can construct a broken 'reaction' with an invalid target.
  ;; the messages of it will not be delivered. Maybe it could be, but probably it makes no sense:
  (let [sent (atom false)
        child-1 (reacl/class "child-1"
                             this [parent]
                             component-did-mount
                             (fn []
                               (reacl/return :message [parent this]))
                             
                             render
                             (dom/div)

                             handle-message
                             (fn [msg]
                               (reset! sent true)
                               (reacl/return)))
        child-2 (reacl/class "child-2"
                             state this []
                             handle-message (fn [msg] ;; = 42
                                              (reacl/return :app-state :this-shall-not-be-sent))
                             render (dom/div))
        parent (reacl/class "parent"
                            this []
                            local-state [component-1 nil]
                            handle-message (fn [comp-1]
                                             (reacl/return :local-state comp-1))
                            render (dom/div (if component-1
                                              ;; make child-1 the reaction target of child-2:
                                              (child-2 (reacl/opt :reaction (reacl/reaction component-1 identity))
                                                       nil)
                                              (dom/div))
                                            (child-1 this)))]
    (let [c (test-util/instantiate&mount parent)
          comp-2 (dom-with-class c child-2)

          pre reacl/warning
          warning-args (atom nil)]
      (try
        (set! reacl/warning (fn [& args] (reset! warning-args args)))
        (test-util/send-message! comp-2 42)
        (finally
          (set! reacl/warning pre)))
      (is (= (second @warning-args)
             :this-shall-not-be-sent))
      (is (not @sent)))))

(deftest app-state-preservation
  ;; With two messages in once cycle, where the first changes the
  ;; app-state it got lost if the second is not changing it
  (let [class1 (reacl/class "class1" this state []
                            render (dom/div)
                            handle-message
                            (fn [msg]
                              (if (= :start msg)
                                (reacl/return :message [this :msg2]
                                              :app-state (inc state))
                                (reacl/return))))]
    (let [c (test-util/instantiate&mount class1 0)]
      (test-util/send-message! c :start)
      (is (= 1 (test-util/extract-app-state c))))))
