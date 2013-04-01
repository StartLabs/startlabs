(ns startlabs.jobs
  (:use [jayq.core :only [$]]
        [dommy.template :only [node]]
        [startlabs.views.job-list 
         :only [job-list job-card markdownify
                ordered-job-keys
                required-job-keys
                visible-job-keys]])

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
  (google.maps.Marker. (clj->js options)))

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

(defn lat-lng [lat lng]
  (google.maps.LatLng. (js/parseFloat lat) (js/parseFloat lng)))

(def map-options (clj->js {:center (lat-lng 30 0)
                           :zoom 2
                           :mapTypeId google.maps.MapTypeId.ROADMAP}))

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
(def query-map (atom {:q "" :sort-field "company"}))

(defn job-with-id [id]
  (first 
    (filter 
      (fn [job] (= (:id job) id))
      job-data)))

(defn toggle-job-details [e]
  (.preventDefault e)
  (this-as this
           (let [$this ($ this)]
             (-> $this (.find ".more") .toggle)
             (-> $this (.find ".read") .toggle)
             (let [$chk (.find $this ".checkbox")
                   new-val (not (.prop $chk "checked"))]
               (.prop $chk "checked" new-val)
               (.val (.siblings $chk "input[type=hidden]") new-val)))))

(defn drop-nil-pairs [m]
  (into {} (filter second m)))

(defn find-jobs []
  (let [$job-list ($ "#job-list")
        parent    (.parent $job-list)
        params    (js/jQuery.param
                   (clj->js (drop-nil-pairs @query-map)))]
    (jq/ajax (str "/jobs.edn?" params)
             {:contentType :text/edn
              :success (fn [data status xhr]
                         (let [new-list (job-list data)]
                           (reset! filtered-jobs (:jobs data))
                           (.remove $job-list)
                           (.html parent (node new-list))
                           ;; why do all of the js hiccup libs
                           ;; fail at literal html?
                           (doseq [elem ($ ".job-info .description")]
                             (let [$elem ($ elem)]
                               (.html $elem (.text $elem))))))})))

(defn add-jobs-marker [job] 
  (fn [coords]
    (let [marker  (make-job-marker job gmap coords)
          job-sel (str "#" (:id job))]
      (google.maps.event.addListener marker "click" (fn []
        (.trigger ($ (str job-sel " .job-info")) "click")
        (set! (.-hash js/location) job-sel)
        ;; animate highlight
        (let [$job-sel ($ job-sel)]
          (.addClass $job-sel "highlighted")
          (js/setTimeout #(.removeClass $job-sel "highlighted") 5000))))
      (swap! markers conj marker))))

(defn setup-find-jobs [k r o n]
  (if (not= o n)
    (do
      (js/clearTimeout search-timeout)
      (set! search-timeout (js/setTimeout find-jobs 500)))))

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
            ((add-jobs-marker job) coords)))))))

  (add-watch query-map :query setup-find-jobs)

  (let [$map-box ($ "#map-box")]
    (jq/on $map-box :keyup "#job-search" 
           (fn [e]
             ;filter jobs as you search
             (this-as job-search
                      (swap! query-map assoc :q
                             (str/trim (jq/val ($ job-search)))))))

    (jq/on $map-box :click "#map-toggle" 
           (fn [e]
             (.preventDefault e)
             (.toggle ($ "#map")))))

  (let [$filter ($ "#filter")]
    (jq/on ($ "#role-buttons") :click "a" 
           (fn [e]
             (.preventDefault e)
             (this-as this
                      (let [$this ($ this)
                            name (.attr $this "id")
                            val (if (.hasClass $this "active") false true)]
                        (.val ($ (str "input[name='" name "']")) val)))))

    (jq/on $filter :submit "form" 
           (fn [e]
             (.preventDefault e)
             (.modal $filter "hide")
             (this-as this
                      (let [$this ($ this)
                            body (.serialize $this)]
                        (jq/ajax (.attr $this "action")
                                 {:data body
                                  :success (fn [data status xhr]
                                             (if (= status "success")
                                               (find-jobs)
                                               (u/log (str "Error: " status))))
                                  :type "POST"}))))))

  (jq/on ($ "#sort") :click "li a"
         (fn [e]
           (.preventDefault e)
           (.removeClass ($ "#sort li") "active")
           (this-as this
                    (let [$this ($ this)
                          field (.data $this "field")]
                      (.addClass (.parent $this "li") "active")
                      (swap! query-map assoc :sort-field field)))))

  (jq/on ($ "#sort-order") :click 
         (fn [e]
           (this-as this
                    (let [$this         ($ this)
                          current-order (.data $this "order")
                          new-order     (mod (inc current-order) 2)]
                      (.preventDefault e)
                      (.data $this "order" new-order)
                      (-> (.children $this "span")
                          (.toggleClass "hidden"))
                      (swap! query-map assoc :sort-order new-order)))))
  
  (jq/on ($ "#job-subscribe") :click ".close"
         (fn [e]
            (jq/ajax "jobs/hide-subscribe"
                     {:success (fn [data status xhr]
                                 (u/log data))})))

  (reset! filtered-jobs job-data))

