(ns startlabs.models.user
  (:use [datomic.api :only [q db] :as d]
        [startlabs.models.database :only [conn]])
  (:require [startlabs.secrets :as secrets]
            [clojure.string :as str]
            [oauth.google :as oa]
            [clj-http.client :as client]
            [noir.session :as session]))

(def redirect-url   "http://localhost:8000/oauth2callback")
(def googleapis-url "https://www.googleapis.com/oauth2/v1/")

(defn scope-strings [scopes origin]
  (apply concat ; flatten the output
    (for [[k v] scopes]
      (map #(str origin (name k) "." (name %1)) v))))

(defn joined-scope-strings [scopes origin]
  (str/join " " (scope-strings scopes origin)))

; pass the current url as the state
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

(def user-with-id '[:find ?u 
                    :in $ ?id 
                    :where [?u :user/id ?id]])

(defn find-or-create-user
  "Finds the user in the database or creates a new one based on their user-id"
  [access-token user-id]
  (let [userinfo-url (str googleapis-url "userinfo")
        conn-db      (db conn)
        user         (first (q user-with-id conn-db user-id))]
    (if (not user)
      ; also need to write the user to the db...
      (get-request-with-token userinfo-url access-token)
      (let [user-entity (d/entity db user)]
        ; just return the user's name for now
        (:user/name user-entity)))))

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