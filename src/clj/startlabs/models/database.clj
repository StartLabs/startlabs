(ns startlabs.models.database
  (:use [datomic.api :only [q db] :as d]
        [clojure.pprint :only [pp pprint]]
        [clojure.tools.logging :only [log]]))

(def uri  "datomic:free://localhost:4334/startlabs")
(def ^:dynamic *conn* nil)

(defn do-setup [uri]
  (d/create-database uri)
  ;; reset the connection since we just created the database
  (def ^:dynamic *conn* (d/connect uri)))

(defn do-default-setup []
  (println "Connecting to db...")
  (do-setup uri))