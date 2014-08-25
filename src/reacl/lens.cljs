(ns reacl.lens)

(defprotocol Lens
  "Protocol for types that can be used as a lens, defined by a
   function to yank some value out of a given data value, and a function
   to shove an updated value back in."
  (-yank [lens data])
  (-shove [lens data v]))

;; TODO document lens laws

(defn yank
  "Yank a value from the given data value, as defined by the given
   lens."
  [data lens]
  (-yank lens data))

(defn shove
  "Shove a new value v into the given data value, as defined by the
   given lens, and return the updated data structure."
  [data lens v]
  (-shove lens data v))

;; Keywords are lenses over a map (or object), focusing on the value associated with that keyword.
(extend-type cljs.core.Keyword
  Lens
  (-yank [kw data] (kw data))
  (-shove [kw data v] (assoc data kw v)))

(defrecord ExplicitLens
    ^{:private true}
  [yanker shover]
  Lens
  (-yank [lens data] (yanker data))
  (-shove [lens data v] (shover data v)))

(defn lens
  "Returns a new lens defined by the given yanker function, which takes a data
   structure and must return the focused value, and the given shover function
   which takes a data structure and the new value in the focus."
  [yank shove]
  (ExplicitLens. yank shove))

(defn xmap
  "Returns a \"view lens\", that transforms a whole data strucutre
   to something else (f) and back (g)."
  [f g]
  (lens f #(g %2)))

(defrecord IdentityLens []
  Lens
  (-yank [_ data] data)
  (-shove [_ data v] v))

(def
  ^{:doc "Identity lens, that just show a data structure as it is.
          It's also the neutral element of lens concatenation
          reacl.lens/>>."}
  id (xmap identity identity))

(defn- >>2
  [l1 l2]
  (lens (fn [data] (yank (yank data l1) l2))
        (fn [data v] (shove data l1 (shove (yank data l1) l2 v)))))

(defn >>
  "Returns a concatenation of two or more lenses, so that the combination shows the
   value of the last one, in a data structure that the first one is put
   over."
  [l1 & lmore]
  (loop [res l1
         lmore lmore]
    (if (empty? lmore)
      res
      (recur (>>2 res (first lmore)) (rest lmore)))))

(defn default
  "Returns a lens that shows nil as the given value, but does not change any other value."
  [v]
  (xmap #(if (nil? %) v %)
        #(if (= v %) nil %)))

(defn- consx [v coll]
  (if (and (nil? v) (empty? coll))
    coll
    (cons v coll)))

(def
  ^{:doc "A lens focusing on the first element in a collection. It
  yanks nil if the collection is empty, and will not insert nil into an empty collection."}
  head
  (lens #(first %)
        #(consx %2 (rest %1))))

(def
  ^{:doc
  "A lens focusing on the first element in a non-empty
  collection. Behaviour on an empty collection is undefined."}
  nel-head
  (lens #(first %)
        #(cons %2 (rest %1))))

(def
  ^{:doc "A lens focusing on the all but the first element in a collection.
  Note that nil will be prepended when shoving into an empty collection."}
  tail
  (lens #(rest %)
        #(consx (first %1) %2)))

(def
  ^{:doc "A lens focusing on the all but the first element in a non-empty collection.
  Behaviour on an empty collection is undefined."}
  nel-tail
  (lens #(rest %)
        #(cons (first %1) %2)))

(defn pos
  "A lens over the nth element in a collection. Note that when shoving a
  new value nils may be added before the given position, if the the collection is smaller."
  [n]
  (assert (number? n))
  (assert (>= n 0))
  ;; there are probably more efficient implementations:
  (if (= n 0)
    head
    (>> tail (pos (- n 1)))))

(def ^{:doc "A lens that views a sequence as a set."}
  as-set
  (xmap set seq))

(defn contains
  "Returns a lens showing the membership of the given value in a set."
  [v]
  (lens #(contains? % v)
        #(if %2
           (conj %1 v)
           (disj %1 v))))

(def ^{:doc "A lens that views a sequence of pairs as a map."}
  as-map
  (xmap #(into {} %) seq))

(defn member
  "Returns a lens showing the value mapped to the given key in a map,
  not-found or nil if key is not present. Note that when not-found (or
  nil) is shoved into the map, the association is removed."
  [key & [not-found]]
  (lens #(get % key not-found)
        #(if (= %2 not-found)
           (dissoc %1 key)
           (assoc %1 key %2))))

(def ^{:doc "A trivial lens that just shows nil over anything, and does never change anything."}
  void
  (lens (constantly nil) (fn [data _] data)))

(defn is
  "Returns a lens showing if a data structure equals the non-nil value v."
  [v]
  (assert (not (nil? v)))
  (lens #(= % v)
        #(if %2
           v
           (if (= %1 v)
             nil
             %1))))

(defn **
  "Return the product of several lenses, which means that each lens is
  held over an element of a collection in the order they appear in the
  argument list."
  [& lenses]
  (lens (fn [data] (map #(yank %1 %2)
                       data lenses))
        (fn [data v] (map #(shove %1 %2 %3)
                         data lenses v))))

(comment not very general: defn repeated
  [n]
  (lens #(take n (repeat %))
        (fn [data v]
          (or (some #(not (= % data))
                    v)
              data))))

(defn ++
  "Returns a lens over some data structure that shows a sequence of
  elements that each of the given lenses show on that. Note that the
  behaviour is undefined if those lenses do not show distrinct parts
  of the data structure."
  [& lenses]
  (lens (fn [data] (map #(yank %1 %2)
                       (repeat data)
                       lenses))
        (fn [data v] (reduce (fn [data [l v]] (shove data l v))
                            data
                            (map vector lenses v)))))



(defn at-index
  "Returns a lens that focuses on the value at position n in a sequence.
  The sequence must have >= n elements."
  [n]
  (lens (fn [coll] (nth coll n))
        (fn [coll v]
          (let [[front back] (split-at n coll)]
            (concat front
                    [v]
                    (rest back))))))

(defn at-key
  [extract-key key]
  (lens (fn [coll]
          (some (fn [el]
                  (and (= key (extract-key el))
                       el))
                coll))
        (fn [coll v]
          (map (fn [el]
                 (if (= key (extract-key el))
                   v
                   el))
               coll))))

(defn map-keyed
  [extract-key f coll]
  (map (fn [el]
         (let [key (extract-key el)]
           (f el key (at-key extract-key key))))
       coll))

(defrecord Path
    [path]
  Lens
  (-yank [this data]
    (reduce yank data path))
  (-shove [this data v]
    (letfn [(u [data path]
              (if-let [[p & ps] path]
                (shove data p (u (yank data p) ps))
                v))]
      (u data (seq path)))))

(defn in
  [& accessors]
  (Path. accessors))
