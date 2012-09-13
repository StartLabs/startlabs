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
^{:line 42} (defn job-card [job-info] [:div.thumbnail.job-info [:h2 [:a {:href ^{:line 44} (or ^{:line 44} (linkify ^{:line 44} (:website job-info)) "#")} ^{:line 44} (:company job-info) ":"] [:small " " ^{:line 45} (:position job-info)]] [:div.row-fluid.dateloc [:div.span6 [:i.icon.icon-calendar] ^{:line 48} (:start_date job-info) " — " ^{:line 48} (:end_date job-info)] [:div.span6 [:i.icon.icon-map-marker] ^{:line 49} (:location job-info)]] [:div.row-fluid [:div.description ^{:line 53} (markdownify ^{:line 53} (:description job-info))] [:div.well.well-small "Contact: " [:i.icon.icon-envelope] ^{:line 58} (let [contact-info ^{:line 58} (:contact_info job-info)] [:a {:href ^{:line 60} (linkify contact-info)} contact-info])]]])
^{:line 63} (defn job-list [jobs] [:ul#job-list.thumbnails ^{:line 65} (for [job jobs] [:li.job-brick.span6 {:id ^{:line 66} (:id job)} ^{:line 67} (job-card job)])])