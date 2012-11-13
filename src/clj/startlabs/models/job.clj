(ns startlabs.models.job
  (:use [datomic.api :only [q db ident] :as d]
        [clj-time.core :only [now]]
        [clj-time.coerce :only [to-long to-date]]
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
  (let [job-map-with-id (conj job-map {:id (util/uuid) :secret (util/uuid)})
        tx-data         (util/txify-new-entity :job job-map-with-id)]
    @(d/transact @conn tx-data)
    job-map-with-id))

(defn job-with-id [job-id]
  (util/elem-with-attr :job/id job-id))

(defn job-secret [job-id]
  (ffirst (q '[:find ?secret :in $ ?id :where [?job :job/id ?id]
                                              [?job :job/secret ?secret]] (db @conn) job-id)))

(defn job-map [job-id]
  (let [original-map (util/map-for-datom (job-with-id job-id) :job)]
    (stringify-values original-map)))

(defn update-job-field [job-id field value & [err-msg]]
  (try
    (let [job       (job-with-id job-id)
          field-map {:db/id job field value}]
      (d/transact @conn (list field-map))
      value)
    (catch Exception e
      (session/flash-put! :message [:error 
        (if err-msg
          (str err-msg ": " e)
          (str "Trouble updating " (name field) ": " e))])
      false)))

(defn update-job
  "Expects new-fact-map to *not* already be namespaced with user/"
  [job-id new-fact-map]
  (try
    (let [job           (job-with-id job-id)
          tranny-facts  (util/namespace-and-transform :job new-fact-map)
          idented-facts (assoc tranny-facts :db/id job)]
      (d/transact @conn (list idented-facts))
      (session/flash-put! :message [:success (str "Updated info successfully!")])
      true)
    (catch Exception e
      (session/flash-put! :message [:error (str "Trouble updating job: " e)])
      false)))

(defn confirm-job [job-id]
  (update-job-field job-id :job/confirmed? true "Trouble confirming job"))

(defn remove-job [job-id]
  (update-job-field job-id :job/removed? true "Trouble deleting job"))

(defn after-now?
  "Determines if the provided date is in the future."
  [date]
  (> (to-long date) (to-long (now))))

(defn find-upcoming-jobs 
  "returns all confirmed, non-removed jobs whose start dates are after a certain date"
  []
  (let [jobs      (q '[:find ?job :where [?job :job/confirmed? true]
                                         [?job :job/end_date ?end]
                                         [(startlabs.models.job/after-now? ?end)]] (db @conn))
        job-ids   (map first jobs)
        job-maps  (util/maps-for-datoms job-ids :job)
        ;; make sure to remove the secret!
        safe-maps (map #(dissoc % :secret) job-maps)]

    (map stringify-values safe-maps)))


;; whitelist


(defn update-whitelist [whitelist]
  (let [tx-data (util/txify-new-entity :joblist {:whitelist whitelist :updated (to-date (now))})]
    @(d/transact @conn tx-data)
    whitelist))

(defn get-current-whitelist
  "returns all confirmed jobs whose start dates are after a certain date"
  []
  (let [whitelists (q '[:find ?when ?wl :in $
                        :where [?whitelist :joblist/whitelist ?wl]
                               [?whitelist :joblist/updated ?when]] (db @conn))
        whitelist  (last (sort-by first whitelists))]
    (or (last whitelist) "")))


