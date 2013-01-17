(defproject startlabs "0.1.0-SNAPSHOT"
  :description "The new Start Labs Pad"
  :url "http://startlabs.org"
  :plugins [[lein-cljsbuild "0.2.10"]
            [lein-ring "0.8.0"
             :exclusions [lein-jacker]]
            [com.keminglabs/cljx "0.2.0"
             :exclusions [org.clojure/core.logic
                          jonase/kibit]]]

  :dependencies [ ;clj core
                 [org.clojure/clojure "1.4.0"]
                 [org.clojure/data.json "0.2.1"]
                 [org.clojure/tools.logging "0.2.4"]
                 [org.clojure/core.incubator "0.1.2"] ; for -?> goodness
                 [org.clojure/math.numeric-tower "0.0.2"]
                 
                 ;clj other
                 [compojure "1.1.5"
                  :exclusions [org.clojure/tools.macro]]
                 [hiccup "1.0.2"]
                 [lib-noir "0.3.4"
                  :exclusions [cheshire]]
                 [org.clojars.cjschroed/sandbar "0.4.0"
                  :exclusions [inflections
                               slingshot]]

                 [clj-http "0.6.3"
                  :exclusions [commons-codec]]
                 [oauth-clj "0.1.1"]
                 [com.datomic/datomic-free "0.8.3731"
                  :exclusions [com.amazonaws/aws-java-sdk
                               org.codehaus.jackson/jackson-core-asl
                               org.slf4j/slf4j-nop
                               org.slf4j/log4j-over-slf4j]]
                 [org.slf4j/slf4j-log4j12 "1.7.2"]
                 [environ "0.3.1"]
                 [clj-aws-s3 "0.3.3"]
                 [markdown-clj "0.9.18"]
                 [clj-time "0.4.4"]
                 [com.draines/postal "1.9.2"]
                 [climp "0.1.2"]
                 [sherbondy/clj-stripe "1.0.5"]

                 ;cljs
                 [crate "0.2.3"]
                 [jayq "2.0.0"]
                ]

  :source-paths ["src/clj" ".generated/clj"]

  :cljsbuild {:builds 
              [{:source-path "src/cljs"
                :compiler {:output-to "resources/public/client.js"
                           :pretty-print true
                           :optimizations :simple}}]}
                           ; switch to :advanced for production

  :cljx {:builds 
         [ ;clj
          {:source-paths ["src/cljx"]
           :output-path ".generated/clj"
           :rules cljx.rules/clj-rules}
           ;cljs
          {:source-paths ["src/cljx"]
           ;careful! cannot give cljx and cljs files conflicting names
           :output-path "src/cljs"
           :extension "cljs"
           :include-meta true
           :rules cljx.rules/cljs-rules}]}

  :datomic {:schemas ["conf" ["schema.dtm"]]}

  :profiles {:dev 
             {:datomic
              {:config "conf/free-transactor-template.properties"
               :db-uri "datomic:free://localhost:4334/startlabs"}
              :dependencies [[ring-serve "0.1.2"
                              :exclusions [ring/ring-devel
                                           ring/ring-jetty-adapter]]]}}
  
  :ring {:handler startlabs.server/app
         :init startlabs.server/init
         :port 8000
         :auto-reload? true})
