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
        [environ.core :only [env]]))

(defpartial user-info-p [info]
  (if info
    [:div#user-info
      [:p "Hey, " [:a {:href "/me"} (:name info)]]
      [:a#logout {:href "/logout"} "Logout"]]
    [:a {:href "/login"} "Login"]))

(defn user-info
  ([]     (user-info-p (user/get-my-info)))
  ([info] (user-info-p info)))

(defpage "/" []
  (common/layout
    [:div#content
      (user-info)]))

(defpage "/login" []
  (response/redirect (user/get-login-url)))

(defpage "/logout" []
  (session/clear!)
  (response/redirect "/"))

(defpage "/oauth2callback" []
  (common/layout
    [:div#loading "Fetching credentials..."]))

(def editable-attrs [:link :studying :name :bio :graduation_year :picture])

(defpartial user-table [info-map editable?]
  [:table
    (for [key editable-attrs]
      (let [key-str  (name key)
            key-word (str/capitalize (str/replace key-str "_" " "))
            value    (key info-map)
            inp-elem (if (= key :bio) :textarea :input)]
        [:tr
          [:td [:label {:for key-str} key-word]]
          [:td
            (if editable?
              [inp-elem {:id key-str :name key-str :type "text" :value value} 
                (if (= inp-elem :textarea) value)]
              [:span value])
            (if (= key :picture)
              [:div
                [:a#new-picture {:href "#"} "Upload Picture"]
                [:img#preview {:src value :width 50 :height 50}]])
          ]]))])

(defpage [:get ["/me"]] []
  (if-let [my-info (user/get-my-info)]
  	(common/layout
      (user-info my-info)
      [:h1 "Edit my info"]
      [:form#me {:action "/me" :method "post"}
        (user-table my-info true)
        [:input {:type "submit" :value "Submit"}]])
    (response/redirect "/login")))

; http://www.filepicker.io/api/file/l2qAORqsTSaNAfNB6uP1
(defn save-file-to-s3 
  "takes a file from a temporary url, downloads it, and saves to s3, returning
   the url of the file on s3."
  [temp-url]
  (let [aws-creds   {:access-key (env :aws-key) :secret-key (env :aws-secret)}
        file-hash   (str (abs (hash temp-url)))
        bucket-name "startlabs"]
    (with-open [picture-file (input-stream temp-url)]
      (s3/put-object aws-creds bucket-name file-hash picture-file)
      (s3/update-object-acl aws-creds bucket-name file-hash (s3/grant :all-users :read))
      (str "https://s3.amazonaws.com/" bucket-name "/" file-hash))))

; right now, a user could hypothetically add additional post params...
(defpage [:post "/me"] params
  (let [my-info (user/get-my-info)
        new-facts (util/map-diff params my-info)]
    (if (not (empty? new-facts))
      ; s3 api is having trouble with https (yields SunCertPathBuilderException)
      (if-let [picture-url (-?> (:picture new-facts) (str/replace #"^https" "http"))]
        (user/update-my-info (assoc new-facts :picture (save-file-to-s3 picture-url)))
        (user/update-my-info new-facts))))

    (response/redirect "/me"))

(defpage [:get ["/team/:name" :name #"\w+"]] {:keys [name]}
  (let [email       (str name "@startlabs.org")
        member-info (user/find-user-with-email email)]
    (common/layout
      (user-info)
      [:h1 (:name member-info)]
      (user-table member-info false))))