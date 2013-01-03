(ns startlabs.models.migrate
  (:require [clojure.string :as str]
            [clj-http.client :as client])
  (:use [datomic.api :only [q db entity ident tempid transact] :as d]
        [ring.util.codec :only [url-encode]]
        [startlabs.models.database :only [do-default-setup *conn*]]))

;; This file serves to migrate the old database entries,
;; namely users and jobs, to the new database schema.
;; The most important things we do here are:
;; - convert underscores in keywords to dashes for consistency,
;;   e.g. :user/end_date becomes :user/end-date
;;
;; - add latitude and longitude values to jobs, since these
;;   attributes are now required on job creation. We get these
;;   seed values from the google maps http API. See: add-geo

;; Here's the flow: first, grab the backup database.
;; Then call save-entries
;; Then do `lein datomic initialize` to wipe the old database
;; and use the new schema
;; Then call migrate-database

;; write uris as tagged literals

(if (not *conn*)
  (do-default-setup))

(def uri-reader (fn [x] (java.net.URI. x)))

(set! *data-readers* (assoc *data-readers* 'uri uri-reader))

(defmethod print-method java.net.URI
  [^java.net.URI uri ^java.io.Writer w]
  (.write w (str "#uri \"" uri "\"")))

(def the-db (db *conn*))

(defn maps-url [address]
  (str "http://maps.googleapis.com/maps/api/geocode/json?address="
       (url-encode address) "&sensor=false"))

(defn add-geo [job-map]
  (let [{:keys [lat lng]} 
        (get-in (client/get (maps-url (:job/location job-map)) {:as :json})
                [:body :results 0 :geometry :location])]
    (assoc job-map :job/latitude (float lat) :job/longitude (float lng))))

(defn fix-kw [kw]
  (keyword (namespace kw) 
           (str/replace (name kw) "_" "-")))

(defn mapmerge [f m]
  (apply merge (map f m)))

(defn fix-kws [ent-map]
  (mapmerge (fn [[k v]] {(fix-kw k) v}) ent-map))

(defn fix-job-fields [m]
  (let [fulltime? (not (re-find #"(?i)intern" (:job/position m)))]
    (assoc m
      :job/company-size (get m :job/company-size 0)
      :job/fulltime?    (get m :job/fulltime? fulltime?))))

(defn assoc-id [m]
  (assoc m :db/id (tempid :db.part/user)))

(defn elems-with-attr [attr]
  (let [ents (q [:find '?elem :where ['?elem attr '_]] the-db)]
    (map #(->> (first %)
               (entity the-db)
               fix-kws
               assoc-id) ents)))

(defn find-all-users []
  (elems-with-attr :user/id))

;; (find-all-users)

(defn find-all-jobs []
  (map #(->> % add-geo fix-job-fields)
       (elems-with-attr :job/id)))

;; fetch email addresses to contact jobs list people
;; (pprint (map :job/email (elems-with-attr :job/id)))

(defn find-whitelist []
  [(dissoc (first (elems-with-attr :joblist/whitelist))
           :joblist/updated)])

;; (find-whitelist)

(defn generate-data []
  (vec (concat (find-all-users)
               (find-all-jobs)
               (find-whitelist))))

(defn save-entries []
  (let [entries (generate-data)]
    (spit "startlabs-data.dtm" entries)))

;; (save-entries)

(defn migrate-database [file]
  (let [tx-data (read-string (slurp file))]
    @(transact *conn* tx-data)))

;; (migrate-database "startlabs-data.dtm")

;; (pprint (read-string (slurp "startlabs-data.dtm")))