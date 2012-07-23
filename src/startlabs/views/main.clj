(ns startlabs.views.main
  (:require [startlabs.views.common :as common]
            [startlabs.models.user :as user]
            [noir.response :as response])
  (:use [noir.core :only [defpage]]
        [hiccup.core :only [html]]))

(defpage "/" []
  (common/layout
    [:div#content
      [:a {:href "/login"} "Login"]]))

(defpage "/login" []
  (response/redirect (user/get-login-url)))

(defpage "/oauth2callback" []
  (common/layout))