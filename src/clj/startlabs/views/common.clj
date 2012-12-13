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
    [:li.pull-right [:a {:href "/login"} "Login &rsaquo;"]]))

(defpartial webmaster-link [text]
  [:a {:href "mailto:ethan@startlabs.org"} text])

; could autopopulate routes from defpages that are nested only one layer deep.
(def routes [[:home "/"] [:jobs "/jobs"] [:resources "/resources"] 
             [:about "/about"] [:partners "/partners"] [:team "/team"]])

(def google-analytics 
  "var _gaq = _gaq || [];
   _gaq.push(['_setAccount', 'UA-36464612-1']);
   _gaq.push(['_trackPageview']);

  (function() {
    var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
    ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
  })();")

(def calendar-rss "http://www.google.com/calendar/feeds/startlabs.org_5peolh5d72ol1r9c7hf624ke9g%40group.calendar.google.com/public/basic")

(defpartial layout [& content]
  (let [[message-type message] (session/flash-get :message)
        request (ring-request)]
    (html5
      [:head
        [:title "StartLabs"]

        ; make mobile device interface fixed
        [:meta {:name "viewport" :content "width=device-width, maximum-scale=1.0"}]

        [:link {:rel "stylesheet" :type "text/css" 
               :href (font-link ["Open Sans" [300,400,600]]
                                ["Kreon" [400,700]])}]

       [:link {:rel "alternate" :type "application/rss+xml" :title "StartLabs Events Calendar"
               :href calendar-rss}]

        (include-css "/css/custom.pretty.css"
                     "http://cdn.leafletjs.com/leaflet-0.4/leaflet.css")]

      [:body
        [:div.wrapper [:div#nav [:div.container
          [:a#nav-logo {:href "/"} [:img {:src "/img/logo_small.png" :width "110px"}]]

          [:ul.visible-phone.visible-tablet.nav.nav-pills.pull-right
            [:li
              [:a {:data-toggle "collapse" :data-target".nav-collapse" :href "#"} "Navigate"]]]

          (let [current-uri (:uri request)]
            [:div.nav-collapse
              [:ul.nav.nav-pills
                (for [[page location] routes]
                  [:li [:a {:href location :class (if (= current-uri location) "active")} 
                    (str/capitalize (name page))]])

                (login-info)]])]]

        [:div#content.container
          (if message
            [:div#message {:class (str "alert alert-" (name message-type))}
              [:button {:type "button" :class "close" :data-dismiss "alert"} "x"]
              [:h3 (str/capitalize (name message-type))]
              [:p message]])

          content
         [:div.push]]]
       [:footer.container
        [:p "&copy; 2012 Startlabs. Follow us on "
         [:a {:href "http://twitter.com/Start_Labs"} "Twitter"] " or "
         [:a {:href "https://www.facebook.com/pages/StartLabs/178890518841863"} "Facebook"]]]

        [:script {:type "text/javascript"} google-analytics]

        (include-js "/markdown.js"
                    "/jquery.js"
                    "/bootstrap/js/bootstrap.min.js"
                    "https://api.filepicker.io/v0/filepicker.js"
                    (str "//maps.googleapis.com/maps/api/js?key=" (env :google-maps-key) "&sensor=false")
                    "/client.js")
    ])))


