(defproject reacl "0.1.0-SNAPSHOT"
  :description "ClojureScript wrappers for programming with React"
  :url "http://github.com/active-group/reacl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ^:replace ["-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2173" :scope "provided"]
                 [com.facebook/react "0.9.0.1"]]

  :plugins [[lein-cljsbuild "1.0.2"]
            [com.cemerick/clojurescript.test "0.3.0"]]
  
  :cljsbuild
  
  { :builds [{:id "test"
              :source-paths ["src" "test"]
              :compiler {:preamble ["react/react.min.js"]
                         :output-to "target/tests.js"
                         :optimizations :whitespace
                         :pretty-print true}}
              ;; examples
              {:id "products"
               :source-paths ["src" "examples/products"]
               :compiler {
                          :output-to "examples/products/main.js"
                          :output-dir "examples/products/out"
                          :source-map true
                          :optimizations :none}}
              {:id "todo"
               :source-paths ["src" "examples/todo"]
               :compiler {
                          :output-to "examples/todo/main.js"
                          :output-dir "examples/todo/out"
                          :source-map true
                          :optimizations :none}}]
   :test-commands {"unit-tests" ["slimerjs" :runner "target/tests.js"]}})

