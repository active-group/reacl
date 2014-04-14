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


