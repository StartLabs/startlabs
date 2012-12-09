(ns startlabs.jobs
  (:use [jayq.core :only [$]]
        [jayq.util :only [clj->js]]
        [singult.core :only [render]]
        [c2.core :only [unify]]
        [startlabs.views.jobx :only [job-list job-card]])

  (:require [clojure.string :as str]
            [fetch.remotes :as remotes]
            [jayq.core :as jq]
            [jayq.util :as util]
            [startlabs.util :as u])

  (:use-macros [c2.util :only [bind!]])

  (:require-macros [fetch.macros :as fm]
                   [jayq.macros :as jm]))

;; eventually I should split this out into two separate modules:
;; 1. A generic cloudmade/leaflet api wrapper
;; 2. The jobs-page-specific logic.

(def cloudmade-key "fe134333250f494fb51ac8903b83c9fb")

(def tile-layer-url 
  (str "http://{s}.tile.cloudmade.com/" cloudmade-key 
       "/997/256/{z}/{x}/{y}.png"))

; shorthand
(def CM js/CM)
(def L js/L)

; the following are initialized in setup-maps
(def ^:dynamic lmap nil) ; the leaflet map object
(def ^:dynamic markers nil) ; the leaflet layergroup containing the markers
(def ^:dynamic oms nil) ; the overlapping marker spiderfier
(def ^:dynamic search-timeout nil)

(def geocoder (CM/Geocoder. cloudmade-key))

; slurp up the job data from the script tag embedded in the page
(def job-data (js->clj (.-job_data js/window) :keywordize-keys true))
(def filtered-jobs (atom []))
(def active-job (atom {}))

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
          bounds       (:bounds response-map)]

      (if (and bounds zoom?)
        (let [south-west   (apply latlng (map #(nth (bounds 0) %) [0 1]))
              north-east   (apply latlng (map #(nth (bounds 1) %) [0 1]))]
          (.fitBounds lmap (latlng-bounds south-west north-east))))

      (let [feature    (first (:features response-map))
            coords     (:coordinates (:centroid feature))
            new-marker (marker coords :title 
                         (str (:company job) ": " (:position job) 
                              " (" (:location job) ")"))
            job-id     (:id job)]
        
        (set! (.-id new-marker) job-id)
        (.addLayer markers new-marker)
        (.addMarker oms new-marker)
))))

(defn geocode [place callback]
  (.getLocations geocoder place callback))

(defn job-with-id [id]
  (first 
    (filter 
      (fn [job] (= (:id job) id))
      job-data)))


(defn update-job-card []
  (let [job-map (u/form-to-map ($ "#job-form"))]
    (.html ($ "#job-preview")
           (render (job-card job-map false))))
  ; singult is escaping the generated markdown :(
  (.html ($ "#job-preview .description")
         (markdown/mdToHtml (.val ($ "#description")))))

(defn change-fulltime [val]
  (update-job-card)
  (let [$tr (.eq (.parents ($ "#end_date") "tr") 0)]
    (if (= val "true")
      (.hide $tr)
      (.show $tr))))

(defn setup-radio-buttons []
  (.each ($ "div.btn-group[data-toggle-name=*]") 
    (fn [i elem]
      (this-as this
        (let [group ($ this)
               form (.eq (.parents group "form") 0)
               name (.attr group "data-toggle-name")
               $inp ($ (str "input[name='" name "']") form)]
           (.each ($ "button" group) 
             (fn [i elem]
               (this-as btn
                 (let [$btn ($ btn)]

                   (jq/bind $btn :click
                     (fn [e]
                       (let [btn-val (.val $btn)]
                         (.preventDefault e)
                         (.val $inp btn-val)
                         (change-fulltime btn-val))))
                 
                   (if (= (.val $btn) (.val $inp))
                     (.addClass $btn "active"))))))

           (change-fulltime (.val $inp)))))))

(defn setup-job-submit []
  (.datepicker ($ ".datepicker"))
  (setup-radio-buttons)
  (let [$elems ($ "#job-form input, #job-form textarea")]
    (jq/bind $elems :keyup update-job-card)
    (jq/bind $elems :blur  update-job-card)))

(defn show-job-details [e]
  (.preventDefault e)
  (this-as this
           (-> ($ this) (.find ".read") .toggle)
           (-> ($ this) (.find ".more") .toggle)))

(defn find-jobs [query]
  (fn []
    (fm/remote (jobsearch query) [results]
               (let [$job-list ($ "#job-list")
                     parent (.parent $job-list)]
                 (reset! filtered-jobs (:jobs results))
                 (.remove $job-list)
                 (.html parent (:html results))))))

(defn setup-jobs-list []
  (def lmap (.map L "map"))
  (.setView lmap (array 42 -40) 3)

  (def markers (L/LayerGroup.))
  ; add markers layer to map
  (.addTo markers lmap)
  ; add tiles to map
  (.addTo (.tileLayer L tile-layer-url (clj->js {:maxZoom 20})) lmap)

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

  (jq/bind ($ "#job-search") :keyup (fn [e]
    ; filter jobs as you search
    (this-as job-search
      (let [query (str/trim (jq/val ($ job-search)))]
        (js/clearTimeout search-timeout)
        (set! search-timeout (js/setTimeout (find-jobs query) 500))
    ))))

  (jq/bind ($ "#map-toggle") :click (fn [e]
    (.preventDefault e)
    (.toggle ($ "#map"))))

  (let [$job-container ($ "#job-container")]
    (jq/on $job-container :click ".job" nil show-job-details)
    (jq/on $job-container :click ".more a" nil (fn [e] (.stopPropagation e))))
	
  (reset! filtered-jobs job-data)
)

