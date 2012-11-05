(ns startlabs.models.event
    (:use [datomic.api :only [q db ident] :as d]
          [clj-time.core :only [now]]
          [clj-time.coerce :only [to-date]]
          [startlabs.models.database :only [conn]]
          [startlabs.util :only [stringify-values]])
  (:require [clojure.string :as str]
            [startlabs.models.user :as user]
            [startlabs.models.util :as util]))


(defn create-event [event-map]
  (let [event-map (assoc event-map :updated (to-date (now)))
        tx-data   (util/txify-new-entity :event event-map)]
    @(d/transact @conn tx-data)
    event-map))

(defn get-latest-event
  "returns all confirmed jobs whose start dates are after a certain date"
  []
  (let [events (q '[:find ?updated ?descr :in $
                    :where [?event :event/description ?descr]
                           [?event :event/updated ?updated]] (db @conn))
        event (last (sort-by first events))]
    (last event)))