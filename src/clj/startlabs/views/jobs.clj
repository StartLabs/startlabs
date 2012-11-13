(ns startlabs.views.jobs
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [noir.session :as session]
            [noir.response :as response]
            [noir.validation :as vali]
            [postal.core :as postal]
            [startlabs.models.util :as mu]
            [startlabs.util :as u]
            [startlabs.views.common :as common]
            [startlabs.models.job :as job]
            [startlabs.models.user :as user])
  (:use [clojure.core.incubator]
        [clojure.tools.logging :only [info]]
        [clj-time.coerce :only [to-long]]
        [environ.core :only [env]]
        [noir.core :only [defpage defpartial render url-for]]
        [startlabs.views.jobx :only [job-card]])
  (:import java.net.URI))

;; jobs

(defn edit-link [job-map]
  (str (u/home-uri) (url-for edit-job {:id (:id job-map)}) "?secret=" (:secret job-map)))

(defpartial job-email-body [job-map]
  (let [conf-link     (str (u/home-uri) (url-for confirm-job {:id (:id job-map)}))
        the-edit-link (edit-link job-map)]
    [:div
      [:p "Hey there,"]
      [:p "Thanks for submitting to the StartLabs jobs list."]
      [:p "To confirm your listing, " [:strong (:position job-map)] ", please visit this link:"]
      [:p [:a {:href conf-link} conf-link]]

      [:p "If you ever need to edit the listing, use this link:"]
      [:p [:a {:href the-edit-link} the-edit-link]]

      [:p "If this email was sent in error, feel free to ignore it or contact "
          (common/webmaster-link "our webmaster") "."]]))

(defn send-confirmation-email [job-map]
  (postal/send-message ^{:host (env :email-host)
                         :user (env :email-user)
                         :pass (env :email-pass)
                         :ssl  :yes}
    {:from    "jobs@startlabs.org"
     :to      (:email job-map)
     :subject "Confirm your StartLabs Job Listing"
     :body [{:type    "text/html; charset=utf-8"
             :content (job-email-body job-map)}]}))

(def ordered-job-keys
  [:company :position :location :website :start_date :end_date 
   :description :contact_info :email])

(defmulti input-for-field (fn [field type docs v] 
  (keyword (name type))) :default :string)

(defmethod input-for-field :string [field type docs v]
  (let [input-type (if (= field :description) :textarea :input)]
    [input-type {:type "text" :id field :name field :value v
                 :rows 6 :placeholder docs :class "span12"}
      (if (= input-type :textarea) v)]))

(defmethod input-for-field :instant [field type docs v]
  (let [date-format (str/lower-case mu/default-date-format)]
    [:input.datepicker {:type "text" :data-date-format date-format :value v
                        :id field :name field :placeholder date-format}]))

(defpartial error-item [[first-error]]
  [:span.help-block first-error])

(defpartial fields-from-schema [schema ordered-keys params]
  [:table.table
    [:tbody
      (for [field ordered-keys]
        (let [field-name  (name field)
              field-kw    (mu/namespace-key :job (name field))
              [type docs] (field-kw schema)
              error?      (vali/on-error field (fn [_] true))
              v           (or (field params) "")]
          [:tr {:class (if error? "error")}
            [:td [:label {:for field} (common/phrasify field-name)]]
            [:td
              ; dispatch input based on type
              (input-for-field field type docs v)
              (vali/on-error field error-item)]]))
      [:tr
        [:td]
        [:td
          [:input.btn.btn-primary {:type "submit" :value "Submit"}]]]]])

(def sample-job-fields
  {:position "Lab Assistant" :company "StartLabs" :location "Cambridge, Massachusetts"
   :website "http://www.startlabs.org" :start_date "May 30, 2013" :end_date "August 30, 2013"
   :description "Smart people tackling difficult problems at a great location with *nice perks*.
\n\nMust have **4+ years** of lab experience.
\n\nWe prefer candidates who wear green clothing."
   :contact_info "contact@startlabs.org"})