(defn setup-job-container []
  (let [$job-container ($ "#job-container")]
    (jq/on $job-container :click ".job-info" toggle-job-details)

    (jq/on $job-container :click ".read"
           (fn [e]
             (.preventDefault e)
             (this-as this
                      (-> ($ this) 
                          (.parents ".job-info")
                          (.trigger "click")))))

    (jq/on $job-container :click ".btn-danger"
           (fn [e]
             (.preventDefault e)
             (this-as this
                     (-> ($ this)
                         (.attr "href") $ (.modal "show")))))

    (jq/on $job-container :click "a, button"
           (fn [e] (.stopPropagation e)))

    (jq/on $job-container :click "form button"
           (fn [e]
             (this-as this
                      (.submit (.parent ($ this))))))))


;;
;; JOB ANALYTICS
;;

(def analytics-data (atom {}))
(def analytics-table (atom []))


;; looks like I need to separately specify the columns and rows. No biggie.
(defn draw-chart [& args]
  (let [$elem    ($ "#analytics-chart")
       ; need $content because analytics-chart initially has no width
        $content ($ "#content") 
        table    @analytics-table
        cols     (first table)
        rows     (clj->js (rest table))
        data     (google.visualization.DataTable.)
        options  (clj->js {:title  "Click Events by Date"
                           :width  (.width $content)
                           :height (.height $elem)})
        chart   (google.visualization.LineChart. (first $elem))]
    (doseq [col cols]
      (.addColumn data (if (= col "date") "date" "number") col))
    (.addRows data rows)
    (.draw chart data options)))

(defn datify [table]
  (cons (first table) ;; extract the headers
        (for [[date & rest] (rest table)]
          ;; months in javascript are zero indexed, wtf.
          (let [date-obj (js/Date. (js/parseInt (.substring date 0 4) 10)
                                   (dec (js/parseInt (.substring date 4 6) 10))
                                   (js/parseInt (.substring date 6 8) 10))]
            (cons date-obj rest)))))

(defn reset-analytics! [data]
  (reset! analytics-data data))

(defn render-fail [msg]
  (
   [:div#ajax-fail.alert.alert-error
    [:button.close {:type "button" :data-dismiss "alert"} "x"]
    [:strong "Error: "] msg]))

(defn check-for-failure [xhr status]
  (.remove ($ "#ajax-fail"))
  (if (not= status "success")
    (do
      (.prepend ($ "#content")
        (render-fail (str "Unable to update analytics. "
                          "Make sure the start and end dates are valid."))))))

(defn setup-job-analytics []                                
  (jq/on ($ "#analytics") :changeDate "#a-start-date, #a-end-date" 
    (fn [e]
      (this-as this
        (let [$form  (.parents ($ this) "form")
              action (.attr $form "action")
              url (str action "?" (.serialize $form))]
          (jq/ajax url
                   {:contentType :text/edn
                    :success (fn [data status xhr]
                               (reset-analytics! data))
                    :complete check-for-failure})))))

  (add-watch analytics-data :redraw-analytics
             (fn [k r o n]
               (doseq [field [:unique-events :total-events]]
                 (let [$elem ($ (str "#" (name field)))
                       value (field n)]
                   (.text $elem value)))
               (reset! analytics-table (datify (:table n)))))

  (add-watch analytics-table :redraw-chart draw-chart)

  (let [initial-data (reader/read-string (.text ($ "#analytics-data")))]
    (google.load "visualization" "1.0" 
                 (clj->js {:packages ["corechart"]
                           :callback (fn []
                                       (reset-analytics! initial-data)
                                       (.resize ($ js/window) draw-chart))}))))



;;
;; JOB SUBMIT
;;

;; submit globals
(def ^:dynamic preview-map nil)
(def ^:dynamic preview-marker nil)


(defn update-job-card []
  (let [job-map (u/form-to-map ($ "#job-form"))]
    (.html ($ "#job-preview")
           (node (job-card job-map false))))
  ; singult is escaping the generated markdown :(
  (.html ($ "#job-preview .description")
         (markdownify (.val ($ "#description")))))

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
                         (.trigger $inp "change"))))
                 
                   (if (= (.val $btn) (.val $inp))
                     (.addClass $btn "active")))))))))))

(defn update-preview-marker [coords]
  (.setPosition preview-marker coords))

(defn update-location []
  (let [location (.val ($ "#location"))]
    (geocode location (grab-coords update-preview-marker))))

(defn update-visible-fields
  "Update the visible job fields based on the currently selected role"
  []
  (let [role (keyword (.val ($ "#role")))
        visible-keys (visible-job-keys role)
        required-keys (required-job-keys role)]
    (doseq [field ordered-job-keys]
      (let [$elem (-> ($ (str "#" (name field)))
                      (.parents "tr"))]
        (if (contains? visible-keys field)
          (.show $elem)
          (.hide $elem))
        (if (contains? required-keys field)
          (.addClass $elem "required")
          (.removeClass $elem "required"))))))

;; (.change ($ "#role") update-visible-fields)

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
  
  (setup-radio-buttons)
  (update-visible-fields)

  (let [$job-form ($ "#job-form")]
    (jq/on $job-form [:keyup :blur :change] "input, textarea" update-job-card)
    (.change ($ "#role") update-visible-fields)
    (jq/on $job-form :blur "#location" update-location)))