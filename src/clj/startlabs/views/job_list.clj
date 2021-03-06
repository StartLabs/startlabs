^{:cljs
  '(ns startlabs.views.job-list
      (:use [clojure.set :only [union difference]]))}

(ns startlabs.views.job-list
  (:use [clojure.set :only [union difference]]
        [noir.validation :only [is-email?]]
        [markdown.core :only [md-to-html-string]]
        [startlabs.util :only [intify]]))

; this is taken straight from lib-noir.validation
#_(:cljs 
   (defn is-email? [v]
     (re-matches #"(?i)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" v)))

(def ^:clj markdownify md-to-html-string)
#_(:cljs (def converter (Markdown/getSanitizingConverter.)))
#_(:cljs (defn markdownify [text] (.makeHtml converter text)))

#_(:cljs
   (defn intify [n fallback]
     (let [res (js/parseInt n)]
       (if (js/isNaN res) fallback
           res))))

(def ordered-job-keys
  [:role :company :founder-name
   :position :location :website 
   :start-date :end-date 
   :company-size :description 
   :contact-info :email])

(defn visible-job-keys [role]
  (condp = role
    :internship (difference (set ordered-job-keys) #{:founder-name})
    :fulltime   (difference (visible-job-keys :internship) 
                            #{:end-date})
    :cofounder  (union
                 (difference (visible-job-keys :internship)
                            #{:start-date :end-date :position})
                 #{:founder-name})
    (visible-job-keys :internship)))

(defn required-job-keys [role]
  (condp = role
    :internship (visible-job-keys role)
    :fulltime   (visible-job-keys role)
    :cofounder  (difference (visible-job-keys role)
                            #{:website :company-size})
    (required-job-keys :internship)))

(defn is-phone? 
  "Naive, really just checks that the characters are only 
   numbers, digits, dashes, parens, or dots."
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
      nil?      ""
      is-email? "mailto:"
      is-phone? "tel://"
      is-www?   "http://"
      "")
    text))

(defn more-id [id]
  (str "more-" id))

(defn job-delete-modal [job]
  [:div {:id (str "delete-" (:id job)) :class "modal hide fade" :tabindex "-1" 
         :role "dialog" :aria-hidden true}
    [:div.modal-header
      [:button.close {:type "button" :data-dismiss "modal" :aria-hidden true} 
       "&times;"]
      [:h3 "Are you sure you want to remove this job?"]]

    [:div.modal-body
      [:p (:company job) ": " (:position job)]
      [:p "This will hide it from the listing."]]

    [:form.modal-footer {:action (str "/job/" (:id job) "/delete") 
                         :method "post"}
      [:a.btn {:href "#" :data-dismiss "modal" :aria-hidden true}
       "Whoops, never mind."]
      [:button.btn.btn-danger {:type "submit"} "Yes, Remove it."]]])

(defn job-summary [job-info editable?]
  (let [subheader    (if (empty? (:position job-info))
                       (:founder-name job-info)
                       (:position job-info))
        company-size (intify (:company-size job-info) 0)]
    [:div.job-summary
      (if editable?
        [:div.pull-right
         [:a.edit-link {:href (str "/job/" (:id job-info))} "Edit"]
         [:a.btn.btn-danger
          {:href (str "#delete-" (:id job-info)) :role "button"} "Delete"]])
  
      [:h2 
       (if (and editable? (= (:approved? job-info) "false"))
         [:div
           [:input.checkbox {:type "checkbox"}]
           [:input {:type "hidden" :value "0" :name (:id job-info)}]])
       [:a {:href (or (linkify (:website job-info)) "#")}
        (:company job-info) ":"]
       [:small " " subheader]]
  
     [:div.row-fluid.meta
      ; need to format dates
      [:div.span6 [:i.icon.icon-calendar] (:start-date job-info) 
       (if (= (:role job-info) "internship")
         (str " - " (:end-date job-info)))]
  
      [:div.span6 [:span.label.label-info (:role job-info)]]
      [:div.span6 [:i.icon.icon-map-marker] (:location job-info)]
  
      (if (> company-size 0)
        [:div.span6.employees 
         [:span.badge.badge-info company-size]
         "Employee" (when (not= 1 company-size) "s")])
  
     [:a.read {:href (str "#" (more-id (:id job-info)))}
      "Read More..." ]]]))

(defn job-card [job-info editable?]
  [:div.job-info
   (if editable? (job-delete-modal job-info))
   (job-summary job-info editable?)
    
   [:div.row-fluid.more {:id (more-id (:id job-info))}
    [:div.description
     (markdownify (:description job-info))]

    (if editable?
      [:p [:a {:href (str "/job/" (:id job-info) "/edit")}
           "Resend edit link to author"]])

    [:div.well.well-small
     "Contact: "
     [:i.icon.icon-envelope]
     (let [contact-info (:contact-info job-info)]
       ; need to handle phone numbers
       [:a {:href (linkify contact-info)
            :onclick (str "_gaq.push(['_trackEvent', 'Jobs', 'Contact', '"
                          (:id job-info)"']);")}
        contact-info])]]])

(defn half-list [half-jobs editable?]
  [:div.span6
   (for [job half-jobs]
     [:div.job.thumbnail {:id (:id job)}
      (job-card job editable?)])])

;; the base-url currently depends on the state of the session,
;; which is LOUSY. Should make more robust (accept all parameters)
;; which means we should really pass a map of filters as an argument
;; and spit out the appropriate query string
(defn job-list [{:keys [jobs editable? q page page-count]}]
  (let [[left-jobs right-jobs] (split-at (/ (count jobs) 2) jobs)
        base-url (str "/jobs?q=" q "&page=")
        inc-pc (inc page-count)]
    (if (empty? left-jobs)
      [:div#job-list.span12
       [:h2 "No jobs found. Try revising your query."]]
      ;else
      [:div#job-list.span12
       (half-list left-jobs editable?)
       (half-list right-jobs editable?)

       [:div.span12.pagination.pagination-centered
        [:ul
         [:li {:class (if (= page 1) "disabled" "active")} 
          [:a {:href  (if (= page 1) "#"
                          (str base-url (dec page)))} "Prev"]]

         (for [i (range 1 inc-pc)]
           [:li {:class (if (= page i) "disabled" "active")} 
            [:a {:href  (if (= page i) "#"
                            (str base-url i))} i]])

         [:li {:class (if (= page page-count) "disabled" "active")}
          [:a {:href  (if (= page page-count) "#"
                          (str base-url (inc page)))} "Next"]]]]])))
