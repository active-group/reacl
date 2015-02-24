(defproject reacl "1.0-SNAPSHOT"
  :description "ClojureScript wrappers for programming with React"
  :url "http://github.com/active-group/reacl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ^:replace ["-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2913" :scope "provided"]
                 [com.facebook/react "0.11.1"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [codox "0.8.10"]]

  :profiles {:dev {:dependencies [[active-clojure "0.3.0" :exclusions [org.clojure/clojure]]]}}
  
  :cljsbuild
  
  { :builds [{:id "test-dom"
              :source-paths ["src" "test-dom"]
              :compiler {:preamble ["react/react_with_addons.js"] ; TestUtils aren't in minified version
                         :output-to "target/test-dom.js"
                         :optimizations :whitespace
                         :externs ["react/externs/react.js"]
                         :pretty-print true}}
              ;; examples
              {:id "products"
               :source-paths ["src" "examples/products"]
               :compiler {:preamble ["react/react_with_addons.min.js"]
                          :output-to "target/products/main.js"
                          :output-dir "target/products/out"
                          :source-map "target/products/main.map"
                          :externs ["react/externs/react.js"]
                          :optimizations :whitespace}}
              {:id "todo"
               :source-paths ["src" "examples/todo"]
               :compiler {:preamble ["react/react_with_addons.min.js"]
                          :output-to "target/todo/main.js"
                          :output-dir "target/todo/out"
                          :source-map "target/todo/main.map"
                          :externs ["react/externs/react.js"]
                          :optimizations :whitespace}}
              {:id "comments"
               :source-paths ["src" "examples/comments"]
               :compiler {:preamble ["react/react_with_addons.min.js"]
                          :output-to "target/comments/main.js"
                          :output-dir "target/comments/out"
                          :source-map "target/comments/main.map"
                          :externs ["react/externs/react.js"]
                          :optimizations :whitespace}}
              {:id "delayed"
               :source-paths ["src" "examples/delayed"]
               :compiler {:preamble ["react/react_with_addons.min.js"]
                          :output-to "target/delayed/main.js"
                          :output-dir "target/delayed/out"
                          :source-map "target/delayed/main.map"
                          :externs ["react/externs/react.js"]
                          :optimizations :whitespace}}]
   :test-commands {"phantom" ["phantomjs" 
                              "test/vendor/unit-test.js" "test/vendor/unit-test.html"]}}

   :codox {:language :clojurescript
           :defaults {:doc/format :markdown}
           :src-dir-uri "http://github.com/active-group/reacl/blob/master/"
           :src-linenum-anchor-prefix "L"})


