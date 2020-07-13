(ns reacl2.test-util.xpath
  "An xpath is basically a convenient and compositional way to define
  a filter function on nodes in a virtual DOM tree, resp. the React
  test renderer tree, roughly based on a 'path' through the tree.

  The functions [[select]], [[select-all]], [[select-one]]
  and [[contains?]] apply that filter, starting from a specific
  _context node_.

  For example:

```
(require [reacl2.test-util.xpath :as xp :include-macros true])

(>> / ** \"div\" [xp/text (xp/is= \"Hello\")])
```

  matches on all `div` elements anywhere below the context node, that have a text content equal to `\"Hello\"`.

```
(>> / my-class / \"span\" [:id (xp/is= \"foo\")])
```

  matches on all `span` children of instances of the class `my-class` below the context node, that have the id `\"foo\"`.

```
(>> my-class [xp/args (xp/is? = [42])])
```

  matches the context node, if it is an instance of `my-class` and if its argument vector equals `[42]`.

```
(>> / \"span\" [xp/first])
```

  matches the first span element below the context node.

"
  
  (:require [reacl2.core :as reacl]
            [reacl2.dom :as dom]
            [clojure.string :as str]
            [clojure.set :as set]
            [reacl2.test-util.alpha :as alpha])
  (:refer-clojure :exclude [type comp range first last nth or and contains? key not]))

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

(defn- node? [v]
  (satisfies? XPathNode v))

(defn- class-type? [v]
  ;; (reacl/reacl-class? v) seemed to work, but doesn't anyore? (probably it's the React class)
  (clojure.core/and (clojure.core/not (string? v))
                    (clojure.core/not (keyword? v))))

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
                          (clojure.core/not (string? (node-type node))) ;; is class?
                          )
    (.-instance (node-value node))))

;; selectors

(defprotocol ^:no-doc XPathSelector
  ;; Note: this is probably not the most efficient api for large node trees, but should be good enough for tests:
  (-map-nodes [this nodes] "Return a list of nodes matching this selector relative to the given nodes."))

(defrecord ^:no-doc SimpleCompose [sel1 sel2]
  XPathSelector
  (-map-nodes [this nodes]
    (->> nodes
         (-map-nodes sel1)
         (-map-nodes sel2))))

(defrecord ^:private Id []
  XPathSelector
  (-map-nodes [this nodes] nodes))

