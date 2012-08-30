(defproject startlabs "0.1.0-SNAPSHOT"
  :description "The new Start Labs Pad"
  :url "http://startlabs.org"
  :plugins [[lein-cljsbuild "0.2.7"]]
  :dependencies [ ;clj
                  [org.clojure/clojure "1.4.0"]
                  [org.clojure/tools.logging "0.2.4"]
                  [noir "1.3.0-beta10"]
                  [fetch "0.1.0-alpha2"]
                  [clj-http "0.5.2"]
                  [oauth-clj "0.0.5"]
                  [com.datomic/datomic-free "0.8.3435" 
                   :exclusions [org.slf4j/slf4j-nop org.slf4j/log4j-over-slf4j]]
                  [org.slf4j/slf4j-log4j12 "1.6.4"]
                  ;cljs
                  [com.keminglabs/c2 "0.2.1"]
                  [jayq "0.1.0-alpha4"]
                ]
  ; download datomic here: http://downloads.datomic.com/free.html
  ; put it into datomic-free, then install locally with mvn
  ; mvn install:install-file -DgroupId=com.datomic -DartifactId=datomic-free -Dfile=datomic-free-0.8.3435.jar -DpomFile=pom.xml
  :repositories {"local" ~(str (.toURI (java.io.File. "datomic-free")))}

  :source-paths ["src/clj"]
  :cljsbuild {:builds
              [{:source-path "src/cljs"
                :compiler {:output-to "resources/public/client.js"
                           :pretty-print true
                           :optimizations :simple}}]}
                           ; switch to :advanced for production

  :main ^{:skip-aot true} startlabs.server)
