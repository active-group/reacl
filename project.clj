(defproject reacl "1.5.0"
  :description "ClojureScript wrappers for programming with React"
  :url "http://github.com/active-group/reacl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ^:replace ["-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.228" :scope "provided"]
                 [cljsjs/react-with-addons "0.13.3-0"]] ; addons needed for tests only

  :plugins [[lein-cljsbuild "1.1.2"]
            [lein-doo "0.1.6"]
            [lein-codox "0.9.3"]]

  :profiles {:dev {:dependencies [[active-clojure "0.11.0" :exclusions [org.clojure/clojure]]
                                  [lein-doo "0.1.6"]]}
             ;; see https://github.com/weavejester/codox/issues/90
             :doc {:dependencies [[org.clojure/clojurescript "0.0-2985"]]}}
  
  :cljsbuild
  
  { :builds [{:id "test-dom"
              :source-paths ["src" "test-dom"]
              :compiler {:output-to "target/test-dom.js"
                         :main reacl.test.runner
                         :optimizations :none}}
              ;; examples
              {:id "products"
               :source-paths ["src" "examples/products"]
               :compiler {:output-to "target/products/main.js"
                          :output-dir "target/products/out"
                          :source-map "target/products/main.map"
                          :optimizations :whitespace
                          :parallel-build true}}
              {:id "todo"
               :source-paths ["src" "examples/todo"]
               :compiler {:output-to "target/todo/main.js"
                          :output-dir "target/todo/out"
                          :source-map "target/todo/main.map"
                          :optimizations :whitespace
                          :parallel-build true}}
              {:id "comments"
               :source-paths ["src" "examples/comments"]
               :compiler {:output-to "target/comments/main.js"
                          :output-dir "target/comments/out"
                          :source-map "target/comments/main.map"
                          :optimizations :whitespace
                          :parallel-build true}}
              {:id "delayed"
               :source-paths ["src" "examples/delayed"]
               :compiler {:output-to "target/delayed/main.js"
                          :output-dir "target/delayed/out"
                          :source-map "target/delayed/main.map"
                          :optimizations :whitespace
                          :parallel-build true}}]}

  :aliases {"test-dom" ["doo" "phantom" "test-dom"]}

  :codox {:language :clojurescript
          :defaults {:doc/format :markdown}
          :src-dir-uri "http://github.com/active-group/reacl/blob/master/"
          :src-linenum-anchor-prefix "L"})


