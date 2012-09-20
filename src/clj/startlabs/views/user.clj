(ns startlabs.views.user
  (:require [startlabs.views.common :as common]
            [startlabs.models.user :as user]
            [noir.session :as session]
            [noir.response :as response]
            [noir.validation :as vali]
            [clojure.string :as str]
            [startlabs.models.util :as mu]
            [startlabs.util :as u])

  (:use [clojure.core.incubator]
        [noir.core :only [defpage defpartial render url-for]]
        [noir.request :only [ring-request]]
        [markdown :only [md-to-html-string]]))

;; account-related routes

(defpage "/login" []
  (let [referer (:referer (ring-request))]
    (response/redirect (user/get-login-url referer))))

(defpage "/logout" []
  (session/clear!)
  (response/redirect "/"))

(defpage "/oauth2callback" []
  (let [referer (:referer (ring-request))]
    (common/layout
      [:h1#loading "Fetching credentials..."])))

(def editable-attrs [:name :role :bio :link :studying :graduation_year :picture])

(defpartial user-table [info-map editable?]
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

(defpage [:get ["/me"]] []
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

(defpage [:post "/me"] params
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


(defpage [:get ["/team/:name" :name #"\w+"]] {:keys [name]}
  (let [email       (str name "@startlabs.org")
        member-info (user/find-user-with-email email)]
    (common/layout
      [:h1 (or (:name member-info) "User Does Not Exist")]
      (if (some #(not (nil? %)) (vals member-info))
        (user-table member-info false)
        [:p "We could not find a user with the email address: " email]))))

(defpartial team-member [person]
  (let [person    (u/nil-empty-str-values person)
        major     (:studying person)
        grad-year (:graduation_year person)]
    [:li.span3
      [:div.thumbnail
        [:img {:src (:picture person)}]
        [:h3 [:a {:href (:link person)} (:name person)]]
        [:h4 (:role person)]
        [:p  (if major (str "Studying " major))
             (if (and major grad-year) ", ")
             (if grad-year (str "Class of " grad-year))]
        [:p  (md-to-html-string (:bio person))]]]))

(defpage "/team" []
  (common/layout
    [:h1 "Our Team"]
    [:div.row
      [:div.span12
        [:ul#team.thumbnails
          (for [person (sort-by #(:family_name %) (user/find-all-users))]
            (team-member person)
          )]]]))



