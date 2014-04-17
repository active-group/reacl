(ns reacl.dom
  (:require-macros [reacl.dom :refer [defdom]]))

(defn- map->obj
  [mp]
  (apply js-obj
         (apply concat
                (map (fn [e] [(name (key e)) (val e)]) mp))))

(defn attributes
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
  (-get-dom [thing]))

(defrecord DomBinding [dom ref]
  HasDom
  (-get-dom [db] @(:dom db)))

(defn make-dom-binding
  [n]
  (DomBinding. (atom nil) (name (gensym n))))

(defn dom-node-ref
  [this binding]
  (. (aget (.-refs this) (:ref binding)) getDOMNode))

(defrecord KeyedDom
    [key dom])

(defn keyed
  [key dom]
  (KeyedDom. key dom))

(defn set-dom-key!
  [dom key]
  (aset (.-props dom) "key" key)) ; undocumented internals

(defn- normalize-arg
  [arg]
  (cond
   (satisfies? HasDom arg) (-get-dom arg)

   ;; sequence of KeyedDom
   (seq? arg) (to-array
               (map (fn [rd]
                      (let [dom (normalize-arg (:dom rd))]
                        (set-dom-key! dom (:key rd))
                        dom))
                    arg))

   ;; deprecated
   (array? arg) (to-array (map normalize-arg arg))
   
   :else arg))

(defn dom-function
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

