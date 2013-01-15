(ns startlabs.models.payment
  (:require [clj-stripe.charges :as charges]
            [clj-stripe.common :as stripe]
            [startlabs.models.util :as util])

  (:use [clojure.walk :only [keywordize-keys]]
        [clj-time.core :only [now]]
        [clj-time.coerce :only [to-long to-date]]
        [datomic.api :only [q db ident] :as d]
        [environ.core :only [env]]
        [startlabs.models.database :only [*conn*]]
        [startlabs.util :only [stringify-values]]))

(def fee (stripe/money-quantity 25000 "usd"))

(defn charge-payment [descr params]
  (binding [stripe/*stripe-token* (env :stripe-secret-key)]
    (if-let [stripe-token (:stripeToken params)]
      (let [params (assoc params :description descr)
            charge (charges/create-charge fee (assoc (stripe/card stripe-token) 
                                                :description (str params)))
            response (keywordize-keys (stripe/execute charge))]
        (cond
         (:error response) (:message (:error response))
         (not= "pass" (:cvc_check (:card response))) "CVC Check failed."
         :else :success))

      ;else
      {:error "No stripe token submitted."})))