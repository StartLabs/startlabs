^:clj (ns startlabs.views.job
        (:use [hiccup.core :only [html]]
              [noir.validation :only [is-email?]]))

^:cljs (ns startlabs.views.job
          (:require [singult.core :as s]))

; this is taken straight from lib-noir.validation
^:cljs  (defn is-email? [v]
          (re-matches #"(?i)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" v))

; trickery: pretend hiccup's html = singult's render
; would use crate, but it's unmaintained and spits out errors :(
^:cljs  (def html s/render)

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
        ; need to markdownify
        [:p (:description job-info)]
        [:p.well
          "Contact: "
          [:i.icon.icon-envelope]
          (let [contact-info (:contact_info job-info)]
            [:a {:href (str (if (is-email? contact-info) "mailto:") contact-info)} 
              contact-info])]]]))