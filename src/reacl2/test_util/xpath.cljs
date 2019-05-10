(ns reacl2.test-util.xpath
  (:require [reacl2.core :as reacl]
            [reacl2.dom :as dom]
            [reacl2.test-util.alpha :as alpha]
            cljsjs.react.test-renderer)
  (:refer-clojure :exclude [type comp range first last nth or and contains?]))

;; Idea: an xpath is a concatenated sequence of selectors (>> sel1 sel2 ...) that can be
;; applied to a list of nodes, where each selector maps that list to a
;; new list of nodes. Nodes can be components (reacl class instances)
;; or virtual dom nodes, as well as the app-state, local-state or the
;; argments vector of components and attributes or text content of
;; virtual dom nodes.

;; nodes

;; Note: nodes should have an identity; so that we prevent finding
;; duplicates (in an 'or' for example), while still returning text
;; nodes that occurr multiple times.

(defprotocol ^:private XPathNode
  (node-value [this] "Return the value represented by this node.")
  (node-type [this] "Return a type of this node; a string for dom nodes, a React class, or a keyword for other nodes.")
  (node-parent [this] "Return the parent node instance, if not the root node.")
  (node-children [this] "Return a sequence of nodes that are immediate children of this node."))

(declare ^:private react-test-instance)

(defn- find-pos [lst v]
  (loop [i 0
         lst (seq lst)]
    (when-not (empty? lst)
      (if (= v (clojure.core/first lst))
        i
        (recur (inc i) (rest lst))))))

(defn- find-node-pos [node]
  (if-let [p (clojure.core/and (.hasOwnProperty node "parent") ;; is a ReactTestInstance?
                               (.-parent node)
                               (find-pos (.-children (.-parent node)) node))]
    p
    nil))

