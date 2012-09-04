(ns startlabs.maps
  (:use [jayq.core :only [$]])

  (:require [clojure.string :as str]
            [fetch.remotes :as remotes]
            [jayq.core :as jq]
            [jayq.util :as util])

  (:require-macros [fetch.macros :as fm]
                   [jayq.macros :as jm]))

(def cloudmade-key "fe134333250f494fb51ac8903b83c9fb")

(defn setup-maps []
  (.log js/console "MAPPIN"))