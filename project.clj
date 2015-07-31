(defproject reacl "1.3.0-SNAPSHOT"
  :description "ClojureScript wrappers for programming with React"
  :url "http://github.com/active-group/reacl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ^:replace ["-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-3308" :scope "provided"]
                 [cljsjs/react-with-addons "0.13.3-0"]] ; addons needed for tests only

  :plugins [[lein-cljsbuild "1.0.6"]
            [codox "0.8.13"]]

  :profiles {:dev {:dependencies [[active-clojure "0.11.0" :exclusions [org.clojure/clojure]]]}}
  
  :cljsbuild
  
  { :builds [{:id "test-dom"
              :source-paths ["src" "test-dom"]
              :compiler {:output-to "target/test-dom.js"
                         :optimizations :whitespace
                         :pretty-print true}}
              ;; examples
              {:id "products"
               :source-paths ["src" "examples/products"]
               :compiler {:output-to "target/products/main.js"
                          :output-dir "target/products/out"
                          :source-map "target/products/main.map"
                          :optimizations :whitespace}}
              {:id "todo"
               :source-paths ["src" "examples/todo"]
               :compiler {:output-to "target/todo/main.js"
                          :output-dir "target/todo/out"
                          :source-map "target/todo/main.map"
                          :optimizations :whitespace}}
              {:id "comments"
               :source-paths ["src" "examples/comments"]
               :compiler {:output-to "target/comments/main.js"
                          :output-dir "target/comments/out"
                          :source-map "target/comments/main.map"
                          :optimizations :whitespace}}
              {:id "delayed"
               :source-paths ["src" "examples/delayed"]
               :compiler {:output-to "target/delayed/main.js"
                          :output-dir "target/delayed/out"
                          :source-map "target/delayed/main.map"
                          :optimizations :whitespace}}]
   :test-commands {"phantom" ["phantomjs" 
                              "test/vendor/unit-test.js" "test/vendor/unit-test.html"]}}

   :codox {:language :clojurescript
           :defaults {:doc/format :markdown}
           :src-dir-uri "http://github.com/active-group/reacl/blob/master/"
           :src-linenum-anchor-prefix "L"})


