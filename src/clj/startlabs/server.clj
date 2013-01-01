(ns startlabs.server
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.string :as str]
            [noir.response :as response]
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

(defn init []
  (db/do-default-setup)
  (println "Database is setup."))

(defn get-referer [req]
  (get-in req [:headers "referer"]))


;; split the job routes so /jobs is 3 pages: /jobs, /whitelist, /job/new
(defroutes job-routes
  (GET "/" [& params] (jobs/get-edit-job params))
  (POST "/" [& params] (jobs/post-edit-job params))
  (POST "/delete" [id] (jobs/delete-job id))
  (GET "/confirm" [id] (jobs/confirm-job id))
  (GET "/edit" [id] (jobs/resend-edit-link id))
  (GET "/analytics" [id a-start-date a-end-date]
       (jobs/get-job-analytics id a-start-date a-end-date)))

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
  (GET "/me/refresh" [:as req] (user-views/refresh-me (get-referer req)))

  (GET "/team" [] (user-views/team))
  (GET ["/team/:name" :name #"\w+"] [name] (user-views/get-team-member name))

  (GET "/jobs" [] (jobs/get-jobs))
  (GET "/jobs.edn" [q] (jobs/job-search q))

  (GET "/whitelist" [] (jobs/get-whitelist))
  (POST "/whitelist" [the-list] (jobs/post-whitelist the-list))

  (GET "/job/new" [& params] (jobs/get-new-job params))
  (POST "/job/new" [& params] (jobs/post-new-job params))
  (GET "/job/success" [] (jobs/job-success))

  (context "/job/:id" [id] job-routes)

  (GET "/analytics/authorize" [:as req] 
       (user-views/authorize-analytics (get-referer req)))

  ;; Redirect. Dead links = evil
  (GET "/company" [] (response/redirect "/about"))
  (GET "/contact" [] (response/redirect "/about"))
  (GET "/postJob" [] (response/redirect "/jobs"))

  (route/resources "/")

  (route/not-found (main/four-oh-four)))

(def app
  (-> (handler/site main-routes)
      wrap-noir-validation
      wrap-strip-trailing-slash
      wrap-stateful-session))