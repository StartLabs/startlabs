(ns startlabs.views.main
  (:require [climp.core :as mc] ; mailchimp
            [clojure.string :as str]
            [noir.response :as response]
            [noir.validation :as vali]
            [sandbar.stateful-session :as session]
            [startlabs.models.event :as event]
            [startlabs.models.user :as user]
            [startlabs.views.common :as common])
  (:use [compojure.response :only [render]]
        [hiccup.def :only [defhtml]]
        [markdown.core :only [md-to-html-string]]
        [environ.core :only [env]]))

;; :get "/"
(defn home [& [email]]
  (common/layout
    [:h1.slug "Interested in " [:strong "Startups"] "?"]
    [:div.row-fluid
      [:div.span5
        [:h2.centered "You've come to the right place."]
        [:p [:strong "StartLabs"] " was established by MIT engineering students
            in the summer of 2011 to spread entrepreneurship.
            We support and encourage students to:"]
            [:ol
              [:li "Start their own companies – transforming ideas and class
                    projects into seed-stage ventures."]
              [:li "Work in rapidly expanding companies – place students in internships
                    and full-time positions at promising startups."]]
       [:p "We are creating the next generation of technical entrepreneurs."]]
	[:div.span2.centered
	  [:h2.centered "Register For"]
	  [:a.guestlist-event-136884 {:href "#"} [:img {:src "/img/career-fair.png" :width "auto"}]]
	  [:a {:href "https://guestlistapp.com/events/136884"} "More Information"]
	  [:script {:src "https://guestlistapp.com/javascripts/guestlist-embed.js"}]]
       
      (let [event-descr (event/get-latest-event)
            logged-in?  (user/logged-in?)]
            
        [:div#upcoming-events.span5
         [:h2.centered "Upcoming Events"
          (if logged-in?
            [:a#edit-upcoming.btn.pull-right {:href "#"} "Edit"])]

          (if logged-in?
            [:form#event-form.hidden {:action "/event" :method "post"}
              [:textarea#event-text.span9 {:name "description" :placeholder "Markdown supported" :rows 6}
                event-descr]
              [:input.btn.pull-right {:type "submit"}]])

          [:div#event-info
            (md-to-html-string event-descr)]

          [:p "To keep up with going-ons, subscribe to our "
            [:a {:href common/calendar-rss} "event calendar"] "."]])
     ]

     [:div.row-fluid.push-down
       [:div.span4]
       [:div.span5
       [:h3 "Join our mailing list to stay in the loop:"]
       [:form {:action "/" :method "post"}
        [:input#email.span7 {:name "email" 
                             :placeholder "Your email address"
                             :value (if (not (empty? email)) email "")}]
        [:button#submit.btn.btn-primary.span3.pull-left
         {:type "submit"} "Submit"]
        ]]
       [:div.span4]
      ]))

;; :post "/event"
(defn post-event [{:keys [description] :as event-map}]
  (if (user/logged-in?)
    (event/create-event event-map)

    ;; else
    (session/flash-put! :message [:error "You must be logged in to do that."]))
  (response/redirect "/"))

;; :post "/"
(defn post-mailing-list [email]
  (if (not (vali/is-email? email))
    (do
      (session/flash-put! :message [:error "Invalid email address."])
      (render home email))
    (try
      (let [list-id (env :mc_list_id)]
        (binding [mc/*mc-api-key* (env :mc_api_key)]
          (mc/subscribe list-id email)
          (session/flash-put! :message
                              [:success "You've been subscribed! We promise not to spam you. <3"])
          (response/redirect "/")))
      (catch Exception e
        (session/flash-put! :message
                            [:error "Unexpected error. Try again later."])))))

;; :get "/about"
(defn about []
  (common/layout
    [:div.row-fluid
     [:div.span7
      [:h1 "About Us"]
      [:div
       [:p "StartLabs is a non-profit created out of the ideals of collegiate entrepreneurship."]
       [:p "The goal of StartLabs is to catalyze engineering students to bring technical innovations 
           to society through entrepreneurship, specifically by having them:"]
       [:ol
        [:li "Start their own companies – transforming ideas and class projects into seed-stage ventures."]
        [:li "Work in rapidly expanding companies – place students in internships and full-time positions 
              at promising startups."]]

       [:p "StartLabs runs “experiments” to create people that matter."]
       [:p [:a {:href "http://www.twitter.com/Start_Labs"} "Follow us on Twitter"] 
        " to stay in the loop."]]]
     [:div.span5
      [:iframe.map {:width "100%" :height "350" :frameborder "0" :scrolling "no" :marginheight "0" :marginwidth "0" 
                    :src "https://maps.google.com/maps?f=q&source=s_q&hl=en&amp;geocode=&q=MIT+N52&aq=&sll=42.37839,-71.114729&sspn=0.076719,0.181103&ie=UTF8&hq=&hnear=N52,+Cambridge,+Massachusetts+02139&t=m&z=14&iwloc=A&output=embed"}]]
     [:div.clear]]))

(defn logo-for-company
  "The scheme is: take the capital letters in the company name, stick them together,
   append .png to the end. General Catalyst Partners = gcp.png"
  [company]
  (str "/img/partners/" 
    (str/lower-case (apply str (re-seq #"[A-Z]" company))) ".png"))

;; CAPITALIZATION IS CRUCIAL!
(def partner-list
  [["Atlas Venture" "http://www.atlasventure.com"]
   ["Bessemer Venture Partners" "http://www.bvp.com"] 
   ["Boston Seed Capital" "http://www.bostonseed.com"]
   ["General Catalyst Partners (founding partner)" "http://www.generalcatalyst.com"]
   ["Goodwin Procter" "http://www.goodwinprocter.com"]
   ["Highland Capital Partners" "http://www.hcp.com"] 
   ["NEVCA" "http://www.newenglandvc.org"] 
   ["North Bridge Venture Partners" "http://www.nbvp.com"]
   ["OpenView Venture Partners" "http://openviewpartners.com"]
   ["WilmerHale" "http://www.wilmerhale.com"]])

;; "/partners"
(defn partners []
  (common/layout
    [:div.row-fluid
     [:div.span1]
     [:div.span10
      [:h1 "Partners"]
      [:p "StartLabs simply would not be possible without the help of our gracious partners."]
      [:p "These groups sponsor our efforts to foster the next generation 
          of technical entrepreneurs:"]
      [:div.row-fluid
       [:ul#partners.thumbnails
        (for [[partner link] partner-list]
          (let [logo-url (logo-for-company partner)]
            [:li.thumbnail.span4
             [:a {:href link} 
              [:h3 partner]
              [:img.center {:src logo-url :alt partner}]]]))
        ]]]
     [:div.span1]]))

(defhtml four-oh-four [] 
    [:h1 "Sorry, we lost that one."]
    [:p "We couldn't find the page you requested."]
    [:p "If you got here through a link on another website, then we've
         probably made a mistake."]
    [:p "Feel free to contact " (common/webmaster-link "the webmaster") 
        " so we can be aware of the issue."])

(defhtml internal-error []
    [:h1 "Something very bad has happened."]
    [:p "Well this is embarassing. Please notify " (common/webmaster-link "our webmaster") 
      " of the issue."]
    [:p "Sorry for the inconvience."])
