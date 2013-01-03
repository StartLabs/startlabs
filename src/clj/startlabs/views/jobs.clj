(ns startlabs.views.jobs
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clj-time.core :as t]
            [noir.response :as response]
            [noir.validation :as vali]
            [postal.core :as postal]
            [ring.util.codec :as c]
            [ring.util.response :as rr]
            [sandbar.stateful-session :as session]
            [startlabs.models.analytics :as analytics]
            [startlabs.models.job :as job]
            [startlabs.models.user :as user]
            [startlabs.models.util :as mu]
            [startlabs.util :as u]
            [startlabs.views.common :as common])

  (:use [compojure.response :only [render]]
        [clojure.core.incubator]
        [clojure.math.numeric-tower :only [abs ceil]]
        [clojure.tools.logging :only [info]]
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
      [:p "To confirm your listing, " [:strong (:position job-map)]
          ", please visit this link:"]
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
  [:company :position :location :website :fulltime? :start-date :end-date 
   :company-size :description :contact-info :email])

(def hidden-job-keys [:longitude :latitude])

(defmulti input-for-field (fn [field type docs v]
  (keyword (name type))) :default :string)

(defmethod input-for-field :string [field type docs v]
  (let [input-type (if (= field :description) :textarea :input)]
    [input-type {:type "text" :id field :name field :value v
                 :rows 6 :placeholder docs :class "span12"}
      (if (= input-type :textarea) v)]))

(defhtml datepicker [name value date-format]
  [:input.datepicker {:type "text" :data-date-format date-format :value value
                      :id name :name name :placeholder date-format}])

(def jobs-date-fmt   (str/lower-case u/default-date-format))
(def google-date-fmt (str/lower-case analytics/google-date-format))

(defmethod input-for-field :instant [field type docs v]
  (datepicker field v jobs-date-fmt))

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
            [:td [:label {:for field} (u/phrasify field-name)]]
            [:td
              ; dispatch input based on type
              (input-for-field field type docs v)
              (vali/on-error field error-item)]]))
      [:tr
        [:td]
        [:td
          [:input.btn.btn-primary {:type "submit" :value "Submit"}]]]]])

(def sample-job-fields
  {:position "Lab Assistant" :company "StartLabs" 
   :location "Cambridge, Massachusetts"
   :website "http://www.startlabs.org" 
   :start-date "May 30, 2013" :end-date "August 30, 2013"
   :description "Smart people tackling difficult problems at a great location with *nice perks*.
\n\nMust have **4+ years** of lab experience.
\n\nWe prefer candidates who wear green clothing."
   :contact-info "contact@startlabs.org"})

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
          [:a {:href (str "mailto:jobs@startlabs.org?subject=Jobs List Request: [Your Company Name]&body=" job-list-email-body)} "email us"] 
          " for consideration for the Jobs List."])
       (fields-from-schema (job/job-fields) ordered-job-keys params)]

      (if editing?
        [:input {:type "hidden" :name "secret" :value (:secret params)}])

      (for [hidden-key hidden-job-keys]
        (let [hname (name hidden-key)]
          [:input {:type "hidden" :name hname :id hname 
                   :value (hidden-key params)}]))

      [:div.span6.clearfix.thumbnail
       [:div#job-preview
        (job-card (if has-params? params sample-job-fields) false)]
       [:h3.centered "Job Location Preview (Drag Pin to Relocate)"]
       [:div#job-location]]
      ]]))

(defn parse-job-filters [filters]
  (apply merge (for [[k v] filters]
                 (u/re-case (name k)
                            #"date" {k (u/parse-date v)}
                            #"size" {k (u/inty v)}
                            #"show" {k (or (empty? v)
                                           (Boolean. v))}))))

;; [:post /jobs/filters]
(defn post-job-filters [params]
  (let [parsed-filters (parse-job-filters params)]
    (session/session-put! :filters parsed-filters)
    params))

;; add arguments for sort and entries-per-page and current page...
;; unfortunately, cannot do negations currently in datomic where clauses,
;; so we must specify not removed here.
(defn get-all-jobs [sort-field filters]
  (sort-by sort-field
           (filter #(not= (:removed? %) "true")
                   (job/find-upcoming-jobs filters))))

