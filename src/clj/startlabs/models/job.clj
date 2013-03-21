(ns startlabs.models.job
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [*conn*]]
        [startlabs.util :only 
         [stringify-values uuid after-now? now-date flash-message!]])
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

(defn job-role-ent [role]
  (util/get-enum-entity (keyword (str "job.role/" role))))

(defn create-job [job-map]
  (let [job-map-with-id (conj job-map {:id (uuid) 
                                       :secret (uuid)
                                       :approved? false})
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

(defn update-job-fields [job-id field-map & [err-msg]]
  (try
    (let [job       (job-with-id job-id)
          field-map (assoc field-map :db/id job)]
      @(d/transact *conn* (list field-map))
      ;; return true to indicate success
      true)
    (catch Exception e
      (flash-message!
       :error
       (if err-msg
         (str err-msg ": " e)
         ;; no default error message provided
         (str "Trouble updating " 
              (str/join ", " (apply name (keys field-map))) ": " e)))
      false)))

(defn update-job
  "Expects new-fact-map to *not* already be namespaced with job/"
  [job-id new-fact-map]
  (try
    (let [job           (job-with-id job-id)
          map-no-secret (dissoc new-fact-map :secret) ;; avoid overwriting the secret
          tranny-facts  (util/namespace-and-transform :job map-no-secret)
          idented-facts (assoc tranny-facts :db/id job)]
      @(d/transact *conn* (list idented-facts))
      (flash-message! :success (str "Updated info successfully!"))
      true)
    (catch Exception e
      (println e)
      (flash-message! :error (str "Trouble updating job: " e))
      false)))

(defn confirm-job [job-id]
  (update-job-fields job-id {:job/confirmed? true}
                     "Trouble confirming job"))

(defn approve-job [job-id]
  (update-job-fields job-id {:job/approved? true
                             :job/post-date (now-date)}
                     "Trouble approving job"))

(defn remove-job [job-id]
  (update-job-fields job-id {:job/removed? true}
                     "Trouble deleting job"))

;; HERE BE DRAGONS.
(defn upcoming-jobs-q [{:keys [show-internships show-fulltime
                               min-company-size max-company-size
                               min-start-date max-start-date
                               min-end-date max-end-date
                               show-approved] :as filters
                        :or {show-internships true
                             show-fulltime true
                             show-approved true}}]
  (let [lmin-start (if min-start-date (tc/to-long min-start-date))
        lmax-start (if max-start-date (tc/to-long max-start-date))
        lmin-end   (if min-end-date   (tc/to-long min-end-date))
        lmax-end   (if max-end-date   (tc/to-long max-end-date))
        truff      `[(true? true)]]
    [:find '?job :where
     ['?job :job/confirmed? true]
     ['?job :job/approved? '?show-approved]
     ['?job :job/company-size '?size]
     ['?job :job/start-date '?start]
     ['?job :job/end-date '?end]
     ['(clj-time.coerce/to-long ?start) '?lstart]
     ['(clj-time.coerce/to-long ?end) '?lend]
     ['(startlabs.util/after-now? ?end)]
     ;;(if (not= true show-fulltime) `[(= ~'?fulltime false)] truff)
     (if min-company-size `[(>= ~'?size ~min-company-size)] truff)
     (if max-company-size `[(<= ~'?size ~max-company-size)] truff)
     (if lmin-start `[(>= ~'?lstart ~lmin-start)] truff)
     (if lmax-start `[(<= ~'?lstart ~lmax-start)] truff)
     (if lmin-end `[(>= ~'?lend ~lmin-end)] truff)
     (if lmax-end `[(<= ~'?lend ~lmax-end)] truff)]))

(defn find-upcoming-jobs 
  "returns all confirmed, non-removed jobs whose start dates 
   are after a certain date"
  [filters]
  {:post [(nil? (:secret (first %)))]}
  (let [jobs      (q (upcoming-jobs-q filters) (db *conn*))
        job-ids   (map first jobs)
        job-maps  (util/maps-for-datoms job-ids :job)
        ;; make sure to remove the secret!
        safe-maps (map #(dissoc % :secret) job-maps)]
    safe-maps))

;; unfortunately, cannot do negations currently in datomic where clauses,
;; so we must specify not removed here.
(def sort-order-fn {0 #(compare %1 %2)
                    1 #(compare %2 %1)})

(defn get-all-jobs [sort-field sort-order filters]
  (map stringify-values
       (sort-by (keyword sort-field)
                (sort-order-fn sort-order)
                (filter #(not= (:removed? %) "true")
                        (find-upcoming-jobs filters)))))

;; COMMENT THIS IN PRODUCTION
(comment
  (defn get-all-jobs [sort-field sort-order filters]
    (map stringify-values
         (sort-by (keyword sort-field)
                  (sort-order-fn sort-order)
                  (repeatedly 100 #(u/fake-job))))))

(defn filtered-jobs [query sort-field sort-order filters]
  (let [all-jobs (get-all-jobs sort-field sort-order filters)]
    (if (empty? query)
      all-jobs
      ;; search
      (filter (fn [job]
        (some #(re-find (re-pattern (str "(?i)" query)) %) 
              (map job [:position :company :location])))
        all-jobs))))

;; whitelist

(defn get-whitelist []
  (let [whitelist (q '[:find ?ent ?whitelist
                       :where [?ent :joblist/whitelist ?whitelist]]
                     (db *conn*))]
    (first whitelist)))

(defn update-whitelist [whitelist]
  (util/create-or-update (first (get-whitelist))
                         :joblist
                         {:whitelist whitelist}))
