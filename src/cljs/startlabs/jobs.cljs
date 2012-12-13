(ns startlabs.jobs
  (:use [jayq.core :only [$]]
        [jayq.util :only [clj->js]]
        [singult.core :only [render]]
        [c2.core :only [unify]]
        [startlabs.views.jobx :only [job-list job-card markdownify]])

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


(defn update-job-card []
  (let [job-map (u/form-to-map ($ "#job-form"))]
    (.html ($ "#job-preview")
           (render (job-card job-map false))))
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
  (fn []
    (fm/remote (jobsearch query) [results]
               (let [$job-list ($ "#job-list")
                     parent (.parent $job-list)]
                 (reset! filtered-jobs (:jobs results))
                 (.remove $job-list)
                 (.html parent (:html results))))))

(defn add-marker-callback [result status]
  (if (= status google.maps.GeocoderStatus.OK)
    (let [coords (.-location (.-geometry (nth result 0)))
          marker (google.maps.Marker. (clj->js {:position coords :map gmap}))]
      (.log js/console (.lat coords)))))

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

        (doseq [job n]
          (let [location (:location job)]
            (geocode location add-marker-callback)
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
    (jq/on $job-container :click ".more a" nil (fn [e] (.stopPropagation e))))
	
  (reset! filtered-jobs job-data)
)


;; maps
(defn elem-by-id [id]
  (.getElementById js/document id))

(def map-options (clj->js {:center (google.maps.LatLng. 30 0)
                           :zoom 2
                           :mapTypeId google.maps.MapTypeId.ROADMAP}))

(jm/ready
 (let [map (elem-by-id "map")]
   (set! gmap (google.maps.Map. map map-options))
   ))
