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

(defn job-delete-modal [job]
  [:div {:id (str "delete-" (:id job)) :class "modal hide fade" :tabindex "-1" :role "dialog" :aria-hidden true}
    [:div.modal-header
      [:button.close {:type "button" :data-dismiss "modal" :aria-hidden true} "&times;"]
      [:h3 "Are you sure you want to remove this job?"]]
    [:div.modal-body
      [:p (:company job) ": " (:position job)]
      [:p "This will hide it from the listing."]]
    [:form.modal-footer {:action (str "/job/" (:id job) "/delete") :method "post"}
      [:a.btn {:href "#" :data-dismiss "modal" :aria-hidden true} "Whoops, never mind."]
      [:input.btn.btn-danger {:type "submit" :value "Yes, Remove it."}]]])

(defn job-summary [job-info show-delete?]
  [:div.job-summary
    (if show-delete?
      [:a.btn.btn-danger.pull-right 
        {:href (str "#delete-" (:id job-info)) :role "button" :data-toggle "modal"} "Delete"])

    [:h2 
      [:a {:href (or (linkify (:website job-info)) "#")} (:company job-info) ":"]
      [:small " " (:position job-info)]]

      [:div.row-fluid.dateloc
        ; need to format dates
        [:div.span6 [:i.icon.icon-calendar] (:start_date job-info) " — " (:end_date job-info)]
        [:div.span6 [:i.icon.icon-map-marker] (:location job-info)]][:a.read {:href (str "#" (:id job-info))} "Read More..." ]])

(defn job-card [job-info show-delete?]
  [:div.job-info.test
    (if show-delete? (job-delete-modal job-info))
    (job-summary job-info show-delete?)

    [:div.row-fluid.more
      [:div.description
        (markdownify (:description job-info))]

      (if show-delete?
        [:p [:a {:href (str "/job/" (:id job-info) "/edit")} "Send edit link to author"]])

      [:div.well.well-small
        "Contact: "
        [:i.icon.icon-envelope]
        (let [contact-info (:contact_info job-info)]
          ; need to handle phone numbers
          [:a {:href (linkify contact-info)} 
           contact-info])]]])

(defn half-list [half-jobs show-delete?]
  [:div.span6
   (for [job half-jobs]
     [:div.job.thumbnail {:id (:id job)}
      (job-card job show-delete?)])])

(defn job-list [jobs show-delete?]
  (let [[left-jobs right-jobs] (split-at (/ (count jobs) 2) jobs)]
    [:div#job-list.span12
     (half-list left-jobs show-delete?)
     (half-list right-jobs show-delete?)]))