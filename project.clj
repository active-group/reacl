(defproject reacl "0.7.0-SNAPSHOT"
  :description "ClojureScript wrappers for programming with React"
  :url "http://github.com/active-group/reacl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ^:replace ["-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2341" :scope "provided"]
                 [com.facebook/react "0.11.1"]
                 [active-clojure "0.3.0-SNAPSHOT" :exclusions [org.clojure/clojure]]]

  :plugins [[lein-cljsbuild "1.0.3"]
            ;; NB: This needs a version of clojurescript.test with the Nashorn runner,
            ;; for example from the nashorn-runner branch from
            ;; https://github.com/active-group/clojurescript.test
            [com.cemerick/clojurescript.test "0.3.2-SNAPSHOT"]
            [codox "0.8.10"]
            [org.bodil/lein-nashorn "0.1.2"]]
  
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
                          :output-to "examples/products/main.js"
                          :output-dir "examples/products/out"
                          :source-map "examples/products/main.map"
                          :externs ["react/externs/react.js"]
                          :optimizations :whitespace}}
              {:id "todo"
               :source-paths ["src" "examples/todo"]
               :compiler {:preamble ["react/react_with_addons.min.js"]
                          :output-to "examples/todo/main.js"
                          :output-dir "examples/todo/out"
                          :source-map "examples/todo/main.map"
                          :externs ["react/externs/react.js"]
                          :optimizations :whitespace}}
              {:id "comments"
               :source-paths ["src" "examples/comments"]
               :compiler {:preamble ["react/react_with_addons.min.js"]
                          :output-to "examples/comments/main.js"
                          :output-dir "examples/comments/out"
                          :source-map "examples/comments/main.map"
                          :externs ["react/externs/react.js"]
                          :optimizations :whitespace}}
              {:id "delayed"
               :source-paths ["src" "examples/delayed"]
               :compiler {:preamble ["react/react_with_addons.min.js"]
                          :output-to "examples/delayed/main.js"
                          :output-dir "examples/delayed/out"
                          :source-map "examples/delayed/main.map"
                          :externs ["react/externs/react.js"]
                          :optimizations :whitespace}}]
   ;; React needs global binding to function, see
   ;; http://augustl.com/blog/2014/jdk8_react_rendering_on_server/
   :test-commands {"phantom" ["phantomjs" :runner 
                              "window.literal_js_executed=true"
                              "test/vendor/es5-shim.js"
                              "test/vendor/es5-sham.js"
                              "test/vendor/console-polyfill.js"
                              "target/test-dom.js"]}}

   :codox {:language :clojurescript
           :defaults {:doc/format :markdown}
           :src-dir-uri "http://github.com/active-group/reacl/blob/master/"
           :src-linenum-anchor-prefix "L"})


