^:clj (ns startlabs.views.job
        (:use [noir.core :only [defpartial]]
              [noir.validation :only [is-email?]]))

^:cljs (ns startlabs.views.job
          (:use-macros [crate.def-macros :only [defpartial]]))

; this is taken straight from lib-noir.validation
^:cljs  (defn is-email? [v]
          (re-matches #"(?i)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" v))

(defpartial job-card [job-info]
  [:div.thumbnail
    [:h2 [:a {:href (:website job-info)} (:company job-info) ":"]
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
            contact-info])]]])