(ns startlabs.models.visitors
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [*conn*]]
        [startlabs.util :only [stringify-values uuid after-now?]])
  (:require [clojure.string :as str]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            [sandbar.stateful-session :as session]
            [startlabs.models.util :as util]))

(defn visitor-fields []
  (util/map-of-entity-tuples :visitor))

(defn create-visitor [attrs]
  (try
    (let [tx-data (util/txify-new-entity :visitor 
                                         (assoc attrs :visit-time (t/now)))]
      @(d/transact *conn* tx-data)
      (first tx-data))
    (catch Exception e
      false)))

(defn visitor-with-id [id]
  (if-let [visitor (util/elem-with-attr :visitor/id id)]
    visitor))

(defn get-visitor [id]
  (if-let [visitor (visitor-with-id id)]
    (util/map-for-datom visitor :visitor)))

(defn get-signins [id]
  (q '[:find ?signins :in $ ?id 
       :where [?visitor :visitor/id ?id] 
              [?visitor :visitor/visit-time ?signins]] (db *conn*) id))

(defn last-signin [id]
  (let [signins (get-signins id)]
    (first (sort #(compare %2 %1) 
                 (flatten (into [] signins))))))

(defn present? [id]
  (t/before? (t/minus (t/now) (t/hours 6))
             (tc/from-date (last-signin id))))

(defn signin [id]
  (if-let [visitor (visitor-with-id id)]
    @(d/transact *conn* [[:db/add visitor 
                          :visitor/visit-time (tc/to-date (t/now))]])))

;; (create-visitor {:id "11" :email "sherbondye@gmail.com" :name "Ethan Sherbondy"})
;; (visitor-with-id "10")
;; (get-visitor "10")
;; (signin "10")
;; (last-signin "10")
;; (present? "10")

