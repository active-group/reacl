(ns reacl2.test.test-util.xpath-test
  (:require [reacl2.test-util.xpath :as xpath :include-macros true]
            [reacl2.test-util.beta :as tu]
            [reacl2.core :as reacl :include-macros true]
            [reacl2.dom :as dom :include-macros true])
  (:require-macros [cljs.test :refer (is deftest testing)]))

(deftest basics-test
  (let [clazz (reacl/class "test" this state [arg1]
                           render (dom/div (dom/span {:onchange (fn [v] (reacl/send-message! this v))} "Hello")
                                           (dom/span "World")
                                           (dom/span)
                                           (dom/span {:id "foo" :width 42}))
                           handle-message (fn [msg] (reacl/return :app-state msg)))
        comp (tu/mount clazz :initial :myarg)]
    (let [spans (xpath/select-all comp (xpath/comp xpath/children (xpath/tag "div") xpath/children))]
      (is (= (count spans)
             4))

      (is (= (tu/with-component-return comp
               (fn [_] (reacl2.test-util.alpha/invoke-callback (first spans) :onchange ::event)))
             (reacl/return :app-state ::event)))
        
      (is (= (xpath/select-all (first spans) xpath/children)
             ["Hello"]))
      (is (= (xpath/select-all comp (xpath/comp clazz xpath/children "div" xpath/children "span" xpath/text))
             ["Hello" "World"]))

      (is (= (xpath/select-all comp (xpath/comp xpath/children xpath/all (xpath/attr :width)))
             [42]))

      (is (= (xpath/select comp (xpath/comp clazz xpath/app-state (xpath/is= :initial)))
             :initial))
      (is (= (xpath/select comp xpath/args)
             [:myarg]))

      (is (= (xpath/select comp (xpath/comp xpath/root xpath/children (xpath/class clazz)))
             comp))

      (is (= (count (xpath/select-all comp (xpath/comp xpath/children xpath/all (xpath/where (xpath/attr :width)))))
             1))
      (is (= (count (xpath/select-all comp (xpath/comp xpath/children xpath/all (xpath/where (xpath/comp (xpath/attr :width)
                                                                                                         (xpath/is= 42))))))
             1))
      (is (empty? (xpath/select-all comp (xpath/comp xpath/children xpath/all (xpath/where (xpath/comp (xpath/attr :width) (xpath/is? > 42)))))))

      (is (= (count (xpath/select-all comp (xpath/comp xpath/children xpath/all (xpath/where (xpath/comp xpath/text (xpath/re-matches? #"Hell.*"))))))
             1))

      (is (= (xpath/select comp (xpath/comp xpath/children xpath/parent))
             comp))
      (is (= (count (xpath/select-all comp (xpath/comp xpath/children xpath/all (xpath/tag "span") xpath/parent))) ;; all spans have a single parent.
             1))

      (is (= (count (xpath/select-all comp (xpath/comp xpath/children xpath/all (xpath/where (xpath/or (xpath/attr :width)
                                                                                                       xpath/text)))))
             3))
      (is (= (count (xpath/select-all comp (xpath/comp xpath/children xpath/all (xpath/and (xpath/tag "span")
                                                                                           (xpath/where (xpath/attr :width))))))
             1))
      (is (= (count (xpath/select-all comp (xpath/comp xpath/children xpath/all (xpath/id= "foo"))))
             1))
      (is (= (count (xpath/select-all comp (xpath/comp xpath/children xpath/all "span" (xpath/where xpath/first))))
             1))
      (is (= (xpath/select-all comp (xpath/comp xpath/children xpath/all "span" (xpath/where xpath/first)))
             (xpath/select-all comp (xpath/comp xpath/children xpath/all "span" xpath/first))))
      )))

(deftest range-plus-test
  (is (= (xpath/range-plus 5 0 0)
         [0 1 2 3 4]))
  (is (= (xpath/range-plus 5 0 1)
         [0]))
  (is (= (xpath/range-plus 5 1 3)
         [1 2]))
  (is (= (xpath/range-plus 5 3 1)
         [1 2]))
  (is (= (xpath/range-plus 5 0 -1)
         [0 1 2 3]))
  (is (= (xpath/range-plus 5 -1 0)
         [4]))
  (is (= (xpath/range-plus 5 -2 -1)
         [3]))
  (is (= (xpath/range-plus 5 -1 -2)
         [3]))
  )


(deftest macro-test
  ;; a macro for building xpaths with some more convenience

  (is (= (xpath/comp xpath/children (xpath/tag "div") xpath/children)
         (xpath/>> / "div" /)))

  (is (= xpath/root
         (xpath/>> ...)))
  (is (= (xpath/comp xpath/root xpath/all)
         (xpath/>> ... **)))

  (is (= (xpath/comp xpath/all "span" xpath/text)
         (xpath/>> ** "span" xpath/text)))

  (is (= (xpath/comp xpath/children xpath/all (xpath/attr :width))
         (xpath/>> . / ** :width)))

  (is (= (xpath/comp xpath/all (xpath/where (xpath/attr :id)))
         (xpath/>> ** [:id])))
  )

(deftest css-class-test
  (is (xpath/css-class-match "ab cd ef" ["ab" "ef"]))
  (is (not (xpath/css-class-match "ab cd ef" ["cd" "ab" "ef"])))
  
  (is (= (xpath/css-class? "ab cd")
         (xpath/where (xpath/comp (xpath/attr :class)
                                  (xpath/is? xpath/css-class-match
                                             ["ab" "cd"])))))
  (is (= (xpath/css-class? #{"ab" "cd"})
         (xpath/where (xpath/comp (xpath/attr :class)
                                  (xpath/and (xpath/is? xpath/css-class-match
                                                        ["ab"])
                                             (xpath/is? xpath/css-class-match
                                                        ["cd"]))))))
  
  (is (= (xpath/css-class? #{"ab"})
         (xpath/css-class? "ab"))))
