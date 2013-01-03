(ns startlabs.models.analytics
  (:use [clj-time.coerce :only [from-date]]
        [datomic.api :only [q db ident] :as d]
        [environ.core :only [env]]
        [startlabs.models.database :only [*conn*]]
        [startlabs.util :only [after-now? stringify-values]])
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [sandbar.stateful-session :as session]
            [startlabs.models.user :as user]
            [startlabs.models.util :as util]))

(defn get-analytics-ent []
  (first (q '[:find ?ent ?user :in $
              :where [?ent :analytics/user ?user]] (db *conn*))))

(defn get-analytics-token []
  (let [user-info (util/map-for-datom (second (get-analytics-ent)) :user)]
    (if (after-now? (:expiration user-info))
      ;; return the current access token if valid
      (:access-token user-info)
      ;; if expired, fetch a new one
      (:access-token (user/refresh-user-with-info user-info)))))

(defn ga [s]
  ;ignore dates and pre-formatted strings
  (if (and (string? s)
           (or (re-find #"^-?ga:" s)
               (re-matches #"\d{4}-\d{2}-\d{2}" s)))
    s
    ; else, go to work
    (let [sname  (name s)
          rsname (str/replace sname #"^-" "")
          neg?   (not= sname rsname)]
      (str (if neg? "-") "ga:" rsname))))

(def analytics-url "https://www.googleapis.com/analytics/v3/data/ga")

(def default-params
  {:ids (env :google-analytics-id)
   :dimensions [:eventAction :date]
   :metrics [:uniqueEvents :totalEvents]
   :sort [:date]})

(defn params-to-string [params]
  (apply merge 
         (for [[k vs] params]
           (let [vs (map ga (flatten [vs]))]
             {k (str/join "," vs)}))))

;; (params-to-string default-params)

(def google-date-format "YYYY-MM-dd")
(def google-date-formatter (tf/formatter google-date-format))

(defn unparse-date [date]
  (tf/unparse google-date-formatter date))

(defn a-month-ago []
  (unparse-date (t/minus (t/now) (t/months 1))))

(defn analytics-link [job-id start-date end-date]
  (let [access-token  (get-analytics-token)
        custom-params  {:filters    [(str "ga:eventLabel==" job-id)]
                        :start-date [start-date]
                        :end-date   [end-date]}]
    (str analytics-url "?access_token=" access-token "&"
       (client/generate-query-string 
        (params-to-string (merge default-params custom-params))))))

(defn analytics-for-job [job-id & [start-date end-date]]
  (let [start-date (or start-date (a-month-ago))
        end-date   (or end-date (unparse-date (t/now)))
        link       (analytics-link job-id start-date end-date)]
    (let [response (client/get link {:as :json})]
      (if (= (:status response) 200)
        (:body response)
        []))))

;; (pprint (analytics-for-job "3bfd1881-0909-4ab7-80d0-be3d3b775a49"))

;; this is brittle, and will require revision if we tweak the
;; analytics query to include more stats...
(defn data-map
  "Convert columns from the analytics api into an intermediate
   hash-map, keyed by date."
  [data]
  (let [rows (:rows data)
        m (atom {})]
    (doseq [[event date unique total] rows]
      (let [event-map {(str (str/capitalize event) " Unique") (Integer. unique)
                       (str (str/capitalize event) " Total")  (Integer. total)}]
        (if-let [elem (get @m date)]
          (do
            (swap! m assoc date (merge elem event-map)))
          (do
            (swap! m assoc date event-map)))))
    @m))

(defn data-array [data headers]
  (let [results (data-map data)
        rheaders (rest headers)]
    (cons headers
          (sort-by first
                   (for [[k v] results]
                     (cons k (for [header rheaders]
                               (get v header 0))))))))

(defn google-chart-array [data]
  (data-array data
              ["date" 
               "More Unique" "More Total" 
               "Contact Unique" "Contact Total"]))

(defn google-chart-map 
  "expects job-id & [start-date, end-date]. See analytics-for-job"
  [id & [start end]]
  (let [data (analytics-for-job id start end)]
    {:unique-events (get-in data [:totalsForAllResults :ga:uniqueEvents])
     :total-events  (get-in data [:totalsForAllResults :ga:totalEvents])
     :start-date    (get-in data [:query :start-date])
     :end-date      (get-in data [:query :end-date])
     :table         (google-chart-array data)}))

(defn set-analytics-user
  "Expects a user entity"
  [user]
  (util/create-or-update (first (get-analytics-ent)) 
                         :analytics
                         {:user user}))