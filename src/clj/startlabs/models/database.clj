(ns startlabs.models.database
  (:use [datomic.api :only [q db] :as d]
        [clojure.pprint]))

(def uri    "datomic:free://localhost:4334/startlabs")
(def schema "startlabs/models/schema.dtm")

(defn do-setup [uri schema]
  (if (d/create-database uri)
    (do
      (def conn (d/connect uri))
      (def schema-tx (read-string (slurp "startlabs/models/schema.dtm")))
      @(d/transact conn schema-tx)))
    "Database already exists")

(defn do-default-setup []
  (do-setup uri schema))