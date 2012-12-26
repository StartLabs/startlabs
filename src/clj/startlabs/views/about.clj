(ns startlabs.views.about
  (:require [startlabs.views.common :as common])
  (:use [hiccup.def :only [defhtml]]))

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
