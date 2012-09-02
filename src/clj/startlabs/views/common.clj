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
    [:li.dropdown.pull-right
      [:a.dropdown-toggle {:data-toggle "dropdown" :href "#"}
        (:name info)
        [:b.caret]
      ]
      [:ul.dropdown-menu
        [:li [:a {:href (str "/team/" (user/username info))} "My Info"]]
        [:li [:a {:href "/me"} "Edit Profile"]]
        [:li.divider]
        [:li [:a {:href "/logout"} "Logout"]]
      ]]
    [:li.pull-right [:a {:href "/login"} "Login"]]))

; could autopopulate routes from defpages that are nested only one layer deep.
(def routes [[:home "/"] [:jobs "/jobs"] [:about "/about"] [:team "/team"]])

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
        [:div#nav
          [:div.container
            [:ul.nav.nav-pills
              (for [[page location] routes]
                [:li [:a {:href location} (str/capitalize (name page))]])

            (login-info)]]]

        [:div#content.container
          (if message
            [:div#message.alert
              [:button {:type "button" :class "close" :data-dismiss "alert"} "x"]
              [:p message]])

          content]

       (include-js "https://api.filepicker.io/v0/filepicker.js"
                   "/markdown.js"
                   "/jquery.js"
                   "/client.js"
                   "/less-1.3.0.min.js"
                   "/bootstrap/js/bootstrap.min.js")])))