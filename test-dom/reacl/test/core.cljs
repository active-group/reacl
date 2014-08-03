(ns reacl.test.core
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]
            [reacl.lens :as lens]
            [cemerick.cljs.test :as t])
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing test-var)]))

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
                              (lens/in lens :done?)
                              checked?))))


(defn render-to-text
  [dom]
  (js/React.renderComponentToStaticMarkup dom))

; see http://stackoverflow.com/questions/22463156/updating-react-component-state-in-jasmine-test
(defn instantiate&mount
  [clazz app-state & args]
  (let [div (js/document.createElement "div")]
    (apply reacl/render-component div clazz app-state args)))

(deftest dom
  (let [d (dom/h1 "Hello, world!")]
    (is (= "<h1>Hello, world!</h1>"
           (render-to-text d)))))

(deftest simple
  (let [item (reacl/instantiate-toplevel to-do-item (Todo. "foo" false) lens/id)]
    (is (= (reacl/extract-app-state item)
           (Todo. "foo" false)))
    (is (= "<div><input type=\"checkbox\" value=\"false\">foo</div>"
           (render-to-text item)))))

(deftest handle-message-simple
  (let [item (instantiate&mount to-do-item (Todo. "foo" false) lens/id)]
    (let [[app-state _] (reacl/handle-message->state item true)]
      (is (= app-state (Todo. "foo" true))))))

(reacl/defclass foo
  this bam [bar]
  local [baz (+ bam 15)
         bla (+ bar 7)]
  render
  (dom/span (dom/div (str baz)) (dom/div (str bla))))

(defn dom-with-tag
  [comp tag-name]
  (js/React.addons.TestUtils.findRenderedDOMComponentWithTag comp tag-name))

(defn doms-with-tag
  [comp tag-name]
  (into []
        (js/React.addons.TestUtils.scryRenderedDOMComponentsWithTag comp tag-name)))

(defn dom-content
  [comp]
  (.-textContent (.getDOMNode comp)))

(deftest locals-sequential
  (let [item (instantiate&mount foo 42 12)
        divs (doms-with-tag item "div")]
    (is (= ["57" "19"]
           (map dom-content divs)))))

(reacl/defclass bar
  this app-state local-state []
  render
  (dom/span (foo this local-state))
  initial-state 1
  handle-message
  (fn [new]
    (reacl/return :local-state new)))

(deftest local-change
  (let [item (instantiate&mount bar 5)]
    (reacl/send-message! item 2)
    (is (= ["20" "9"]
           (map dom-content (doms-with-tag item "div"))))))
