(ns startlabs.views.main
  (:require [startlabs.views.common :as common]
            [startlabs.secrets :as secrets]
            [clojure.string :as str]
            [oauth.google :as oa]
            [noir.response :as response])
  (:use [noir.core :only [defpage]]
        [hiccup.core :only [html]]))

(defn scope-strings [scopes origin]
  (apply concat ; flatten the output
    (for [[k v] scopes]
      (map #(str origin (name k) "." (name %1)) v))))

(defn joined-scope-strings [scopes origin]
  (str/join " " (scope-strings scopes origin)))

(def redirect-url "http://localhost:8000/oauth2callback")
(def userinfo-url "https://www.googleapis.com/oauth2/v1/userinfo")

; pass the current url as the state
(defn get-login-url []
  (let [scopes {:userinfo [:email :profile]}
        scope-origin "https://www.googleapis.com/auth/"]
    (oa/oauth-authorization-url secrets/oauth-client-id redirect-url 
      :scope (joined-scope-strings scopes scope-origin) 
      :response_type "token")))

(defpage "/" []
  (common/layout
    [:div#content
      [:a {:href "/login"} "Login"]]))

(defpage "/login" []
  (response/redirect (get-login-url)))

(defpage "/oauth2callback" []
  "Grab that access token")