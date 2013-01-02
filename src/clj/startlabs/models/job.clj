(ns startlabs.models.job
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [*conn*]]
        [startlabs.util :only [stringify-values uuid after-now?]])
  (:require [clojure.string :as str]
            [clj-http.client :as client]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [sandbar.stateful-session :as session]
            [oauth.google :as oa]
            [startlabs.models.util :as util])
  (:import java.net.URI))

(defn job-fields []
  (util/map-of-entity-tuples :job))

;; modify this to @() deref, then resolve tempid to real id, then retun map with real id conj'd.
(defn create-job [job-map]
  ; might need to conj :confirmed? false
  (let [job-map-with-id (conj job-map {:id (uuid) :secret (uuid)})
        tx-data         (util/txify-new-entity :job job-map-with-id)]
    @(d/transact *conn* tx-data)
    job-map-with-id))

(defn job-with-id [job-id]
  (util/elem-with-attr :job/id job-id))

(defn job-secret [job-id]
  (ffirst (q '[:find ?secret :in $ ?id :where [?job :job/id ?id]
                                              [?job :job/secret ?secret]] (db *conn*) job-id)))

(defn job-map [job-id]
  (let [original-map (util/map-for-datom (job-with-id job-id) :job)]
    (stringify-values original-map)))

(defn update-job-field [job-id field value & [err-msg]]
  (try
    (let [job       (job-with-id job-id)
          field-map {:db/id job field value}]
      (d/transact *conn* (list field-map))
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
          map-no-secret (dissoc new-fact-map :secret) ;; avoid overwriting the secret
          tranny-facts  (util/namespace-and-transform :job map-no-secret)
          idented-facts (assoc tranny-facts :db/id job)]
      (d/transact *conn* (list idented-facts))
      (session/flash-put! :message [:success (str "Updated info successfully!")])
      true)
    (catch Exception e
      (session/flash-put! :message [:error (str "Trouble updating job: " e)])
      false)))

(defn confirm-job [job-id]
  (update-job-field job-id :job/confirmed? true "Trouble confirming job"))

(defn remove-job [job-id]
  (update-job-field job-id :job/removed? true "Trouble deleting job"))

;; HERE BE DRAGONS.
(defn upcoming-jobs-q [{:keys [show-internships show-fulltime
                               min-company-size max-company-size
                               min-start-date max-start-date
                               min-end-date max-end-date]
                        :or {show-internships true
                             show-fulltime true}}]

  (let [lmin-start (if min-start-date (tc/to-long min-start-date))
        lmax-start (if max-start-date (tc/to-long max-start-date))
        lmin-end   (if min-end-date   (tc/to-long min-end-date))
        lmax-end   (if max-end-date   (tc/to-long max-end-date))]
    `[:find ~'?job :where 
      [~'?job :job/confirmed? true]
      [~'?job :job/company_size ~'?size]
      [~'?job :job/fulltime? ~'?fulltime]
      [~'?job :job/start_date ~'?start]
      [~'?job :job/end_date ~'?end]
      [(tc/to-long ~'?start) ~'?lstart]
      [(tc/to-long ~'?end) ~'?lend]
      ~(if (not= true show-internships) `[(= ~'?fulltime true)] `[(true? true)])
      ~(if (not= true show-fulltime) `[(= ~'?fulltime false)] `[(true? true)])
      ~(if min-company-size `[(>= ~'?size ~min-company-size)] `[(true? true)])
      ~(if max-company-size `[(<= ~'?size ~max-company-size)] `[(true? true)])
      ~(if lmin-start `[(>= ~'?lstart ~lmin-start)] `[(true? true)])
      ~(if lmax-start `[(<= ~'?lstart ~lmax-start)] `[(true? true)])
      ~(if lmin-end `[(>= ~'?lend ~lmin-end)] `[(true? true)])
      ~(if lmax-end `[(<= ~'?lend ~lmax-end)] `[(true? true)])
      [(after-now? ~'?end)]]))

(defn find-upcoming-jobs 
  "returns all confirmed, non-removed jobs whose start dates 
   are after a certain date"
  [filters]
  (let [jobs      (q (upcoming-jobs-q filters) (db *conn*))
        job-ids   (map first jobs)
        job-maps  (util/maps-for-datoms job-ids :job)
        ;; make sure to remove the secret!
        safe-maps (map #(dissoc % :secret) job-maps)]

    (map stringify-values safe-maps)))


;; whitelist

(defn update-whitelist [whitelist]
  (let [tx-data (util/txify-new-entity :joblist {:whitelist whitelist 
                                                 :updated (tc/to-date (t/now))})]
    @(d/transact *conn* tx-data)
    whitelist))

(defn get-current-whitelist
  "returns all confirmed jobs whose start dates are after a certain date"
  []
  (let [whitelists (q '[:find ?when ?wl :in $
                        :where [?whitelist :joblist/whitelist ?wl]
                               [?whitelist :joblist/updated ?when]] (db *conn*))
        whitelist  (last (sort-by first whitelists))]
    (or (last whitelist) "")))


