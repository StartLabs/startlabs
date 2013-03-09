(ns startlabs.views.pay
  (:require [noir.response :as response]
            [noir.validation :as vali]
            [sandbar.stateful-session :as session]
            [startlabs.views.common :as common]
            [startlabs.models.user :as user]
            [startlabs.models.payment :as payment]
            [startlabs.util :as u])

  (:use [compojure.response :only [render]]
        [environ.core :only [env]]
        [hiccup.core :only [html]]
        [hiccup.def :only [defhtml]]))

;; Eventually we should have dynamic payment pages for different expenses
;; In the database, the schema would look something like:
;; expense/name "career-fair"
;; expense/amount 10000 => $100 (amount in cents to align with stripe api)
;; Then you would send users to /pay/career-fair

(defhtml stripe-button []
  [:script.stripe-button {:src "https://button.stripe.com/v1/button.js"
                          :data-key (env :stripe-pub-key)
                          :data-amout (payment/fee "amount")}])

(defhtml control-group [type & [id descr params]]
  (let [kw-id (or (keyword id) nil)
        param (if params (kw-id params) "")]
    [:div {:class (u/cond-class "control-group" [(and kw-id (vali/get-errors kw-id)) "error"])}
     (if descr
       [:label.control-label {:for id} descr])
     [:div.controls
      (if id
        [:input {:id id :name id :type type :value param}]
        [:input {:type type}])]]))

(defn get-pay [params]
  (let [has-params? (not (empty? params))]
    (common/layout
     [:div.row-fluid
      [:div.span10.offset1
       [:h1 "StartLabs Career Fair Payment"]
       [:div.well 
        [:p "To finish your registration for the StartLabs Career Fair, please submit a
             payment of " [:strong (str "$" (/ (payment/fee "amount") 100))]
             ". Make sure to provide your company name and contact information below."]]

       [:form#payment.form-horizontal {:method "post" :action "/pay"}
        (control-group "text" "company" "Company Name" params) 
        (control-group "text" "email" "Contact Email" params)
        [:div {:class (u/cond-class "control-group" [(vali/get-errors :stripe) "error"])}
         [:label.control-label "Payment Info"]
         [:div.controls (stripe-button)]]
        (control-group "submit")]]])))

(defn valid-payment? [params]
  (dorun (map u/empty-rule params))

  (vali/rule (not (empty? (:stripeToken params)))
             [:stripe "Credit card info not submitted."])

  (vali/rule (vali/is-email? (:email params))
             [:email "Invalid email address."])

  (not (apply vali/errors? (conj (keys params) :stripe))))


(defn post-pay [{:keys [company email stripeToken] :as params}]
  (let [params (u/trim-vals params)]
    (if (valid-payment? params)
      (let [response (payment/charge-payment "Career fair admittance fee" params)]
        (if (= response :success)
          (do
            (u/flash-message! 
             :success 
             (str "Thanks! Your payment has been received."
                  " See you at the Career Fair."))
            (response/redirect "/"))
          ;else
          (do
            (u/flash-message! :error response)
            (render get-pay params))))
      ;else
      (do
        (u/flash-message! 
         :error 
         (str "Make sure you entered a valid email address" 
              " and filled in all of the fields."))
        (render get-pay params)))))

(defn get-payments []
  (if (user/logged-in?)
    (common/layout
     [:div
      [:h1 "Payments Received"]
      [:p "Show a fancy table here."]])

    ;else
    (do
      (u/flash-message! 
       :error "You must be logged in to view the payments page.")
      (response/redirect "/"))))
