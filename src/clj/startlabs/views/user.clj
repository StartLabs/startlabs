(ns startlabs.views.user
  (:require [clojure.string :as str]
            [noir.response :as response]
            [noir.validation :as vali]
            [sandbar.stateful-session :as session]
            [startlabs.models.analytics :as analytics]
            [startlabs.models.user :as user]
            [startlabs.models.util :as mu]
            [startlabs.views.common :as common]
            [startlabs.util :as u])
  (:use [compojure.response :only [render]]
        [clojure.core.incubator]
        [environ.core :only [env]]
        [hiccup.def :only [defhtml]]
        [hiccup.page :only [include-js]]
        [markdown.core :only [md-to-html-string]]))  
;; account-related routes

(defn login [referer]
  (session/session-put! :referer referer)
  (response/redirect (user/get-login-url referer)))

(defn logout [referer]
  (session/destroy-session!)
  (response/redirect referer))

(defn oauth-callback [state code]
  (if (not (user/verify-code code))
    (session/flash-put! :message
                        [:error "Invalid login. Make sure you're using your email@startlabs.org."]))
  (response/redirect state))

(def editable-attrs [:name :role :bio :link 
                     :studying :graduation-year :picture])

(defhtml user-table [info-map editable?]
  [:table.table
    [:tbody
      (for [key editable-attrs]
        (let [key-str  (name key)
              key-word (u/phrasify key-str)
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

(defn get-me []
  (if-let [my-info (user/get-my-info)]
    (common/layout
     [:h1 "Edit my info"]
     [:form#me {:action "/me" :method "post"}
      (user-table my-info true)
      [:input.btn.btn-primary.offset2 {:type "submit" :value "Submit"}]]
     (include-js "//api.filepicker.io/v1/filepicker.js"))
    (response/redirect "/login")))

(defn correct-link [m]
  (if-let [link (:link m)]
    (conj m {:link (u/httpify-url link)})
    m))

(defn post-me [params]
  (try
    (let [params    (correct-link params) ; prefix link with http:// if necessary
          my-info   (user/get-my-info)
          new-facts (u/map-diff params my-info)]
      (if (not (empty? new-facts))
        ; s3 api is having trouble with pulling a file from an https url (yields SunCertPathBuilderException)
        (if-let [picture-url (-?> (:picture new-facts) (str/replace #"^https" "http"))]
          (let  [file-name   (user/username my-info)]
            (try
              (user/update-my-info 
                (assoc new-facts :picture (mu/save-file-to-s3 picture-url file-name)))
              (catch Exception e
                (session/flash-put! :message [:error "Unable to grab the specified picture"]))))
          (user/update-my-info new-facts))))
    (catch Exception e
      (session/flash-put! :message [:error e])))

  (response/redirect "/me"))

(defn authorize-analytics [referer]
  (if-let [my-info (user/get-my-info)]
    ;; indicate in the database the user id for analytics
    (do
      (analytics/set-analytics-user (user/user-with-id (:id my-info)))
      (session/flash-put! :message [:success "Your account is now set as the analytics provider."]))

    (session/flash-put! :message [:error "You must be logged in to authorize analytics."]))
  (response/redirect referer))

(defn get-team-member [name]
  (let [my-info     (user/get-my-info)
        email       (str name "@startlabs.org")
        member-info (user/find-user-with-email email)
        me?         (= email (:email my-info))]
    (common/layout       
      [:h1 (or (:name member-info) "User Does Not Exist")
       (if me?
         [:a.btn.btn-primary.pull-right 
          {:href "/analytics/authorize"} "Use my credentials for analytics."])]

      (if me?
        ;; show session data for debugging
        [:div
         [:table.table
          (if (empty? (:refresh-token my-info))
            [:div.well
             [:p "Please go to " 
              [:a {:href "https://accounts.google.com/IssuedAuthSubTokens"} 
               "The Google Accounts Site"]
              ", Revoke Access for StartLabs, Log Out, then Log In to get a new refresh token."]])
                                 
          (for [k [:access-token :refresh-token :expiration]]
            [:tr 
             [:td (u/phrasify k)]
             [:td (u/stringify-value (k my-info))]])
          [:tr
           [:td]
           [:td [:a.btn {:href "/me/refresh"} "Refresh Token"]]]]])
          
      (if (some #(not (nil? %)) (vals member-info))
        (user-table member-info false)
        [:p "We could not find a user with the email address: " email]))))

(defn refresh-me [referer]
  (if-let [my-info (user/get-my-info)]
    (do
      (user/refresh-user-with-info my-info)
      (session/flash-put! :message [:success "Your access token has been refreshed."]))
    (do
      (session/flash-put! :message [:error "You are not currently logged in."])))
  (response/redirect referer))

(defhtml team-member [person]
  (let [person    (u/nil-empty-str-values person)
        major     (:studying person)
        grad-year (:graduation-year person)]
    [:li.span6
      [:div.thumbnail
        [:div.holder [:span " "][:img {:src (:picture person)}]]
        [:h3 [:a {:href (:link person)} (:name person)]]
        [:h4 (:role person)]
        [:p  (if major (str "Studying " major))
             (if (and major grad-year) ", ")
             (if grad-year (str "Class of " grad-year))]
        [:p  (md-to-html-string (:bio person))]]]))

(defn team []
  (common/layout
    [:div.row-fluid
      [:div.span12
	    [:h1 "Our Team"]
        [:ul#team.thumbnails
          (for [person (shuffle (user/find-all-users))]
            (team-member person))]]]
    [:div.clear]))