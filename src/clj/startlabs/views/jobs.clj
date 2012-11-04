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
        [startlabs.views.jobx :only [job-card job-list]])
  (:import java.net.URI))

;; jobs

(defpartial job-email-body [job-map]
  (let [conf-link (str (u/home-uri) (url-for confirm-job {:id (:id job-map)}))]
    [:div
      [:p "Hey there,"]
      [:p "Thanks for submitting to the StartLabs jobs list."]
      [:p "To confirm your listing, " [:strong (:position job-map)] ", please visit this link:"]
      [:p [:a {:href conf-link} conf-link]]
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
  [:div#submit {:class (u/cond-class "tab-pane" [has-params? "active"])}
    [:h1 "Submit a Job"]

    [:form#job-form.row-fluid {:method "post" :action "/jobs"}
      [:div.span6
        [:div.well "In order to submit a job, your email address and 
                    company website domain must match."]
        (fields-from-schema (job/job-fields) ordered-job-keys params)]

      [:div#job-preview.span6.clearfix
        ; generate this in js
        (job-card (if has-params? params sample-job-fields) false)]
    ]])


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

        [:div.row-fluid
          (if (empty? all-jobs)
            [:h1 "No jobs posted yet. Come back later!"])
          
          (job-list all-jobs (first all-jobs))

          [:div#active-job.span6.pull-right
            (for [job all-jobs]
              [:div {:id (:id job)}
                (job-card job show-delete?)])]]

        [:script#job-data
          (str "window.job_data = " (json/json-str all-jobs) ";")]
     ]))

(defn split-sites [sitelist]
  (str/split sitelist #"\s+"))

(defpage [:post "/whitelist"] {:keys [the-list]}
  (common/layout
   [:h2 "Success, the Whitelist is now:"]
   (for [site (sort (split-sites the-list))]
     [:div site])))

(defpartial whitelist [has-params?]
  [:div#whitelist {:class "tab-pane"}
   [:h2 "Company Whitelist"]
   [:div.well
    [:p "Just put each company domain name on a new line."]
    [:p "Do not include the http:// or the www."]]
   [:form {:action "/whitelist" :method "post"}
    [:div.span6.pull-left
     [:textarea#the-list.span6 {:name "the-list" :rows 20}]
     [:input.btn.btn-primary.span3 {:type "submit"}]]
    [:div.span5
     [:input.btn.btn-primary.span3 {:type "submit"}]]]
   ])

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
       (whitelist has-params?)
       (submit-job has-params? params)])))

(defn empty-rule [[k v]]
  (vali/rule 
    (vali/has-value? v) [k "This field cannot be empty."]))

(defn valid-job? [job-params]
  (let [site-uri    (URI. (or (:website job-params) ""))
        replace-www (fn [x] (str/replace x "www."  ""))
        site-host   (-?> site-uri .getHost replace-www)]
    (dorun (map empty-rule job-params))

    (vali/rule (not (nil? site-host))
      [:website "Must be a valid website." site-host])

    ; also allow submissions from startlabs members
    (vali/rule (or (re-matches (re-pattern (str ".*" site-host "$")) (:email job-params))
                   (re-matches #".*@startlabs.org$" (:email job-params)))
      [:email "Your email address must match the company website."])

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

(defpage [:post "/jobs"] {:as params}
  (let [trimmed-params (u/trim-vals params)
        fixed-params   (fix-job-params params)]
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
        (session/flash-put! :message [:error "Please correct the form and resubmit."])
        (render "/jobs" fixed-params)))))

(defpage "/jobs/success" []
  (common/layout
    [:h1 "Submission Received"]
    [:p "Please, check your email for a confirmation link."]))

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
      [:div
        [:h1 "Something went wrong."]
        [:p  "Sorry for the inconvenience. Please contact the "
          (common/webmaster-link "webmaster") "."]])))
