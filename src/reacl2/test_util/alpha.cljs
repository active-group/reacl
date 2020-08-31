(ns ^{:doc "Various testing utilities for Reacl."}
  reacl2.test-util.alpha
  (:require [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true]
            ["react-dom/server" :as react-dom-server]
            ["react-dom/test-utils" :as react-tu]
            ["react-test-renderer" :as rtr]
            ["react-test-renderer/shallow" :as rtr-shallow]))

(defn send-message!
  [comp msg]
  (reacl/send-message! comp msg))

(defn extract-app-state
  [comp]
  (reacl/extract-app-state (reacl/resolve-component comp)))

(defn extract-local-state
  [comp]
  (reacl/extract-local-state (reacl/resolve-component comp)))

(defn extract-args
  [comp]
  (reacl/extract-args (reacl/resolve-component comp)))

(defn render-to-text
  [dom]
  (react-dom-server/renderToStaticMarkup dom))

; see http://stackoverflow.com/questions/22463156/updating-react-component-state-in-jasmine-test
(defn instantiate&mount
  [clazz & args]
  (let [div (js/document.createElement "div")]
    (apply reacl/render-component div clazz args)))

(defn- test-class* [clazz & args]
  (let [root (js/document.createElement "div")
        ;;_ (.appendChild (.-body js/document) root)
        comp (apply reacl/render-component root clazz args)]
    {:send-message! #(reacl/send-message! comp %)
     :get-app-state! #(reacl/extract-app-state comp)
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
  [[clazz & args] initial-check & interactions]
  (let [utils (apply test-class* clazz args)]
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
  ReactTestUtils.Simulate object and the dom node of the
  tested component. The simulator object has methods like `click` that
  you can call to dispatch a DOM event. The given checks are performed
  afterwards."
  [f & checks]
  (fn [{:keys [get-dom!]}]
    (f react-tu/Simulate (get-dom!))
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
     (render-shallowly element (rtr-shallow/createRenderer)))
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

(declare resolve-toplevel element-children)

(defn render->hiccup
  [element]
  (letfn [(recurse
            [element]
            (if (string? element)
              element
              (let [ttype (.-type element)]
                (if (string? ttype) ; DOM
                  (let [props (dissoc
                               (into {}
                                     (map (fn [[k v]]
                                            (let [k (keyword k)]
                                              (case k
                                                (:className) [:class v]
                                                [k v])))
                                          (js->clj (.-props element))))
                               :children)
                        ch (map recurse (element-children element))]
                    ;;(println "YO" ttype ch (element-children element) (.findAll element (fn [el] true)))
                    (if (empty? props)
                      (vec (cons (keyword ttype) ch))
                      (vec (list* (keyword ttype) props ch))))
                  (recur (.find element (fn [ti]
                                          (string? (.-type ti)))))))))]
    (recurse (.-root (rtr/create element)))))

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

(defrecord ^:private PathElement [type props-predicate])

(defn ->path-element-type
  [x]
  (cond
    (keyword? x) (name x)
    (reacl/reacl-class? x) (reacl/react-class x)
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
       ((.-props-predicate ^js path-element) (.-props element))))

(defn resolve-toplevel
  [element]
  (.find element
         (fn [ti]
           (not (identical? (.-type ti) reacl/uber-class)))))

(defn descend-into-element
  [element path]
  (let [toplevel (resolve-toplevel element)
        path (map ->path-element path)]
    (letfn [(descend
              [e path]
              (if (empty? path)
                e
                (let [pe (first path)]
                  (if-let [child (.find e
                                        (fn [ti]
                                          (identical? (:type pe) (.-type ti))))]
                    (descend child (rest path))
                    (throw (str "failed finding path element" pe))))))]
      (descend toplevel path))))

(defn create-renderer
  "Create a renderer for testing"
  [& [el]]
  (rtr/create el))

(defn render!
  "Render an element into a renderer."
  [renderer el]
  (.update renderer el))

(defn render-output
  "Get the output of rendering."
  [renderer]
  (.-root renderer))

(defn element-children
  "Get the children of a rendered element as a vector."
  [element]
  (vec (.-children element)))

(defn dom-children
  [dom]
  (let [ch (.-children (.-props dom))]
    (if (array? ch)
      (vec ch)
      [ch])))

(defn element-has-type?
  "Check if an element has a given type, denoted by `tag`.

  `tag` may be a keyword or string with the name of a DOM element,
  or React or a Reacl class."
  [element tag]
  (let [element (resolve-toplevel element)
        ty (.-type element)]
    (cond
     (keyword? tag)
     (= (name tag) ty)

     (string? tag)
     (= tag ty)

     (reacl/reacl-class? tag)
     (identical? ty (reacl/react-class tag))

     :else
     (identical? ty tag))))

(defn render-output=dom?
  "Compare two React DOM elements for equality."
  [tree dom]
  (letfn [(recurse
            [tree dom]
            (if (string? tree)
              (= tree dom)
              (let [ttype (.-type tree)]
                (if (string? ttype) ; DOM
                  (and (= ttype (.-type dom))
                       (let [tree-ch (element-children tree)
                             dom-ch (dom-children dom)]
                         (every? (fn [[t d]]
                                   (recurse t d))
                                 (map vector tree-ch dom-ch))))
                  (let [ch (element-children tree)]
                    (when-not (empty? (rest ch))
                      (throw "component has >1 child")) ; unsatisfactory
                    (recur (first ch) dom))))))]
    (recurse (resolve-toplevel tree) dom)))
          
              

    
(defn invoke-callback
  "Invoke a callback of an element.

  `callback` must a keyword naming the attribute (`onchange`).

  `event` must be a React event - don't forget the ´#js {...}`"
  [element callback event]
  (let [element (reacl/resolve-component element)
        n (aget dom/reacl->react-attribute-names (name callback)) ]
    ((aget (.-props element) n) event)))

(defn handle-message
  "Invoke the message handler of a Reacl element.

  - `cl` is the Reacl class.
  - `app-state` is the app state
  - `local-state` is the local state
  - `msg` is the message.

  This returns a pair `[cmp ret]` where:

  - `ret` is  a ``Returned`` record with `app-state`, `local-state`, and `actions` fields."
  [cl app-state args local-state msg]
  (let [rcl (reacl/react-class cl)
        cmp #js {}
        handle-message-internal (aget (.-prototype rcl) "__handleMessage")]
    ;; FIXME: move Reacl guts exposed here back into the core
    (handle-message-internal nil ;; should have been cmp? (but might break someones tests)
                             app-state local-state
                             true
                             args [] msg)))

(defn handle-message->state
  "Handle a message for a Reacl component.

  This returns application state and local state."
  [comp msg]
  (let [comp (reacl/resolve-component comp)
        ret (reacl/handle-message comp msg)
        
        ret (reacl/reduce-returned-actions comp (extract-app-state comp) ret)
        app-state (reacl/returned-app-state ret)
        local-state (reacl/returned-local-state ret)]
    
    [(if (not (reacl/keep-state? app-state)) app-state (reacl/extract-app-state comp))
     (if (not (reacl/keep-state? local-state)) local-state (reacl/extract-local-state comp))]))

