(ns startlabs.views.main
  (:require [startlabs.views.common :as common]
            [startlabs.models.user :as user]
            [noir.session :as session]
            [noir.response :as response]
            [clojure.string :as str])
  (:use [noir.core :only [defpage defpartial]]
        [hiccup.core :only [html]]))

(defpartial user-info-p [info]
  (if info
    [:div#user-info
      [:p "Hey, " [:a {:href "/users/me"} (:name info)]]
      [:a#logout {:href "/logout"} "Logout"]]
    [:a {:href "/login"} "Login"]))

(defn user-info
  ([] (user-info-p (user/get-my-info)))
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

(defpage "/users/me" []
  (let [my-info (user/get-my-info)]
  	(common/layout
      (user-info my-info)
      [:table#me
    		(for [key (keys my-info)]
          (let [key-str (name key)
                key-word (str/capitalize (str/replace key-str "_" " "))]
         		[:tr
              [:td [:label {:for key-str} key-word]]
              [:td
                [:a.editable {:href "#"} (key my-info)]]]))])))