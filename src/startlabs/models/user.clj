(ns startlabs.models.user
  (:require [startlabs.secrets :as secrets]
            [clojure.string :as str]
            [oauth.google :as oa]
            [clj-http.client :as client]))

(def redirect-url "http://localhost:8000/oauth2callback")
(def googleapis-url "https://www.googleapis.com/oauth2/v1/")

(defn scope-strings [scopes origin]
  (apply concat ; flatten the output
    (for [[k v] scopes]
      (map #(str origin (name k) "." (name %1)) v))))

(defn joined-scope-strings [scopes origin]
  (str/join " " (scope-strings scopes origin)))

; (def userinfo-url (str googleapis-url "userinfo"))

; pass the current url as the state
(defn get-login-url []
  (let [scopes {:userinfo [:email :profile]}
        scope-origin "https://www.googleapis.com/auth/"]
    (oa/oauth-authorization-url secrets/oauth-client-id redirect-url 
      :scope (joined-scope-strings scopes scope-origin) 
      :response_type "token")))

(defn get-token-info [access-token]
  (let [tokeninfo-url (str googleapis-url "tokeninfo")
        response (client/get tokeninfo-url {:query-params {"access_token" access-token} :as :json})]
    (:body response)))