(defrecord ^:private Void []
  XPathSelector
  (-map-nodes [this nodes] #{}))

(defrecord ^:private Children []
  XPathSelector
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
  (-map-nodes [this nodes]
    (filter (fn [n]
              (when-let [p (node-parent n)]
                (let [count (node-children p)
                      indixes (set (range-plus count from to))]
                  (clojure.core/contains? indixes (node-position n)))))
            nodes)))

(defrecord ^:private All []
  XPathSelector
  (-map-nodes [this nodes]
    ;; these nodes, their children, and all their children
    (let [cs (-map-nodes (Children.) nodes)]
      (concat nodes
              (when-not (empty? cs)
                (-map-nodes this cs))))))

(defrecord ^:private Type [t]
  XPathSelector
  (-map-nodes [this nodes]
    (let [t (if (reacl/reacl-class? t)
              (reacl/react-class t)
              t)]
      (filter #(= (node-type %) t) nodes)))) ;; FIXME: case insensitive?

(defrecord ^:private Text []
  XPathSelector
  (-map-nodes [this nodes]
    (filter #(instance? TextNode %) (-map-nodes (Children.) nodes))))

(defn- map-prop [n nodes]
  (->> nodes
       (filter (fn [node]
                 (clojure.core/and (some? (node-props node)) ;; not sure why this is nil sometimes; dom and component nodes should have props?
                                   (.hasOwnProperty (node-props node) n))))
       (map (fn [node]
              (NodeProperty. node (keyword n) (aget (node-props node) n))))))

(defrecord ^:private Attr [n]
  XPathSelector
  (-map-nodes [this nodes]
    (->> nodes
         ;; only DOM nodes.
         (filter (fn [n] (string? (node-type n))))
         (map-prop n))))

(defn- map-nodes-to-property [nodes property]
  (->> nodes
       (filter (fn [n] (clojure.core/not (string? (node-type n))))) ;; only components.
       (map-prop property)))

(defrecord ^:private AppState []
  XPathSelector
  (-map-nodes [this nodes]
    ;; FIXME: remove class instances without app-state
    (map-nodes-to-property nodes "reacl_app_state")))

(defrecord ^:private LocalState []
  XPathSelector
  (-map-nodes [this nodes]
    (->> nodes
         (map node-instance)
         (filter some?)
         (map #(NodeProperty. % "local-state" (reacl/extract-local-state %))))))

(defrecord ^:private Args []
  XPathSelector
  (-map-nodes [this nodes]
    (map-nodes-to-property nodes "reacl_args")))

(defrecord ^:private Prop [k]
  XPathSelector
  (-map-nodes [this nodes]
    (-> nodes
        (filter #(clojure.core/or (string? (node-type %)) (class-type? (node-type %))))
        (map-prop k))))

(defrecord ^:private Root []
  XPathSelector
  (-map-nodes [this nodes]
    (assert (not-empty nodes) "Cannot go to the root of an empty node set.") ;; or we could remember the node passed to select?
    #{(RootNode. (alpha/resolve-toplevel (node-value (node-parent (clojure.core/first nodes)))))}))

(defrecord ^:private Where [sel]
  XPathSelector
  (-map-nodes [this nodes]
    (remove (fn [n] (empty? (-map-nodes sel [n])))
            nodes)))

(defrecord ^:private Is [pred args]
  XPathSelector
  (-map-nodes [this nodes]
    (filter #(apply pred (node-value %) args)
            nodes)))

(defrecord ^:private Parent []
  XPathSelector
  (-map-nodes [this nodes]
    ;; Note: multiple nodes may have the same parent; removing duplicates:
    (distinct (filter some? (map node-parent nodes)))))

(defrecord ^:private Or [sels]
  XPathSelector
  (-map-nodes [this nodes]
    (distinct (mapcat #(-map-nodes % nodes)
                      sels))))

(defn ^:no-doc concat-commons [l1 l2]
  (let [s1 (set l1)
        s2 (set l2)]
    (distinct (concat (filter s2 l1)
                      (filter s1 l2)))))

(defrecord ^:private And [sel1 sel2]
  XPathSelector
  (-map-nodes [this nodes]
    (concat-commons (-map-nodes sel1 nodes)
                    (-map-nodes sel2 nodes))))

(defrecord ^:private AnyTagType []
  XPathSelector
  (-map-nodes [this nodes]
    (filter #(string? (node-type %))
            nodes)))

(defrecord ^:private AnyClassType []
  XPathSelector
  (-map-nodes [this nodes]
    (filter #(class-type? (node-type %))
            nodes)))

(defrecord ^:private FirstWhere [sel]
  XPathSelector
  (-map-nodes [this nodes]
    (loop [nodes nodes
           result nil]
      (assert (every? node? nodes) (remove node? nodes))
      (if (empty? nodes)
        result
        (let [{ok false more true} (group-by #(empty? (-map-nodes sel (list %)))
                                             nodes)]
          (assert (every? node? ok) (remove node? ok))
          (assert (every? node? more) (remove node? more))
          (recur (-map-nodes (Children.) more)
                 (concat result ok)))))))

(defrecord ^:private CountIs [n]
  XPathSelector
  (-map-nodes [this nodes]
    (if (= (clojure.core/count nodes) n)
      nodes
      nil)))

(defrecord ^:private Not [sel]
  XPathSelector
  (-map-nodes [this nodes]
    (filter #(empty? (-map-nodes sel (list %)))
            nodes)))

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

(def ^{:doc "Selects the parent of the current node. When using the
  composition macro [[>>]], the literal `..` is translated into this."}
  parent (Parent.))

(def ^{:doc "A selector that drops everything, making a selection empty."} void (Void.))

(defn or
  "Selects the nodes that any of the given selectors selects."
  [& selectors]
  (if (empty? selectors)
    void ;; (or) === false
    (Or. selectors)))

(defn and
  "Selects the nodes that all of the given selectors selects."
  [& selectors]
  (if (empty? selectors)
    self ;; (and) == true
    (reduce #(And. %1 %2)
            selectors)))

(defn type "Selects those nodes that either of the given dom tag type, or the given Reacl class." [t]
  ;; (assert (or (string? t) (reacl/reacl-class? t) (react-class? t)))
  (Type. t))

(def ^{:doc "Selects only those nodes that are a virtual dom element of the given string `type`."
       :arglists '([type])}
  tag type)
(def ^{:doc "Selects only those nodes that are an instance of the given Reacl `c`."
       :arglists '([c])}
  class type)

(def ^{:doc "Selects only those nodes that are a virtual dom element of any tag type."}
  tag? (AnyTagType.))

(def ^{:doc "Selects only those nodes that are a virtual component of any class type."}
  class? (AnyClassType.))

(defn not
  "Selects those nodes that are not selected by the given selectors."
  [sel]
  (Not. sel))

(defn first-where
  "Selects those nodes that are selected by the given selector, and for those that are not, the children are tried recursively."
  [sel]
  (FirstWhere. sel))

(defn count=
  "Keeps the current selection, if it contains the given number of nodes."
  [n]
  (CountIs. n))

(defn- resolve-attr-name [n]
  (clojure.core/or (clojure.core/and (string? n) n)
                   (aget dom/reacl->react-attribute-names (name n))
                   (name n)))

(defn attr
  "Selects the value of an attribute `name` from virtual dom nodes. As a convience, a simple keyword `k` is translated into `(attr k)`."
  [name]
  (Attr. (resolve-attr-name name)))

(defn- lift-selector [sel]
  ;; for convenience, lift some values are that "obviously" meant to be certain selectors:
  (cond
    (string? sel) (tag sel)
    (keyword? sel) (attr sel)
    (reacl/reacl-class? sel) (class sel)
    :else sel))

(defn- flatten-xpath
  "(apply comp (flatten x)) == x  (or at least equivalent)"
  [sel]
  (if (instance? SimpleCompose sel)
    (concat (flatten-xpath (:sel1 sel))
            (flatten-xpath (:sel2 sel)))
    [sel]))

(defn- scomp [sel1 sel2]
  (SimpleCompose. sel1 sel2))

(defn comp "Compose the given xpath selector forms to a combined
  selector, where from left to right, the selectors restrict the filter
  further. \n
  Valid selectors are all the primitives from this module,
  as well as:\n
  - strings stand for a virtual dom node as with [[tag]],
  - keywords stand for attribute names as with [[attr]],
  - Reacl classes stand for a selection by that class as with [[class]]\n
  Also see [[>>]] for a convenience macro version of this.
"
  [& selectors]
  (loop [res self
         selectors (map lift-selector (mapcat flatten-xpath selectors))]
    (if (empty? selectors)
      res
      (let [s1 (clojure.core/first selectors)]
        (cond
          (instance? Id s1) (recur res (rest selectors))
          (instance? Void s1) s1
          (instance? Root s1) (recur s1 (rest selectors))

          (instance? Id res) (recur s1 (rest selectors))
          ;; Note: could also make optimizations over 2 or more.
          :else (recur (scomp res s1) (rest selectors)))))))

(def ^{:doc "Selects the current node and all of its children and
  grand children. When using the composition macro [[>>]], the literal
  `**` is translated into this."}
  all (All.))

(def ^{:doc "Selects the children of the current node. When using the
  composition macro [[>>]], the literal `/` is translated into this."}
  children (Children.))

(defn range
  "Selects nodes based on their position in the children list of their
  parent, starting at index `from` (inclusive) up to index
  `to` (exclusive). Both `from` and `to` can be negative meaning a
  position from the end of the children list. A 0 in `from` means the
  start of the list, but a 0 in `to` stands for the end of list,
  resp. one behind. So `(range 0 0)` selects the full list of
  children, `(range 1 -1)` selects all but the first and the
  last and `(range -1 0)` only the last child."
  [from to]
  (Position. from to))

(defn nth "Select nodes that are the nth child, starting at index 0." [n] (range n (inc n)))
(defn nth-last "Select nodes that are the \"nth but last\" child." [n] (range (- n) (inc (- n))))

(def first "Select nodes that are the first child of their parent." (nth 0))
(def last "Select nodes that are the last child of their parent." (range -1 0))

(def root "Select the root of the node tree. When using the composition macro [[>>]], the literal `...` is translated into this." (Root.))

(def ^{:doc "Selects the child nodes of type 'text'."} text
  (Text.))

(def ^{:doc "Selects the app-state if the current node is a Reacl component that has one."} app-state
  (AppState.))

(def ^{:doc "Selects the local-state if the current node is a Reacl component."} local-state
  (LocalState.))

(def ^{:doc "Selects the argument vector if the current node is a Reacl component."} args
  (Args.))

(defn where
  "Selects the nodes for which the given selector would result in a
  non-empty list of nodes. When using the composition macro [[>>]], a
  vector literal is translated into this."
  [sel]
  (assert (satisfies? XPathSelector sel))
  (Where. sel))

(def ^{:doc "Selects the key property of the nodes, if they have one."} key (Prop. "key"))

(defn is? "Keeps the current node only if `(pred node & args)` holds." [pred & args]
  (Is. pred args))

(defn is= "Keeps the current node only if it is equal to `v`." [v]
  (is? = v))

(defn text= "Selects the nodes where the text content is equal to the given string."
  [s]
  (where (comp text (is= s))))

(defn id= "Keeps the current node only if it has an attribute `id` equal to `v`." [v]
  (where (comp (attr :id) (is= v))))

(defn- re-matches-rev [s re]
  (re-matches re s))

(defn re-matches? "Keeps the current node only if it is matches the given `regex`." [regex]
  (is? re-matches-rev regex))

(defn- parse-css-classes [s]
  (map str/trim (str/split s " ")))

(defn- normalize-css [s]
  (if (sequential? s)
    (mapcat parse-css-classes s)
    (parse-css-classes s)))

(defn ^:no-doc css-class-match [comp-class cs]
  (loop [classes (parse-css-classes comp-class)
         cs cs]
    (if (empty? cs)
      true
      (if (empty? classes)
        false
        (if (= (clojure.core/first cs)
               (clojure.core/first classes))
          (recur (rest classes) (rest cs))
          (recur (rest classes) cs))))))

(defn- is-css-class-match? [s]
  (is? css-class-match (normalize-css s)))

(defn css-class? "Keeps the current node only if it has a `:class` attribute
  matching with the given `s`. If `s` is a string or sequence, then
  the node must have all those classes in the same order. If `s` is a
  set of strings, then it must have all those classes in any order."
  [s]
  (where (comp (attr :class)
               (if (set? s)
                 (do (assert (every? string? s))
                     (if (= 1 (count s))
                       (is-css-class-match? (clojure.core/first s))
                       (reduce and (map is-css-class-match? s))))
                 (do (assert (or (string? s)
                                 (and (sequential? s)
                                      (every? string? s))))
                     (is-css-class-match? s))))))

(defn- style-match [m sub]
  ;; Note: m will be a translated style; as a js object; sub a untranslated clojure map.
  (every? (fn [[k v]]
            (let [p (dom/reacl->react-style-name k)]
              (and (.hasOwnProperty m p)
                   (= v (aget m p)))))
          sub))

(defn style? "Keeps the current node only if it has a `:style`
  attribute, which matches with all styles given in the map `style`. Note
  that it's ok if it has more styles."
  [style]
  (where (comp (attr :style)
               (is? style-match style))))
