(ns reacl.test.lens
  (:require [reacl.lens :as lens]
            [cemerick.cljs.test :as t])
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing test-var)]))

(enable-console-print!)

(defn law-1-holds [l data v]
  ;; you get back what you put in
  (is (= v
         (lens/yank (lens/shove data l v) l))))

(defn law-2-holds [l data]
  ;; putting back what you got doesn't change anything
  (is (= data
         (lens/shove data l (lens/yank data l)))))

(defn law-3-holds [l data v1 v2]
  ;; second set wins, or setting once is the same as setting twice
  (is (= (lens/shove data l v1)
         (lens/shove (lens/shove data l v2) l v1))))

(defn lens-laws-hold [l data v1 v2]
  (and (law-1-holds l data v1)
       (law-2-holds l data)
       (law-3-holds l data v1 v2)))

(deftest void
  (lens-laws-hold lens/void {} nil nil)
  (is (= nil
         (lens/yank {} lens/void)))
  (is (= {}
         (lens/shove {} lens/void 42))))

(deftest as-map
  (lens-laws-hold lens/as-map [[1 2]] {:a 42} {:b 12})
  (is (= {:a 42}
         (lens/yank [[:a 42]] lens/as-map)))
  (is (= [[3 15]]
         (lens/shove [] lens/as-map {3 15}))))

