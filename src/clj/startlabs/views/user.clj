(ns startlabs.views.user
  (:require [clojure.string :as str]
            [noir.response :as response]
            [noir.validation :as vali]
            [sandbar.stateful-session :as session]
            [startlabs.models.user :as user]
            [startlabs.models.util :as mu]
            [startlabs.views.common :as common]
            [startlabs.util :as u])
  (:use [compojure.response :only [render]]
        [clojure.core.incubator]
        [environ.core :only [env]]
        [hiccup.def :only [defhtml]]
        [markdown.core :only [md-to-html-string]]))  
;; account-related routes

(defn get-referer [req]
  (get-in req [:headers "referer"]))

(defn login [req]
  (let [referer (get-referer req)]
    (session/session-put! :referer referer)
    (response/redirect (user/get-login-url referer))))

(defn logout [req]
  (session/destroy-session!)
  (response/redirect (get-referer req)))

(defn oauth-callback [state code]
  (let [access-token (:access-token (user/get-access-token code))
        user-info    (user/get-user-info access-token)
        lab-member?  (and (= (last (str/split (:email user-info) #"@")) "startlabs.org")
                          (:verified_email user-info))]
    (if lab-member?
      (do
        (session/session-put! :access-token access-token)
        (doseq [k [:id :email]]
          (session/session-put! k (k user-info))))
      (do
        (session/flash-put! :message
                            [:error "Invalid login. Make sure you're using your email@startlabs.org."])))
    (response/redirect state)))

(def editable-attrs [:name :role :bio :link :studying :graduation_year :picture])

(defhtml user-table [info-map editable?]
  [:table.table
    [:tbody
      (for [key editable-attrs]
        (let [key-str  (name key)
              key-word (common/phrasify key-str)
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
        [:input.btn.btn-primary.offset2 {:type "submit" :value "Submit"}]])
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


;; [:get ["/team/:name" :name #"\w+"]]
(defn get-team-member [name]
  (let [email       (str name "@startlabs.org")
        member-info (user/find-user-with-email email)]
    (common/layout
      [:h1 (or (:name member-info) "User Does Not Exist")]
      (if (some #(not (nil? %)) (vals member-info))
        (user-table member-info false)
        [:p "We could not find a user with the email address: " email]))))

(defhtml team-member [person]
  (let [person    (u/nil-empty-str-values person)
        major     (:studying person)
        grad-year (:graduation_year person)]
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
            (team-member person)
          )]]]
    [:div.clear]))



