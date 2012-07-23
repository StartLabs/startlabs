(ns startlabs.server
  (:require [noir.server :as server]
            [noir.cljs.core :as cljs]
            [noir.session :as session]
            [clojure.string :as str]
            [startlabs.secrets :as secrets]
            [startlabs.models.user :as user])
  (:use [noir.fetch.remotes :only [defremote]]))


(defremote token-info [access-token]
  (let [token-info (user/get-token-info access-token)
        valid-token? (= (:audience token-info) secrets/oauth-client-id)
        lab-member? (= (last (str/split (:email token-info) #"@")) "startlabs.org")]
    (if (and valid-token? lab-member?)
      (do
        (map (fn [k] session/put! k (k token-info)) [:user_id :email])
        (str token-info))
      (do
        (session/flash-put! :message "Invalid login. Make sure you're using your email@startlabs.org")
        (str "Invalid login")))))

(server/load-views-ns 'startlabs.views)
(def cljs-options {:advanced {:externs ["externs/jquery.js"]}})

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8000"))]
    (cljs/start mode cljs-options)
    (server/start port {:mode mode
                        :ns 'startlabs})))
