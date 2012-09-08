(ns startlabs.models.job
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [conn]]
        [startlabs.util :only [stringify-values]]
        [startlabs.views :only [job-email-body]]
        [environ.core :only [env]])
  (:require [clojure.string :as str]
            [oauth.google :as oa]
            [clj-http.client :as client]
            [noir.session :as session]
            [startlabs.models.util :as util]
            [postal.core :as postal]
            [hiccup.core :as h])
  (:import java.net.URI))

(defn job-fields []
  (util/map-of-entity-tuples :job))

(defn send-confirmation-email [job-map]
  (postal/send-message ^{:host (env :email-host)
                         :user (env :email-user)
                         :pass (env :email-pass)
                         :ssl  :yes}
    {:from    "jobs@startlabs.org"
     :to      (:email job-map)
     :subject "Confirm your StartLabs Job Listing"
     :body [{:type    "text/html; charset=utf-8"
             :content (job-email-body job-map)}]}))

;; modify this to @() deref, then resolve tempid to real id, then retun map with real id conj'd.
(defn create-job [job-map]
  ; might need to conj :confirmed? false
  (let [job-map-with-id (conj job-map {:id (util/uuid)})
        tx-data         (util/txify-new-entity :job job-map)]
    @(d/transact @conn tx-data)
    job-map-with-id))

(defn find-upcoming-jobs 
  "returns all jobs whose start dates are after a certain date"
  []
  (let [jobs     (q '[:find ?job :where [?job :job/position _]] (db @conn))
        job-ids  (map first jobs)
        job-maps (util/maps-for-datoms job-ids :job)]
    (map stringify-values job-maps)))