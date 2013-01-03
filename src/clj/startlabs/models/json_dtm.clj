(ns startlabs.models.json-dtm
  (:require [clojure.data.json :as json]
            [clojure.pprint :as pp]))

(defn get-sample-data []
  (json/read-str (slurp "src/clj/startlabs/models/userinfo_example.json")))

(defn datalogify [data ns]
  (letfn [(dtm-kv [k v]
            {
              :db/id "#db/id[:db.part/db]"
              :db/ident (keyword (str (name ns) "/" (name k)))
              :db/valueType :db.type/string
              :db/cardinality :db.cardinality/one
              :db/doc (str "Example value: " v)
              :db.install/_attribute :db.part/db
            })]
    (pp/pprint (apply vector (map dtm-kv (keys data) (vals data))))))