(ns reacl.test.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]
            [reacl.test-util :as test-util]
            [active.clojure.lens :as lens]
            [cljsjs.react]
            [cljs.test :as t])
  (:require-macros [cljs.test
                    :refer (is deftest run-tests testing)]))

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
               :onChange (fn [e]
                           (reacl/send-message! this (.-checked e)))})]
   (dom/div checkbox
            (:text todo)))
  handle-message
  (fn [checked?]
    (reacl/return :app-state
                  (assoc todo :done? checked?))))

(deftest simple
  (let [item (reacl/instantiate-toplevel to-do-item (Todo. 42 "foo" false))]
    (is (= (reacl/extract-app-state item)
           (Todo. 42 "foo" false)))
    (is (= "<div><input type=\"checkbox\" value=\"false\"/>foo</div>"
           (test-util/render-to-text item)))))

(deftest handle-message-simple
  (let [item (test-util/instantiate&mount to-do-item (Todo. 42 "foo" false))]
    (let [[app-state _] (reacl/handle-message->state item true)]
      (is (= app-state (Todo. 42 "foo" true))))))

(deftest to-do-elements
  (let [e (to-do-item (Todo. 42 "foo" true))]
    (is (test-util/hiccup-matches? [:div [:input {:type "checkbox", :value true, :onChange fn?}] "foo"]
                                   (test-util/render->hiccup e)))))

(deftest to-do-message
  (let [e (to-do-item (Todo. 42 "foo" true))
        renderer (js/React.addons.TestUtils.createRenderer)]
    (.render renderer e)
    (let [t (.getRenderOutput renderer)]
      (let [input (test-util/descend-into-element t [:div :input])]
        (.onChange (.-props input) #js {:checked false})))
    (let [t (.getRenderOutput renderer)]
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
  (js/React.addons.TestUtils.findRenderedDOMComponentWithTag comp tag-name))

(defn doms-with-tag
  [comp tag-name]
  (into []
        (js/React.addons.TestUtils.scryRenderedDOMComponentsWithTag comp tag-name)))

(defn dom-with-class
  [comp clazz]
  (js/React.addons.TestUtils.findRenderedComponentWithType comp (reacl/react-class clazz)))

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
    (is (= [12] (reacl/extract-args e)))
    (is (= 42 (reacl/extract-initial-app-state e)))))

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
    (reacl/send-message! item 2)
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
    (reacl/send-message! item 6)
    (is (= ["13"]
           (map dom-content (doms-with-tag item "div"))))))

(reacl/defclass blaz
  this app-state []
  render
  (dom/span (blam 5)))
  
(deftest local-app-state-change-embed
  (let [item (test-util/instantiate&mount blaz 5)
        embedded (dom-with-class item blam)]
    (reacl/send-message! embedded 6)
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
    (reacl/send-message! item 6)
    (is (= ["19"] ;; 2*6+7
           (map dom-content (doms-with-tag item "div"))))))
