(ns startlabs.views.main
  (:require [startlabs.views.common :as common]
            [startlabs.models.user :as user]
            [startlabs.models.job :as job]
            [noir.session :as session]
            [noir.response :as response]
            [noir.validation :as vali]
            [clojure.string :as str]
            [startlabs.models.util :as mu])
  (:use [clojure.core.incubator]
        [clojure.math.numeric-tower]
        [noir.core :only [defpage defpartial render]]
        [noir.request :only [ring-request]]
        [hiccup.core :only [html]]
        [markdown :only [md-to-html-string]]
        [clj-time.coerce :only [to-long]]
        [startlabs.util :only [map-diff cond-class]]
        [startlabs.views.jobx :only [job-card]])
  (:import java.net.URI))

(defpage "/" []
  (common/layout (ring-request)
    [:h1 "Welcome"]))


;; account-related routes

(defpage "/login" []
  (response/redirect (user/get-login-url)))

(defpage "/logout" []
  (session/clear!)
  (response/redirect "/"))

(defpage "/oauth2callback" []
  (common/layout (ring-request)
    [:h1#loading "Fetching credentials..."]))

(defn phrasify [key-str]
  (str/capitalize (str/replace key-str "_" " ")))

(def editable-attrs [:name :role :bio :link :studying :graduation_year :picture])

(defpartial user-table [info-map editable?]
  [:table.table
    [:tbody
      (for [key editable-attrs]
        (let [key-str  (name key)
              key-word (phrasify key-str)
              value    (key info-map)
              inp-elem (if (= key :bio) :textarea :input)]
          [:tr
            [:td.span3 [:label {:for key-str} key-word]]
            [:td.span4
              (if editable?
                [inp-elem {:id key-str :class "span4" :name key-str 
                           :type "text" :value value :rows 4} 
                  (if (= inp-elem :textarea) value)]
                [:span
                  (if (= key :bio)
                    (md-to-html-string value)
                    value)])]
            [:td
              [:div {:id (str key-str "-preview")}
                (if (= key :picture)
                  [:div
                    (if editable? [:a#new-picture.pull-left.btn {:href "#"} "Upload Picture"])
                    [:img#preview.pull-right {:src value :width 50 :height 50}]])]]
          ]))]])

(defpage [:get ["/me"]] []
  (if-let [my-info (user/get-my-info)]
  	(common/layout (ring-request)
      [:h1 "Edit my info"]
      [:form#me {:action "/me" :method "post"}
        (user-table my-info true)
        [:input.btn.btn-primary.offset2 {:type "submit" :value "Submit"}]])
    (response/redirect "/login")))

; This is really bad: datomic is not returning the schema for bio, role, and studying.
(defpage [:post "/me"] params
  (try
    (let [my-info (user/get-my-info)
          new-facts (map-diff params my-info)]
      (if (not (empty? new-facts))
        ; s3 api is having trouble with pulling a file from an https url (yields SunCertPathBuilderException)
        (if-let [picture-url (-?> (:picture new-facts) (str/replace #"^https" "http"))]
          (let  [file-name   (user/username my-info)]
            (try
              (user/update-my-info (assoc new-facts :picture (mu/save-file-to-s3 picture-url file-name)))
              (catch Exception e
                (session/flash-put! :message [:error "Unable to grab the specified picture"]))))
          (user/update-my-info new-facts))))
    (catch Exception e
      (session/flash-put! :message [:error e])))

  (response/redirect "/me"))


(defpage [:get ["/team/:name" :name #"\w+"]] {:keys [name]}
  (let [email       (str name "@startlabs.org")
        member-info (user/find-user-with-email email)]
    (common/layout (ring-request)
      [:h1 (:name member-info)]
      (user-table member-info false))))



;; jobs

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
            [:td [:label {:for field} (phrasify field-name)]]
            [:td
              ; dispatch input based on type
              (input-for-field field type docs v)
              (vali/on-error field error-item)]]))
      [:tr
        [:td]
        [:td
          [:input.btn.btn-primary {:type "submit"}]]]]])

(def sample-job-fields
  {:position "Frontend Engineering Intern" :company "Square Inc" :location "San Francisco, CA"
   :website "http://www.squareup.com" :start_date "May 30, 2013" :end_date "August 30, 2013"
   :description "Smart people tackling difficult problems at a great location with *nice perks*. \n\nMust have **4+ years** of programming experience.\n\n We prefer candidates who have created or contributed to large open-source projects." 
   :contact_info "jobs@squareup.com"})

(defpartial submit-job [has-params? params]
  [:div#submit {:class (cond-class "tab-pane" [has-params? "active"])}
    [:h1 "Submit a Job"]

    [:form#job-form.row-fluid {:method "post" :action "/jobs"}
      [:div.span6
        [:div.well "In order to submit a job, your email address and 
                    company website domain must match."]
        (fields-from-schema (job/job-fields) ordered-job-keys params)]

      [:div#job-preview.span6.clearfix
        ; generate this in js
        (job-card (if has-params? params sample-job-fields))]
    ]])

(defpartial browse-jobs [has-params?]
  [:div#browse {:class (cond-class "tab-pane" [(not has-params?) "active"])}
    ;; sort by date and location.
    ;; search descriptions and company names
    [:h1 "Browse Startup Jobs"]

    [:div.row-fluid
      [:div#map-toggles.span5 {:data-spy "affix"}
        [:div#map]]

      [:div#job-list.span7]]
  ])

(defpage [:get "/jobs"] {:as params}
  (let [has-params? (not (empty? params))]
    (common/layout (ring-request)
      [:div#job-toggle.btn-group.pull-right {:data-toggle "buttons-radio"}
        [:a {:class (cond-class "btn" [(not has-params?) "active"]) :href "#browse" :data-toggle "tab"} 
          "Browse Available"]
        [:a {:class (cond-class "btn" [has-params? "active"]) :href "#submit" :data-toggle "tab"}
          "Submit a Job"]]
      [:div.clearfix]

      [:div.tab-content
        (browse-jobs has-params?)
        (submit-job has-params? params)])))

(defn empty-rule [[k v]]
  (vali/rule 
    (vali/has-value? v) [k "This field cannot be empty."]))

(defn valid-job? [job-params]
  (let [site-uri    (URI. (:website job-params))
        replace-www (fn [x] (str/replace x "www."  ""))
        site-host (-?> site-uri
                       .getHost
                       replace-www)]
    (dorun (map empty-rule job-params))

    (vali/rule (not (nil? site-host))
      [:website "Must be a valid website." site-host])

    (vali/rule (re-matches (re-pattern (str ".*" site-host "$")) (:email job-params))
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

(defpage [:post "/jobs"] {:as params}
  (let [trimmed-params (into {} (map (fn [[k v]] {k (str/trim v)}) params))]
    (if (valid-job? trimmed-params)
      (try
        @(job/create-job trimmed-params)
        (response/redirect "/jobs/success")
        (catch Exception e
          (session/flash-put! :message [:error (str "Trouble connecting to the database:" e)])
          (render "/jobs" trimmed-params)))

      (do ;invalid job, flash an error message
        (session/flash-put! :message [:error "Please correct the form and resubmit."])
        (render "/jobs" trimmed-params)))))

(defpage "/jobs/success" []
  (common/layout (ring-request)
          [:h1 "Submission Received"]
          [:p "Please, check your email for a confirmation link."]))

;; easy stuff

(defpage "/about" []
  (common/layout (ring-request)
    [:h1 "About Us"]
    [:div
      [:p "We love you."]]))

;; Redirect. Dead links = evil
(defpage "/company" [] (response/redirect "/about"))

(defpage "/team" []
  (common/layout (ring-request)
    [:h1 "Our Team"]
    [:div.row
      [:div.span12
        [:ul.thumbnails
          (for [person (repeat 5 (first (user/find-all-users)))]
            [:li.span3
              [:div.thumbnail
                [:img {:src (:picture person)}]
                [:h3 [:a {:href (:link person)} (:name person)]]
                [:h4  (:role person)]
                [:p  "Studying " (:studying person) ", Class of " (:graduation_year person)]
                [:p  (md-to-html-string (:bio person))]]]
          )]]]
  ))