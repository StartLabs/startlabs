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
        [startlabs.util :only [map-diff cond-class]]
        [startlabs.views.job :only [job-card]]))

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
  (let [date-format "mm/dd/yyyy"]
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
   :website "http://www.squareup.com" :start_date "May 30, 2012" :end_date "August 30, 2012"
   :description "People, location, hard problems, great perks." :contact_info "jobs@squareup.com"})

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
        (job-card sample-job-fields)]
    ]])

(defpartial browse-jobs [has-params?]
  [:div#browse {:class (cond-class "tab-pane" [(not has-params?) "active"])}
    ;; sort by date and location.
    ;; search descriptions and company names
    [:h1 "Browse Startup Jobs"]
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
  (dorun (map empty-rule job-params))
  (not (apply vali/errors? ordered-job-keys)))

(defpage [:post "/jobs"] {:as params}
  (if (valid-job? params)
    (common/layout (ring-request)
      [:h1 "Job submitted."]
      [:p "Check your email for a confirmation link."])
    (render "/jobs" params)
  ))


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