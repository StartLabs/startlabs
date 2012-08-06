(ns startlabs.main
  (:use [singult.core :only [render]]
        [c2.core :only [unify]])
  (:require [goog.string :as str]
            [fetch.remotes :as remotes]
            [c2.dom :as dom]
            [c2.event :as event]
            [c2.util :as util])
  (:require-macros [fetch.macros :as fm]))

(def $ dom/select)

(def $content ($ "#content"))

(def up-and-running
  [:p.alert "CLJS is compiled and active... Time to build something!"])

(dom/append! $content (render up-and-running))

(def location-hash (.-hash js/location))

(defn mapify-hash []
  (let [hash (.slice location-hash 1)
        split-hash (.split hash #"[=&]")
        keyvals (map-indexed (fn [idx val] (if (even? idx) (keyword val) val)) split-hash)]
    (apply hash-map keyvals)))


(defn handle-hash-change [& e]
  (let [hash-vals (mapify-hash)
        access-token (:access_token hash-vals)
        expiration (:expires_in hash-vals)]
    (if access-token
      (fm/remote (token-info access-token) [result]
        (do
          (.log js/console result)
          ; redirect to home
          (set! (.-location js/window) "/"))))))

(defn main []
  (if location-hash (handle-hash-change))
  (set! (.-onhashchange js/window) handle-hash-change))

(event/on-load
  (do
    (.log js/console "Hello world!")
    (main)))

