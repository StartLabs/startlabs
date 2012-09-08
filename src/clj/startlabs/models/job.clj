(ns startlabs.models.job
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [conn]]
        [startlabs.util :only [stringify-values]])
  (:require [clojure.string :as str]
            [oauth.google :as oa]
            [clj-http.client :as client]
            [noir.session :as session]
            [startlabs.models.util :as util]
            [hiccup.core :as h])
  (:import java.net.URI))

(defn job-fields []
  (util/map-of-entity-tuples :job))

;; modify this to @() deref, then resolve tempid to real id, then retun map with real id conj'd.
(defn create-job [job-map]
  ; might need to conj :confirmed? false
  (let [job-map-with-id (conj job-map {:id (util/uuid)})
        tx-data         (util/txify-new-entity :job job-map)]
    @(d/transact @conn tx-data)
    job-map-with-id))

(defn job-with-id [job-id]
  (util/elem-with-attr :job/id job-id))

(defn confirm-job [job-id]
  (try
    (let [job (job-with-id job-id)
          confirm-map {:db/id job :job/confirmed? true}]
      (d/transact @conn (list confirm-map))
      true)
    (catch Exception e
      (session/flash-put! :message [:error (str "Trouble confirming job: " e)])
      false)))

(defn find-upcoming-jobs 
  "returns all confirmed jobs whose start dates are after a certain date"
  []
  (let [jobs     (q '[:find ?job :where [?job :job/position _]] (db @conn))
        job-ids  (map first jobs)
        job-maps (util/maps-for-datoms job-ids :job)]
    (map stringify-values job-maps)))