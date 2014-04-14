(ns reacl.lens)

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
