(ns startlabs.models.database
  (:use [datomic.api :only [q db] :as d]
        [clojure.pprint :only [pp pprint]]
        [clojure.tools.logging :only [log]]))

(def uri  "datomic:free://localhost:4334/startlabs")
(def conn (atom nil))

(defn- set-conn! []
  (reset! conn (d/connect uri)))

(defn do-setup [uri schema]
  (if (d/create-database uri)
    (let [schema-tx (read-string (slurp schema))]
      (set-conn!) ; reset the connection since we just created the database
      @(d/transact @conn schema-tx))

    ; if create returns false, then the db already exists
    (do
      (set-conn!)
      (pprint "Database already exists"))))

(defn do-default-setup []
  (let [schema "src/clj/startlabs/models/schema.dtm"]
    (do-setup uri schema)))