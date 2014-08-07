(ns reacl.test.lens
  (:require [reacl.lens :as lens]
            [cemerick.cljs.test :as t])
  (:require-macros [cemerick.cljs.test
                    :refer (is deftest with-test run-tests testing test-var)]))

(enable-console-print!)

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
