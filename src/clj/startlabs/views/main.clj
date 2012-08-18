(ns startlabs.views.main
  (:require [startlabs.views.common :as common]
            [startlabs.models.user :as user]
            [noir.session :as session]
            [noir.response :as response])
  (:use [noir.core :only [defpage]]
        [hiccup.core :only [html]]))

(defpage "/" []
  (let [user-info (user/get-my-info)]
    (common/layout
      [:div#content
        (if user-info
          [:div#user-info
            [:p "Hey, " [:a {:href "/users/me"} (:name user-info)]]
            [:a#logout {:href "/logout"} "Logout"]]
          [:a {:href "/login"} "Login"])])))

(defpage "/login" []
  (response/redirect (user/get-login-url)))

(defpage "/logout" []
  (session/clear!)
  (response/redirect "/"))

(defpage "/oauth2callback" []
  (common/layout
    [:div#loading "Fetching credentials..."]))

(defpage "/users/me" []
  (let [user-info (user/get-my-info)]
  	(common/layout
      [:table#me
		(for [key (keys user-info)]
       		[:tr
             [:td (str key)]
             [:td (key user-info)]])])))