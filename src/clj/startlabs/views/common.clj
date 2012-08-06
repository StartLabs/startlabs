(ns startlabs.views.common
  (:use [noir.core :only [defpartial]]
        [hiccup.page :only [include-css include-js html5]])
  (:require [noir.session :as session]))

(defpartial layout [& content]
  (let [message (session/flash-get :message)]
    (html5
      [:head
       [:title "startlabs"]]
      [:body
        (if message
          [:div#message message])
       [:div#wrapper
        content]
       (include-js "/jquery.js")
       (include-js "/client.js")])))