(defrecord ^:private ReactTestInstance [ri idx]
  XPathNode
  (node-value [this] ri)
  (node-type [this] (.-type ri))
  (node-children [this] (map-indexed #(react-test-instance this %1 %2) (.-children ri)))
  (node-parent [this] (let [p (.-parent ri)]
                        (ReactTestInstance. p (find-node-pos p)))))

(defrecord ^:private RootNode [toplevel]
  XPathNode
  (node-value [this] (assert false "Cannot select on the root along. Add some tag of class selectors.")) ;; also something special in DOM xpath
  (node-type [this] ::root)
  (node-children [this] (map-indexed #(react-test-instance this %1 %2) (.-children toplevel)))
  (node-parent [this] nil))

(defrecord ^:private TextNode [parent idx s]
  XPathNode
  (node-value [this] s)
  (node-type [this] ::string)
  (node-children [this] nil)
  (node-parent [this] parent))

(defn- node-position [n]
  (if (clojure.core/or (instance? TextNode n)
                       (instance? ReactTestInstance n))
    (:idx n)
    nil))

;; used for attribute values, app-state and args lists.
(defrecord ^:private NodeProperty [parent id value]
  XPathNode
  (node-value [this] value)
  (node-type [this] id) ;; ??
  (node-children [this] nil)
  (node-parent [this] parent))

(defn- react-test-instance [parent idx v]
  (if (string? v)
    (TextNode. parent idx v)
    ;; the raw ReactTestInstance (v) should already have an identity.
    (ReactTestInstance. v idx)))

(defn- node-props [node]
  ;; = attributes for dom nodes.
  (when (instance? ReactTestInstance node)
    (.-props (node-value node))))

(defn- node-instance [node]
  ;; corresponds to 'this' inside a class.
  (when (clojure.core/and (instance? ReactTestInstance node)
                          (not (string? (node-type node))) ;; is class?
                          )
    (.-instance (node-value node))))

;; selectors

(defprotocol ^:no-doc XPathSelector
  (-compose [this other] "Return a new selector, that is equivalent to this followed by the given other selector.")
  ;; Note: this is probably not the most efficient api for large node trees, but should be good enough for tests:
  (-map-nodes [this nodes] "Return a list of nodes matching this selector relative to the given nodes."))

(defrecord ^:no-doc SimpleCompose [sel1 sel2]
  XPathSelector
  (-compose [this other] (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (->> nodes
         (-map-nodes sel1)
         (-map-nodes sel2))))

(defrecord ^:private Id []
  XPathSelector
  (-compose [this other] other)
  (-map-nodes [this nodes] nodes))

(defrecord ^:private Void []
  XPathSelector
  (-compose [this other] this)
  (-map-nodes [this nodes] #{}))

(defrecord ^:private Children []
  XPathSelector
  (-compose [this other]
    ;; Note: could optimize this here if other is Type for example.
    (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (mapcat node-children nodes)))

(defn ^:no-doc range-plus [count from to]
  (let [to (if (zero? to)
             count
             (if (< to 0)
               (+ count to)
               to))
        from (if (< from 0)
               (+ count from)
               from)
        [from to] (if (< from to)
                    [from to]
                    [to from])
        from (if (< from 0) 0 from)
        to (if (> to count) count to)]
    (clojure.core/range from to)))

(defrecord ^:private Position [from to]
  XPathSelector
  (-compose [this other]
    ;; Note: could optimize this here if other is Type for example.
    (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (filter (fn [n]
              (when-let [p (node-parent n)]
                (let [count (node-children p)
                      indixes (set (range-plus count from to))]
                  (clojure.core/contains? indixes (node-position n)))))
            nodes)))

(defrecord ^:private All []
  XPathSelector
  (-compose [this other]
    ;; Note: could optimize this here if other is Type for example.
    (SimpleCompose. this other))
  (-map-nodes [this nodes]
    ;; these nodes, their children, and all their children
    (let [cs (-map-nodes (Children.) nodes)]
      (concat nodes
              (when-not (empty? cs)
                (-map-nodes this cs))))))

(defrecord ^:private Type [t]
  XPathSelector
  (-compose [this other] (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (let [t (if (reacl/reacl-class? t)
              (reacl/react-class t)
              t)]
      (filter #(= (node-type %) t) nodes)))) ;; FIXME: case insensitive?

(defrecord ^:private Text []
  XPathSelector
  (-compose [this other] (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (filter #(instance? TextNode %) (-map-nodes (Children.) nodes))))

(defrecord ^:private Attr [n]
  XPathSelector
  (-compose [this other] (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (->> nodes
         ;; only DOM nodes.
         (filter (fn [n] (string? (node-type n))))
         (filter #(.hasOwnProperty (node-props %) n))
         (map (fn [node]
                (NodeProperty. node (keyword n) (aget (node-props node) n)))))))

(defn- map-nodes-to-property [nodes property]
  (->> nodes
       (filter (fn [n] (not (string? (node-type n)))))
       (filter #(.hasOwnProperty (node-props %) property))
       (map (fn [node]
              (NodeProperty. node (keyword property) (aget (node-props node) property))))))

(defrecord ^:private AppState []
  XPathSelector
  (-compose [this other] (SimpleCompose. this other))
  (-map-nodes [this nodes]
    ;; FIXME: remove class instances without app-state
    (map-nodes-to-property nodes "reacl_app_state")))

(defrecord ^:private LocalState []
  XPathSelector
  (-compose [this other] (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (->> nodes
         (map node-instance)
         (filter some?)
         (map #(NodeProperty. % "local-state" (reacl/extract-local-state %))))))

(defrecord ^:private Args []
  XPathSelector
  (-compose [this other] (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (map-nodes-to-property nodes "reacl_args")))

(defrecord ^:private Root []
  XPathSelector
  (-compose [this other]
    (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (assert (not-empty nodes) "Cannot go to the root of an empty node set.") ;; or we could remember the node passed to select?
    #{(RootNode. (alpha/resolve-toplevel (node-value (node-parent (clojure.core/first nodes)))))}))

(defrecord ^:private Has [sel]
  XPathSelector
  (-compose [this other]
    (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (remove (fn [n] (empty? (-map-nodes sel #{n})))
            nodes)))

(defrecord ^:private Is [pred args]
  XPathSelector
  (-compose [this other]
    (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (filter #(apply pred (node-value %) args)
            nodes)))

(defrecord ^:private Parent []
  XPathSelector
  (-compose [this other]
    (SimpleCompose. this other))
  (-map-nodes [this nodes]
    ;; Note: multiple nodes may have the same parent; removing duplicates:
    (distinct (filter some? (map node-parent nodes)))))

(defrecord ^:private Or [sel1 sel2]
  XPathSelector
  (-compose [this other]
    (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (distinct (concat (-map-nodes sel1 nodes)
                      (-map-nodes sel2 nodes)))))

(defn ^:no-doc concat-commons [l1 l2]
  (let [s1 (set l1)
        s2 (set l2)]
    (distinct (concat (filter s2 l1)
                      (filter s1 l2)))))

(defrecord ^:private And [sel1 sel2]
  XPathSelector
  (-compose [this other]
    (SimpleCompose. this other))
  (-map-nodes [this nodes]
    (concat-commons (-map-nodes sel1 nodes)
                    (-map-nodes sel2 nodes))))

;; external api

(defn select-all
  "Returns all nodes selected by `selector` in the given `node`."
  [node selector]
  (map node-value
       (-map-nodes selector [(let [nn (reacl/resolve-component node)]
                               (ReactTestInstance. nn (find-node-pos nn)))])))

(defn select
  "Returns the node selected by `selector` in the given `node`, or throws if there is more than one, or returns `nil` otherwise."
  [node selector]
  (let [r (select-all node selector)]
    (cond
      (empty? r) nil
      (empty? (rest r)) (clojure.core/first r)
      :else (throw (js/Error. "More than one node matches the given path.")))))

(defn select-one
  "Returns the node selected by `selector` in the given `node`, or throws if there is none or more than one."
  [node selector]
  (let [r (select-all node selector)]
    (cond
      (empty? r) (throw (js/Error. "No node matches the given path."))
      (empty? (rest r)) (clojure.core/first r)
      :else (throw (js/Error. "More than one node matches the given path.")))))

(defn contains?
  "Returns if the given `selector` selects anything in the given `node`, i.e. if the result of [[select-all]] would be not-empty."
  [node selector]
  (not-empty (select-all node selector)))

(def ^{:doc "Selects the current node. This the identity element of composing selects with [[comp]]."} self (Id.))

(def ^{:doc "Selects the parent of the current node."}
  parent (Parent.))

(defn or
  "Selects all nodes that any of the given selectors selects."
  [& selectors]
  (if (empty? selectors)
    self ;; ???
    (reduce #(Or. %1 %2)
            selectors)))

(def ^{:doc "A selector that drops everything, making a selection empty."} void (Void.))

(defn and
  "Selects all nodes that all of the given selectors selects."
  [& selectors]
  (if (empty? selectors)
    void ;; ???
    (reduce #(And. %1 %2)
            selectors)))

(defn type "Selects those nodes that either of the given dom tag type, or the given Reacl class." [t]
  ;; (assert (or (string? t) (reacl/reacl-class? t) (react-class? t)))
  (Type. t))

(def ^{:doc "Selects only those nodes that are a virtual dom element of the given string `type`."
       :arglists '([type])}
  tag type)
(def ^{:doc "Selects only those nodes that are a class instance given Reacl `class`."
       :arglists '([class])}
  class type)

(defn- lift-selector [sel]
  ;; for convenience, lift some values are that "obviously" meant to be certain selectors:
  (cond
    (string? sel) (tag sel)
    (keyword? sel) (tag (name sel))
    (reacl/reacl-class? sel) (class sel)
    :else sel))

(defn comp "Compose the given xpath selector forms to a combined
  selector, where from left to right, the selectors restrict the query
  further. \n
  Valid selectors are all the primitives from this module,
  as well as:\n
  - strings or keywords stand for a virtual dom node as with [[tag]],
  - Reacl classes stand for a selection by that class as with [[class]]\n
  Also see [[reacl/>>]] for a convenience macro version of this.
"
  ([] self) ;; TODO: or void? or error?
  ([& selectors] (reduce -compose (map lift-selector selectors))))

(def ^{:doc "Selects the current node and all of it's children and grand children."} all (All.))

(def ^{:doc "Selects the children of the current node."} children (Children.))

(defn range
  "Selects nodes based on their position in the children list of their
  parent, starting at index `from` (inclusive) up to index
  `to` (exclusive). Both `from` and `to` can be negative meaning a
  position from the end of the children list. A 0 in `from` means the
  start of the list, but a 0 in `to` stands for the end of list,
  resp. one behind. So `(range 0 0)` means the full list."
  [from to]
  (Position. from to))

(defn nth "Select nodes that are the nth child, starting at index 0." [n] (range n (inc n)))
(defn nth-last "Select nodes that are \"nth but last\" child." [n] (range (- n) (inc (- n))))

(def first "Select nodes that are the first child of their parent." (nth 0))
(def last "Select nodes that are the last child of their parent." (range -1 0))

(def root "Select the root of node tree." (Root.))

(def ^{:doc "Selects the child nodes of type 'text'."} text
  (Text.))

(defn- resolve-attr-name [n]
  (clojure.core/or (clojure.core/and (string? n) n)
                   (aget dom/reacl->react-attribute-names (name n))
                   (name n)))

(defn attr
  "Selects the value of an attribute `name` from virtual dom nodes."
  [name]
  (Attr. (resolve-attr-name name)))

(def ^{:doc "Selects the app-state if the current node is a Reacl component that has one."} app-state
  (AppState.))

(def ^{:doc "Selects the local-state if the current node is a Reacl component."} local-state
  (LocalState.))

(def ^{:doc "Selects the argument vector if the current node is a Reacl component."} args
  (Args.))

(defn has?
  "Selects the nodes for which the given selector would result in a non-empty list of nodes."
  [sel]
  (Has. sel))

(defn is? "Keeps the current node only if `(pred node & args)` returns truthy." [pred & args]
  (Is. pred args))

(defn is= "Kepps the current node only if it is equal to `v`." [v]
  (is? = v))

(defn id= "Kepps the current node only if it has an attribute `id` equaling `v`." [v]
  (comp (attr :id) (is= v)))

(defn- re-matches-rev [s re]
  (re-matches re s))

(defn re-matches? "Kepps the current node only if it is matches the given `regex`." [regex]
  (is? re-matches-rev regex))
