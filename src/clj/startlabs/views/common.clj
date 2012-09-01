(ns startlabs.views.common
  (:use [noir.core :only [defpartial]]
        [hiccup.page :only [include-css include-js html5]])
  (:require [noir.session :as session]
            [clojure.string :as str]))

(defn font [name weights]
  (str (str/replace name " " "+")
       (str/join "," weights)))

(defn fonts [args]
  (map (comp apply font) args))

(defpartial layout [& content]
  (let [message (session/flash-get :message)]
    (html5
      [:head
       [:title "startlabs"]
       [:link :rel "stylesheet" :type "text/css" 
        :href (str "http://fonts.googleapis.com/css?family="
                (str/join "|" [(font "Josefin Sans" [400,700])
                               (font "Josefin Slab" [400,700])
                               (font "Crimson Text" [400,"400italic",600,"600italic",700,"700italic"])
                               (font "Ultra")]))
      ]

      [:body
        (if message
          [:div#message message])
       [:div#wrapper
        content]
       (include-js "https://api.filepicker.io/v0/filepicker.js"
                   "/markdown.js"
                   "/jquery.js"
                   "/client.js")])))