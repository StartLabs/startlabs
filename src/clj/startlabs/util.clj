(ns startlabs.util
  (:require [startlabs.models.util :as mu]
            [clj-time.format :as t])
  (:use [clj-time.coerce :only [from-date]])
  (:import java.util.Date))

(defn stringify-value [val]
  (condp = (type val)
    clojure.lang.Keyword (name val)
    Date    (t/unparse mu/default-date-formatter (from-date val))
    (str val)))

(defn stringify-values [m]
  (into {} (map (fn [[k v]] 
    {k (stringify-value v)}) m)))

(defn map-diff 
  "Returns a map of the key/value pairs in m1 
   for which the values are different from m2
   Eg: (map-diff {:a :one :b :two} {:a :one :b :three :c :test})
   would yield {:b :two}"
  [m1 m2]
  (into {}  (map (fn [[k v]]
              (if (not (= v (k m2)))
                {k v}))
            m1)))

(defn nil-empty-str-values [m]
  (into {} (map (fn [[k v]] 
    {k (if (= v "") nil v)}) m)))

(defn cond-class 
  "Returns a string of class names separated by spaces.
   default is returned regardless of the conditions.
   Conditions is a sequence of [condition value] pairs,
   for which the class 'value' is returned if the condition is met.
   Eg: (cond-class \"pizza\" [true \"bagels\"] [false \"spinach\"])
   would return: \"pizza bagels\""
  [default & conditions]
  (apply str default
    (for [[condition value] conditions]
      (if condition (str " " value)))))