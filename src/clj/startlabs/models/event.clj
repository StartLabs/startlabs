(ns startlabs.models.event
    (:use [datomic.api :only [q db ident] :as d]
          [clj-time.core :only [now]]
          [clj-time.coerce :only [to-date]]
          [startlabs.models.database :only [*conn*]]
          [startlabs.util :only [stringify-values]])
  (:require [clojure.string :as str]
            [startlabs.models.user :as user]
            [startlabs.models.util :as util]))

(defn get-event []
  (let [events (q '[:find ?event ?description :in $
                    :where [?event :event/description ?description]] (db *conn*))
        event (first events)]
    event))

(defn create-event [event-map]
  (util/create-or-update (first (get-event))
                         :event
                         event-map))