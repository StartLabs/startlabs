(ns startlabs.views.partners
  (:require [startlabs.views.common :as common]
            [clojure.string :as str])
  (:use [hiccup.def :only [defhtml]]))

(defn logo-for-company
  "The scheme is: take the capital letters in the company name, stick them together,
   append .png to the end. General Catalyst Partners = gcp.png"
  [company]
  (str "/img/partners/" 
    (str/lower-case (apply str (re-seq #"[A-Z]" company))) ".png"))

;; CAPITALIZATION IS CRUCIAL!
(def partner-list
  [["Andreessen Horowitz" "http://a16z.com"]
   ["Atlas Venture" "http://www.atlasventure.com"]
   ["Boston Seed Capital" "http://www.bostonseed.com"]
   ["General Catalyst Partners (founding partner)" "http://www.generalcatalyst.com"]
   ["Goodwin Procter" "http://www.goodwinprocter.com"]
   ["Highland Capital Partners" "http://www.hcp.com"] 
   ["NEVCA" "http://www.newenglandvc.org"] 
   ["North Bridge Venture Partners" "http://www.nbvp.com"]
   ["OpenView Venture Partners" "http://openviewpartners.com"]
   ["WilmerHale" "http://www.wilmerhale.com"]])

;; "/partners"
(defn partners []
  (common/layout
    [:div.row-fluid
     [:div.span1]
     [:div.span10
      [:h1 "Partners"]
      [:p "StartLabs simply would not be possible without the help of our gracious partners."]
      [:p "These groups sponsor our efforts to foster the next generation 
          of technical entrepreneurs:"]
      [:div.row-fluid
       [:ul#partners.thumbnails
        (for [[partner link] partner-list]
          (let [logo-url (logo-for-company partner)]
            [:li.thumbnail.span4
             [:a {:href link} 
              [:h3 partner]
              [:img.center {:src logo-url :alt partner}]]]))
        ]]]
     [:div.span1]]))
