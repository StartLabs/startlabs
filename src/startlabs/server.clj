(ns startlabs.server
  (:require [noir.server :as server]
            [noir.cljs.core :as cljs]
            [noir.session :as session]
            [startlabs.secrets :as secrets]
            [startlabs.models.user :as user])
  (:use [noir.fetch.remotes :only [defremote]]))


(defremote user-info [access-token]
  (let [token-info (user/get-token-info access-token)
        valid-token? (= (:audience token-info) secrets/oauth-client-id)]
    (if valid-token?
      (do
        (map #(session/put! k (k vals)) [:user_id :email])
        (str "Token is valid! " token-info))
      (:audience token-info))))

(server/load-views-ns 'startlabs.views)
(def cljs-options {:advanced {:externs ["externs/jquery.js"]}})

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8000"))]
    (cljs/start mode cljs-options)
    (server/start port {:mode mode
                        :ns 'startlabs})))
