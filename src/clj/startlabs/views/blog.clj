(ns startlabs.views.blog
  (:require [startlabs.views.common :as common])
  (:use [hiccup.def :only [defhtml]]))

;; "/blog"
(defn blog []
  (common/layout
    [:div.row-fluid
     [:div.span12
      [:h1 "Blog"]
	  [:script {:src "http://startlabs.tumblr.com/js"}]
    ]]))