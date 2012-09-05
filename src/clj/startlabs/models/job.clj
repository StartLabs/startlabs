(ns startlabs.models.job
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [conn]]
        [startlabs.util :only [stringify-values]])
  (:require [clojure.string :as str]
            [oauth.google :as oa]
            [clj-http.client :as client]
            [noir.session :as session]
            [startlabs.models.util :as util])
  (:import java.net.URI))

(defn job-fields []
  (util/map-of-entity-tuples :job))

(defn create-job [job-map]
  ; might need to conj :confirmed? false
  (let [tx-data (util/txify-new-entity :job job-map)]
    (d/transact @conn tx-data)))

(defn find-upcoming-jobs 
  "returns all jobs whose start dates are after a certain date"
  []
  (let [jobs     (q '[:find ?job :where [?job :job/position _]] (db @conn))
        job-ids  (map first jobs)
        job-maps (util/maps-for-datoms job-ids :job)]
    (map stringify-values job-maps)))