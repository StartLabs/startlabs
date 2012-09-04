(ns startlabs.maps
  (:use [jayq.core :only [$]]
        [jayq.util :only [clj->js]])

  (:require [clojure.string :as str]
            [fetch.remotes :as remotes]
            [jayq.core :as jq]
            [jayq.util :as util])

  (:require-macros [fetch.macros :as fm]
                   [jayq.macros :as jm]))

(def cloudmade-key "fe134333250f494fb51ac8903b83c9fb")
(def tile-layer-url (str "http://{s}.tile.cloudmade.com/" cloudmade-key "/997/256/{z}/{x}/{y}.png"))

(def L js/L)

(defn geocode [place]
  )

(defn setup-maps []
  (let [lmap (.setView (.map L "map") (array 42 -92) 3)]
    (.log js/console "MAPPIN")
    (.addTo (.tileLayer L tile-layer-url (clj->js {:maxZoom 18})) lmap)))