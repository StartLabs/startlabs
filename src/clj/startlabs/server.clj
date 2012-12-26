(ns startlabs.server
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.string :as str]
            [startlabs.models.user :as user]
            [startlabs.models.database :as db]
            [startlabs.views.about :as about]
            [startlabs.views.jobs :as jobs]
            [startlabs.views.main :as main]
            [startlabs.views.partners :as partners]
            [startlabs.views.resources :as resources]
            [startlabs.views.user :as user-views])

  (:use compojure.core
        [noir.validation :only [wrap-noir-validation]]
        [noir.util.middleware :only [wrap-strip-trailing-slash]]
        [sandbar.stateful-session :only [wrap-stateful-session]]))

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

(defn get-referer [req]
  (get-in req [:headers "referer"]))

;; split the job routes so /jobs is 3 pages: /jobs, /whitelist, /job/create
(defroutes job-routes
  (GET "/" [& params] (jobs/get-edit-job params))
  (POST "/" [& params] (jobs/post-edit-job params))
  (POST "/delete" [id] (jobs/delete-job id))
  (GET "/confirm" [id] (jobs/confirm-job id))
  (GET "/edit" [id] (jobs/resend-edit-link id)))

(defroutes main-routes
  (GET "/" [] (main/home))
  (POST "/" [email] (main/post-mailing-list email))
  (POST "/event" [& params] (main/post-event params))
  
  (GET "/about" [] (about/about))
  (GET "/resources" [] (resources/resources))
  (GET "/partners" [] (partners/partners))

  (GET "/login" [:as req] (user-views/login (get-referer req)))
  (GET "/logout" [:as req] (user-views/logout (get-referer req)))
  (GET "/oauth2callback" [state code] (user-views/oauth-callback state code))

  (GET "/me" [] (user-views/get-me))
  (POST "/me" [& params] (user-views/post-me params))

  (GET "/team" [] (user-views/team))
  (GET ["/team/:name" :name #"\w+"] [name] (user-views/get-team-member name))

  (GET "/jobs" [& params] (jobs/get-jobs params))
  (POST "/jobs" [& params] (jobs/post-jobs params))
  (GET "/jobs.edn" [q] (jobs/job-search q))
  (GET "/jobs/success" [] (jobs/job-success))

  (POST "/whitelist" [the-list] (jobs/post-whitelist the-list))

  (context "/job/:id" [id] job-routes)

  (route/resources "/"))

(def app
  (-> (handler/site main-routes)
      wrap-noir-validation
      wrap-strip-trailing-slash
      wrap-stateful-session))