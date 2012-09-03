(ns startlabs.server
  (:require [noir.server :as server]
            [noir.session :as session]
            [clojure.string :as str]
            [startlabs.models.user :as user]
            [startlabs.models.database :as db])
  (:use [noir.fetch.remotes :only [defremote]]
        [environ.core :only [env]]))


(defremote token-info [access-token]
  (let [token-map    (user/get-token-info access-token)
        valid-token? (= (:audience token-map) (env :oauth-client-id))
        lab-member?  (and (= (last (str/split (:email token-map) #"@")) "startlabs.org") (:verified_email token-map))]
    (if (and valid-token? lab-member?)
      (do
        (session/put! :access-token access-token)
        (doseq [k [:user_id :email]] (session/put! k (k token-map)))
        token-map)
      (do
        (session/clear!)
        (session/flash-put! :message [:error "Invalid login. Make sure you're using your email@startlabs.org."])
        "Invalid login"))))

(server/load-views-ns 'startlabs.views)

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8000"))]
    (db/do-default-setup)
    (server/start port {:mode mode
                        :ns 'startlabs})))