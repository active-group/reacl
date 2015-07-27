(ns ^{:author "Michael Sperber, David Frese"
      :doc "Various testing utilities for Reacl."}
  reacl.test-util
  (:require [reacl.core :as reacl :include-macros true]
            [cljsjs.react]))

(defn render-to-text
  [dom]
  (js/React.renderToStaticMarkup dom))

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
     :get-app-state! #(reacl/extract-current-app-state comp)
     :get-local-state! #(reacl/extract-local-state comp)
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
  (let [utils (apply test-class* clazz init-app-state args)]
    (initial-check utils)
    (doseq [interaction interactions]
      (doseq [check (interaction utils)]
        (check utils)))))

(defn after
  "Creates an interaction the sends the given message to the tested
  component, and performs the given checks afterwards. Should be used
  in the context a [[testing-class*]] call."
  [message & checks]
  (fn [{:keys [send-message!]}]
    (send-message! message)
    checks))

(defn simulate
  "Creates an interaction, that will call `f` with the
  React.addons.TestUtils.Simulate object and the dom node of the
  tested component. The simulator object has methods like `click` that
  you can call to dispatch a DOM event. The given checks are performed
  afterwards."
  [f & checks]
  (fn [{:keys [get-dom!]}]
    (f js/React.addons.TestUtils.Simulate (get-dom!))
    checks))

(def no-check
  "An empty check that does nothing."
  (fn [utils]
    nil))

(defn the-app-state
  "Create a check on the app-state, by calling the given function f
  with the app-state at that time. Should be used in the context a
  [[testing-class*]] call."
  [f]
  (fn [{:keys [get-app-state!]}]
    (f (get-app-state!))))

(defn the-local-state
  "Create a check on the local-state, by calling the given function f
  with the local-state at that time. Should be used in the context a
  [[testing-class*]] call."
  [f]
  (fn [{:keys [get-local-state!]}]
    (f (get-local-state!))))

(defn the-dom
  "Create a check on the dom rendered by a component, by calling the
  given function f with the dom-node at that time. Should be used in
  the context a [[testing-class*]] call."
  [f]
  (fn [{:keys [get-dom!]}]
    (f (get-dom!))))

(defn render-shallowly
  "Render an element shallowly."
  [element]
  (let [renderer (js/React.addons.TestUtils.createRenderer)]
    (.render renderer element)
    (.getRenderOutput renderer)))

(defn rendered-children
  "Retrieve children from a rendered element."
  [element]
  (let [c (.-children (.-props element))]
    (cond
     (nil? c) c
     (array? c) (vec c)
     :else (vector c))))

(defn render->hiccup
  [element]
  (if-let [t (aget element "type")]
    (if (string? t)
      (let [props (.-props element)
            attrs (dissoc (into {} (for [k (js-keys props)]
                                     [(keyword k) (aget props k)]))
                          :children)]
        `[~(keyword t)
          ~@(if (empty? attrs)
              '()
              [attrs])
          ~@(map render->hiccup (rendered-children element))])
      (render->hiccup (render-shallowly element)))
    element))

(defn hiccup-matches?
  [pattern data]
  (cond
   (or (keyword? pattern)
       (string? pattern)
       (number? pattern)
       (true? pattern)
       (false? pattern))
   (= pattern data)

   (= '_ pattern) true

   (fn? pattern) (pattern data)

   (vector? pattern)
   (and (vector? data)
        (= (count pattern) (count data))
        (every? some?
                (map hiccup-matches? pattern data)))
   
   (map? pattern)
   (and (map? data)
        (= (keys pattern) (keys data))
        (every? some?
                (map hiccup-matches? (vals pattern) (vals data))))

   :else
   (throw (str "invalid pattern: " pattern))))

