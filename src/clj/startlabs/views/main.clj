(ns startlabs.views.main
  (:require [startlabs.views.common :as common]
            [noir.response :as response])
  (:use [noir.core :only [defpage]]
        [markdown :only [md-to-html-string]]))

(defpage "/" []
  (common/layout
    [:h1 "Welcome"]))

(defpage "/about" []
  (common/layout
    [:h1 "About Us"]
    [:div
      [:p "We love you."]]))

;; Redirect. Dead links = evil
(defpage "/company" [] (response/redirect "/about"))