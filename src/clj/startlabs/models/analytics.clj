(ns startlabs.models.analytics
  (:use [clj-time.coerce :only [from-date]]
        [datomic.api :only [q db ident] :as d]
        [environ.core :only [env]]
        [startlabs.models.database :only [*conn*]]
        [startlabs.util :only [stringify-values]])
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [sandbar.stateful-session :as session]
            [startlabs.models.user :as user]
            [startlabs.models.util :as util]))

(defn get-analytics-ent []
  (first (q '[:find ?ent ?user :in $
              :where [?ent :analytics/user ?user]] (db *conn*))))

(defn get-analytics-token []
  (let [user-info (util/map-for-datom (second (get-analytics-ent)) :user)]
    ;; if the current access token has expired, fetch a new one
    (if (t/before? (from-date (:expiration user-info)) (t/now))
      (:access-token (user/refresh-user-with-info user-info))
      ;; otherwise, just return the current one
      (:access-token user-info))))

(defn set-analytics-user
  "Expects a user entity"
  [user]
  (try
    (if-let [[entity user] (get-analytics-ent)]
      ;; if the analytics user has been set previously
      (let [fact         (util/namespace-and-transform :analytics {:user user})
            idented-fact (assoc fact :db/id entity)]
        @(d/transact *conn* idented-fact))
      ;; otherwise, create a new entity
      (let [new-fact (util/txify-new-entity :analytics {:user user})]
        @(d/transact *conn* new-fact)))
    (catch Exception e
      (session/flash-put! :message [:error (str "Trouble setting analytics user: " e)]))))