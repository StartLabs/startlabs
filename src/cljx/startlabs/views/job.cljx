^:clj (ns startlabs.views.job
        (:use [hiccup.core :only [html]]
              [noir.validation :only [is-email?]]
              [markdown :only [md-to-html-string]]))

^:cljs (ns startlabs.views.job
          (:require [singult.core :as s]))

; this is taken straight from lib-noir.validation
^:cljs  (defn is-email? [v]
          (re-matches #"(?i)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" v))

; trickery: pretend hiccup's html = singult's render
; would use crate, but it's unmaintained and spits out errors :(
^:cljs  (def html s/render)

^:clj  (def markdownify md-to-html-string)
^:cljs (def markdownify markdown/mdToHtml)

(defn is-phone? 
  "Naive, really just checks that the characters are only numbers, digits, dashes, parens, or dots."
  [v]
  (re-matches #"^[\d-\.\(\)\s]{7,15}$" v))

(defn linkify 
  "Converts an email address, telephone number, or url into a proper link
   to be used as the href attribute in an HTML anchor."
  [text]
  (str 
    (condp apply [text]
      is-email? "mailto:"
      is-phone? "tel://"
      nil)

    text))

(defn job-card [job-info]
  (html
    [:div.thumbnail
      [:h2 [:a {:href (or (:website job-info) "#")} (:company job-info) ":"]
        [:small " " (:position job-info)]]
      [:div.row-fluid
        ; need to format dates
        [:p.span6 [:i.icon.icon-calendar] (:start_date job-info) " — " (:end_date job-info)]
        [:p.span6 [:i.icon.icon-map-marker] (:location job-info)]]
      [:div.row-fluid
        [:div (markdownify (:description job-info))]

        [:p.well
          "Contact: "
          [:i.icon.icon-envelope]
          (let [contact-info (:contact_info job-info)]
            ; need to handle phone numbers
            [:a {:href (linkify contact-info)} 
              contact-info])]]]))