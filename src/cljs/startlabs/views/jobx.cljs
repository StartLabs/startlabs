;;This file autogenerated from 
;;
;;  src/cljx/startlabs/views/jobx.cljx
;;
^{:cljs true, :line 7} (ns startlabs.views.jobx ^{:line 8} (:require [singult.core :as s]) ^{:line 9} (:use [c2.core :only [unify]]))
^{:cljs true, :line 12} (defn is-email? [v] ^{:line 13} (re-matches #"(?i)[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?" v))
^{:cljs true, :line 16} (def markdownify markdown/mdToHtml)
^{:line 18} (defn is-phone? "Naive, really just checks that the characters are only numbers, digits, dashes, parens, or dots." [v] ^{:line 21} (re-matches #"^[\d-\.\(\)\s]{7,15}$" v))
^{:line 23} (defn is-www? "Checks if site begins with www. (but not http)" [v] ^{:line 26} (not ^{:line 26} (nil? ^{:line 26} (re-find #"^www\." v))))
^{:line 28} (defn linkify "Converts an email address, telephone number, or url into a proper link\n   to be used as the href attribute in an HTML anchor." [text] ^{:line 32} (str ^{:line 33} (condp apply [text] is-email? "mailto:" is-phone? "tel://" is-www? "http://" nil) text))
^{:line 41} (defn job-delete-modal [job] [:div {:id ^{:line 42} (str "delete-" ^{:line 42} (:id job)), :class "modal hide fade", :tabindex "-1", :role "dialog", :aria-hidden true} [:div.modal-header [:button.close {:type "button", :data-dismiss "modal", :aria-hidden true} "&times;"] [:h3 "Are you sure you want to remove this job?"]] [:div.modal-body [:p ^{:line 47} (:company job) ": " ^{:line 47} (:position job)] [:p "This will hide it from the listing."]] [:form.modal-footer {:action ^{:line 49} (str "/job/" ^{:line 49} (:id job) "/delete"), :method "post"} [:a.btn {:href "#", :data-dismiss "modal", :aria-hidden true} "Whoops, never mind."] [:input.btn.btn-danger {:type "submit", :value "Yes, Remove it."}]]])
^{:line 53} (defn job-summary [job-info show-delete?] [:div.job-summary ^{:line 55} (if show-delete? [:a.btn.btn-danger.pull-right {:href ^{:line 57} (str "#delete-" ^{:line 57} (:id job-info)), :role "button", :data-toggle "modal"} "Delete"]) [:h2 [:a {:href ^{:line 60} (or ^{:line 60} (linkify ^{:line 60} (:website job-info)) "#")} ^{:line 60} (:company job-info) ":"] [:small " " ^{:line 61} (:position job-info)]] [:div.row-fluid.dateloc [:div.span6 [:i.icon.icon-calendar] ^{:line 65} (:start_date job-info) " — " ^{:line 65} (:end_date job-info)] [:div.span6 [:i.icon.icon-map-marker] ^{:line 66} (:location job-info)]] [:a.read {:name ^{:line 66} (str "#" ^{:line 66} (:id job-info))} "Read More..."]])
^{:line 68} (defn job-card [job-info show-delete?] [:div.job-info.test ^{:line 70} (if show-delete? ^{:line 70} (job-delete-modal job-info)) ^{:line 71} (job-summary job-info show-delete?) [:div.row-fluid.more [:div.description ^{:line 75} (markdownify ^{:line 75} (:description job-info))] ^{:line 77} (if show-delete? [:p [:a {:href ^{:line 78} (str "/job/" ^{:line 78} (:id job-info) "/edit")} "Send edit link to author"]]) [:div.well.well-small "Contact: " [:i.icon.icon-envelope] ^{:line 83} (let [contact-info ^{:line 83} (:contact_info job-info)] [:a {:href ^{:line 85} (linkify contact-info)} contact-info])]]])