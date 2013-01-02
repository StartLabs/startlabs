(ns startlabs.models.migrate
  (:require [clojure.string :as str]
            [clj-http.client :as client])
  (:use [clojure.java.io]
        [datomic.api :only [q db entity ident tempid] :as d]
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

;; (do-default-setup)

(defn maps-url [address]
  (str "http://maps.googleapis.com/maps/api/geocode/json?address="
       (url-encode address) "&sensor=false"))

(defn add-geo [job-map]
  (let [{:keys [lat lng]} 
        (get-in (client/get (maps-url "Raleigh, NC") {:as :json}) 
                [:body :results 0 :geometry :location])]
    (assoc job-map :job/latitude lat :job/longitude lng)))

(def the-db (db *conn*))

(defn fix-kw [kw]
  (keyword (namespace kw) 
           (str/replace (name kw) "_" "-")))

(defn fix-kws [ent-map]
  (apply merge (map (fn [[k v]] {(fix-kw k) v}) ent-map)))

(defn elems-with-attr [attr]
  (let [ents (q [:find '?elem :where ['?elem attr '_]] the-db)]
    (map #(assoc (fix-kws (entity the-db (first %))) 
            :db/id (tempid :db.part/user)) ents)))

(defn find-all-users []
  (elems-with-attr :user/id))

(defn find-all-jobs []
  (map add-geo (elems-with-attr :job/id)))

(defn generate-data []
  (vec (concat (find-all-users)
               (find-all-jobs))))

(defn save-entries []
  (let [entries (generate-data)]
    (with-open [wrtr (writer "startlabs-data.dtm")]
      (.write wrtr (str entries)))
    (println "Entries saved.")))

;; (save-entries)