(deftest as-set
  (lens-laws-hold lens/as-set [1 2 3] #{:a :b} #{13 12})
  (is (= #{13 42}
         (lens/yank [13 42] lens/as-set)))
  (is (= [15]
         (lens/shove [7] lens/as-set #{15}))))

(deftest head
  (lens-laws-hold lens/head [1 2 3] 7 42)
  (lens-laws-hold lens/head [] nil 42)
  (is (= 13
         (lens/yank [13 42] lens/head)))
  (is (= nil
         (lens/yank [] lens/head)))
  (is (= [15]
         (lens/shove [7] lens/head 15)))
  (is (= [42]
         (lens/shove [] lens/head 42)))
  (is (= []
         (lens/shove [] lens/head nil))))

(deftest nel-head
  (lens-laws-hold lens/nel-head [1 2 3] 7 42)
  (is (= 13
         (lens/yank [13 42] lens/nel-head)))
  (is (= [15]
         (lens/shove [7] lens/nel-head 15)))
  (is (= [42]
         (lens/shove [] lens/nel-head 42)))
  (is (= [nil]
         (lens/shove [] lens/nel-head nil))))

(deftest tail
  (lens-laws-hold lens/tail [1 2 3] [7] [45])
  (lens-laws-hold lens/tail [] [] [42])
  (is (= [42]
         (lens/yank [13 42] lens/tail)))
  (is (= []
         (lens/yank [] lens/tail)))
  (is (= [7 15]
         (lens/shove [7] lens/tail [15])))
  (is (= [nil 15]
         (lens/shove [] lens/tail [15])))
  (is (= [15]
         (lens/shove [15] lens/tail nil)))
  (is (= [42]
         (lens/shove [42] lens/tail []))))

(deftest nel-tail
  (lens-laws-hold lens/nel-tail [1 2 3] [7] [45])
  (is (= [42]
         (lens/yank [13 42] lens/nel-tail)))
  (is (= [7 15]
         (lens/shove [7] lens/nel-tail [15])))
  (is (= [7 nil]
         (lens/shove [7 0] lens/nel-tail [nil])))
  (is (= [42]
         (lens/shove [42] lens/nel-tail []))))

(deftest member
  (let [l (lens/member 42)]
    (lens-laws-hold l {12 "a" 42 "b"} "c" "d")
    (is (= "b"
           (lens/yank {12 "a" 42 "b"} l)))
    (is (= nil
           (lens/yank {} l)))
    (is (= 0
           (lens/yank {} (lens/member 42 0))))
    (is (= {12 "a" 42 "b"}
           (lens/shove {12 "a"} l "b")))
    (is (= {}
           (lens/shove {42 "b"} l nil)))))

(deftest contains
  (let [l (lens/contains 42)]
    (lens-laws-hold l #{12 42} true false)
    (is (lens/yank #{12 42} l))
    (is (not (lens/yank #{} l)))
    (is (= #{13 42}
           (lens/shove #{13} l true)))
    (is (= #{}
           (lens/shove #{42} l false)))))

(deftest pos
  (let [l (lens/pos 1)]
    (lens-laws-hold l [12 42] 7 65)
    (is (= 42
           (lens/yank [12 42] l)))
    (is (= nil
           (lens/yank [] l)))
    (is (= nil
           (lens/yank nil l)))
    (is (= nil
           (lens/yank [10] l)))
    (is (= [[nil 3]]
           (lens/shove nil (lens/>> (lens/pos 0) (lens/pos 1)) 3)))
    (is (= [nil [nil 3]]
           (lens/shove nil (lens/>> (lens/pos 1) (lens/pos 1)) 3)))
    (is (= [13 42]
           (lens/shove [13 0] l 42)))
    (is (= [13 42]
           (lens/shove [13] l 42)))
    (is (= [nil 42]
           (lens/shove [] l 42)))
    (is (= [nil 42] ;; ??
           (lens/shove nil l 42)))))

(deftest default
  (let [l (lens/default 42)]
    (lens-laws-hold l nil 3 7)
    (is (= 42
           (lens/yank nil l)))
    (is (= 13
           (lens/yank 13 l)))
    (is (= nil
           (lens/shove 13 l 42)))
    (is (= 13
           (lens/shove 42 l 13)))))

(deftest xmap
  (let [l (lens/xmap str js/parseInt)]
    (lens-laws-hold l 42 "13" "1")
    (is (= "42"
           (lens/yank 42 l)))
    (is (= 13
           (lens/shove 42 l "13")))))

(deftest is-t
  (let [l (lens/is 42)]
    (lens-laws-hold l 13 true false)
    (is (lens/yank 42 l))
    (is (not (lens/yank 13 l)))
    (is (= 42
           (lens/shove 13 l true)))
    (is (= 13
           (lens/shove 13 l false)))
    (is (= nil
           (lens/shove 42 l false)))))

(deftest ++
  (let [l (lens/++ :a :b)]
    (lens-laws-hold l {:a 1 :b 2 :c 3} [6 7] [9 10])
    (is (= [1 2]
           (lens/yank {:a 1 :b 2 :c 3} l)))
    (is (= [nil 42]
           (lens/yank {:b 42} l)))
    (is (= {:a 42 :b 21 :c 3}
           (lens/shove {:a 13 :c 3} l [42 21])))
    (is (= [1 nil]
           (lens/yank {:a 1 :b 2 :c 3} (lens/++ :a lens/void))))
    ))

(deftest >>
  (let [l (lens/>> :a :b)]
    (lens-laws-hold l {:a {:b 42} :c 4} 27 5)
    (is (= 42
           (lens/yank {:a {:b 42} :c 4} l)))
    (is (= nil
           (lens/yank {} l)))
    (is (= {:a {:b 8} :c 3}
           (lens/shove {:a {:b 42} :c 3} l 8)))))

(deftest **
  (let [l (lens/** :a lens/id)]
    (lens-laws-hold l [{:a 5} 27] [1 2] [3 4])
    (is (= [5 27]
           (lens/yank [{:a 5} 27] l)))
    (is (= [{:a 42} 13]
           (lens/shove [{:a 5} 27] l [42 13])))))



(deftest explicit
  (let [car (lens/lens first (fn [l v] (cons v (rest l))))]
    (is (= 'foo
           (lens/yank '(foo bar baz) car)))
    (is (= '(bla bar baz)
           (lens/shove '(foo bar baz) car 'bla)))))

(deftest kw
  (is (= 'foo
         (lens/yank {:foo 'foo :bar 'bar} :foo)))
  (is (= {:foo 'baz :bar 'bar}
         (lens/shove {:foo 'foo :bar 'bar} :foo 'baz))))

(deftest path
  (let [l (lens/in :foo :baz)]
    (is (= 'baz
           (lens/yank {:foo {:bla 'bla :baz 'baz} :bar 'bar} l)))
    (is (= {:foo {:bla 'bla :baz 'bam} :bar 'bar}
           (lens/shove {:foo {:bla 'bla :baz 'baz} :bar 'bar} l 'bam)))))

(deftest at-index
  (let [l (lens/at-index 2)]
    (is (= 'baz
           (lens/yank '[foo bar baz bla] l)))
    (is (= '[foo bar bam bla]
           (lens/shove '[foo bar baz bla] l 'bam)))))

(deftest id
  (is (= 'baz
         (lens/yank 'baz lens/id))
      (= 'bar
         (lens/shove 'baz lens/id 'bar))))

(deftest at-key
  (is (= {:k 'bar :v 'baz}
         (lens/yank [{:k 'foo :v 'bar} {:k 'bar :v 'baz} {:k 'bla :v 'bam}]
                    (lens/at-key :k 'bar))))
  (is (= [{:k 'foo :v 'bar} {:k 'bar :v 'blam} {:k 'bla :v 'bam}]
         (lens/shove [{:k 'foo :v 'bar} {:k 'bar :v 'baz} {:k 'bla :v 'bam}]
                     (lens/at-key :k 'bar)
                     {:k 'bar :v 'blam}))))

(deftest map-keyed
  (let [d [{:k 'foo :v 'bar} {:k 'bar :v 'baz} {:k 'bla :v 'bam}]]
    (is (= [[{:k 'foo :v 'bar} 'foo 'foo]
            [{:k 'bar :v 'baz} 'bar 'bar]
            [{:k 'bla :v 'bam} 'bla 'bla]]
           (lens/map-keyed :k
                           (fn [el key lens]
                             [el key (:k (lens/yank d lens))])
                           d)))))