(defpartial submit-job [has-params? params]
  ;; make the distinction between editing existing values vs. creating a new job
  (let [editing? (not (empty? (:id params)))
        heading  (if editing? "Edit Job" "Submit a Job")
        action   (if editing? (str "/job/" (:id params)) "/jobs")]

    [:div#submit {:class (u/cond-class "tab-pane" [has-params? "active"])}
      [:h1 heading]

      [:form#job-form.row-fluid {:method "post" :action action}
        [:div.span6
          (if (not editing?)
            [:div.well "In order to submit a job, your email address and company website domain must match. Also, "
                        [:strong "your company must be preapproved"] ". Please " 
                        [:a {:href "mailto:team@startlabs.org?subject=Jobs List Request: [Your_Company_Name]"} "email us"] 
                        " for consideration for the Jobs List."])
          (fields-from-schema (job/job-fields) ordered-job-keys params)]

          (if editing?
            [:input {:type "hidden" :name "secret" :value (:secret params)}])

        [:div#job-preview.span6.clearfix.thumbnail
          ; generate this in js
          (job-card (if has-params? params sample-job-fields) false)]
      ]]))


(defpartial browse-jobs [has-params?]
  (let [all-jobs     (sort-by #(:company %) 
                       (filter #(not= (:removed? %) "true") (job/find-upcoming-jobs)))
        show-delete? (user/logged-in?)]
    [:div#browse {:class (u/cond-class "tab-pane" [(not has-params?) "active"])}
      ;; sort by date and location.
      ;; search descriptions and company names
      [:h1 "Browse Startup Jobs"]

      [:div#map-box.row-fluid
        [:div#map.thumbnail]
        [:div.navbar
          [:div.navbar-inner
            [:form.navbar-search.pull-left {:action "#"}
             [:input#job-search.search-query
              {:type "text" :placeholder "Search"}]]
            [:ul.nav.pull-right
              [:li [:a#map-toggle {:href "#"} "Toggle Map"]]]
        ]]]

        [:div#job-list.row-fluid
          (if (empty? all-jobs)
            [:h1 "No jobs posted yet. Come back later!"])
          
			(let [[left-jobs right-jobs] (split-at (/ (count all-jobs) 2) all-jobs)]
			[:div.span6 
			  (for [job left-jobs]
		        [:div.job.thumbnail {:id (:id job)}
				  (job-card job show-delete?)])])
				  
			(let [[left-jobs right-jobs] (split-at (/ (count all-jobs) 2) all-jobs)]  
			[:div.span6 
			  (for [job right-jobs]
		        [:div.job.thumbnail {:id (:id job)}
				  (job-card job show-delete?)])])
				  
        [:script#job-data
          (str "window.job_data = " (json/json-str all-jobs) ";")]
     ]]))

(defn split-sites [sitelist]
  (str/split sitelist #"\s+"))

(defpage [:post "/whitelist"] {:keys [the-list]}
  (if (user/logged-in?)
    (do
      (job/update-whitelist the-list)
      (session/flash-put! :message [:success "The whitelist has been updated successfully."])))

    (do
      (session/flash-put! :message [:error "You must be logged in to change the whitelist."])))

  (response/redirect "/jobs")

(defpartial whitelist []
  (let [whitelist (job/get-current-whitelist)]
    [:div#whitelist {:class "tab-pane"}
      [:h2 "Company Whitelist"]

      [:div.well
        [:p "Just put each company domain name on a new line."]
        [:p "Do not include the http:// or the www."]]

      [:form {:action "/whitelist" :method "post"}
        [:div.span6.pull-left
          [:textarea#the-list.span6 {:name "the-list" :rows 20 :placeholder "google.com"}
            whitelist]
        [:input.btn.btn-primary.span3 {:type "submit"}]]

      [:div.span5
       [:input.btn.btn-primary.span3 {:type "submit"}]]]
     ]))

(defpage [:get "/jobs"] {:as params}
  (let [has-params? (not (empty? params))]
    (common/layout
      [:div#job-toggle.btn-group.pull-right {:data-toggle "buttons-radio"}
       [:a {:class (u/cond-class "btn" [(not has-params?) "active"])
            :href "#browse" :data-toggle "tab"}
        "Browse Available"]
       
       (if (user/logged-in?)
         [:a {:class "btn" :href "#whitelist" :data-toggle "tab"} "Whitelist"])
       
       [:a {:class (u/cond-class "btn" [has-params? "active"])
            :href "#submit" :data-toggle "tab"}
          "Submit a Job"]]
      [:div.clearfix]

      [:div.tab-content
       (browse-jobs has-params?)
       (whitelist)
       (submit-job has-params? params)])))

(defn empty-rule [[k v]]
  (vali/rule 
    (vali/has-value? v) [k "This field cannot be empty."]))

(defn get-hostname [url]
  (try
    (let [site-uri    (URI. (or url ""))
          replace-www (fn [x] (str/replace x "www."  ""))
          site-host   (-?> site-uri .getHost replace-www)]
      site-host)
    (catch Exception e
      "")))

(defn valid-job? [job-params]
  (let [site-host (get-hostname (:website job-params))
        whitelist (job/get-current-whitelist)]

    (dorun (map empty-rule job-params))

    (vali/rule (not (or (nil? site-host) (= "" site-host)))
      [:website "Must be a valid website." site-host])

    ; also allow submissions from startlabs members
    (vali/rule (or (re-matches (re-pattern (str ".*" site-host "$")) (:email job-params))
                   (re-matches #".*@startlabs.org$" (:email job-params)))
      [:email "Your email address must match the company website."])

    (vali/rule (re-find (re-pattern (str "\\b" site-host "\\b")) whitelist)
      [:website "Sorry, your site is not on our whitelist."])

    (doseq [date [:start_date :end_date]]
      (vali/rule 
        (mu/parse-date (date job-params))
        [date "Invalid date."]))

    ; make sure the end date comes after the start
    (vali/rule
      (let [[start end] (map #(mu/parse-date (% job-params)) [:start_date :end_date])]
        (and (and start end) 
             (= -1 (apply compare (map to-long [start end])))))
        [:end_date "The end date must come after the start date."])

    (not (apply vali/errors? ordered-job-keys))))

(defn fix-job-params [params]
  (let [website (:website params)]
    (conj params 
      {:website (if (not (empty? website)) 
        (u/httpify-url website) 
        "")})))

(defn flash-job-error []
  (session/flash-put! :message [:error "Please correct the form and resubmit."]))

(defn trim-and-fix-params [params]
  (let [trimmed-params (u/trim-vals params)
        fixed-params   (fix-job-params trimmed-params)]
      fixed-params))

(defpage [:post "/jobs"] {:as params}
  (let [fixed-params (trim-and-fix-params params)]
    (if (valid-job? fixed-params)
      (try
        (let [job-info  (job/create-job fixed-params)
              email-res (send-confirmation-email job-info)]
          (if (= (:error email-res) :SUCCESS)
            (do
              (response/redirect "/jobs/success"))
            (do
              (session/flash-put! 
                :message [:error "Trouble sending confirmation email:" (:message email-res)])
              (render "/jobs" fixed-params))))

        (catch Exception e
          (session/flash-put! :message [:error (str "Trouble connecting to the database:" e)])
          (render "/jobs" fixed-params)))

      (do ;invalid job, flash an error message
        (flash-job-error)
        (render "/jobs" fixed-params)))))

(defpage "/jobs/success" []
  (common/layout
    [:h1 "Submission Received"]
    [:p "Please, check your email for a confirmation link."]))




;; individual job editing

(defpartial unexpected-error [& [error]]
  [:div
    [:h1 "Something went wrong."]
    (if error
      [:p "Here's the problem: " error])
    [:p  "Sorry for the inconvenience. Please contact the "
      (common/webmaster-link "webmaster") "."]])

(defpartial job-not-found []
  [:div
    [:h1 "Job not found"]

    [:p "Sorry, we couldn't find any jobs with that ID."]
    [:p "If you think this is a mistake, feel free to contact the "
      (common/webmaster-link "webmaster") "."]])


(defpartial job-edit-email-body [job-map]
  (let [the-link (edit-link job-map)]
    [:div
      [:p "Hey there,"]
      [:p "To edit your StartLabs jobs posting, please visit this link:"]
      [:p [:a {:href the-link} the-link]]
      [:p "Don't share it with anyone!"]
      [:p "If this email was sent in error, feel free to ignore it or contact "
          (common/webmaster-link "our webmaster") "."]]))

(defn send-edit-email [job-map]
  (postal/send-message ^{:host (env :email-host)
                         :user (env :email-user)
                         :pass (env :email-pass)
                         :ssl  :yes}
    {:from    "jobs@startlabs.org"
     :to      (:email job-map)
     :subject "Edit your StartLabs Job Listing"
     :body [{:type    "text/html; charset=utf-8"
             :content (job-edit-email-body job-map)}]}))


;; going here triggers an edit link to be sent to the author of the listing
(defpage [:get "/job/:id/edit"] {:keys [id]}
  (common/layout
    (if (user/logged-in?)
      (if-let [job-map (job/job-map id)]
        ;; here we find the existing secret or create a new one
        (let [secret     (or (:secret job-map) 
                             (job/update-job-field id :secret (mu/uuid)))
              secret-map (assoc job-map :secret secret)]

          (send-edit-email secret-map)

          [:div
            [:h1 "Edit Link Sent"]
            [:p "The author can now check their email for a link to edit the job listing."]])

        (job-not-found))

      ;;else
      (response/redirect "/jobs"))))


(defpage edit-job "/job/:id" {:keys [id] :as params}
  (common/layout
    (if-let [job-map (job/job-map id)]
      (let [secret-map (assoc job-map :secret (:secret params))]
        (submit-job true secret-map))
        ;; else
        (job-not-found))))

(defn flash-error-and-render [error render-url params]
  (session/flash-put! :message [:error error])
  (render render-url params))

(defpage [:post "/job/:id"] {:keys [id] :as params}
  (let [fixed-params (trim-and-fix-params params)
        job-url      (url-for edit-job {:id id})
        job-secret   (job/job-secret id)]

    (try
      (if (= (:secret fixed-params) job-secret)
        (if (valid-job? fixed-params)
          (if (job/update-job id params)
            (do
              (session/flash-put! :message [:success "Your job has been updated successfully."])
              (response/redirect "/jobs"))
            ; else
            (flash-error-and-render "Unable to update job. Sorry for the inconvenience." 
                                    job-url fixed-params))

          ; invalid job, flash an error message, return to edit page
          (do (flash-job-error) (render job-url fixed-params)))

        ; secret does not match job secret
        (flash-error-and-render "Invalid job secret." job-url fixed-params))

      (catch Exception e
        ;; else: no idea what's wrong, generic error page
        (unexpected-error (str [(str e) id fixed-params]))))))


(defpage [:post "/job/:id/delete"] {:keys [id]}
  (if (user/logged-in?)
    (if (job/remove-job id)
      (session/flash-put! :message [:success "The job has been removed."]))

    (do
      (session/flash-put! :message [:error "You cannot delete that job!"])))

  (response/redirect "/jobs"))

(defpage confirm-job "/job/:id/confirm" {:keys [id]}
  (common/layout
    (if (job/confirm-job id)
      [:div
        [:h1 "Thanks for Confirming"]
        [:p "Your listing should now be posted."]
        [:p "Check it out " [:a {:href (str "/jobs#" id)} "on the jobs page"] "."]]

      ;; else
      (unexpected-error))))