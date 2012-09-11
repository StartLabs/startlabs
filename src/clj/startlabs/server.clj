(ns startlabs.server
  (:require [noir.server :as server]
            [noir.session :as session]
            [clojure.string :as str]
            [startlabs.models.user :as user]
            [startlabs.models.database :as db]
            [clojure.tools.nrepl.server :as nrepl])
  (:use [noir.fetch.remotes :only [defremote]]
        [environ.core :only [env]]))

(defonce repl-server (atom nil))
(defonce noir-server (atom nil))

(defremote token-info [access-token]
  (let [token-map    (user/get-token-info access-token)
        valid-token? (= (:audience token-map) (env :oauth-client-id))
        lab-member?  (and (= (last (str/split (:email token-map) #"@")) "startlabs.org")
                          (:verified_email token-map))]
    (if (and valid-token? lab-member?)
      (do
        (session/put! :access-token access-token)
        (doseq [k [:user_id :email]] (session/put! k (k token-map)))
        token-map)
      (do
        (session/flash-put! :message [:error "Invalid login. Make sure you're using your email@startlabs.org."])
        "Invalid login"))))

(server/load-views-ns 'startlabs.views)

(defn -main [& m]
  (let [mode (if (env :dev) :dev :prod)
        port (Integer. (or (env :port) "8000"))]
    (db/do-default-setup)
    (reset! repl-server (nrepl/start-server :port (Integer. (env :nrepl-port))))
    (reset! noir-server (server/start port {:mode mode
                                            :ns 'startlabs}))))