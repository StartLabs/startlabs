(ns startlabs.models.user
  (:use [datomic.api :only [q db ident] :as d]
        [environ.core :only [env]]
        [oauth.io :only [request]]
        [startlabs.models.database :only [*conn*]]
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
  (let [scopes {:userinfo [:email :profile] :analytics [:readonly]}
        scope-origin "https://www.googleapis.com/auth/"]
    (oa/oauth-authorization-url (env :oauth-client-id) redirect-url 
      :scope (joined-scope-strings scopes scope-origin)
      :response_type "code"
      :access_type "offline"
      :state referer)))

(defn get-user-info [access-token]
  (oa/user-info (oa/oauth-client access-token)))

(defn get-access-token [code]
  (oa/oauth-access-token (env :oauth-client-id)
                         (env :oauth-client-secret)
                         code
                         redirect-url))

(defn expiration-to-date
  "Takes the expires_in response from access token requests
   and converts it to a date."
  [seconds]
  (t/plus (t/now) (t/secs seconds)))

;; this should eventually be rolled into oauth-clj
(defn oauth-refresh-token
  "Obtain the OAuth access token from a refresh token."
  [url client-id client-secret refresh-token]
  (request
   {:method :post
    :url url
    :form-params
    {"client_id" client-id
     "client_secret" client-secret
     "refresh_token" refresh-token
     "grant_type" "refresh_token"}}))

(defn refresh-access-token [refresh-token]
  (oauth-refresh-token oa/*oauth-access-token-url*
                       (env :oauth-client-id)
                       (env :oauth-client-secret)
                       refresh-token))

(defn create-user [user-info]
  "Need to make this more flexible: should handle the case of new fields"
  (try
    (let [tx-data (util/txify-new-entity :user user-info)]
      @(d/transact *conn* tx-data)
      user-info)

    (catch Exception e
      (session/flash-put! :message [:error (str "Trouble creating user: " e)]))))

(defn user-with-id [& args]
  (apply util/elem-with-attr :user/id args))

(defn user-with-email [& args]
  (apply util/elem-with-attr :user/email args))

;; these actually return maps with attributes
(defn find-user-with-email [email]
  (util/map-for-datom (user-with-email email) :user))

(defn find-user-with-id [id]
  (util/map-for-datom (user-with-id id) :user))

(defn update-user
  "Expects new-fact-map to *not* already be namespaced with user/"
  [user-id new-fact-map]
  (try
    (let [user          (user-with-id user-id)
          tranny-facts  (util/namespace-and-transform :user new-fact-map)
          idented-facts (assoc tranny-facts :db/id user)]
      @(d/transact *conn* (list idented-facts)))
    (catch Exception e
      (session/flash-put! :message [:error (str "Trouble updating user: " e)]))))

(defn refresh-user [user-map oauth-map]
  (let [real-refresh-token (or (:refresh-token oauth-map)
                               (:refresh-token user-map)
                               "")
        expiration-date    (expiration-to-date (:expires-in oauth-map))
        update-map         {:access-token (:access-token oauth-map)
                            :refresh-token real-refresh-token
                            :expiration expiration-date}]
    (update-user (:id user-map) update-map)
    (merge user-map update-map)))

(defn refresh-user-with-info [user-info]
  (let [refresh-token  (:refresh-token user-info)
        new-oauth-map  (refresh-access-token refresh-token)]
    (refresh-user user-info new-oauth-map)))

(defn find-or-create-user
  "Finds the user in the database or creates a new one based on their user-id.
   Returns a hash-map of the user's data, with the user/ namespace stripped out."
  [user-info oauth-map]
  (let [user-id (:id user-info)
        user    (user-with-id user-id)] ;user should be a vector with one entry
    (if (not user)
      (create-user user-info))
    ; now return the user's data from the db, 
    ; but update the access token and expiration first
    (let [user-info (find-user-with-id user-id)]
      (refresh-user user-info oauth-map))))

(defn verify-code [code]
  (let [oauth-map     (get-access-token code)
        access-token  (:access-token oauth-map)
        user-info     (get-user-info access-token)
        lab-member?   (and (= (last (str/split (:email user-info) #"@")) 
                              "startlabs.org")
                           (:verified-email user-info))]
    (if lab-member?
      (do
        (find-or-create-user user-info oauth-map)
        (doseq [k [:id :email]]
          (session/session-put! k (k user-info)))))
    lab-member?))

(def q-ungraduated-users
  '[:find ?user 
    :in $ ?current-year
    :where  [?user :user/id _]
            [?user :user/graduation-year ?grad-year]
            [(<= ?current-year ?grad-year)]])

(defn find-all-users []
  (let [users     (q q-ungraduated-users
                     (db *conn*) (t/year (t/now)))
        user-ids  (map first users)
        user-maps (util/maps-for-datoms user-ids :user)]
    (map stringify-values user-maps)))

(defn username [person-info]
  (first (str/split (:email person-info) #"@")))

(defn update-my-info [new-fact-map]
  (let [user-id (session/session-get :id)]
    (if user-id
      (do
        (update-user user-id new-fact-map)
        (session/flash-put! :message [:success (str "Updated info successfully!")]))
      
      (session/flash-put! :message [:error "Please log back in"]))))

(defn get-my-info []
  (try
    (if-let [user-id (session/session-get :id)]
      (stringify-values (find-user-with-id user-id))
      nil)

    (catch Exception e
      nil)))

(defn logged-in? []
  (not (nil? (get-my-info))))