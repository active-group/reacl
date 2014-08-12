(ns ^{:author "Michael Sperber"
      :doc "Convenience API for constructing virtual DOM.

  This has ClojureScript wrappers for the various HTML elements.

  These all expect attributes as a ClojureScript map.

  Moreover, sub-element sequences need to be ClojureScript sequences of
  objects constructed using `keyed-dom'.

  Moreover, the `letdom' form constructing virtual DOM elements for
  easy reference in an event handler."}
  reacl.dom
  (:require-macros [reacl.dom :refer [defdom]]))

(defn- map->obj
  "Convert a Clojure map with keyword keys to a JavaScript hashmap with string keys."
  [mp]
  (apply js-obj
         (apply concat
                (map (fn [e] [(name (key e)) (val e)]) mp))))

(defn- attributes
  "Convert attributes represented as a Clojure map to a React map.

  This knows about :style, and expects a Clojure map for the value."

  [mp]
  (apply js-obj
         (apply concat
                (map (fn [e]
                       (let [k (key e)
                             v0 (val e)
                             v (case k
                                 :style (map->obj v0)
                                 v0)]
                         [(name k) v]))
                     mp))))

(defprotocol HasDom
  "General protocol for objects that contain or map to a virtual DOM object.

  This is needed for `letdom', which wraps the DOM nodes on its
  right-hand sides."  
  (-get-dom [thing]))

(defrecord DomBinding 
    ^{:doc "Composite object
  containing an atom containing DOM object and a name for referencing.

  This is needed for `letdom'."}
  [dom ref]
  HasDom
  (-get-dom [db] @(:dom db)))

(defn make-dom-binding
  "Make an empty DOM binding from a ref name."
  [n]
  (DomBinding. (atom nil) n))

(defn dom-node
  "Get the real DOM node associated with a binding.

  Needs the component object."
  [this binding]
  (. (aget (.-refs this) (:ref binding)) getDOMNode))

(defrecord KeyedDom
    ^{:doc "DOM with a key, for use as sequences of sub-elements."}
    [key dom])

(defn keyed
  "Associate a key with a virtual DOM node."
  [key dom]
  (KeyedDom. key dom))

(defn- set-dom-key
  "Attach a key property to a DOM object."
  [dom key]
  (js/React.addons.cloneWithProps dom #js {:key key}))

(defn- normalize-arg
  "Normalize the argument to a DOM-constructing function.

  In particular, each HasDom objects is mapped to its DOM object.

  Also, sequences of KeyeDDom sub-elements are mapped to their
  respective DOM elements."
  [arg]
  (cond
   (satisfies? HasDom arg) (-get-dom arg)

   ;; sequence of KeyedDom
   (seq? arg) (to-array
               (map (fn [rd]
                      (set-dom-key (normalize-arg (:dom rd)) (:key rd)))
                    arg))

   ;; deprecated
   (array? arg) (to-array (map normalize-arg arg))

   :else arg))

(defn dom-function
  "Internal function for constructing wrappers for DOM-construction function."
  [f]
  (fn ([maybe & rest]
         (let [[mp args]
               (if (and (map? maybe)
                        (not (satisfies? HasDom maybe)))
                 [(attributes maybe) rest]
                 [nil (cons maybe rest)])]
           (apply f mp (map normalize-arg args))))
    ([] (f nil))))

(defn set-dom-binding!
  "Internal function for use by `letdom'.

  This sets the dom field of a DomBinding object, providing a :ref attribute."
  [dn dom]
  (reset! (:dom dn)
          (js/React.addons.cloneWithProps dom #js {:ref (:ref dn)})))

(defdom div)
(defdom span)
(defdom p)
(defdom h1)
(defdom h2)
(defdom h3)
(defdom h4)
(defdom h5)
(defdom h6)
(defdom table)
(defdom thead)
(defdom tbody)
(defdom tr)
(defdom td)
(defdom th)
(defdom input)
(defdom form)
(defdom button)
(defdom ul)
(defdom li)
(defdom br)
(defdom label)
(defdom i)
(defdom hr)
(defdom a)
(defdom select)
(defdom option)


