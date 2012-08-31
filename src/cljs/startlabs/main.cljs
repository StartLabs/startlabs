(ns startlabs.main
  (:use [singult.core :only [render]]
        [jayq.core :only [$]])
  (:require [goog.string :as str]
            [fetch.remotes :as remotes]
            [clojure.browser.repl :as repl]
            [jayq.core :as jq]
            [jayq.util :as util])
  (:require-macros [fetch.macros :as fm]
                   [jayq.macros :as jm]))

; browser repl for development
; (repl/connect "http://localhost:9000/repl")

(def location-hash (.-hash js/location))

(defn mapify-hash 
  "convert location.hash into a clojure map"
  []
  (let [hash (.slice location-hash 1)
        split-hash (.split hash #"[=&]")
        keyvals (map-indexed (fn [idx val] (if (even? idx) (keyword val) val)) split-hash)]
    (apply hash-map keyvals)))


(defn handle-hash-change [& e]
  (let [hash-vals (mapify-hash)
        access-token (:access_token hash-vals)
        expiration (:expires_in hash-vals)]
    (if access-token
      (do
        (jq/text ($ "#loading") "Verifying credentials...")
        (fm/remote (token-info access-token) [result]
          (.log js/console (util/clj->js result))
          (set! (.-location js/window) "/")
        )))))

(defn main []
  (if location-hash (handle-hash-change))
  (set! (.-onhashchange js/window) handle-hash-change))

(jm/ready
  (.log js/console "Hello world!")
  (main))

