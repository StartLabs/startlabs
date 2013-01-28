(defproject startlabs "0.1.0-SNAPSHOT"
  :description "The new Start Labs Pad"
  :url "http://startlabs.org"
  :plugins [[lein-cljsbuild "0.2.10"
             :exclusions [org.clojure/clojure
                          com.google.guava/guava]]
            [lein-ring "0.8.0"
             :exclusions [lein-jacker
                          org.clojure/clojure]]
            [com.birdseye-sw/lein-dalap "0.1.0"]]

                 ;clj core
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/tools.logging "0.2.4"]
                 [org.clojure/core.incubator "0.1.2"] ; for -?> goodness
                 [org.clojure/math.numeric-tower "0.0.2"]
                 
                 ;clj other
                 [aleph "0.3.0-beta7"]
                 [cheshire "5.0.1"]
                 [climp "0.1.2"
                  :exclusions [cheshire]]
                 [clj-aws-s3 "0.3.3"]
                 [clj-http "0.6.3"
                  :exclusions [commons-codec]]
                 [clj-time "0.4.4"]
                 [compojure "1.1.5"
                  :exclusions [org.clojure/tools.macro]]
                 [environ "0.3.1"]
                 [hiccup "1.0.2"]
                 [lib-noir "0.3.4"
                  :exclusions [cheshire]]
                 [markdown-clj "0.9.18"]
                 [oauth-clj "0.1.1"]
                 [ring-basic-authentication "1.0.1"]

                 ;clj other other
                 [com.datomic/datomic-free "0.8.3731"
                  :exclusions [com.amazonaws/aws-java-sdk
                               org.codehaus.jackson/jackson-core-asl
                               org.slf4j/slf4j-nop
                               org.slf4j/log4j-over-slf4j]]
                 [com.draines/postal "1.9.2"]
                 [org.clojars.cjschroed/sandbar "0.4.0"
                  :exclusions [inflections
                               ring/ring-core
                               slingshot]]
                 [org.slf4j/slf4j-log4j12 "1.7.2"]
                 [sherbondy/clj-stripe "1.0.5"]

                 ;cljs
                 [cljs-hash "0.0.2"]
                 [jayq "2.0.0"]

                 [org.clojure/google-closure-library "0.0-2029"]
                 [prismatic/dommy "0.0.1"]]

  :source-paths ["src/clj"]

  :hooks [leiningen.dalap]

  :cljsbuild {:builds 
              [{:source-path "src/cljs"
                :compiler {:output-to "resources/public/client.js"
                           :pretty-print true
                           :optimizations :simple}}]}
                           ; switch to :advanced for production

  :datomic {:schemas ["conf" ["schema.dtm"]]}

  :profiles {:dev 
             {:datomic
              {:config "conf/free-transactor-template.properties"
               :db-uri "datomic:free://localhost:4334/startlabs"}}}
  
  :ring {:handler startlabs.server/app
         :init startlabs.server/init
         :port 8000
         :auto-reload? true})
