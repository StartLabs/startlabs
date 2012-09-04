(ns startlabs.models.user
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [conn]]
        [environ.core :only [env]]
        [startlabs.util :only [stringify-values]])
  (:require [clojure.string :as str]
            [oauth.google :as oa]
            [clj-http.client :as client]
            [noir.session :as session]
            [startlabs.models.util :as util]))

(def redirect-url   "http://localhost:8000/oauth2callback")

(def googleapis-url "https://www.googleapis.com/oauth2/v1/")

(defn scope-strings [scopes origin]
  (apply concat ; flatten the output
    (for [[k v] scopes]
      (map #(str origin (name k) "." (name %1)) v))))

(defn joined-scope-strings [scopes origin]
  (str/join " " (scope-strings scopes origin)))

(defn get-login-url []
  (let [scopes {:userinfo [:email :profile]}
        scope-origin "https://www.googleapis.com/auth/"]
    (oa/oauth-authorization-url (env :oauth-client-id) redirect-url 
      :scope (joined-scope-strings scopes scope-origin) 
      :response_type "token")))

(defn get-request-with-token [url access-token]
  (let [response (client/get url {:query-params {"access_token" access-token} :as :json})]
    (:body response)))

(defn get-token-info [access-token]
  (let [tokeninfo-url (str googleapis-url "tokeninfo")
        response-body (get-request-with-token tokeninfo-url access-token)]
    response-body))

(def ns-matches-user '[[(ns-matches ?ns)
                        [(= "user" ?ns)]]])

(defn namespace-and-transform [tx-data]
  (let [entity-map        (util/map-of-entities ns-matches-user)
        tranny-user-data  (util/transform-tx-values (util/namespace-keys :user tx-data) 
                                                    entity-map)
        ; only add attributes that are present in the schema
        clean-user-data   (into {} (map (fn [[k v]] (if (k entity-map) {k v})) tranny-user-data))]
    clean-user-data))

(defn txify-user-data 
  "Convert a map representation of the userinfo response from google into a database transaction"
  [user-data]
  (let [clean-user-data   (namespace-and-transform user-data)
        tx-map            (util/temp-identify clean-user-data)]
    [tx-map]))

(defn create-user [access-token user-id]
  "Need to make this more flexible: should handle the case of new fields"
  (let [userinfo-url (str googleapis-url "userinfo")
        user-data    (get-request-with-token userinfo-url access-token)
        tx-data      (txify-user-data user-data)]
    (d/transact @conn tx-data)))

(defn user-with-attr
  ([k v] (user-with-attr k v (db @conn)))
  ([k v conn-db]
    (let [namespaced-key (util/namespace-key :user k)]
      (ffirst (q [:find '?u
                  :in '$ '?v
                  :where ['?u namespaced-key '?v]] conn-db v)))))

(defn user-with-id [& args]
  (apply user-with-attr :id args))

(defn user-with-email [& args]
  (apply user-with-attr :email args))

(defn user-map-for-user
  "Takes as input a single datomic user-id (the output of ffirsting query)"
  [user]
  (let [user-entity (d/entity (db @conn) user)]
    (util/denamespace-keys (conj (util/entity-map-with-nil-vals ns-matches-user)
                                 (into {} user-entity)))))

(defn find-or-create-user
  "Finds the user in the database or creates a new one based on their user-id.
   Returns a hash-map of the user's data, with the user/ namespace stripped out."
  [access-token user-id]
  (let [conn-db      (db @conn)
        user         (user-with-id user-id conn-db)] ; should be a vector with one entry
    (if (not user)
      (try
        @(create-user access-token user-id) ; dereferencing forces the transaction to return
        (find-or-create-user access-token user-id) ; now we should be able to find the user
        (catch Exception e ; transaction may fail, returning an ExecutionException
          (session/flash-put! :message [:error (str "Trouble connecting to the database: " e)])))
      ; else return the user's data from the db
      (user-map-for-user user))))

(defn find-user-with-email [email]
  (user-map-for-user (user-with-email email)))

(defn find-all-users []
  (let [users (q '[:find ?user :where [?user :user/id _]] (db @conn))]
    (map #(stringify-values (user-map-for-user (first %))) users)))

(defn username [person-info]
  (first (str/split (:email person-info) #"@")))

(defn update-user 
  "Expects new-fact-map to *not* already be namespaced with user/"
  [user-id new-fact-map]
  (try
    (let [user          (user-with-id user-id)
          tranny-facts  (namespace-and-transform new-fact-map)
          idented-facts (assoc tranny-facts :db/id user)]
      (d/transact @conn (list idented-facts))
      (session/flash-put! :message [:success (str "Updated info successfully!")]))
    (catch Exception e
      (session/flash-put! :message [:error (str "Trouble updating user: " e)]))))

(defn update-my-info [new-fact-map]
  (let [user-id (session/get :user_id)]
    (if user-id
      (update-user user-id new-fact-map)
      (session/flash-put! :message [:error "Please log back in"]))))

(defn get-my-info []
  (let [access-token (session/get :access-token)
        user-id      (session/get :user_id)]
    (if (and access-token user-id)
      (try
        (stringify-values (find-or-create-user access-token user-id))
        (catch Exception e
          (do
            (session/clear!)
            (session/flash-put! :error "Invalid session. Try logging in again.")
            e) ;return nil if there's an exception
          ))
      ; lack of else clause = implicit nil
      )))