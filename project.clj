(defproject startlabs "0.1.0-SNAPSHOT"
  :description "The new Start Labs Pad"
  :url "http://startlabs.org"
  :plugins [[lein-cljsbuild "0.2.7"]
            [com.keminglabs/cljx "0.1.4"]]

  :dependencies [ ;clj core
                  [org.clojure/clojure "1.4.0"]
                  [org.clojure/data.json "0.1.3"]
                  [org.clojure/tools.logging "0.2.4"]
                  [org.clojure/core.incubator "0.1.1"] ; for -?> goodness
                  [org.clojure/math.numeric-tower "0.0.1"]
                  [org.clojure/tools.nrepl "0.2.0-beta9"]
                  
                  ;clj other
                  [noir "1.3.0-beta10"]
                  [clj-http "0.5.2"]
                  [oauth-clj "0.0.5"]
                  [com.datomic/datomic-free "0.8.3488" 
                   :exclusions [org.slf4j/slf4j-nop org.slf4j/log4j-over-slf4j]]
                  [org.slf4j/slf4j-log4j12 "1.6.4"]
                  [environ "0.3.0"]
                  [clj-aws-s3 "0.3.2"]
                  [markdown-clj "0.9.8"]
                  [clj-time "0.4.4"]
                  [com.draines/postal "1.8.0"]

                  ;cljs
                  [com.keminglabs/c2 "0.2.0"]
                  [com.keminglabs/singult "0.1.4"]
                  [fetch "0.1.0-alpha2"]
                  [jayq "0.1.0-alpha4"]
                ]

  :source-paths ["src/clj" ".generated/clj"]

  :cljsbuild {:builds [
                       {:source-path "src/cljs"
                        :compiler {:output-to "resources/public/client.js"
                                   :pretty-print true
                                   :optimizations :simple}}]}
                                   ; switch to :advanced for production

  :cljx {:builds [ ;clj
                  {:source-paths ["src/cljx"]
                   :output-path ".generated/clj"
                   :rules cljx.rules/clj-rules}
                   ;cljs
                  {:source-paths ["src/cljx"]
                   :output-path "src/cljs" ; careful! cannot name cljx and cljs the same
                   :extension "cljs"
                   :include-meta true
                   :rules cljx.rules/cljs-rules}]}

  ;;generate cljx before JAR
  :hooks [cljx.hooks]

  :main ^{:skip-aot true} startlabs.server)
