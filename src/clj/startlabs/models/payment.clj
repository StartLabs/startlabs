(ns startlabs.models.payment
  (:require [clj-stripe.charges :as charges]
            [clj-stripe.common :as stripe]
            [startlabs.models.util :as util])

  (:use [datomic.api :only [q db ident] :as d]
        [clj-time.core :only [now]]
        [clj-time.coerce :only [to-long to-date]]
        [startlabs.models.database :only [conn]]
        [startlabs.util :only [stringify-values]]))

(def fee (stripe/money-quantity 25000 "usd"))

(defn charge-payment [params]
  :success)