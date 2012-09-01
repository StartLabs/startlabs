(ns startlabs.models.user
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [conn]]
        [environ.core :only [env]])
  (:require [clojure.string :as str]
            [oauth.google :as oa]
            [clj-http.client :as client]
            [noir.session :as session]
            [startlabs.util :as util])
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

(def q-user-schema '[:find ?name ?val-type
                     :where [_ :db.install/attribute ?a]
                            [?a :db/ident ?name]
                            [(str ?name) ?nom]
                     		[(re-find #"^:user" ?nom)]
                            [?a :db/valueType ?val-type]])

; the transforming and de/namespacing functions should be their own helper library...
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

(defmethod transform-attr [String :db.type/uri] [attr _]
  (URI. attr))

(defmethod transform-attr [String :db.type/long] [attr _]
  (Integer/parseInt attr))

(defmethod transform-attr [String :user/gender] [attr _]
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

(defn namespace-key [the-ns k]
  (keyword (str (name the-ns) "/" (name k))))

(defn namespace-keys 
  "Converts the keys of the-map to be prefixed with the-ns/
  (namespace-keys :user {:locale 'en'}) returns {:user/locale 'en'}"
  [the-ns the-map]
  (into {} (for [[k v] the-map] 
    {(namespace-key the-ns k) v})))

(defn denamespace-keys
  "The inverse of namespace-keys. Do not have to specify the-ns"
  [the-map]
  (into {} (for [[k v] the-map] 
    {(keyword (last (str/split (name k) #"/")))
     v})))

(defn namespace-and-transform [user-data]
  (let [entity-map        (map-of-entities)
        tranny-user-data  (transform-tx-values (namespace-keys :user user-data) 
                                               entity-map)
        clean-user-data   (into {} (map (fn [[k v]] (if (k entity-map) {k v})) tranny-user-data))]
    clean-user-data))

(defn temp-identify [user-map]
  (assoc user-map :db/id (d/tempid :db.part/user)))

(defn txify-user-data 
  "Convert a map representation of the userinfo response from google into a database transaction"
  [user-data]
  (let [clean-user-data   (namespace-and-transform user-data)
        tx-map            (temp-identify clean-user-data)]
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
    (let [namespaced-key (namespace-key :user k)]
      (ffirst (q [:find '?u
                  :in '$ '?v
                  :where ['?u namespaced-key '?v]] conn-db v)))))

(defn user-with-id [& args]
  (apply user-with-attr :id args))

(defn user-with-email [& args]
  (apply user-with-attr :email args))

(defn entity-map-with-nil-vals []
  (zipmap (keys (map-of-entities)) (repeat nil)))

(defn user-map-for-user
  "Takes as input a single datomic user-id (the output of ffirsting query)"
  [user]
  (let [user-entity (d/entity (db @conn) user)]
    (denamespace-keys (conj (entity-map-with-nil-vals)
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
          (session/flash-put! :message (str "Trouble connecting to the database: " e))))
      ; else return the user's data from the db
      (user-map-for-user user))))

(defn find-user-with-email [email]
  (user-map-for-user (user-with-email email)))

(defn update-user 
  "Expects new-fact-map to *not* already be namespaced with user/"
  [user-id new-fact-map]
  (try
    (let [user          (user-with-id user-id)
          tranny-facts  (namespace-and-transform new-fact-map)
          idented-facts (assoc tranny-facts :db/id user)]
      (d/transact @conn (list idented-facts))
      (session/flash-put! :message (str "Updated info successfully!")))
    (catch Exception e
      (session/flash-put! :message (str "Trouble updating user: " e)))))

(defn update-my-info [new-fact-map]
  (let [user-id (session/get :user_id)]
    (if user-id
      (update-user user-id new-fact-map))))

(defn get-my-info []
  (let [access-token (session/get :access-token)
        user-id      (session/get :user_id)]
    (if (and access-token user-id)
      (try
        (util/stringify-values (find-or-create-user access-token user-id))
        (catch Exception e
          (do
            (session/clear!)
            (session/flash-put! :error "Invalid session. Try logging in again.")
            e) ;return nil if there's an exception
          ))
      ; lack of else clause = implicit nil
      )))