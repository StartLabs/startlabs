(ns startlabs.util
  (:use [jayq.core :only [$]])
  
  (:require [clojure.string :as str]))

(defn redirect! [url]
  (set! (.-location js/window) url))

(def location-hash (.-hash js/location))

(defn log [v]
  (.log js/console v))

(defn exists? [$sel]
  (not= (.-length ($ $sel)) 0))

(defn form-to-map 
  "Converts a form's inputs into a hashmap.
  Takes a jQuery selector as input."
  [$form]
  (into {} (for [field (.serializeArray $form)]
    { (keyword (.-name field)) (str/trim (.-value field))})))

(defn hash-mapify-vector
  "Converts a vector of strings into a hashmap,
   assuming that the strings alternate between k and v.
   Auto-keywordizes the keys."
  [v]
  (apply hash-map
    (map-indexed 
      (fn [idx val] 
        (if (even? idx) 
            (keyword val) 
            val)) v)))

(defn mapify-hash 
  "convert location.hash into a clojure map"
  []
  (let [hash (.slice location-hash 1)
        split-hash (.split hash #"[=&]")]
    (hash-mapify-vector split-hash)))