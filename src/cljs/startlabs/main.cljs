(ns startlabs.main
  (:require [goog.string :as str]
            [fetch.remotes :as remotes])
  (:use [jayq.core :only [$ append]]
        [jayq.util :only [map->js clj->js]]
        [singult.core :only [render]])
  (:require-macros [fetch.macros :as fm]
                   [jayq.macros :as jq]))

(def $content ($ :#content))

(def up-and-running
  [:p.alert "CLJS is compiled and active... Time to build something!"])

(append $content (render up-and-running))

(defn mapify-hash []
  (let [hash (.slice (.-hash js/location) 1)
        split-hash (.split hash #"[=&]")
        keyvals (map-indexed (fn [idx val] (if (even? idx) (keyword val) val)) split-hash)]
    (apply hash-map keyvals)))

(defn main []
  (let [hash-vals (mapify-hash)
        access-token (:access_token hash-vals)
        expiration (:expires_in hash-vals)]
    (if access-token
      (fm/remote (token-info access-token) [result]
        (do
          (.log js/console result)
          (set! (.-location js/window) "/"))))))

(jq/ready
  (.log js/console "Hello world")
  (main))

