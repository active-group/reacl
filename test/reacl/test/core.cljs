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
    (println "handle-message")
    (reacl/return :app-state
                  (lens/shove todos
                              (lens/in lens :done?)
                              checked?))))


(defn render
  [dom]
  (js/React.renderComponentToStaticMarkup dom))

(deftest dom
  (let [d (dom/h1 "Hello, world!")]
    (is (= "<h1>Hello, world!</h1>"
           (render d)))))

(deftest simple
  (let [item (reacl/instantiate-toplevel to-do-item (Todo. "foo" false) lens/id)]
    (is (= (reacl/extract-app-state item)
           (Todo. "foo" false)))
    (is (= "<div><input type=\"checkbox\" value=\"false\">foo</div>"
           (render item)))))

(deftest handle-message-simple
  (let [item (reacl/instantiate-toplevel to-do-item (Todo. "foo" false) lens/id)]
    (js/React.addons.TestUtils.renderIntoDocument item)
    (let [[app-state _] (reacl/handle-message->state item true)]
      (is (= app-state (Todo. "foo" true))))))
