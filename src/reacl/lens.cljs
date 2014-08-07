(ns reacl.lens)

(enable-console-print!)

(defprotocol Lens
  (-yank [lens data])
  (-shove [lens data v]))

(defn yank
  [data lens]
  (-yank lens data))

(defn shove
  [data lens v]
  (-shove lens data v))

(extend-type cljs.core.Keyword
  Lens
  (-yank [kw data] (kw data))
  (-shove [kw data v] (assoc data kw v)))

(defrecord ExplicitLens 
    [yanker shover]
  Lens
  (-yank [lens data] (yanker data))
  (-shove [lens data v] (shover data v)))

(defn lens
  [yank shove]
  (ExplicitLens. yank shove))

(defrecord IdentityLens []
  Lens
  (-yank [_ data] data)
  (-shove [_ data v] v))

(def id (IdentityLens.))

(defn at-index
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
