(ns startlabs.models.user
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [conn]])
  (:require [startlabs.secrets :as secrets]
            [clojure.string :as str]
            [oauth.google :as oa]
            [clj-http.client :as client]
            [noir.session :as session])
  (:import java.net.URI))

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
    (oa/oauth-authorization-url secrets/oauth-client-id redirect-url 
      :scope (joined-scope-strings scopes scope-origin) 
      :response_type "token")))

(defn get-request-with-token [url access-token]
  (let [response (client/get url {:query-params {"access_token" access-token} :as :json})]
    (:body response)))

(defn get-token-info [access-token]
  (let [tokeninfo-url (str googleapis-url "tokeninfo")
        response-body (get-request-with-token tokeninfo-url access-token)]
    response-body))

(def q-user-with-id '[:find ?u 
                      :in $ ?id 
                      :where [?u :user/id ?id]])

(def q-user-schema '[:find ?name ?val-type
                     :where [_ :db.install/attribute ?a]
                            [?a :db/ident ?name]
                            [(str ?name) ?nom]
                     		[(re-find #"^:user" ?nom)]
                            [?a :db/valueType ?val-type]])

(defmulti 
  transform-attr 
  "Transforms an attribute to the proper database type,
   dispatching based on the attribute's current type and 
   the desired datomic type.
   Example: (transform-attr 'www.google.com' :db.type/uri)
   will yield <URI 'www.google.com'>"
  (fn [attr attr-type] [(type attr) attr-type])
  :default [String :db.type/string])

(defmethod transform-attr [String :db.type/string] [attr _] attr)

(defmethod transform-attr [String :db.type/uri] [attr attr-type]
  (URI. attr))

(defmethod transform-attr [String :user/gender] [attr attr-type]
  (keyword (str "user.gender/" attr)))

(defn map-of-entities 
  "Converts the set of [:attr-name valueType-entid] pairs returned by querying for q-user-schema 
   into a single map of {:attr-name :valueType-ident ...} pairs"
  []
  (let [conn-db (db @conn)
        schema (q q-user-schema conn-db)]
    (into {} (for [[k v] schema]
                {k (ident conn-db v)}))))

(defn transform-tx-values
  "Takes a map for a pending transaction and transforms the values, 
   dispatching based on current and desired :db/valueType,
   e.g. transforms the string 'http://www.google.com'
   to a proper URI if its corresponding :db/valueType is :db.type/uri.
   If the type is :db.type/ref, dispatches based on the key instead, eg: :user/gender"
  [tx-map entity-map]
  (into {} (for [[k v] tx-map]
    (let [attr-type (k entity-map)
          dereferenced-type (if (= attr-type :db.type/ref)
                              k
                              attr-type)]
      {k (transform-attr v dereferenced-type)}))))

(defn namespace-keys 
  "Converts the keys of the-map to be prefixed with the-ns/
  (namespace-keys {:locale 'en'} :user) returns {:user/locale 'en'}"
  [the-map the-ns]
  (into {} (for [[k v] the-map] 
    {(keyword (str (name the-ns) "/" (name k))) 
     v})))

(defn denamespace-keys
  "The inverse of namespace-keys. Do not have to specify the-ns"
  [the-map]
  (into {} (for [[k v] the-map] 
    {(keyword (last (str/split (name k) #"/")))
     v})))

(defn txify-user-data 
  "Convert a map representation of the userinfo response from google into a database transaction"
  [user-data]
  (let [entity-map        (map-of-entities)
        tranny-user-data  (transform-tx-values (namespace-keys user-data "user") 
                                               entity-map)
        ; remove any keys that don't have corresponding schema
        clean-user-data   (into {} (map (fn [[k v]] (if (k entity-map) {k v})) tranny-user-data))
        tx-map            (assoc clean-user-data :db/id (d/tempid :db.part/user))]
    [tx-map]))

(defn create-user [access-token user-id]
  "Need to make this more flexible: should handle the case of new fields"
  (let [userinfo-url (str googleapis-url "userinfo")
        user-data    (get-request-with-token userinfo-url access-token)
        tx-data      (txify-user-data user-data)]
    (d/transact @conn tx-data)))

(defn find-or-create-user
  "Finds the user in the database or creates a new one based on their user-id"
  [access-token user-id]
  (let [conn-db      (db @conn)
        user         (first (q q-user-with-id conn-db user-id))] ; should be a vector with one entry
    (if (not user)
      (try
        @(create-user access-token user-id) ; force the transaction to return
        (find-or-create-user access-token user-id) ; now we should be able to find the user
        (catch Exception e ; transaction may fail, returning an ExecutionException
          (session/flash-put! :message (str "Trouble connecting to the database: " e))))

      ; else return the user's data from the db
      (let [user-entity (d/entity conn-db (first user))]
        ; include all keys from map-of-entities, plus the filled-out values
        (denamespace-keys (conj (map-of-entities) 
                                (into {} user-entity)))))))

(defn get-my-info []
  (let [access-token (session/get :access-token)
        user-id      (session/get :user_id)]
    (if (and access-token user-id)
      (try
        (find-or-create-user access-token user-id)
        (catch Exception e
          (do
            (session/clear!)
            (session/flash-put! :error "Invalid session. Try logging in again.")
            e) ;return nil if there's an exception
          ))
      ; lack of else clause = implicit nil
      )))