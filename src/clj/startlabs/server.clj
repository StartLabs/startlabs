(ns startlabs.server
  (:require
   [compojure.handler :as handler]
   [compojure.route :as route]
   [clojure.string :as str]
   [noir.response :as response]
   [org.httpkit.server :as http]
   [ring.middleware.reload :as reload]

   [startlabs.models.user :as user]
   [startlabs.models.database :as db]

   [startlabs.views.about :as about]
   [startlabs.views.blog :as blog]
   [startlabs.views.jobs :as jobs]
   [startlabs.views.main :as main]
   [startlabs.views.partners :as partners]
   [startlabs.views.pay :as pay]
   [startlabs.views.resources :as resources]
   [startlabs.views.user :as user-views]
   [startlabs.views.visitors :as visitors])

  (:use 
   compojure.core
   [environ.core :only [env]]
   [noir.validation :only [wrap-noir-validation]]
   [noir.util.middleware :only [wrap-strip-trailing-slash]]
   [ring.middleware.basic-authentication :only [wrap-basic-authentication]]
   [sandbar.stateful-session :only [wrap-stateful-session]]
   [startlabs.views.common :only [*uri*]]))

(defn init []
  (db/do-default-setup)
  (println "Database is setup."))

(defn get-referer [req]
  (get-in req [:headers "referer"]))

(defn uri-middleware [app]
  (fn [req]
    (binding [*uri* (:uri req)]
      (app req))))

(defn authenticated? [name pass]
  (and (= name "pi")
       (= pass (env :pi-password))))

;; split the job routes so /jobs is 3 pages: /jobs, /whitelist, /job/new
(defroutes job-routes
  (GET "/" [& params] (jobs/get-edit-job params))
  (POST "/" [& params] (jobs/post-edit-job params))
  (POST "/delete" [id] (jobs/delete-job id))
  (GET "/confirm" [id] (jobs/confirm-job id))
  (GET "/edit" [id] (jobs/resend-edit-link id))
  (GET "/analytics" [id a-start-date a-end-date]
       (jobs/get-job-analytics id a-start-date a-end-date))
  (GET "/analytics.edn" [id a-start-date a-end-date]
       (jobs/analytics-search id a-start-date a-end-date)))

(defroutes signin-routes
  (GET "/" [& params] (visitors/get-signin params))
  (POST "/" [& params] (visitors/post-signin params)))

(defroutes main-routes
  (GET "/" [] (main/home))
  (POST "/" [email] (main/post-mailing-list email))
  (POST "/event" [& params] (main/post-event params))
  
  (GET "/about" [] (about/about))
  (GET "/blog" [] (blog/blog))
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

  (GET "/jobs" [& params] (jobs/get-jobs params))
  (POST "/jobs/filters" [& params] (jobs/post-job-filters params))

  (GET ["/jobs.:fmt" :fmt #"(edn|json)"] [fmt & params] 
       (jobs/job-search (keyword fmt) params))

  (GET "/whitelist" [] (jobs/get-whitelist))
  (POST "/whitelist" [the-list] (jobs/post-whitelist the-list))

  (GET "/job/new" [& params] (jobs/get-new-job params))
  (POST "/job/new" [& params] (jobs/post-new-job params))
  (GET "/job/success" [] (jobs/job-success))

  (context "/job/:id" [id] job-routes)

  (GET "/analytics/authorize" [:as req]
       (user-views/authorize-analytics (get-referer req)))

  (GET "/pay" [& params] (pay/get-pay params))
  (POST "/pay" [& params] (pay/post-pay params))
  (GET "/payments" [] (pay/get-payments))

  (context "/signin" [& params] 
           (wrap-basic-authentication signin-routes authenticated?))

  ;; Redirect. Dead links = evil
  (GET "/company" [] (response/redirect "/about"))
  (GET "/contact" [] (response/redirect "/about"))
  (GET "/postJob" [] (response/redirect "/jobs"))

  (route/resources "/")

  (route/not-found (main/four-oh-four)))

(def app
  (-> main-routes
      handler/site

      wrap-noir-validation
      wrap-strip-trailing-slash
      wrap-stateful-session
      uri-middleware))

;; For interactive development, evaluate these:
(comment
  (do
    ;; 1. Initialize the database
    (init)
    ;; 2. Run the server
    (def app (reload/wrap-reload app))
    (defonce server (http/run-server #'app {:port 8000})))
)

;; To stop the server, just do:
;; (server)
