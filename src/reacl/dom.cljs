(ns reacl.dom
  (:require-macros [reacl.dom :refer [defdom]]))

(defn map->obj
  [mp]
  (apply js-obj
         (apply concat
                (map (fn [e] [(name (key e)) (val e)]) mp))))

(defrecord DomBinding [dom ref])

(defn make-dom-binding
  [n]
  (DomBinding. (atom nil) (name (gensym n))))

(defn dom-node-ref
  [this binding]
  (. (aget (.-refs this) (:ref binding)) getDOMNode))

(defn- normalize-arg
  [arg]
  (cond
   (instance? DomBinding arg) @(:dom arg)

   (array? arg) (to-array (map normalize-arg arg))

   :else arg))
   
(defn dom-function
  [f]
  (fn ([maybe & rest]
         (let [[mp args]
               (if (and (map? maybe)
                        (not (instance? DomBinding maybe)))
                 [(map->obj maybe) rest]
                 [nil (cons maybe rest)])]
           (apply f mp (map normalize-arg args))))
    ([] (f nil))))

(defn set-dom-binding!
  [dn dom]
  (aset (.-props dom) "ref" (:ref dn)) ; undocumented internals
  (reset! (:dom dn) dom))

(defdom div)
(defdom span)
(defdom p)
(defdom h1)
(defdom h2)
(defdom h3)
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

(defn make-ref
  [n]
  (let [ref (name (gensym n))]
    ref))

(defn deref-dom
  [this ref]
  (. (aget (.. this -refs) ref) getDOMNode))

;; FIXME: want recursive binding form that sticks refs in all the DOMs
;; and makes them usable

;; Probably requires our own DOM library

;; Q: Can we modify attributes of DOM node after the fact?  Not
;; necessary if we do our own DOM stuff, but would be nice.






