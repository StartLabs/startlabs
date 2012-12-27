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

;; eventually I should split this out into two separate modules:
;; 1. A generic cloudmade/leaflet api wrapper
;; 2. The jobs-page-specific logic.

(def ^:dynamic gmap nil)
(def ^:dynamic preview-map nil)
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


(defn update-job-card []
  (let [job-map (u/form-to-map ($ "#job-form"))]
    (.html ($ "#job-preview")
           (html (job-card job-map false))))
  ; singult is escaping the generated markdown :(
  (.html ($ "#job-preview .description")
         (markdownify (.val ($ "#description")))))

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

(defn make-marker [options]
  ( google.maps.Marker. (clj->js options)))

(defn add-marker-callback [job] 
  (fn [result status]
    (if (= status google.maps.GeocoderStatus.OK)
      (let [coords (.-location (.-geometry (nth result 0)))
            marker (make-marker {:position coords :map gmap 
                                 :title (str (:company job) ": " 
                                             (:position job))})]
        (google.maps.event.addListener marker "click" 
          (fn []
            (set! (.-hash js/location) (str "#" (:id job)))
            ))
        (swap! markers conj marker)
        (.log js/console (.lat coords))))))

(def geocoder (google.maps.Geocoder.))

(defn geocode [location callback]
  (let [request (clj->js {:address location})]
    (.geocode geocoder request callback)))

(defn setup-jobs-list []
  ; key, reference, old state, new state
  (add-watch filtered-jobs :mapper (fn [k r o n]
    (if (not= o n)
      (do
        (doseq [marker @markers]
          (.setMap marker nil))
        
        (reset! markers [])

        (doseq [job n]
          (let [location (:location job)]
            (geocode location (add-marker-callback job))
            ))
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
    (jq/on $job-container :click ".edit-link" nil (fn [e] (.stopPropagation e)))
    (jq/on $job-container :click ".more a" nil (fn [e] (.stopPropagation e))))
	
  (reset! filtered-jobs job-data)
)


;; maps
(defn elem-by-id [id]
  (.getElementById js/document id))

(def map-options (clj->js {:center (google.maps.LatLng. 30 0)
                           :zoom 2
                           :mapTypeId google.maps.MapTypeId.ROADMAP}))

(def mit (google.maps.LatLng. 42.358449 -71.09122))

(jm/ready
 (let [map (elem-by-id "map")
       map2 (elem-by-id "job-location")]
   (if (u/exists? map) (set! gmap (google.maps.Map. map map-options)))
   (if (u/exists? map2) (set! preview-map (google.maps.Map. map2 map-options)))

   (let [preview-marker (make-marker {:map preview-map
                                      :title "You can drag me to the right location." 
                                      :position mit
                                      :draggable true})]
     (google.maps.event.addListener preview-marker "dragend" 
                                    (fn []
                                      (.log js/console (.getPosition preview-marker))
                                      ;; here is where we would tweak the lat/lng input
                                      ))
     )
   ))
