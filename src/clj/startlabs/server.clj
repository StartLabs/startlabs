(ns startlabs.server
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.string :as str]
            [startlabs.models.user :as user]
            [startlabs.models.database :as db]
            [startlabs.views.main :as main])
  (:use compojure.core
        [noir.util.middleware :only [wrap-strip-trailing-slash]]
        [sandbar.stateful-session :only [wrap-stateful-session]]
        ;; [noir.fetch.remotes :only [defremote]]
        [environ.core :only [env]]))

;; this belongs in main or user, not server.
;; (defremote token-info [access-token]
;;   (let [token-map    (user/get-token-info access-token)
;;         valid-token? (= (:audience token-map) (env :oauth-client-id))
;;         lab-member?  (and (= (last (str/split (:email token-map) #"@")) "startlabs.org")
;;                           (:verified_email token-map))]
;;     (if (and valid-token? lab-member?)
;;       (do
;;         (session/put! :access-token access-token)
;;         (doseq [k [:user_id :email]] (session/put! k (k token-map)))
;;         token-map)
;;       (do
;;         (session/flash-put! :message
;;                             [:error "Invalid login. Make sure you're using your email@startlabs.org."])
;;         "Invalid login"))))


;; add these to the routes
;;(status/set-page! 404 (four-oh-four))
;;(status/set-page! 500 (internal-error))

;; Redirect. Dead links = evil
;; (defpage "/company" [] (response/redirect "/about"))
;; (defpage "/contact" [] (response/redirect "/about"))
;; (defpage "/postJob" [] (response/redirect "/jobs"))

(defn init []
  (db/do-default-setup)
  (println "Database is setup."))

(defroutes main-routes
  (GET "/" [] (main/home))
  (route/resources "/"))

(def app
  (-> (handler/site main-routes)
      wrap-strip-trailing-slash
      wrap-stateful-session))