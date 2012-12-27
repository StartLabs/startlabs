(ns startlabs.jobs
  (:use [jayq.core :only [$]]
        [jayq.util :only [clj->js]]
        [crate.core :only [html]]
        [startlabs.views.jobx :only [job-list job-card markdownify]])

  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [jayq.core :as jq]
            [jayq.util :as util]
            [startlabs.util :as u])

  (:require-macros [jayq.macros :as jm]))

;; eventually I should split this out into three separate modules:
;; 1. Generic google maps wrapper
;; 2. Jobs list script
;; 3. Jobs submit script

;;
;; Generic google maps wrapper
;;

;; these should go in util
(defn have-values? [& elems]
  (not (some true? (map #(empty? (.val %)) elems))))

(defn elem-by-id [id]
  (.getElementById js/document id))


(defn make-marker [options]
  ( google.maps.Marker. (clj->js options)))

(defn make-job-marker [job map coords & [opts]]
  (make-marker (merge opts
                      {:position coords 
                       :map map
                       :title (str (:company job) ": " (:position job))})))

(def geocoder (google.maps.Geocoder.))

(defn geocode [location callback]
  (let [request (clj->js {:address location})]
    (.geocode geocoder request callback)))

(defn grab-coords 
  "Extracts the coordinates from a geocode response and passes
   them to success-fn on success."
  [success-fn]
  (fn [result status]
    (if (= status google.maps.GeocoderStatus.OK)
      (let [coords (.-location (.-geometry (nth result 0)))]
        (success-fn coords)))))

(def map-options (clj->js {:center (google.maps.LatLng. 30 0)
                           :zoom 2
                           :mapTypeId google.maps.MapTypeId.ROADMAP}))

(defn lat-lng [lat lng]
  (google.maps.LatLng. (js/parseFloat lat) (js/parseFloat lng)))

(def mit (lat-lng 42.358449 -71.09122))

;;
;; JOB LIST
;;

;; job list globals
(def ^:dynamic gmap nil)
(def ^:dynamic search-timeout nil)
; slurp up the job data from the script tag embedded in the page
(def job-data (js->clj (.-job_data js/window) :keywordize-keys true))
(def markers (atom []))
(def filtered-jobs (atom []))
(def active-job (atom {}))


(defn job-with-id [id]
  (first 
    (filter 
      (fn [job] (= (:id job) id))
      job-data)))

(defn show-job-details [e]
  (.preventDefault e)
  (this-as this
           (-> ($ this) (.find ".read") .toggle)
           (-> ($ this) (.find ".more") .toggle)))

(defn find-jobs [query]
  #(let [$job-list ($ "#job-list")
         parent (.parent $job-list)]
     (jq/ajax (str "/jobs.edn?q=" query)
              {:contentType :text/edn
               :success (fn [data status xhr]
                            (let [data (reader/read-string data)]
                              (reset! filtered-jobs (:jobs data))
                              (.remove $job-list)
                              (.html parent (:html data))))
               })))

(defn add-jobs-marker [job] 
  (fn [coords]
    (let [marker (make-job-marker job gmap coords)]
      (google.maps.event.addListener marker "click" (fn []
        (set! (.-hash js/location) (str "#" (:id job)))))
      (swap! markers conj marker))))

(defn setup-jobs-list []
  (let [gmap-elem (elem-by-id "map")]
    (set! gmap (google.maps.Map. gmap-elem map-options)))

  ; key, reference, old state, new state
  (add-watch filtered-jobs :mapper (fn [k r o n]
    (if (not= o n)
      (do
        (doseq [marker @markers]
          (.setMap marker nil))
        
        (reset! markers [])

        (doseq [job n]
          (let [coords (lat-lng (:latitude job) (:longitude job))]
            (u/log coords)
            ((add-jobs-marker job) coords))
          ))
      )))

  (let [$map-box ($ "#map-box")]
    (jq/on $map-box :keyup "#job-search" 
           (fn [e]
             ; filter jobs as you search
             (this-as job-search
                      (let [query (str/trim (jq/val ($ job-search)))]
                        (js/clearTimeout search-timeout)
                        (set! search-timeout (js/setTimeout (find-jobs query) 500))
                        ))))

    (jq/on $map-box :click "#map-toggle" 
           (fn [e]
             (.preventDefault e)
             (.toggle ($ "#map"))))
    )

  (let [$job-container ($ "#job-container")]
    (jq/on $job-container :click ".job" nil show-job-details)
    (jq/on $job-container :click ".edit-link" nil (fn [e] (.stopPropagation e)))
    (jq/on $job-container :click ".more a" nil (fn [e] (.stopPropagation e))))
	
  (reset! filtered-jobs job-data)
)




;;
;; JOB SUBMIT
;;

;; submit globals
(def ^:dynamic preview-map nil)
(def ^:dynamic preview-marker nil)


(defn update-job-card []
  (let [job-map (u/form-to-map ($ "#job-form"))]
    (.html ($ "#job-preview")
           (html (job-card job-map false))))
  ; singult is escaping the generated markdown :(
  (.html ($ "#job-preview .description")
         (markdownify (.val ($ "#description")))))

(defn change-fulltime [val]
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
                         (.trigger $inp "change")
                         (change-fulltime btn-val))))
                 
                   (if (= (.val $btn) (.val $inp))
                     (.addClass $btn "active"))))))

           (change-fulltime (.val $inp)))))))

(defn update-preview-marker [coords]
  (.setPosition preview-marker coords)
  )

(defn update-location []
  (let [location (.val ($ "#location"))]
    (geocode location (grab-coords update-preview-marker))))


(defn setup-job-submit []
  (let [preview-map-elem (elem-by-id "job-location")
        $lat ($ "#latitude")
        $lng ($ "#longitude")
        starting-position (if (have-values? $lat $lng)
                            (lat-lng (.val $lat) (.val $lng))
                            mit)]

    (set! preview-map (google.maps.Map. preview-map-elem map-options))

    (set! preview-marker (make-marker {:map preview-map
                                       :title "Drag me to the right location."
                                       :position starting-position
                                       :draggable true}))

    (google.maps.event.addListener preview-marker "position_changed" 
                                   (fn []
                                     (let [pos (.getPosition preview-marker)
                                           [lat lng] [(.lat pos) (.lng pos)]]
                                       (.val $lat lat)
                                       (.val $lng lng)))))

  (.datepicker ($ ".datepicker"))
  
  (setup-radio-buttons)

  (let [$job-form ($ "#job-form")]
    (jq/on $job-form [:keyup :blur :change] "input, textarea" nil update-job-card)
    (jq/on $job-form :blur "#location" nil update-location)))

;; three things:
;; 1. If lat/lng is set on load, add marker corresponding to lat lng.
;; 2. If location is changed, lookup lat/lng and set inputs + marker.

