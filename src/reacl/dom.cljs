(ns ^{:author "Michael Sperber"
      :doc "Convenience API for constructing virtual DOM.

  This has ClojureScript wrappers for the various HTML elements.

  These all expect attributes as a ClojureScript map.

  Moreover, sub-element sequences need to be ClojureScript sequences of
  objects constructed using `keyed-dom'.

  Moreover, the `letdom' form constructing virtual DOM elements for
  easy reference in an event handler."}
  reacl.dom
  (:require-macros [reacl.dom :refer [defdom]])
  (:refer-clojure :exclude (meta map)))

(defn- ^:no-doc map->obj
  "Convert a Clojure map with keyword keys to a JavaScript hashmap with string keys."
  [mp]
  (apply js-obj
         (apply concat
                (cljs.core/map (fn [e] [(name (key e)) (val e)]) mp))))

(defn- ^:no-doc attributes
  "Convert attributes represented as a Clojure map to a React map.

  This knows about `:style`, and expects a Clojure map for the value."

  [mp]
  (apply js-obj
         (apply concat
                (cljs.core/map (fn [e]
                                 (let [k (key e)
                                       v0 (val e)
                                       v (case k
                                           :style (map->obj v0)
                                           v0)]
                                   [(name k) v]))
                               mp))))

(defprotocol HasDom
  "General protocol for objects that contain or map to a virtual DOM object.

  This is needed for [[letdom]], which wraps the DOM nodes on its
  right-hand sides."  
  (-get-dom [thing]))

(defprotocol ^:no-doc IDomBinding
  (binding-get-dom [this])
  (binding-set-dom! [this v])
  (binding-get-ref [this])
  (binding-set-ref! [this v])
  (binding-get-literally? [this]))

(deftype ^{:doc "Composite object
  containing an atom containing DOM object and a name for referencing.
  This is needed for [[letdom]]."
           :no-doc true}
    DomBinding
 [^{:unsynchronized-mutable true} dom ^{:unsynchronized-mutable true} ref literally?]
  IDomBinding
  (binding-get-dom [_] dom)
  (binding-set-dom! [this v] (set! dom v))
  (binding-get-ref [_] ref)
  (binding-set-ref! [this v] (set! ref v))
  (binding-get-literally? [_] literally?)
  HasDom
  (-get-dom [_] dom))

(defn make-dom-binding
  "Make an empty DOM binding from a ref name.

  If `literally?` is not true, gensym the name."
  [n literally?]
  (DomBinding. nil 
               (if literally?
                 n
                 (gensym n))
               literally?))

(defn dom-node
  "Get the real DOM node associated with a binding.

  Needs the component object."
  [this binding]
  (. (aget (.-refs this) (binding-get-ref binding)) getDOMNode))

(defrecord ^{:doc "DOM with a key, for use as sequences of sub-elements."
             :no-doc true}
    KeyedDom
    [key dom])

(defn keyed
  "Associate a key with a virtual DOM node."
  [key dom]
  (KeyedDom. key dom))

(defn- ^:no-doc set-dom-key
  "Attach a key property to a DOM object."
  [dom key]
  (js/React.addons.cloneWithProps dom #js {:key key}))

(defn- ^:no-doc normalize-arg
  "Normalize the argument to a DOM-constructing function.

  In particular, each [[HasDom]] object is mapped to its DOM object.

  Also, sequences of [[KeyeDom]] sub-elements are mapped to their
  respective DOM elements."
  [arg]
  (cond
   (satisfies? HasDom arg) (-get-dom arg)

   ;; sequence of KeyedDom
   (seq? arg) (to-array
               (cljs.core/map (fn [rd]
                                (set-dom-key (normalize-arg (:dom rd)) (:key rd)))
                              arg))

   ;; deprecated
   (array? arg) (to-array (cljs.core/map normalize-arg arg))

   :else arg))

(defn ^:no-doc dom-function
  "Internal function for constructing wrappers for DOM-construction function."
  [f]
  (fn ([maybe & rest]
         (let [[mp args]
               (if (and (map? maybe)
                        (not (satisfies? HasDom maybe)))
                 [(attributes maybe) rest]
                 [nil (cons maybe rest)])]
           (apply f mp (cljs.core/map normalize-arg args))))
    ([] (f nil))))

(defn ^:no-doc set-dom-binding!
  "Internal function for use by `letdom'.

  This sets the dom field of a DomBinding object, providing a :ref attribute."
  [dn dom]
  (if-let [ref (aget (.-props dom) "ref")]
    ;; it already has a ref, hopefully unique
    (do
      (assert (not (binding-get-literally? dn)))
      (binding-set-ref! dn ref)
      (binding-set-dom! dn dom))
    (binding-set-dom! dn
                      (js/React.addons.cloneWithProps dom #js {:ref (binding-get-ref dn)}))))

;; The following HTML elements are supported by react (http://facebook.github.io/react/docs/tags-and-attributes.html)
(defdom a)
(defdom abbr)
(defdom address)
(defdom area)
(defdom article)
(defdom aside)
(defdom audio)
(defdom b)
(defdom base)
(defdom bdi)
(defdom bdo)
(defdom big)
(defdom blockquote)
(defdom body)
(defdom br)
(defdom button)
(defdom canvas)
(defdom caption)
(defdom cite)
(defdom code)
(defdom col)
(defdom colgroup)
(defdom data)
(defdom datalist)
(defdom dd)
(defdom del)
(defdom details)
(defdom dfn)
(defdom div)
(defdom dl)
(defdom dt)
(defdom em)
(defdom embed)
(defdom fieldset)
(defdom figcaption)
(defdom figure)
(defdom footer)
(defdom form)
(defdom h1)
(defdom h2)
(defdom h3)
(defdom h4)
(defdom h5)
(defdom h6)
(defdom head)
(defdom header)
(defdom hr)
(defdom html)
(defdom i)
(defdom iframe)
(defdom img)
(defdom input)
(defdom ins)
(defdom kbd)
(defdom keygen)
(defdom label)
(defdom legend)
(defdom li)
(defdom link)
(defdom main)
(defdom map)
(defdom mark)
(defdom menu)
(defdom menuitem)
(defdom meta)
(defdom meter)
(defdom nav)
(defdom noscript)
(defdom object)
(defdom ol)
(defdom optgroup)
(defdom option)
(defdom output)
(defdom p)
(defdom param)
(defdom pre)
(defdom progress)
(defdom q)
(defdom rp)
(defdom rt)
(defdom ruby)
(defdom s)
(defdom samp)
(defdom script)
(defdom section)
(defdom select)
(defdom small)
(defdom source)
(defdom span)
(defdom strong)
(defdom style)
(defdom sub)
(defdom summary)
(defdom sup)
(defdom table)
(defdom tbody)
(defdom td)
(defdom textarea)
(defdom tfoot)
(defdom th)
(defdom thead)
(defdom time)
(defdom title)
(defdom tr)
(defdom track)
(defdom u)
(defdom ul)
(defdom var)
(defdom video)
(defdom wbr)
