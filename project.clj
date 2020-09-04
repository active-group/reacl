(defproject reacl "2.2.8-SNAPSHOT"
  :description "ClojureScript wrappers for programming with React"
  :url "http://github.com/active-group/reacl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :jvm-opts ^:replace ["-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.9.0" :scope "provided"]
                 [org.clojure/clojurescript "1.10.439" :scope "provided"]
                 [cljsjs/react "16.13.0-0"]
                 [cljsjs/react-dom "16.13.0-0"]
                 [cljsjs/create-react-class "15.6.3-0" :exclusions [cljsjs/react]]
                 [cljsjs/prop-types "15.6.2-0" :exclusions [cljsjs/react]]
                 [cljsjs/react-test-renderer "16.13.0-3" :exclusions [cljsjs/react]]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-doo "0.1.10"]
            [lein-codox "0.10.5"]
            [lein-auto "0.1.3"]]

  :profiles {:dev        {:dependencies   [[active-clojure "0.11.0" :exclusions [org.clojure/clojure]]
                                           [lein-doo "0.1.7"]
                                           [codox-theme-rdash "0.1.2"]
                                           [com.bhauman/figwheel-main "0.2.0"]
                                           [com.bhauman/rebel-readline-cljs "0.1.4"]]
                          :resource-paths ["target" "dev-resources"]}
             :test       {:source-paths ["src" "test-nodom" "test-dom" "examples"]}
             :test-dom   {:source-paths ["src" "test-dom"]}
             :test-nodom {:source-paths ["src" "test-nodom"]}}

  ;; open http://localhost:9500/figwheel-extra-main/auto-testing for the tests.
  ;; open http://localhost:9500/figwheel-extra-main/todo and others for the examples
  :aliases {"fig" ["trampoline" "with-profile" "+dev,+test" "run" "-m" "figwheel.main" "-b" "dev" "-r"]

            ;; google-chrome-stable seems to be broken at the moment:
            ;; https://github.com/bhauman/figwheel-main/issues/159
            ;; we start chrome headless in karma in travis.yml

            ;; "figtest-dom-travis" ["trampoline" "with-profile" "+dev,+test-dom" "run" "-m" "figwheel.main" "-fwo" "{:launch-js [\"google-chrome-stable\" \"--no-sandbox\" \"--headless\" \"--disable-gpu\" \"--repl\" :open-url] :repl-eval-timeout 30000}" "-co" "test-dom.cljs.edn" "-m" reacl2.test.figwheel-test-runner]
            ;; "figtest-nodom-travis" ["trampoline" "with-profile" "+dev,+test-nodom" "run" "-m" "figwheel.main" "-fwo" "{:launch-js [\"google-chrome-stable\" \"--no-sandbox\" \"--headless\" \"--disable-gpu\" \"--repl\" :open-url] :repl-eval-timeout 30000}" "-co" "test-nodom.cljs.edn" "-m" reacl2.test.figwheel-test-runner]
            }


  :codox {:language :clojurescript
          :metadata {:doc/format :markdown}
          :themes [:rdash]
          :src-dir-uri "http://github.com/active-group/reacl/blob/master/"
          :src-linenum-anchor-prefix "L"}

  :clean-targets ^{:protect false} [:target-path "out" "target"]

  ;; for test driven development use
  ;; > lein auto do clean, doo chrome-headless test-nodom once, doo chrome-headless test-dom once
  :auto {:default {:paths ["src" "test-dom" "test-nodom" "examples"]}}

  :cljsbuild

  { :builds [{:id "test-dom"
              :source-paths ["src" "test-dom"]
              :compiler {:output-to "target/test-dom.js"
                         :output-dir "target/test-dom"
                         :main reacl2.test.runner
                         :optimizations :whitespace}}

             {:id "test-nodom"
              :source-paths ["src" "test-nodom"]
              :compiler {:output-to "target/test-nodom.js"
                         :output-dir "target/test-nodom"
                         :main reacl2.test.runner
                         }}

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
             {:id "todo-reacl1"
               :source-paths ["src" "examples/todo-reacl1"]
               :compiler {:output-to "target/todo-reacl1/main.js"
                          :output-dir "target/todo-reacl1/out"
                          :source-map "target/todo-reacl1/main.map"
                          :optimizations :whitespace
                          :parallel-build true}}
              {:id "comments"
               :source-paths ["src" "examples/comments"]
               :compiler {:output-to "target/comments/main.js"
                          :output-dir "target/comments/out"
                          :source-map "target/comments/main.map"
                          :optimizations :whitespace
                          :parallel-build true}}]}

  )