;; COMMENT THIS IN PRODUCTION
(comment
  (defn get-all-jobs [sort-field filters]
    (map u/stringify-values
         (sort-by sort-field
                  (repeatedly 100 #(u/fake-job))))))

(defn filter-jobs [query filters]
  (let [sort-field (keyword (session/session-get :sort-field))
        all-jobs   (get-all-jobs sort-field filters)]
    (if (empty? query)
      all-jobs
      (filter (fn [job]
        (some #(re-find (re-pattern (str "(?i)" query)) %) 
              (map job [:position :company :location])))
        all-jobs))))

(defn jobs-on-page [jobs page page-size]
  (take page-size (drop (* (dec page) page-size) jobs)))

(defhtml input-range [field classes filters]
  (let [field-name  (name field)
        min-field   (str "min-" field-name)
        max-field   (str "max-" field-name)
        field-label (u/phrasify field)
        class-str   (apply str (interpose " " classes))
        min-val     (or ((keyword min-field) filters) "")
        max-val     (or ((keyword max-field) filters) "")]
    [:div.control-group
     [:label.control-label {:for min-field} field-label]
     [:div.controls
      (for [[elem val placeholder] [[min-field min-val "Min"] 
                                    [max-field max-val "Max"]]]
        [:input
         {:type "text" :id elem :name elem :class class-str
          :placeholder placeholder :value (u/stringify-value val)}])]]))

(defhtml job-filters [filters]
  [:div#filter.modal.hide.fade {:tabindex "-1" :role "dialog" 
                                :aria-labelledby "filter-label"
                                :aria-hidden "true"}
   [:div.modal-header
    [:button.close {:type"button" :data-dismiss "modal" 
                    :aria-hidden "true"} "x"]
    [:h2#filter-label "Filtering Options"]]

   [:form.form-horizontal {:action "/jobs/filters" :method "POST"}
    [:div.modal-body
     [:div.control-group
      [:div.controls
       [:div.btn-group {:data-toggle "buttons-checkbox"}
        (for [kw [:show-fulltime :show-internships]]
          (let [id  (name kw)
                val (kw filters)]
            [:a {:href "#" :id id
                 ;; kw could be nil, in which case, resort to true
                 :class (u/cond-class "btn" [(not= val false) "active"])}
             (u/phrasify kw)]))]]]

     (for [kw [:show-fulltime :show-internships]]
       [:input {:name (name kw) :type "hidden" 
                :value (str (or (nil? filters)
                                (true? (kw filters))))}])

     (input-range :company-size ["input-small"] filters)
     (input-range :start-date   ["datepicker"] filters)
     (input-range :end-date     ["datepicker"] filters)]
    
    [:div.modal-footer
     [:button.btn {:data-dismiss "modal" :aria-hidden "true"} "Close"]
     [:button.btn.btn-primary "Save Changes"]]]])

;; make browse-jobs take a page as input,
;; operate on pages of jobs at a time...
(defhtml browse-jobs [q sort-field page-jobs list-html]
  (let [filters (session/session-get :filters)]
    [:div#browse
     ;; sort by date and location.
     ;; search descriptions and company names
     [:h1 "Browse Startup Jobs"]

     (job-filters filters)
     
     [:div#map-box.row-fluid
      [:div#map.thumbnail]
      [:div.navbar
       [:div.navbar-inner
        [:form.navbar-search.pull-left {:method "GET"}
         [:input#job-search.search-query
          {:type "text" :placeholder "Search" :name "q" :value q}]]

        [:div.nav-collapse.collapse
         [:ul.nav.pull-right
          [:li [:a#filter-toggle {:href "#filter" :data-toggle "modal"} 
                [:i.icon-filter] "Filter"]]

          [:li.dropdown
           [:a#sort-toggle.dropdown-toggle
            {:href "#" :data-toggle "dropdown"} 
            [:i.icon-list] "Sort" [:b.caret]]
           [:ul#sort.dropdown-menu {:role "menu" :arial-labelledby "sort-toggle"}
            (for [field [:company :company-size :start-date :end-date 
                         :longitude :latitude]]
              [:li {:class (if (= (keyword sort-field) field) "active")}
               [:a {:href "#" :data-field (name field)} (u/phrasify field)]])]]

          [:li [:a#map-toggle {:href "#map"}
                [:i.icon-map-marker] "Toggle Map"]]]]
        ]]]

     [:div#job-container.row-fluid
      (if (empty? page-jobs)
        [:h1 "Sorry, no jobs posted currently. Come back later."])

      list-html]
     
     [:script#job-data
      (str "window.job_data = " (json/write-str page-jobs) ";")]
     ]))

(defn jobs-and-list-html [{:keys [q page page-size sort-field] 
                           :or {q "" page 1 page-size 20 
                                sort-field (or (session/session-get :sort-field)
                                               "company")}}]
  (session/session-put! :sort-field sort-field)
  (let [valid-page?  (not (nil? (re-matches #"[1-9]\d+" "10")))
        page         (Integer. (if valid-page? page 1))
        page-size    (Integer. page-size)
        filters      (session/session-get :filters)
        jobs         (filter-jobs q filters)
        page-jobs    (jobs-on-page jobs page page-size)
        show-delete? (user/logged-in?)
        page-count   (ceil (/ (count jobs) page-size))
        body         (job-list page-jobs show-delete? q page page-count)]
    [page-jobs body]))

;; [:get /jobs.edn?q=...]
(defn job-search [params]
  (let [[jobs list-html] (jobs-and-list-html params)
        body (str {:html (html list-html) 
                   :jobs jobs})]
    (-> (rr/response body)
        (rr/content-type "text/edn"))))

(defn split-sites [sitelist]
  (str/split sitelist #"\s+"))

(defhtml nav-buttons [active-tab]
   [:div#job-toggle.btn-group.pull-right
    [:a {:class (u/cond-class "btn" [(= active-tab :jobs) "active"]) 
         :href "/jobs"} "Browse Available"]
    (if (user/logged-in?)
      [:a {:class (u/cond-class "btn" [(= active-tab :whitelist) "active"]) 
           :href "/whitelist"} "Whitelist"])
    [:a {:class (u/cond-class "btn" [(= active-tab :new-job) "active"]) 
         :href "/job/new"} "Submit a Job"]])

;; [:get /jobs]
(defn get-jobs [{:keys [q] :as params}]
  (let [[page-jobs list-html] (jobs-and-list-html params)
        sort-field (session/session-get :sort-field)]
    (common/layout
     (nav-buttons :jobs)
     [:div (browse-jobs q sort-field page-jobs list-html)])))


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
        whitelist (second (job/get-whitelist))
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

    (vali/rule (vali/valid-number? (:company-size job-params))
               [:company-size "The company size must be a valid number."])

    (try
      (let [err [:location "The latitude/longitude of the location are invalid."]]
        (vali/rule (and (<= (abs (Double/parseDouble (:latitude job-params))) 90)
                        (<= (abs (Double/parseDouble (:longitude job-params))) 180))
                   err))
        (catch Exception err))

    (doseq [date [:start-date :end-date]]
      (vali/rule
       (u/parse-date (date job-params))
       [date "Invalid date."]))

  ; make sure the end date comes after the start
    (vali/rule
     (let [[start end] (map #(u/parse-date (% job-params)) 
                            [:start-date :end-date])]
       (and (and start end) 
            (= -1 (apply compare (map to-long [start end])))))
     [:end-date "The end date must come after the start date."])

    (not (apply vali/errors? ordered-job-keys))))

(defn fix-job-params [params]
  (let [website    (:website params)
        fulltime?  (if (= (:fulltime? params) "true") true false)
        start-date (u/parse-date (:start-date params))
        end-date   (if start-date
                     (t/plus start-date (t/months 6)) 
                     (t/plus (t/now) (t/months 6)))]
    (conj params
      {:website (if (not (empty? website)) 
                  (u/httpify-url website) 
                  "")
       :fulltime? fulltime?
       :end-date (if fulltime? 
                   (u/unparse-date end-date)
                   (:end-date params))})))

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
              (response/redirect "/job/success"))
            (do
              (session/flash-put! 
                :message [:error "Trouble sending confirmation email:" 
                          (:message email-res)])
              (render get-new-job fixed-params))))

        (catch Exception e
          (session/flash-put! :message [:error (str "Trouble connecting to the database:" e)])
          (render get-new-job fixed-params)))

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
  (if (user/logged-in?)
    (common/layout
     (if-let [job-map (job/job-map id)]
       ;; here we find the existing secret or create a new one
       (let [map-secret (:secret job-map)
             secret     (if (empty? map-secret) 
                          (job/update-job-field id :job/secret (u/uuid))
                          map-secret)
             secret-map (assoc job-map :secret secret)]
         (send-edit-email secret-map)
         [:div
          [:h1 "Edit Link Sent"]
          [:p "The author can now check their email for a link to edit the job listing."]])
       (job-not-found)))
    ;;else
    (response/redirect "/jobs")))

(defn can-edit-job? [id & candidates]
  (let [job-secret (job/job-secret id)]
    (or (user/logged-in?)
        (some true? (map #(= % job-secret) candidates)))))

(defn analytics-data [job-id start end]
  (try
    (let [session-secret (session/session-get :job-secret)
          data (if (can-edit-job? job-id session-secret)
                 (analytics/google-chart-map job-id start end)
                 {:error "You do not have permissions to view this job's analytics data."})]
      data)
    (catch Exception e
      {:error (.getMessage e)})))

(defn job-edit-tabs [id active]
  [:div.btn-group.pull-right
   [:a {:href (str "/job/" id) 
        :class (u/cond-class "btn" [(= active :edit) "active"])} "Edit Job"]
   [:a {:href (str "/job/" id "/analytics")
        :class (u/cond-class "btn" [(= active :analytics) "active"])} "Analytics"]])

(defhtml job-analytics-view [id data]
  (let [start-date (:start-date data)
        end-date   (:end-date data)]
    (common/layout
     [:div#analytics
      (job-edit-tabs id :analytics)
      [:h1 "Job Analytics"]

      [:div.row-fluid
       [:form.form-horizontal.span6 {:method "GET" 
                                     :action (str "/job/" id "/analytics.edn")}
        (for [[n v l] [["a-start-date" start-date "Start Date"] 
                       ["a-end-date" end-date "End Date"]]]
          [:div.control-group
           [:label.control-label {:for n} l]
           [:div.controls
            (datepicker n v google-date-fmt)]])]

       [:div.span6
        [:div.row-fluid
         [:div.span6.thumbnail
          [:h1#unique-events.centered (:unique-events data)]
          [:h2.centered "Unique Events"]]
         [:div.span6.thumbnail
          [:h1#total-events.centered (:total-events data)]
          [:h2.centered "Total Events"]]]]

       [:div#analytics-chart.span12]]

      (if (not (:error data))
        [:script#analytics-data {:type "text/edn"}
         (str data)])])))

;; [:get /job/:id/analytics.edn?a-start-date=...&a-end-date=...
(defn analytics-search [id & [start end]]
  (let [session-secret (session/session-get :job-secret)
        data (analytics-data id start end)]
    (-> (rr/response (str data))
        (rr/content-type "text/edn")
        (rr/status (if (:error data) 400 200)))))

;; [:get /job/:id/analytics]
(defn get-job-analytics [id & [start end]]
  (let [data (analytics-data id start end)]
    (if (:error data)
      (do
        (session/flash-put! :message [:error (:error data)])
        (response/redirect "/jobs"))

    (job-analytics-view id data))))

;; [:get "/job/:id"]
(defn get-edit-job [{:keys [id secret] :as params}]
  (if secret (session/session-put! :job-secret secret))
  (let [secret (or secret (session/session-get :job-secret))]
    (if (can-edit-job? id secret)
      (common/layout
       ;; params either contains previously submitted (invalid) params that require editing
       ;; or it only contains {:id id :secret secret} if the user just arrived at the edit page.
       [:div#edit
        (job-edit-tabs id :edit)
        (if-let [job-map (if (> (count params) 2)
                           params
                           (job/job-map id))]
          (let [secret-map (assoc job-map :secret (:secret params))]
            (submit-job secret-map))
          ;; else
          (job-not-found))])
      ;else
      (do
        (session/flash-put! :message [:error "You cannot edit this job."])
        (response/redirect "/jobs")))))

(defn flash-error-and-render [error job-id params]
  (session/flash-put! :message [:error error])
  (render get-edit-job params))

;;[:post "/job/:id"]
;; Check for job secret in: session and query params.
;; Also check if user is a team member (logged-in?).
;; If none of these conditions are met, the poster should not be
;; allowed to update the job.
(defn post-edit-job [{:keys [id secret] :as params}]
  (let [session-secret (session/session-get :job-secret)
        params         (if (not (empty? (:secret params)))
                         params
                         (assoc params :secret session-secret))
        fixed-params   (trim-and-fix-params params)]
    (if (can-edit-job? id (:secret fixed-params))
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



;; [:get /whitelist]
(defn get-whitelist []
  (common/layout
   (nav-buttons :whitelist)
   (let [whitelist (second (job/get-whitelist))]
     [:div#whitelist
      [:h2 "Company Whitelist"]

      [:div.well
       [:p "Just put each company domain name on a new line."]
       [:p "Do not include the http:// or the www."]]

      [:form {:action "/whitelist" :method "post"}
       [:div.span6.pull-left
        [:textarea#the-list.span6 {:name "the-list" :rows 20 
                                   :placeholder "google.com"}
         whitelist]
        [:input.btn.btn-primary.span3 {:type "submit"}]]

       [:div.span5
        [:input.btn.btn-primary.span3 {:type "submit"}]]]
      ])))

;; [:post "/whitelist"]
(defn post-whitelist [the-list]
  (if (user/logged-in?)
    (do
      (job/update-whitelist the-list)
      (session/flash-put! :message [:success "The whitelist has been updated successfully."])
      (response/redirect "/whitelist"))
    ;else
    (do
      (session/flash-put! :message [:error "You must be logged in to change the whitelist."])
      (response/redirect "/jobs"))))