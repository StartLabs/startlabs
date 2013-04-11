(ns startlabs.views.main
  (:require [climp.core :as mc] ; mailchimp
            [clojure.string :as str]
            [noir.response :as response]
            [noir.validation :as vali]
            [sandbar.stateful-session :as session]
            [startlabs.models.event :as event]
            [startlabs.models.user :as user]
            [startlabs.views.common :as common]
            [startlabs.util :as u])
  (:use [compojure.response :only [render]]
        [hiccup.def :only [defhtml]]
        [markdown.core :only [md-to-html-string]]
        [environ.core :only [env]]))

;; :get "/"
(defn home [& [email]]
  (common/layout
    [:h1.slug "Interested in " [:strong "Startups"] "?"]
    [:div.row-fluid
      [:div.span6
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
       
     (let [event-descr (second (event/get-event))
           logged-in?  (user/logged-in?)]
            
        [:div#upcoming-events.span6
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
            [:a {:href common/calendar-link} "event calendar"] "."]])]

     [:div.row-fluid.push-down
       [:div.span4]
       [:div.span5
       [:h3 "Join our mailing list to stay in the loop:"]
       [:form {:action "/" :method "post"}
        [:input#email.span7 {:name "email" 
                             :placeholder "Your email address"
                             :value (if (not (empty? email)) email "")}]
        [:button#submit.btn.btn-primary.span3.pull-left
         {:type "submit"} "Submit"]]]
       [:div.span4]]))

;; :post "/event"
(defn post-event [{:keys [description] :as event-map}]
  (if (user/logged-in?)
    (event/create-event event-map)

    ;; else
    (u/flash-message! :error "You must be logged in to do that."))
  (response/redirect "/"))

;; :post "/"
(defn post-mailing-list [email]
  (if (not (vali/is-email? email))
    (do
      (u/flash-message! :error "Invalid email address.")
      (render home email))
    (try
      (let [list-id (env :mc-list-id)]
        (binding [mc/*mc-api-key* (env :mc-api-key)]
          (mc/subscribe list-id email)
          (u/flash-message! 
           :success "You've been subscribed! We promise not to spam you. <3")
          (response/redirect "/")))
      (catch Exception e
        (u/flash-message!
         :error "Unexpected error. Try again later.")))))

(defn internal-error []
  (common/layout
   [:h1 "Something very bad has happened."]
   [:p "Well this is embarassing. Please notify " (common/webmaster-link "our webmaster") 
    " of the issue."]
   [:p "Sorry for the inconvience."]))

(defn four-oh-four []
  (common/layout
   [:h1 "Sorry, we lost that one."]
   [:p "We couldn't find the page you requested."]
   [:p "If you got here through a link on another website, then we've
         probably made a mistake."]
   [:p "Feel free to contact " (common/webmaster-link "the webmaster") 
    " so we can be aware of the issue."]))
