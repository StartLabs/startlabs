(ns startlabs.views.visitors
  (:require [noir.response :as response]
            [noir.validation :as vali]
            [startlabs.views.common :as common]
            [startlabs.models.visitors :as v]
            [startlabs.util :as u])

  (:use [hiccup.def :only [defhtml]]
        [compojure.response :only [render]]))

;; this is the page that gets displayed on the raspberry pi
;; it should use basic http auth so that you can only get
;; to it with a password.
;; make the id field have 0 opacity to make it more magical.
(defn get-signin [& [{:keys [id name email] :as params}]]
  (common/layout
   [:div#visitors.span5.offset3
    [:h1 "Visitor Sign In"]
    [:h2 (if id
           "Welcome, first-timer. Please fill out your details below:"
           "Just swipe your MIT ID to begin.")]

    [:form#signin.form-horizontal {:method "POST" :action "/signin"}
     [:img#gravatar.center {:width "64px" :height "64px"}]
     (for [field [:id :name :email]]
       (let [hide-field? (or (and (= field :id) id)
                             (and (not= field :id) (not id)))]
         [:div {:class (u/cond-class "control-group" [hide-field? "hidden"])}
          [:input {:type (if hide-field? "hidden" "text")
                   :id field :name field 
                   :autofocus (or (= field :id)
                                  (and id (= field :name)))
                   :placeholder (u/phrasify field)
                   :value (field params "")}]]))
     [:input.btn.btn-primary {:type "submit"}]]]))

(defn hello [name]
  (common/layout
   [:div#visitors
    [:h1#hello (str "Hey, " name "!")]
    [:p "Thanks for signing in. Now start something amazing!"]]))

(defn goodbye [name]
  (common/layout
   [:div#visitors
    [:h1#goodbye (str "See you later, " name ".")]
    [:p "Thanks for stopping by. Hope we see you next week."]]))

(defn valid-visitor? [{:keys [name email] :as params}]
  (dorun (map u/empty-rule params))
  (vali/is-email? email)
  (not (apply vali/errors? (:keys params))))

(defn post-signin [{:keys [id name email] :as params}]
  (if (empty? id)
    (response/redirect "/signin")

    (if-let [visitor (v/get-visitor id)]
      (let [name     (:name visitor)
            present? (v/present? id)]
        (v/signin id)
        (if present?
          (render goodbye name)
          (render hello name)))
      
      (if (valid-visitor? params)
        (do
          (v/create-visitor params)
          (render hello name))
        (render get-signin params)))))