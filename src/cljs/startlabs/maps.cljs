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

(def CM js/CM)
(def L js/L)

(def ^:dynamic lmap nil)
(def ^:dynamic markers nil)

(def geocoder (CM/Geocoder. cloudmade-key))

(defn latlng [lat lng]
  (L/LatLng. lat lng))

(defn latlng-bounds [sw ne]
  (L/LatLngBounds. sw ne))

(defn marker [[lat lng] & opts]
  (.marker L (array lat lng) (clj->js (apply array-map opts))))

(defn add-marker-callback [zoom?]
  (fn [response]
    (let [response-map (js->clj response :keywordize-keys true)
          bounds       (:bounds response-map)
          south-west   (apply latlng (map #(nth (bounds 0) %) [0 1]))
          north-east   (apply latlng (map #(nth (bounds 1) %) [0 1]))]

      (if zoom?
        (.fitBounds lmap (latlng-bounds south-west north-east)))

      (let [feature (first (:features response-map))
            coords (:coordinates (:centroid feature))]
        (.addLayer markers (marker coords :title (:name (:properties feature)))))

  )))

(defn geocode [place callback]
  (.getLocations geocoder place callback))

(defn setup-maps []
  (def lmap (.map L "map"))
  (.setView lmap (array 42 -92) 3)

  (def markers (L/LayerGroup.))
  (.addTo markers lmap)

  (.masonry ($ "#job-list") (clj->js {
    :itemSelector ".job-brick"
    :columnWidth 390
  }))

  (.log js/console "MAPPIN")
  (.addTo (.tileLayer L tile-layer-url (clj->js {:maxZoom 18})) lmap)

  (geocode "Brooklyn, New York" (add-marker-callback false))

)