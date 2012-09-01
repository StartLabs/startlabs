(ns startlabs.views.main
  (:require [startlabs.views.common :as common]
            [startlabs.models.user :as user]
            [startlabs.util :as util]
            [noir.session :as session]
            [noir.response :as response]
            [clojure.string :as str])
  (:use [noir.core :only [defpage defpartial]]
        [hiccup.core :only [html]]))

(defpartial user-info-p [info]
  (if info
    [:div#user-info
      [:p "Hey, " [:a {:href "/users/me"} (:name info)]]
      [:a#logout {:href "/logout"} "Logout"]]
    [:a {:href "/login"} "Login"]))

(defn user-info
  ([] (user-info-p (user/get-my-info)))
  ([info] (user-info-p info)))

(defpage "/" []
  (common/layout
    [:div#content
      (user-info)]))

(defpage "/login" []
  (response/redirect (user/get-login-url)))

(defpage "/logout" []
  (session/clear!)
  (response/redirect "/"))

(defpage "/oauth2callback" []
  (common/layout
    [:div#loading "Fetching credentials..."]))

(def editable-attrs [:link :studying :name :bio :graduation_year :picture])

(defpage "/users/me" []
  (let [my-info (user/get-my-info)]
  	(common/layout
      (user-info my-info)
      [:form {:action "/users/me" :method "post"}
        [:table#me
      		(for [key editable-attrs]
            (let [key-str  (name key)
                  key-word (str/capitalize (str/replace key-str "_" " "))
                  value    (key my-info)
                  inp-elem (if (= key :bio) :textarea :input)]
           		[:tr
                [:td [:label {:for key-str} key-word]]
                [:td
                  ; name attr must be filled by script. This way, we only submit values that get changed.
                  [inp-elem {:id key-str :name key-str :type "text" :value value :class "hidden"} 
                    (if (= inp-elem :textarea) value)]
                    (if (= key :picture)
                      [:img#preview {:src value :width 50 :height 50}])]
                ]))]
          [:input {:type "submit" :value "Submit"}]])))

(defpage [:post "/users/me"] params
  (let [my-info (user/get-my-info)
        new-facts (util/map-diff params my-info)]
    (common/layout
      [:p (str my-info)]
      [:p (str params)]
      [:p (str new-facts)])))

