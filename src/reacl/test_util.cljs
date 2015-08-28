(ns ^{:doc "Various testing utilities for Reacl."}
  reacl.test-util
  (:require [reacl.core :as reacl :include-macros true]
            [reacl.dom :as dom :include-macros true]
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
  ([element]
     (render-shallowly element (js/React.addons.TestUtils.createRenderer)))
  ([element renderer]
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

(defrecord PathElement [type props-predicate])

(defn ->path-element-type
  [x]
  (cond
   (keyword x) (name x)
   :else x))

(defn ->path-element
  [x]
  (cond
   (instance? PathElement x) x

   :else (PathElement. (->path-element-type x) (fn [_] true))))

(defn path-element-matches?
  [path-element element]
  (and (some? (.-type element))
       (= (.-type path-element) (.-type element))
       ((.-props-predicate path-element) (.-props element))))

(defn descend-into-element
  [element path]
  (let [path (map ->path-element path)]
    (when-not (path-element-matches? (first path) element)
      (throw (str "path element " (first path) " does not match " element)))
    (letfn [(descend
              [e path]
              (if (empty? path)
                e
                (let [pe (first path)]
                  (if-let [all-children (.-children (.-props e))]
                    (loop [children (seq all-children)]
                      (cond
                       (empty? children)
                       (throw (str "trying to follow path element " pe ", but no child matched of " e))

                       (path-element-matches? pe (first children))
                       (descend (first children) (rest path))

                       :else (recur (rest children))))

                    (throw (str "trying to follow path " path ", but no children in " e))))))]
      (descend element (rest path)))))

(defn create-renderer
  "Create a shallow renderer for testing"
  []
  (js/React.addons.TestUtils.createRenderer))

(defn render!
  "Render an element into a renderer."
  [renderer el]
  (.render renderer el))

(defn render-output
  "Get the output of rendering."
  [renderer]
  (.getRenderOutput renderer))

(defn element-children
  "Get the children of a rendered element as a vector."
  [element]
  (let [ch (.. element -props -children)]
    (if (array? ch)
      (vec ch)
      [ch])))

(defn element-has-type?
  "Check if an element has a given type, denoted by `tag`.

  `tag` may be a keyword or string with the name of a DOM element,
  or React or a Reacl class."
  [element tag]
  (let [ty (.-type element)]
    (cond
     (keyword? tag)
     (= (name tag) ty)

     (string? tag)
     (= tag ty)

     (satisfies? reacl/HasReactClass tag)
     (js/React.addons.TestUtils.isElementOfType element (reacl/react-class tag))

     :else
     (js/React.addons.TestUtils.isElementOfType element tag))))

(defn dom=?
  "Compare two React DOM elements for equality."
  [el1 el2]
  (= (js->clj el1) (js->clj el2)))

(defn invoke-callback
  "Invoke a callback of an element.

  `callback` must a keyword naming the attribute (`onchange`).

  `event` must be a React event - don't forget the ´#js {...}`"
  [element callback event]
  (let [n (aget dom/reacl->react-attribute-names (name callback))]
    ((aget (.-props element) n) event)))

(defn handle-message
  "Invoke the message handler of a Reacl element.

  - `cl` is the Reacl class.
  - `app-state` is the app state
  - `local-state` is the local state
  - `msg` is the message.

  This returns a ``State`` record with `app-state` and `local-state` fields."
  [cl app-state args local-state msg]
  (let [rcl (reacl/react-class cl)
        handle-message-internal (.bind (aget (.-prototype rcl) "__handleMessage")
                                       #js {:props (reacl/make-props cl app-state args)
                                            :state (reacl/make-state cl app-state local-state args)})]
    (handle-message-internal msg)))

