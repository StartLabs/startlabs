(defproject startlabs "0.1.0-SNAPSHOT"
  :description "The new Start Labs Pad"
  :url "http://startlabs.org"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/data.json "0.1.2"]
                 [noir-cljs "0.3.4"]
                 [jayq "0.1.0-alpha4"]
                 [fetch "0.1.0-alpha2"]
                 [crate "0.2.0-alpha4"]
                 [noir "1.3.0-beta6"]
                 [clj-http "0.5.0"]
                 [oauth-clj "0.0.5"]]
  :cljsbuild {:builds [{}]}
  :main ^{:skip-aot true} startlabs.server)
