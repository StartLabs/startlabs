(ns startlabs.models.event
    (:use [datomic.api :only [q db ident] :as d]
          [startlabs.models.database :only [conn]]
          [startlabs.util :only [stringify-values]])
  (:require [clojure.string :as str]
            [startlabs.models.user :as user]
            [startlabs.models.util :as util]))


(defn create-event [event-map]
  ; might need to conj :confirmed? false
  (let [tx-data (util/txify-new-entity :event event-map)]
    @(d/transact @conn tx-data)
    event-map))

(defn get-latest-event
  "returns all confirmed jobs whose start dates are after a certain date"
  []
  (let [events    (q '[:find ?event :where [?event :event/description _]] (db @conn))
        event-id  (first (last events))
        event-map (util/map-for-datom event-id :event)]
    (stringify-values event-map)))