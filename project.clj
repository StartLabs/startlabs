(defproject startlabs "0.1.0-SNAPSHOT"
  :description "The new Start Labs Pad"
  :url "http://startlabs.org"
  :plugins [[lein-cljsbuild "0.2.5"]]
  :dependencies [ ;clj
                  [org.clojure/clojure "1.4.0"]
                  [noir "1.3.0-beta10"]
                  [fetch "0.1.0-alpha2"]
                  [clj-http "0.5.2"]
                  [oauth-clj "0.0.5"]
                  [com.datomic/datomic-free "0.8.3372" 
                   :exclusions [org.slf4j/slf4j-nop org.slf4j/log4j-over-slf4j]]
                  [org.slf4j/slf4j-log4j12 "1.6.4"]
                  ;cljs
                  [com.keminglabs/c2 "0.2.0"]
                  [jayq "0.1.0-alpha4"]
                ]
  ; download datomic here: http://downloads.datomic.com/free.html
  ; put it into datomic-free, then install locally with mvn
  :repositories {"local" ~(str (.toURI (java.io.File. "datomic-free")))}

  :source-paths ["src/clj"]
  :cljsbuild {:builds
              [{:source-path "src/cljs"
                :compiler {:output-to "resources/public/client.js"
                           :pretty-print false
                           :optimizations :simple}}]}
                           ; switch to :advanced for production

  :main ^{:skip-aot true} startlabs.server)
