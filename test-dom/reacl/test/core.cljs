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
  (let [preview (apply reacl/instantiate-toplevel clazz app-state args)
        div (js/document.createElement "div")]
    (js/React.renderComponent preview div)))

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
  this app-state [bar]
  local [baz (+ bar 15)
         bla (+ baz 7)]
  render
  (dom/div (str baz) (str bla)))

(deftest locals-sequential
  (let [item (reacl/instantiate-toplevel foo 12)]
    (is (= "<div>27 34</div>")
        (render-to-text item))))

  