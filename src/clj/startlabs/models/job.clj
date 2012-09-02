(ns startlabs.models.job
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [conn]]
        [environ.core :only [env]])
  (:require [clojure.string :as str]
            [oauth.google :as oa]
            [clj-http.client :as client]
            [noir.session :as session]
            [startlabs.util :as util])
  (:import java.net.URI)))