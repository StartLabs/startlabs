^:clj (ns startlabs.views.jobx
        (:use [c2.core :only [unify]]
              [hiccup.core :only [html]]
              [noir.validation :only [is-email?]]
              [markdown :only [md-to-html-string]]))

^:cljs (ns startlabs.views.jobx
          (:require [singult.core :as s])
          (:use [c2.core :only [unify]]))

; this is taken straight from lib-noir.validation
^:cljs  (defn is-email? [v]
          (re-matches #"(?i)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" v))

^:clj  (def markdownify md-to-html-string)
^:cljs (def markdownify markdown/mdToHtml)

(defn is-phone? 
  "Naive, really just checks that the characters are only numbers, digits, dashes, parens, or dots."
  [v]
  (re-matches #"^[\d-\.\(\)\s]{7,15}$" v))

(defn is-www?
  "Checks if site begins with www. (but not http)"
  [v]
  (not (nil? (re-find #"^www\." v))))

(defn linkify 
  "Converts an email address, telephone number, or url into a proper link
   to be used as the href attribute in an HTML anchor."
  [text]
  (str
    (condp apply [text]
      is-email? "mailto:"
      is-phone? "tel://"
      is-www?   "http://"
      nil)

    text))

(defn job-summary [job-info show-delete?]
  [:div.job-summary
    (if show-delete?
      [:form.pull-right {:action (str "/job/" (:id job-info) "/delete") :method "post"}
        [:input.btn.btn-danger {:type "submit" :value "Delete"}]])

    [:h2 
      [:a {:href (or (linkify (:website job-info)) "#")} (:company job-info) ":"]
      [:small " " (:position job-info)]]

      [:div.row-fluid.dateloc
        ; need to format dates
        [:div.span6 [:i.icon.icon-calendar] (:start_date job-info) " — " (:end_date job-info)]
        [:div.span6 [:i.icon.icon-map-marker] (:location job-info)]]])

(defn job-card [job-info show-delete?]
  [:div.thumbnail.job-info
    (job-summary job-info show-delete?)
    [:div.row-fluid
      ; mark cljs markdown as unrendered because singult is currently unable to embed raw html
      [:div.description
        (markdownify (:description job-info))]

      [:div.well.well-small
        "Contact: "
        [:i.icon.icon-envelope]
        (let [contact-info (:contact_info job-info)]
          ; need to handle phone numbers
          [:a {:href (linkify contact-info)} 
            contact-info])]]])

;; on hover, add icon-white class
(defn job-list [jobs active-job]
  [:ul#job-list.span6
    (for [job jobs]
      [:li 
        [:a {:href (str "#" (:id job)) 
             :class (str "job "
                      (if (= active-job 
                          (str "#" (:id job)))
                            "active"))} 
          (job-summary job false)]])])
