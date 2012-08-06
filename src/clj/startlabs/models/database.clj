(ns startlabs.models.database
  (:use [datomic.api :only [q db] :as d]
        [clojure.pprint]))

(def uri  "datomic:free://localhost:4334/startlabs")
(def conn (d/connect uri))

(defn do-setup [uri schema]
  (if (d/create-database uri)
    (let [schema-tx (read-string (slurp schema))]
      @(d/transact conn schema-tx))
    "Database already exists"))

(defn do-default-setup []
  (let [schema "src/clj/startlabs/models/schema.dtm"]
    (do-setup uri schema)))