(defproject reacl "0.5.0-SNAPSHOT"
  :description "ClojureScript wrappers for programming with React"
  :url "http://github.com/active-group/reacl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ^:replace ["-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2173" :scope "provided"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha" :scope "provided"]
                 [com.facebook/react "0.10.0.1"]]

  :plugins [[lein-cljsbuild "1.0.2"]
            ;; NB: This needs a version of clojurescript.test with the Nashorn runner,
            ;; for example from the nashorn-runner branch from
            ;; https://github.com/active-group/clojurescript.test
            [com.cemerick/clojurescript.test "0.3.2-SNAPSHOT"]
            [org.bodil/lein-nashorn "0.1.2"]]
  
  :cljsbuild
  
  { :builds [{:id "test-nodom"
              :source-paths ["src" "test-nodom"]
              :compiler {:preamble ["react/react_with_addons.js"] ; TestUtils aren't in minified version
                         :output-to "target/test-nodom.js"
                         :optimizations :whitespace
                         :externs ["react/externs/react.js"]
                         :pretty-print true}}
             {:id "test-dom"
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
                          :optimizations :whitespace}}]
   ;; React needs global binding to function, see
   ;; http://augustl.com/blog/2014/jdk8_react_rendering_on_server/
   :test-commands {"nashorn" ["jrunscript" "-e" "var global = this" "-f" :nashorn-runner "target/test-nodom.js"]
                   "phantom" ["phantomjs" :runner 
                              "window.literal_js_executed=true"
                              "test/vendor/es5-shim.js"
                              "test/vendor/es5-sham.js"
                              "test/vendor/console-polyfill.js"
                              "target/test-dom.js"]}})

