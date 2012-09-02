(ns startlabs.views.main
  (:require [startlabs.views.common :as common]
            [startlabs.models.user :as user]
            [startlabs.util :as util]
            [noir.session :as session]
            [noir.response :as response]
            [clojure.string :as str]
            [aws.sdk.s3 :as s3])
  (:use [clojure.core.incubator]
        [clojure.math.numeric-tower]
        [noir.core :only [defpage defpartial]]
        [hiccup.core :only [html]]
        [clojure.java.io :only [input-stream]]
        [environ.core :only [env]]
        [markdown :only [md-to-html-string]]))

(defpartial login-info-p [info]
  (if info
    [:div#login-info.pull-right
      [:p "Hey, " [:a {:href (str "/team/" (user/username info))} (:name info)] 
          " ("    [:a {:href "/me"} "edit profile"] ")"]
      [:a#logout.pull-right {:href "/logout"} "Logout"]]
    [:a {:href "/login"} "Login"]))

(defn login-info
  ([]     (login-info-p (user/get-my-info)))
  ([info] (login-info-p info)))

(defpage "/" []
  (common/layout
    [:div#content
      (login-info)]))

(defpage "/login" []
  (response/redirect (user/get-login-url)))

(defpage "/logout" []
  (session/clear!)
  (response/redirect "/"))

(defpage "/oauth2callback" []
  (common/layout
    [:div#loading "Fetching credentials..."]))

(def editable-attrs [:name :role :bio :link :studying :graduation_year :picture])

(defpartial user-table [info-map editable?]
  [:table.table
    (for [key editable-attrs]
      (let [key-str  (name key)
            key-word (str/capitalize (str/replace key-str "_" " "))
            value    (key info-map)
            inp-elem (if (= key :bio) :textarea :input)]
        [:tr
          [:td [:label {:for key-str} key-word]]
          [:td
            (if editable?
              [:div
                [inp-elem {:id key-str :name key-str :type "text" :value value} 
                  (if (= inp-elem :textarea) value)]]
              [:span 
                (if (= key :bio)
                  (md-to-html-string value)
                  value)])]
          [:td
            [:div {:id (str key-str "-preview")}
              (if (= key :picture)
                [:div
                  (if editable? [:a#new-picture {:href "#"} "Upload Picture"])
                  [:img#preview {:src value :width 50 :height 50}]])]]
          ]))])

(defpage [:get ["/me"]] []
  (if-let [my-info (user/get-my-info)]
  	(common/layout
      (login-info my-info)
      [:h1 "Edit my info"]
      [:form#me {:action "/me" :method "post"}
        (user-table my-info true)
        [:input {:type "submit" :value "Submit" :class "btn"}]])
    (response/redirect "/login")))

; http://www.filepicker.io/api/file/l2qAORqsTSaNAfNB6uP1
(defn save-file-to-s3 
  "takes a file from a temporary url, downloads it, and saves to s3, returning
   the url of the file on s3."
  [temp-url file-name]
  (let [aws-creds   {:access-key (env :aws-key) :secret-key (env :aws-secret)}
        bucket-name "startlabs"]
    (with-open [picture-file (input-stream temp-url)]
      (s3/put-object aws-creds bucket-name file-name picture-file)
      (s3/update-object-acl aws-creds bucket-name file-name (s3/grant :all-users :read))
      (str "https://s3.amazonaws.com/" bucket-name "/" file-name))))

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
              (session/flash-put! :message "Unable to grab the specified picture"))))
        (user/update-my-info new-facts))))

    (response/redirect "/me"))

(defpage [:get ["/team/:name" :name #"\w+"]] {:keys [name]}
  (let [email       (str name "@startlabs.org")
        member-info (user/find-user-with-email email)]
    (common/layout
      (login-info)
      [:h1 (:name member-info)]
      (user-table member-info false))))

(defpage "/team" []
  (let [my-info user/get-my-info]
    (common/layout
      [:h1 "Our Team"]
      (for [person (user/find-all-users)]
        [:div
          [:p (str person)]
          [:h2 [:a {:href (:link person)} (:name person)]]
          [:img {:src (:picture person) :width 200 :height 200}]
          [:p  (:role person)]
          [:p  "Studying " (:studying person) ", Class of " (:graduation_year person)]
          [:p  (md-to-html-string (:bio person))]
        ]
      )
    )))