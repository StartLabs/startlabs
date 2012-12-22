(ns startlabs.models.user
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [*conn*]]
        [environ.core :only [env]]
        [startlabs.util :only [stringify-values home-uri]])
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clj-http.client :as client]
            [clj-time.core :as t]
            [sandbar.stateful-session :as session]
            [oauth.google :as oa]
            [startlabs.models.util :as util]))

(def redirect-url  
  (str (home-uri) "/oauth2callback"))

(defn scope-strings [scopes origin]
  (apply concat ; flatten the output
    (for [[k v] scopes]
      (map #(str origin (name k) "." (name %1)) v))))

(defn joined-scope-strings [scopes origin]
  (str/join " " (scope-strings scopes origin)))

(defn get-login-url [referer]
  (let [scopes {:userinfo [:email :profile]}
        scope-origin "https://www.googleapis.com/auth/"]
    (oa/oauth-authorization-url (env :oauth-client-id) redirect-url 
      :scope (joined-scope-strings scopes scope-origin) 
      :response_type "token"
      :state referer)))

(defn get-user-info [access-token]
  (let [response (client/get "https://www.googleapis.com/oauth2/v1/userinfo" 
                             {:query-params {:access_token access-token}}
                             {:as :json})]
    (json/read-str (:body response) :key-fn keyword)))

(defn get-access-token [code]
  (oa/oauth-access-token (env :oauth-client-id)
                         (env :oauth-client-secret)
                         code
                         redirect-url))

(defn create-user [access-token user-id]
  "Need to make this more flexible: should handle the case of new fields"
  (let [user-data (get-user-info access-token)
        tx-data   (util/txify-new-entity :user user-data)]
    (d/transact *conn* tx-data)))

(defn user-with-id [& args]
  (apply util/elem-with-attr :user/id args))

(defn user-with-email [& args]
  (apply util/elem-with-attr :user/email args))

(defn find-or-create-user
  "Finds the user in the database or creates a new one based on their user-id.
   Returns a hash-map of the user's data, with the user/ namespace stripped out."
  [access-token user-id]
  (let [conn-db      (db *conn*)
        user         (user-with-id user-id conn-db)] ; should be a vector with one entry
    (if (not user)
      (try
        @(create-user access-token user-id) ; dereferencing forces the transaction to return
        (find-or-create-user access-token user-id) ; now we should be able to find the user
        (catch Exception e ; transaction may fail, returning an ExecutionException
          (session/flash-put! :message [:error (str "Trouble connecting to the database: " e)])))
      ; else return the user's data from the db
      (util/map-for-datom user :user))))

(defn find-user-with-email [email]
  (util/map-for-datom (user-with-email email) :user))

(def q-ungraduated-users
  '[:find ?user 
    :in $ ?current-year
    :where  [?user :user/id _]
            [?user :user/graduation_year ?grad-year]
            [(<= ?current-year ?grad-year)]])

(defn find-all-users []
  (let [users     (q  q-ungraduated-users
                      (db *conn*) (t/year (t/now)))
        user-ids  (map first users)
        user-maps (util/maps-for-datoms user-ids :user)]
    (map stringify-values user-maps)))

(defn username [person-info]
  (println "INFO: " person-info)
  (first (str/split (:email person-info) #"@")))

(defn update-user
  "Expects new-fact-map to *not* already be namespaced with user/"
  [user-id new-fact-map]
  (try
    (let [user          (user-with-id user-id)
          tranny-facts  (util/namespace-and-transform :user new-fact-map)
          idented-facts (assoc tranny-facts :db/id user)]
      (d/transact *conn* (list idented-facts))
      (session/flash-put! :message [:success (str "Updated info successfully!")]))
    (catch Exception e
      (session/flash-put! :message [:error (str "Trouble updating user: " e)]))))

(defn update-my-info [new-fact-map]
  (let [user-id (session/session-get :user_id)]
    (if user-id
      (update-user user-id new-fact-map)
      (session/flash-put! :message [:error "Please log back in"]))))

(defn get-my-info []
  (let [access-token (session/session-get :access-token)
        user-id      (session/session-get :id)]
    (if (and access-token user-id)
      (try
        (stringify-values (find-or-create-user access-token user-id))
        (catch Exception e
          (do
            (session/destroy-session!)
            (session/flash-put! :error "Invalid session. Try logging in again.")
            e) ;return nil if there's an exception
          ))
      ; lack of else clause = implicit nil
      )))

(defn logged-in? []
  (not (nil? (get-my-info))))