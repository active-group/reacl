(defproject reacl "1.6.0-SNAPSHOT"
  :description "ClojureScript wrappers for programming with React"
  :url "http://github.com/active-group/reacl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ^:replace ["-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.9.0-alpha10" :scope "provided"]
                 ;; 1.9.198 onwards gives us "No such var: string/index-of"
                 [org.clojure/clojurescript "1.9.89" :scope "provided"]
                 [cljsjs/react-with-addons "0.13.3-0"]] ; addons needed for tests only

  :plugins [[lein-cljsbuild "1.1.5"]
            [lein-doo "0.1.7"]
            [lein-codox "0.9.3"]]

  :profiles {:dev {:dependencies [[active-clojure "0.11.0" :exclusions [org.clojure/clojure]]
                                  [lein-doo "0.1.7"]]}}

  :clean-targets [:target-path "out"]
  
  :cljsbuild
  
  { :builds [;; these need phantom or something like it
             {:id "test-dom"
              :source-paths ["src" "test-dom"]
              :compiler {:output-to "target/test-dom.js"
                         :main reacl.test.runner
                         :optimizations :none}}

             ;; these can run under Nashorn
             {:id "test-nodom"
              :source-paths ["src" "test-nodom"]
              :compiler {:output-to "target/test-nodom.js"
                         :main reacl.test.runner
                         :optimizations :whitespace}}
             
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

  :aliases {"test-dom" ["doo" "phantom" "test-dom"]
            "test-nodom" ["doo" "nashorn" "test-nodom"]}

  :codox {:language :clojurescript
          :metadata {:doc/format :markdown}
          :src-dir-uri "http://github.com/active-group/reacl/blob/master/"
          :src-linenum-anchor-prefix "L"})


