(ns ^{:author "Michael Sperber, David Frese"
      :doc "Various testing utilities for Reacl."}
  reacl.test-util
  (:require [reacl.core :as reacl :include-macros true]))

(defn render-to-text
  [dom]
  (js/React.renderComponentToStaticMarkup dom))

; see http://stackoverflow.com/questions/22463156/updating-react-component-state-in-jasmine-test
(defn instantiate&mount
  [clazz app-state & args]
  (let [div (js/document.createElement "div")]
    (apply reacl/render-component div clazz app-state args)))

(defn- test-class* [clazz app-state & args]
  (let [root (js/document.createElement "div")
        ;;_ (.appendChild (.-body js/document) root)
        comp (apply reacl/render-component root clazz app-state args)]
    {:send-message! #(reacl/send-message! comp %)
     :get-app-state! #(reacl/extract-app-state comp)
     :get-dom! #(.-firstChild root)}))

(defn with-testing-class*
  "Instantiates the given class with an initial app-state and arguments,
  performs the initial-check and all given interactions.
  
  A 'check' is a function taking a function to retrieve the current
  app-state and a function retrieving the current dom node as rendered
  by the class - see [[the-app-state]] and [[the-dom]] to create usual
  checks.

  An 'interaction' is a function taking a function to send a
  message to the component, and returning a sequence of checks - see
  [[after]] to create a usual interaction.

  Note that check implementations should usually contain clojure-test
  assertions. A common pattern could be:

   (with-testing-class* [my-class nil \"en\"]
     (the-dom #(is (= \"Hello World\" (.-innerText %))))
     (after (set-lang-message \"de\")
            (the-dom #(is (= \"Hallo Welt\" (.-innerText %))))))
"
  [[clazz init-app-state & args] initial-check & interactions]
  (let [{:keys [send-message! get-app-state! get-dom!]} (apply test-class* clazz init-app-state args)]
    (initial-check get-app-state! get-dom!)
    (doseq [interaction interactions]
      (doseq [check (interaction send-message!)]
        (check get-app-state! get-dom!)))))

(defn after
  "Creates an interaction the sends the given message to the tested
  component, and performs the given checks afterwards. Should be used
  in the context a [[testing-class*]] call."
  [message & checks]
  (fn [send-message!]
    (send-message! message)
    checks))

(defn the-app-state
  "Create a checks on the app-state, by calling the given function f
  with the app-state at that time. Should be used in the context a
  [[testing-class*]] call."
  [f]
  (fn [get-app-state! _]
    (f (get-app-state!))))

(defn the-dom
  "Create a checks on the dom rendered by a component, by calling the
  given function f with the dom-node at that time. Should be used in
  the context a [[testing-class*]] call."
  [f]
  (fn [_ get-dom!]
    (f (get-dom!))))

