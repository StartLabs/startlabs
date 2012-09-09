(ns startlabs.views.main
  (:require [startlabs.views.common :as common]
            [noir.response :as response])
  (:use [noir.core :only [defpage]]
        [markdown :only [md-to-html-string]]))

(defpage "/" []
  (common/layout
    [:h1.slug "Interested in " [:strong "Startups"] "?"]
    [:div.row-fluid
      [:div.span6
        [:h2 "You've come to the right place."]
        [:p [:strong "StartLabs"] " was established by MIT engineering students "
            "in the summer of 2011 to spread entrepreneurship."
            "We support and encourage students to:"]
            [:ol
              [:li "Start their own companies – transforming ideas and class "
                   "projects into seed-stage ventures."]
              [:li "Work in rapidly expanding companies – place students in internships "
                   "and full-time positions at promising startups."]]
            [:p "We are creating the next generation of technical entrepreneurs."]]
      [:div.span6
        [:h2 "Upcoming Events"]
        [:p "On Monday, October 8th, we'll be hosting " [:strong "Startup Bootcamp"] ", "
            "a free one-day event featuring talks by startup founders."]
        [:p "It's from 9am to 5pm in Kresge Auditorium."]

        [:div
          [:a.center.bootcamp {:href "http://startupbootcamp.mit.edu"}
            [:img {:src "/img/bootcamp.png" :alt "Startup Bootcamp" :width "240px"}]]]]
      ]))

(defpage "/about" []
  (common/layout
    [:h1 "About Us"]
    [:div
      [:p "Here's some lousy copy from the old website:"]
      [:p "StartLabs is a non-profit created out of the ideals of collegiate entrepreneurship."]
      [:p "The goal of StartLabs is to catalyze student engineers to bring technical innovations 
           to society through entrepreneurship, specifically by having students:"]
      [:ol
        [:li "Start their own companies – transforming ideas and class projects into seed-stage ventures."]
        [:li "Work in rapidly expanding companies – place students in internships and full-time positions 
              at promising startups."]]

      [:p "StartLabs runs “experiments” in order to create people that matter."]
      [:p [:a {:href "http://www.twitter.com/Start_Labs"} "Follow us on Twitter"] 
          " to stay in the loop."]]))

;; Redirect. Dead links = evil
(defpage "/company" [] (response/redirect "/about"))
(defpage "/partners" [] (response/redirect "/about"))