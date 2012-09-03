(ns startlabs.views.main
  (:require [startlabs.views.common :as common]
            [startlabs.models.user :as user]
            [startlabs.models.job :as job]
            [startlabs.util :as util]
            [noir.session :as session]
            [noir.response :as response]
            [clojure.string :as str])
  (:use [clojure.core.incubator]
        [clojure.math.numeric-tower]
        [noir.core :only [defpage defpartial]]
        [noir.request :only [ring-request]]
        [hiccup.core :only [html]]
        [markdown :only [md-to-html-string]]
        [startlabs.models.util :only [save-file-to-s3]]))

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

; right now, a user could hypothetically add additional post params...
(defpage [:post "/me"] params
  (let [my-info (user/get-my-info)
        new-facts (util/map-diff params my-info)]
    (if (not (empty? new-facts))
      ; s3 api is having trouble with pulling a file from an https url (yields SunCertPathBuilderException)
      (if-let [picture-url (-?> (:picture new-facts) (str/replace #"^https" "http"))]
        (let  [file-name   (user/username my-info)]
          (try
            (user/update-my-info (assoc new-facts :picture (save-file-to-s3 picture-url file-name)))
            (catch Exception e
              (session/flash-put! :message [:success "Unable to grab the specified picture"]))))
        (user/update-my-info new-facts))))

    (response/redirect "/me"))

(defpage [:get ["/team/:name" :name #"\w+"]] {:keys [name]}
  (let [email       (str name "@startlabs.org")
        member-info (user/find-user-with-email email)]
    (common/layout (ring-request)
      [:h1 (:name member-info)]
      (user-table member-info false))))



;; jobs

(defpartial browse-jobs []
  [:div#browse.tab-pane.active
    ;; sort by date and location.
    ;; search descriptions and company names
    [:h1 "Browse Startup Jobs"]])

(defpartial form-from-schema [schema excluded-attrs]
  [:form.form-horizontal
    (for [[field type] schema]
      (if (not (contains? excluded-attrs field))
        (let [field-name (name field)]
          [:div.control-group
            [:label.control-label {:for field} (phrasify field-name)]
            [:div.controls
              [:input {:type "text" :id field :name field}]]])))])

(defpartial submit-job []
  [:div#submit.tab-pane
    [:h1 "Submit a Job"]
    (form-from-schema (job/job-fields) ["confirmed?"])])

(defpage "/jobs" []
  (common/layout (ring-request)
    [:div#job-toggle.btn-group.pull-right {:data-toggle "buttons-radio"}
      [:a.btn.active {:href "#browse" :data-toggle "tab"} "Browse Available"]
      [:a.btn {:href "#submit" :data-toggle "tab"} "Submit a Job"]]

    [:div.tab-content
      (browse-jobs)
      (submit-job)]))



;; easy stuff

(defpage "/about" []
  (common/layout (ring-request)
    [:h1 "About Us"]
    [:div
      [:p "We love you."]]))

(defpage "/company" [] (response/redirect "/about"))

(defpage "/team" []
  (common/layout (ring-request)
    [:h1 "Our Team"]
    [:div.row
      [:div.span12
        [:ul.thumbnails
          (for [person (repeat 7 (first (user/find-all-users)))]
            [:li.span3
              [:div.thumbnail
                [:img {:src (:picture person)}]
                [:h3 [:a {:href (:link person)} (:name person)]]
                [:h4  (:role person)]
                [:p  "Studying " (:studying person) ", Class of " (:graduation_year person)]
                [:p  (md-to-html-string (:bio person))]]]
          )]]]
  ))