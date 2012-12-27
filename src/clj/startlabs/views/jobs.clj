(ns startlabs.views.jobs
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [noir.response :as response]
            [noir.validation :as vali]
            [postal.core :as postal]
            [ring.util.codec :as c]
            [ring.util.response :as rr]
            [sandbar.stateful-session :as session]
            [startlabs.models.util :as mu]
            [startlabs.models.job :as job]
            [startlabs.models.user :as user]
            [startlabs.util :as u]
            [startlabs.views.common :as common])

  (:use [compojure.response :only [render]]
        [clojure.core.incubator]
        [clojure.tools.logging :only [info]]
        [clj-time.core :only [now plus months]]
        [clj-time.coerce :only [to-long]]
        [environ.core :only [env]]
        [hiccup.core :only [html]]
        [hiccup.def :only [defhtml]]
        [startlabs.views.jobx :only [job-card job-list]])

  (:import java.net.URI))

;; jobs

(defn edit-link [job-map]
  (str (u/home-uri) "/job/" (:id job-map) "?secret=" (:secret job-map)))

(defhtml job-email-body [job-map]
  (let [conf-link     (str (u/home-uri) "/job/" (:id job-map) "/confirm")
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
  [:company :position :location :website :fulltime? :start_date :end_date 
   :company_size :description :contact_info :email])

(def hidden-job-keys [:longitude :latitude])

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

(defmethod input-for-field :boolean [field type docs v]
  (let [str-v (if (or (false? v) (true? v)) (str v) "false")]
    [:div.btn-group {:data-toggle-name field :data-toggle "buttons-radio"}
     (let [choices (if (= field :fulltime?) 
                     [["Fulltime" "true"] ["Internship" "false"]]
                     [["True" "true"] ["False" "false"]])]
       (for [[k val] choices]
         [:button.btn {:value val :data-toggle "button"} k]))
     [:input {:type "hidden" :name field :id field :value str-v}]]))

(defhtml error-item [[first-error]]
  [:span.help-block first-error])

(defhtml fields-from-schema [schema ordered-keys params]
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

(def job-list-email-body
  (c/url-encode
   (str "Please fill out all of the following details for consideration:\n\n"
        "Startup Name: \n"
        "Website URL: \n"
        "Brief Description of your Company: \n\n"
        "Years since Incorporation: \n"
        "Number of Employees: \n"
        "Funding Received (Optional):")))

(defhtml submit-job [params]
  ;; make the distinction between editing existing values vs. creating a new job
  (let [has-params? (not (empty? params))
        editing?    (not (empty? (:id params)))
        heading     (if editing? "Edit Job" "Submit a Job")
        action      (if editing? (str "/job/" (:id params)) "/job/new")]

    [:div#submit
     [:h1 heading]
     [:form#job-form.row-fluid {:method "post" :action action}
      [:div.span6
       (if (not editing?)
         [:div.well "In order to submit a job, your email address and company website domain must match. Also, "
          [:strong "your company must be preapproved"] ". Please " 
          [:a {:href (str "mailto:jobs@startlabs.org?subject=Jobs List Request: [Your Company Name]&body=" 
                          job-list-email-body)} "email us"] 
          " for consideration for the Jobs List."])
       (fields-from-schema (job/job-fields) ordered-job-keys params)]

      (if editing?
        [:input {:type "hidden" :name "secret" :value (:secret params)}])

      (for [hidden-key hidden-job-keys]
        [:input {:type "hidden" :name (name hidden-key) :value (hidden-key params)}])

      [:div.span6.clearfix.thumbnail
       [:div#job-preview
        (job-card (if has-params? params sample-job-fields) false)]
       [:h3.centered "Job Location Preview (Drag Pin to Relocate)"]
       [:div#job-location]]
      ]]))

(defn get-all-jobs []
  (sort-by #(:company %)
           (filter #(not= (:removed? %) "true")
                   (job/find-upcoming-jobs))))

(defhtml browse-jobs []
  (let [all-jobs (get-all-jobs)
        show-delete? (user/logged-in?)]
    [:div#browse
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

     [:div#job-container.row-fluid
      (if (empty? all-jobs)
        [:h1 "Sorry, no jobs posted currently. Come back later."])
      (job-list all-jobs show-delete?)]
				  
     [:script#job-data
      (str "window.job_data = " (json/write-str all-jobs) ";")]
     ]))

(defn filter-jobs [query]
  (let [all-jobs (get-all-jobs)]
    (if (empty? query)
      all-jobs
      (filter (fn [job]
        (some #(re-find (re-pattern (str "(?i)" query)) %) 
              (map job [:position :company :location])))
        all-jobs))))

;; /jobs.edn?q=...
(defn job-search [query]
  (let [jobs (filter-jobs query)
        show-delete? (user/logged-in?)
        body (str {:html (html (job-list jobs show-delete?)) 
                   :jobs jobs})]
    (-> (rr/response body)
        (rr/content-type "text/edn"))))

(defn split-sites [sitelist]
  (str/split sitelist #"\s+"))

(defhtml nav-buttons [active-tab]
   [:div#job-toggle.btn-group.pull-right
    [:a {:class (u/cond-class "btn" [(= active-tab :jobs) "active"]) :href "/jobs"}
     "Browse Available"]
    (if (user/logged-in?)
      [:a {:class (u/cond-class "btn" [(= active-tab :whitelist) "active"]) :href "/whitelist"} "Whitelist"])
    [:a {:class (u/cond-class "btn" [(= active-tab :new-job) "active"]) :href "/job/new"}
     "Submit a Job"]])

;; [:post "/whitelist"]
(defn post-whitelist [the-list]
  (if (user/logged-in?)
    (do
      (job/update-whitelist the-list)
      (session/flash-put! :message [:success "The whitelist has been updated successfully."]))
    ;else
    (do
      (session/flash-put! :message [:error "You must be logged in to change the whitelist."])))
  (response/redirect "/jobs"))

;; [:get /whitelist]
(defn get-whitelist []
  (common/layout
   (nav-buttons :whitelist)
   (let [whitelist (job/get-current-whitelist)]
     [:div#whitelist
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
      ])))

;; [:get /jobs]
(defn get-jobs []
  (common/layout
   (nav-buttons :jobs)
   [:div (browse-jobs)]))


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
        whitelist (job/get-current-whitelist)
        fulltime? (:fulltime? job-params)]

    (dorun (map u/empty-rule job-params))

    (vali/rule (not (or (nil? site-host) (= "" site-host)))
               [:website "Must be a valid website." site-host])

    ; also allow submissions from startlabs members
    (vali/rule (or (re-matches (re-pattern (str ".*" site-host "$")) (:email job-params))
                   (re-matches #".*@startlabs.org$" (:email job-params)))
               [:email "Your email address must match the company website."])

    (vali/rule (re-find (re-pattern (str "\\b" site-host "\\b")) whitelist)
               [:website "Sorry, your site is not on our whitelist."])

    (vali/rule (vali/valid-number? (:company_size job-params))
               [:company_size "The company size must be a valid number."])

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
  (let [website   (:website params)
        fulltime? (if (= (:fulltime? params) "true") true false)
        start-date (mu/parse-date (:start_date params))
        end-date (if start-date
                   (plus start-date (months 6)) 
                   (plus (now) (months 6)))]
    (conj params
      {:website (if (not (empty? website)) 
        (u/httpify-url website) 
        "")
       :fulltime? fulltime?
       :end_date (if fulltime? 
                   (mu/unparse-date end-date)
                   (:end_date params))})))

(def job-error  "Please correct the form and resubmit.")
(defn flash-job-error []
  (session/flash-put! :message [:error job-error]))

(defn trim-and-fix-params [params]
  (let [trimmed-params (u/trim-vals params)
        fixed-params   (fix-job-params trimmed-params)]
    fixed-params))

;; [:get /job/new]
(defn get-new-job [& [params]]
  (common/layout
   (nav-buttons :new-job)
   (submit-job params)))

;; [:post /job/new]
(defn post-new-job [params]
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
              (render get-jobs fixed-params))))

        (catch Exception e
          (session/flash-put! :message [:error (str "Trouble connecting to the database:" e)])
          (render get-jobs fixed-params)))

      (do ;invalid job, flash an error message
        (flash-job-error)
        (render get-new-job fixed-params)))))

;; [:get /job/success]
(defn job-success []
  (common/layout
    [:h1 "Submission Received"]
    [:p "Please check your email for a confirmation link."]))



;; individual job editing

(defhtml unexpected-error [& [error]]
  [:div
    [:h1 "Something went wrong."]
    (if error
      [:p "Here's the problem: " error])
    [:p  "Sorry for the inconvenience. Please contact the "
      (common/webmaster-link "webmaster") "."]])

(defhtml job-not-found []
  [:div
    [:h1 "Job not found"]

    [:p "Sorry, we couldn't find any jobs with that ID."]
    [:p "If you think this is a mistake, feel free to contact the "
      (common/webmaster-link "webmaster") "."]])


(defhtml job-edit-email-body [job-map]
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
;; [:get "/job/:id/edit"] 
(defn resend-edit-link [id]
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

;; [:get "/job/:id"]
(defn get-edit-job [{:keys [id] :as params}]
  (common/layout
   ;; params either contains previously submitted (invalid) params that require editing
   ;; or it only contains {:id id} if the user just arrived at the edit page.
   (if-let [job-map (if (> (count params) 1) 
                      params
                      (job/job-map id))]
     (let [secret-map (assoc job-map :secret (:secret params))]
       (submit-job secret-map))
     ;; else
     (job-not-found))))

(defn flash-error-and-render [error job-id params]
  (session/flash-put! :message [:error error])
  (render get-edit-job params))

;;[:post "/job/:id"]
(defn post-edit-job [{:keys [id secret] :as params}]
  (let [fixed-params (trim-and-fix-params params)
        job-secret   (job/job-secret id)]
    (if (or (= (:secret fixed-params) job-secret)
            (user/logged-in?))

      (if (valid-job? fixed-params)
        (if (job/update-job id params)
          (do
            (session/flash-put! :message [:success "Your job has been updated successfully."])
            (response/redirect "/jobs"))
            ; else
          (flash-error-and-render
           "Unable to update job. Sorry for the inconvenience." id fixed-params))

        ; invalid job, flash an error message, return to edit page
        (flash-error-and-render job-error id fixed-params))

      ; secret does not match job secret
      (flash-error-and-render "Invalid job secret." id fixed-params))))


;; [:post "/job/:id/delete"] 
(defn delete-job [id]
  (if (user/logged-in?)
    (if (job/remove-job id)
      (session/flash-put! :message [:success "The job has been removed."]))
    ;; else
    (session/flash-put! :message [:error "You cannot delete that job!"]))
  (response/redirect "/jobs"))


;; "/job/:id/confirm" {:keys [id]}
(defn confirm-job [id]
  (common/layout
   (if (job/confirm-job id)
     [:div
      [:h1 "Thanks for Confirming"]
      [:p "Your listing should now be posted."]
      [:p "Check it out " [:a {:href (str "/jobs#" id)} "on the jobs page"] "."]]

     ;; else
     (unexpected-error))))