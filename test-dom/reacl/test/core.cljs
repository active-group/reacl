(ns reacl.test.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]
            [reacl.test-util :as test-util]
            [active.clojure.lens :as lens]
            [cljs.test :as t])
  (:require-macros [cljs.test
                    :refer (is deftest run-tests testing)]))

(enable-console-print!)

(defrecord Todo [text done?])

(reacl/defclass to-do-item
  this todos [lens]
  render
  (let [todo (lens/yank todos lens)]
    (dom/letdom
     [checkbox (dom/input
                {:type "checkbox"
                 :value (:done? todo)
                 :onChange #(reacl/send-message! this
                                                 (.-checked (dom/dom-node this checkbox)))})]
     (dom/div checkbox
              (:text todo))))
  handle-message
  (fn [checked?]
    (reacl/return :app-state
                  (lens/shove todos
                              (lens/>> lens :done?)
                              checked?))))


(deftest dom
  (let [d (dom/h1 "Hello, world!")]
    (is (= "<h1>Hello, world!</h1>"
           (test-util/render-to-text d)))))

(deftest simple
  (let [item (reacl/instantiate-toplevel to-do-item (Todo. "foo" false) lens/id)]
    (is (= (reacl/extract-app-state item)
           (Todo. "foo" false)))
    (is (= "<div><input type=\"checkbox\" value=\"false\">foo</div>"
           (test-util/render-to-text item)))))

(deftest handle-message-simple
  (let [item (test-util/instantiate&mount to-do-item (Todo. "foo" false) lens/id)]
    (let [[app-state _] (reacl/handle-message->state item true)]
      (is (= app-state (Todo. "foo" true))))))

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
  (.-textContent (.getDOMNode comp)))

(deftest initial-state-test
  (testing "initial-state-test sees app-state, locals and args"
    (let [item (test-util/instantiate&mount
                (reacl/class "initial-state-test" this app-state local-state [arg1]
                             initial-state
                             (do (is (= app-state "app-state"))
                                 (is (= arg1 "arg1"))
                                 (is (= local1 "local1"))
                                 "local-state")
                             local [local1 "local1"]
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

(reacl/defclass bar
  this app-state local-state []
  initial-state 1
  render
  (dom/span (foo this local-state))
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
  (dom/span (reacl/embed blam this 5 (fn [_] nil))))
  
(deftest local-app-state-change-embed
  (let [item (test-util/instantiate&mount blaz 5)
        embedded (dom-with-class item blam)]
    (reacl/send-message! embedded 6)
    (is (= ["13"]
           (map dom-content (doms-with-tag item "div"))))))

(reacl/defclass blaz2
  this app-state []
  render
  (reacl/embed blam this (* 2 app-state) (fn [_] nil))
  handle-message
  (fn [new]
    (reacl/return :app-state new)))
  
(deftest embedded-app-state-change
  (let [item (test-util/instantiate&mount blaz2 5)
        embedded (dom-with-class item blam)]
    (reacl/send-message! item 6)
    (is (= ["19"] ;; 2*6+7
           (map dom-content (doms-with-tag item "div"))))))
