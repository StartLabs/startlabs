(ns startlabs.views.pay
  (:require [noir.session :as session]
            [noir.response :as response]
            [noir.validation :as vali]
            [startlabs.views.common :as common]
            [startlabs.models.user :as user]
            [startlabs.models.payment :as payment]
            [startlabs.util :as u])

  (:use [environ.core :only [env]]
        [noir.core :only [defpage defpartial render url-for]]))

(defpartial stripe-button []
  [:script.stripe-button {:src "https://button.stripe.com/v1/button.js"
                          :data-key (env :stripe-pub-key)
                          :data-amout (payment/fee "amount")}])

(defpartial control-group [type & [id descr params]]
  (let [kw-id (or (keyword id) nil)
        param (if params (kw-id params) "")]
    [:div {:class (u/cond-class "control-group" [(and kw-id (vali/get-errors kw-id)) "error"])}
     (if descr
       [:label.control-label {:for id} descr])
     [:div.controls
      (if id
        [:input {:id id :name id :type type :value param}]
        [:input {:type type}])]]))

(defpage [:get "/pay"] {:as params}
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


(defpage [:post "/pay"] {:keys [company email stripeToken] :as params}
  (let [params (u/trim-vals params)]
    (if (valid-payment? params)
      (let [response (payment/charge-payment params)]
        (if (= response :success)
          (do
            (session/flash-put! :message [:success "Thanks! Your payment has been received. See you at the Career Fair."])
            (response/redirect "/"))
          ;else
          (do
            (session/flash-put! :message 
                                [:error "Payment failed. You have not been charged.
                                         Try re-entering your card info."])
            (render "/pay" params))))
      ;else
      (do
        (session/flash-put! :message [:error "Make sure you entered a valid email address and filled in all of the fields."])
        (render "/pay" params)))))

(defpage "/payments" []
  (if (user/logged-in?)
    (common/layout
     [:div
      [:h1 "Payments Received"]
      [:p "Show a fancy table here."]])

    ;else
    (do
      (session/flash-put! :message [:error "You must be logged in to view the payments page."])
      (response/redirect "/"))))