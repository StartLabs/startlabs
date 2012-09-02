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
        [noir.request :only [ring-request]]
        [hiccup.core :only [html]]
        [clojure.java.io :only [input-stream]]
        [environ.core :only [env]]
        [markdown :only [md-to-html-string]]))

(defpage "/" []
  (common/layout (ring-request)
    [:h1 "Welcome"]))

(defpage "/login" []
  (response/redirect (user/get-login-url)))

(defpage "/logout" []
  (session/clear!)
  (response/redirect "/"))

(defpage "/oauth2callback" []
  (common/layout (ring-request)
    [:div#loading 
      [:h1 "Fetching credentials..."]]))

(def editable-attrs [:name :role :bio :link :studying :graduation_year :picture])

(defpartial user-table [info-map editable?]
  [:table.table
    [:tbody
      (for [key editable-attrs]
        (let [key-str  (name key)
              key-word (str/capitalize (str/replace key-str "_" " "))
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
              (session/flash-put! :message [:success "Unable to grab the specified picture"]))))
        (user/update-my-info new-facts))))

    (response/redirect "/me"))

(defpage [:get ["/team/:name" :name #"\w+"]] {:keys [name]}
  (let [email       (str name "@startlabs.org")
        member-info (user/find-user-with-email email)]
    (common/layout (ring-request)
      [:h1 (:name member-info)]
      (user-table member-info false))))

(defpage "/jobs" []
  (common/layout (ring-request)
    [:div.btn-group.pull-right
      [:button.btn "Browse Available"]
      [:button.btn "Submit a Job"]]
    [:h1 "Jobs"]
    [:div
      [:p "Work at a startup. Get money. Get paid."]]))

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
                [:p  (md-to-html-string (:bio person))]]])]]]
  ))