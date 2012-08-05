(defproject startlabs "0.1.0-SNAPSHOT"
  :description "The new Start Labs Pad"
  :url "http://startlabs.org"
  :plugins [[lein-cljsbuild "0.2.5"]]
  :dependencies [ ;clj
                  [org.clojure/clojure "1.4.0"]
                  [noir "1.3.0-beta10"]
                  [clj-http "0.5.2"]
                  [oauth-clj "0.0.5"]
                  ;cljs
                  [com.keminglabs/c2 "0.2.0"]
                  [jayq "0.1.0-alpha4"]
                ]
  :source-paths ["src/clj"]
  :cljsbuild {:builds
              [{:source-path "src/cljs"
                :compiler {:output-to "resources/public/client.js"
                           :pretty-print false
                           :optimizations :simple}}]}
                           ; switch to :advanced for production

  :main ^{:skip-aot true} startlabs.server)
