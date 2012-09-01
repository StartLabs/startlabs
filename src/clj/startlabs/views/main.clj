(ns startlabs.views.main
  (:require [startlabs.views.common :as common]
            [startlabs.models.user :as user]
            [startlabs.util :as util]
            [noir.session :as session]
            [noir.response :as response]
            [clojure.string :as str])
  (:use [noir.core :only [defpage defpartial]]
        [hiccup.core :only [html]]))

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
              [:img#preview {:src value :width 50 :height 50}])
          ]]))])

(defpage [:get ["/me"]] []
  (let [my-info (user/get-my-info)]
  	(common/layout
      (user-info my-info)
      [:h1 "Edit my info"]
      [:form#me {:action "/team/me" :method "post"}
        (user-table my-info true)
        [:input {:type "submit" :value "Submit"}]])))

; right now, a user could hypothetically add additional post params...
(defpage [:post "/me"] params
  (let [my-info (user/get-my-info)
        new-facts (util/map-diff params my-info)]
    (if (not (empty? new-facts))
      (user/update-my-info new-facts))
    (response/redirect "/team/me")))

(defpage [:get ["/team/:name" :name #"\w+"]] {:keys [name]}
  (let [email       (str name "@startlabs.org")
        member-info (user/find-user-with-email email)]
    (common/layout
      (user-info)
      [:h1 (:name member-info)]
      (user-table member-info false))))