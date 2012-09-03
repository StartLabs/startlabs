(ns startlabs.models.job
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [conn]]
        [environ.core :only [env]])
  (:require [clojure.string :as str]
            [oauth.google :as oa]
            [clj-http.client :as client]
            [noir.session :as session]
            [startlabs.models.util :as util])
  (:import java.net.URI))

(def ns-matches-job '[[(ns-matches ?nom)
                      [(re-matches #"^:job" ?nom)]]])

(defn job-fields []
  (util/map-of-entities ns-matches-job))