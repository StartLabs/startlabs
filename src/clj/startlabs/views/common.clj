(ns startlabs.views.common
  (:require [clj-time.core :as t]
            [clojure.string :as str]
            [sandbar.stateful-session :as session]
            [startlabs.models.user :as user]
            [startlabs.util :as u])
  (:use [hiccup.def :only [defhtml]]
        [hiccup.page :only [include-css include-js html5]]
        [environ.core :only [env]]))

;; hacky var
(def ^:dynamic *uri* nil)

;; font nicities
(defn font
  ([name]         (font name nil []))
  ([name weights] (font name weights []))
  ([name weights styles]
     (let [styled-weights 
           (for [weight weights style (conj styles "")]
             (str weight style))]
       (str (str/replace name " " "+") 
            (if weights
              (str ":" (str/join "," styled-weights)))))))

(defn fonts [& args]
  (map #(apply font %) args))

(defhtml font-link [& faces]
  [:link {:rel "stylesheet" :type "text/css"
          :href (str "http://fonts.googleapis.com/css?family="
                     (str/join "|" (apply fonts faces)))}])


(defhtml login-info []
  (if-let [info (user/get-my-info)]
    [:li.dropdown.pull-right
      [:a.dropdown-toggle {:data-toggle "dropdown" :href "#"}
        (:name info)
        [:b.caret]]

      [:ul.dropdown-menu
        [:li [:a {:href (str "/team/" (user/username info))} "My Info"]]
        [:li [:a {:href "/me"} "Edit Profile"]]
        [:li.divider]
        [:li [:a {:href "/logout"} "Logout"]]]]

    [:li.pull-right [:a {:href "/login"} "Login &rsaquo;"]]))

(defhtml webmaster-link [text]
  [:a {:href "mailto:ethan@startlabs.org"} text])

; could autopopulate routes from compojure handler somehow...
(def routes [[:home "/"] 
             [:jobs "/jobs"] 
             [:resources "/resources"] 
             [:about "/about"]
             [:partners "/partners"]
             [:team "/team"]
             [:blog "/blog"]])

(def google-analytics 
  "var _gaq = _gaq || [];
   _gaq.push(['_setAccount', 'UA-36464612-1']);
   _gaq.push(['_trackPageview']);

  (function() {
    var ga = document.createElement('script'); ga.type = 'text/javascript'; ga.async = true;
    ga.src = ('https:' == document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
    var s = document.getElementsByTagName('script')[0]; s.parentNode.insertBefore(ga, s);
  })();")

;; calendar links
(def calendar-id 
  "startlabs.org_5peolh5d72ol1r9c7hf624ke9g@group.calendar.google.com")

(def gcal-home "http://www.google.com/calendar/")

(def calendar-rss  (str gcal-home "feeds/" calendar-id "/public/basic"))
(def calendar-link (str gcal-home "embed?src=" calendar-id))

(defhtml rss-link [title href]
  [:link {:rel "alternate" :type "application/rss+xml" 
          :title title :href href}])

;; if the route is a singular link, then simply return a list-item,
;; otherwise, if return a dropdown of the choices in the route sequence
(defn route-link [title route current-uri]
  (if (sequential? route)
    (let [active? (not (nil? (some #{current-uri} route)))]
      [:li.dropdown
       [:a.dropdown-toggle {:data-toggle "dropdown" :href "#" 
                            :class (if active? "active")}
        (u/phrasify title) [:b.caret]]
       [:ul.dropdown-menu
        (for [[nom url] route]
          [:li [:a {:href url} (u/phrasify nom)]])]])
    ;; else
    (let [active? (= current-uri route)]
      [:li 
       [:a {:href route :class (if active? "active")}
        (u/phrasify title)]])))

(defn layout [& content]
  (let [[message-type message] (session/flash-get :message)]
    (html5
     [:head
      [:title "StartLabs"]

      ; make mobile device interface fixed
      [:meta {:name "viewport" 
              :content "width=device-width, maximum-scale=1.0"}]

      (font-link ["Open Sans" [300,400,600]]
                 ["Kreon" [400,700]])

      (rss-link "StartLabs Events Calendar" calendar-rss)
      (rss-link "StartLabs Jobs List" "/jobs.xml")

      (include-js "//www.google.com/jsapi"
                  (str "//maps.googleapis.com/maps/api/js?key=" 
                       (env :google-maps-key) "&sensor=false"))

      (include-css "/css/custom.pretty.css")]

     [:body
      [:div.wrapper 
       [:div#nav 
        [:div.container
         [:a#nav-logo {:href "/"} [:img {:src "/img/logo_small.png"}]]

         [:ul.visible-phone.visible-tablet.nav.nav-pills.pull-right
          [:li
           [:a {:data-toggle "collapse" :data-target".nav-collapse" :href "#"}                "Navigate"]]]

         (let [current-uri *uri*]
           [:div.nav-collapse
            [:ul.nav.nav-pills
             (for [[page location] routes]
               (route-link page location current-uri))
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
       [:p "&copy; " (t/year (t/now)) " Startlabs. Follow us on "
        [:a {:href "http://twitter.com/Start_Labs"} "Twitter"] " or "
        [:a {:href "https://www.facebook.com/pages/StartLabs/178890518841863"}
         "Facebook"] "."]]

      [:script {:type "text/javascript"} google-analytics]
      
      [:script#lt_ws {:type "text/javascript" 
                      :src "http://localhost:34312/socket.io/lighttable/ws.js"}]

      (include-js "/markdown.js"
                  "/jquery.js"
                  "/bootstrap/js/bootstrap.min.js"
                  "/client.js")])))
