(ns startlabs.maps
  (:use [jayq.core :only [$]]
        [jayq.util :only [clj->js]]
        [singult.core :only [render]]
        [c2.core :only [unify]]
        [startlabs.views.jobx :only [job-list job-card]])

  (:require [clojure.string :as str]
            [fetch.remotes :as remotes]
            [jayq.core :as jq]
            [jayq.util :as util])

  (:use-macros [c2.util :only [bind!]])

  (:require-macros [fetch.macros :as fm]
                   [jayq.macros :as jm]))

(def cloudmade-key "fe134333250f494fb51ac8903b83c9fb")
(def tile-layer-url (str "http://{s}.tile.cloudmade.com/" cloudmade-key "/997/256/{z}/{x}/{y}.png"))

(def CM js/CM)
(def L js/L)

(def ^:dynamic lmap nil)
(def ^:dynamic markers nil)

(def geocoder (CM/Geocoder. cloudmade-key))

(def ^:dynamic oms nil)

(def job-data (js->clj (.-job_data js/window) :keywordize-keys true))
(def filtered-jobs (atom []))

(defn latlng [lat lng]
  (L/LatLng. lat lng))

(defn latlng-bounds [sw ne]
  (L/LatLngBounds. sw ne))

(defn marker [[lat lng] & opts]
  (.marker L (array lat lng) 
             (clj->js (apply array-map opts))))

(defn add-marker-callback [job zoom?]
  (fn [response]
    (let [response-map (js->clj response :keywordize-keys true)
          bounds       (:bounds response-map)
          south-west   (apply latlng (map #(nth (bounds 0) %) [0 1]))
          north-east   (apply latlng (map #(nth (bounds 1) %) [0 1]))]

      (if zoom?
        (.fitBounds lmap (latlng-bounds south-west north-east)))

      (let [feature    (first (:features response-map))
            coords     (:coordinates (:centroid feature))
            new-marker (marker coords :title 
                         (str (:company job) ": " (:position job) 
                              " (" (:location job) ")"))]

        (set! (.-id new-marker) (:id job))
        (.addLayer markers new-marker)
        (.addMarker oms new-marker)

        (.addListener oms "click" (fn [marker]
          (set! (.-hash js/location) (str "#" (.-id marker)))))
        )
)))

(defn geocode [place callback]
  (.getLocations geocoder place callback))

(defn jobs-filter [query]
  (fn [_]
    (if (empty? query)
      job-data
      (filter (fn [job]
        (some #(re-find (re-pattern (str "(?i)" query)) %) 
              (map job [:position :company :location])))
        job-data))))

(defn setup-maps []
  (def lmap (.map L "map"))
  (.setView lmap (array 42 -92) 3)

  (def markers (L/LayerGroup.))
  ; add markers layer to map
  (.addTo markers lmap)
  ; add tiles to map
  (.addTo (.tileLayer L tile-layer-url (clj->js {:maxZoom 18})) lmap)

  (def oms (js/OverlappingMarkerSpiderfier. lmap))

  ; key, reference, old state, new state
  (add-watch filtered-jobs :mapper (fn [k r o n]
    (if (not= o n)
      (do
        (.clearLayers markers)
        (.clearMarkers oms)
        (.clearListeners oms "click")

        (doseq [job n]
          (let [location (:location job)]
            (geocode location (add-marker-callback job false))))
  ))))

  (reset! filtered-jobs job-data)

  (bind! "#job-list"
    (job-list @filtered-jobs))

  (jq/bind ($ "#job-search") :keyup (fn [e]
    ; filter jobs as you search
    (this-as job-search
      (let [query (str/trim (jq/val ($ job-search)))]
        (swap! filtered-jobs (jobs-filter query))))))

  (jq/bind ($ "#map-toggle") :click (fn [e]
    (.preventDefault e)
    (.toggle ($ "#map"))))

)

