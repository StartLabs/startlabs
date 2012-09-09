(ns startlabs.views.common
  (:require [noir.session :as session]
            [clojure.string :as str]
            [startlabs.models.user :as user])
  (:use [noir.core :only [defpartial]]
        [noir.request :only [ring-request]]
        [hiccup.page :only [include-css include-js html5]]
        [environ.core :only [env]]))

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

(defn phrasify 
  "Convert an underscore-delimited phrase into capitalized words."
  [key-str]
  (str/capitalize (str/replace key-str "_" " ")))

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

(defn home-uri []
  (if (env :dev)
    "http://localhost:8000"
    "http://www.startlabs.org"))

; could autopopulate routes from defpages that are nested only one layer deep.
(def routes [[:home "/"] [:jobs "/jobs"] [:resources "/resources"] [:about "/about"] [:team "/team"]])

(defpartial layout [& content]
  (let [[message-type message] (session/flash-get :message)
        request (ring-request)]
    (html5
      [:head
        [:title "startlabs"]
        ; switch to style.css in production
        [:link {:rel "stylesheet/less" :type "text/css" 
                :href (if (env :dev) "/css/style.less"
                                     "/css/style.css")}]

        [:link {:rel "stylesheet" :type "text/css" 
               :href (font-link ["Josefin Sans" [400,700]]
                                ["Josefin Slab" [400,700]]
                                ["Crimson Text" [400,"400italic",600,"600italic",700,"700italic"]]
                                ["Ultra"])}]

        (include-css "/bootstrap/css/bootstrap.min.css"
                     "/bootstrap/css/bootstrap-responsive.min.css"
                     "http://cdn.leafletjs.com/leaflet-0.4/leaflet.css")]

      [:body
        [:div#nav [:div.container
          [:a#nav-logo {:href "/"} [:img {:src "/img/logo_small.png" :width "96px"}]]
          (let [current-uri (:uri request)]
            [:ul.nav.nav-pills
              (for [[page location] routes]
                [:li [:a {:href location :class (if (= current-uri location) "active")} 
                  (str/capitalize (name page))]])

              (login-info)])]]

        [:div#content.container
          (if message
            [:div#message {:class (str "alert alert-" (name message-type))}
              [:button {:type "button" :class "close" :data-dismiss "alert"} "x"]
              [:h3 (str/capitalize (name message-type))]
              [:p message]])

          content

          [:footer
            [:p "Follow us on " 
              [:a {:href "http://twitter.com/Start_Labs"} "Twitter"] " or "
              [:a {:href "https://www.facebook.com/pages/StartLabs/178890518841863"} "Facebook"]]]]

        (include-js "/markdown.js"
                    "/jquery.js"
                    "/bootstrap/js/bootstrap.min.js"
                    "https://api.filepicker.io/v0/filepicker.js"
                    "http://cdn.leafletjs.com/leaflet-0.4/leaflet.js"
                    "http://tile.cloudmade.com/wml/latest/web-maps-lite.js"
                    "/oms.min.js"
                    "/client.js")

        (if (env :dev)
          (include-js "/less-1.3.0.min.js"))])))


