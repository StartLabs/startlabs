(ns startlabs.views.common
  (:use [noir.core :only [defpartial]]
        [hiccup.page :only [include-css include-js html5]])
  (:require [noir.session :as session]
            [clojure.string :as str]
            [startlabs.models.user :as user]))

(defn font
  ([name] (font name nil))
  ([name weights]
    (str (str/replace name " " "+") ":"
         (str/join "," weights))))

(defn fonts [& args]
  (map #(apply font %) args))

(defn font-link [& faces]
  (str "http://fonts.googleapis.com/css?family="
    (str/join "|" (apply fonts faces))))

(defpartial login-info []
  (if-let [info (user/get-my-info)]
    [:div#login-info.pull-right
      [:p "Hey, " [:a {:href (str "/team/" (user/username info))} (:name info)] 
          " ("    [:a {:href "/me"} "edit profile"] ")"]
      [:a#logout.pull-right {:href "/logout"} "Logout"]]
    [:a {:href "/login"} "Login"]))

(defpartial layout [& content]
  (let [message (session/flash-get :message)]
    (html5
      [:head
        [:title "startlabs"]
        ; switch to css before deploying
        [:link {:rel "stylesheet/less" :type "text/css" :href "/css/style.less"}]
        [:link {:rel "stylesheet" :type "text/css" 
               :href (font-link ["Josefin Sans" [400,700]]
                                ["Josefin Slab" [400,700]]
                                ["Crimson Text" [400,"400italic",600,"600italic",700,"700italic"]]
                                ["Ultra"])}]

        (include-css "/bootstrap/css/bootstrap.min.css"
                     "/bootstrap/css/bootstrap-responsive.min.css")]

      [:body
        (if message
          [:div#message message])

       [:div#wrapper.container-fluid
        [:div.row-fluid
          [:div.span8.offset2
            (login-info)

            content]]]

       (include-js "https://api.filepicker.io/v0/filepicker.js"
                   "/markdown.js"
                   "/jquery.js"
                   "/client.js"
                   "/less-1.3.0.min.js"
                   "/bootstrap/js/bootstrap.min.js")])))