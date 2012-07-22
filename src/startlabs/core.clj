(ns startlabs.core
	(:require [oauth.google :as oa]
						[startlabs.secrets :as secrets]
						[clojure.string :as str]))

; We'll be using the token-based callback method since all interactions will be done
; when a user is at their computer.
; On the client-side, take advantage of the timeout to tell the user when they need to reauth.
; Also: save the token in the user's browser so they are still authorized even after a window close.
; The server should always verify a token when a request is made:
; https://www.googleapis.com/oauth2/v1/tokeninfo?access_token={accessToken}
; Ensure the audience value matches the oauth-client-id

(def redirect-url "http://localhost:8000/oauth2callback")

(def scopes {:userinfo [:email :profile]})
(def scope-origin "https://www.googleapis.com/auth/")

(def userinfo-url "https://www.googleapis.com/oauth2/v1/userinfo")

(defn scope-strings [scopes origin]
	(apply concat ; flatten the output
		(for [[k v] scopes]
			(map #(str origin (name k) "." (name %1)) v))))

(defn joined-scope-strings [scopes origin]
	(str/join " " (scope-strings scopes origin)))

(defn -main
  [& args]

  (oa/oauth-authorization-url secrets/oauth-client-id redirect-url :scope (joined-scope-strings scopes scope-origin) :response_type "token")

  (def client (oa/oauth-client access-token))

  ; store user info in session. Verify login by email.
  (def user-info (client {:method :get :url userinfo-url}))

  (println "Hello, World!"))