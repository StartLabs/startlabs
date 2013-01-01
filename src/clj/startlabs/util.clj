(ns startlabs.util
  (:require [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.string :as str]
            [noir.validation :as vali])
  (:use [clj-time.coerce :only [from-date to-date to-long]]
        [environ.core :only [env]])
  (:import java.util.Date))

(def default-date-format "MM/dd/YYYY")
(def default-date-formatter (tf/formatter default-date-format))

(defn parse-date 
  "Returns the parsed date if valid, otherwise returns false"
  [the-date]
  (try (tf/parse default-date-formatter the-date) 
    (catch Exception e false)))

(defn unparse-date [the-date]
  (tf/unparse default-date-formatter the-date))

(defn after-now?
  "Determines if the provided date is in the future."
  [date]
  (> (to-long date) (to-long (t/now))))

(defn uuid []
  (str (java.util.UUID/randomUUID)))


(defn phrasify 
  "Convert an underscore-delimited phrase into capitalized words."
  [key-str]
  (str/capitalize (str/replace (name key-str) #"[_-]" " ")))

(defn stringify-value [val]
  (condp = (type val)
    clojure.lang.Keyword (name val)
    Date    (tf/unparse default-date-formatter (from-date val))
    (str val)))

(defn stringify-values [m]
  (into {} (map (fn [[k v]] 
    {k (stringify-value v)}) m)))

(defn trim-vals [m]
  (into {} (map (fn [[k v]] {k (str/trim v)}) m)))

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

(defn httpify-url 
  "Don't force users to type http://..."
  [url]
  (if (and (not (re-find #"^https?://" url))
           (not (empty? url)))
    (str "http://" url)
    url))

(defn home-uri []
  (if (env :dev)
    (str "http://localhost:" (or (env :port) "8000"))
    "http://www.startlabs.org"))

(defn empty-rule [[k v]]
  (vali/rule 
    (or (false? v) (vali/has-value? v)) [k "This field cannot be empty."]))

;; generate a list of fake jobs for testing purposes
(defn fake-job []
  (let [nouns ["Todo" "Note" "Bit" "Bot" "Corp" "Drive" "Net" 
               "SQL" "DB" "Tube" "Wire"]
        adjectives ["Super" "Simple" "Fast" "Easy" "Hyper" 
                    "Ultra" "Mega" "Big" "Small"]
        positions ["Ninja" "Superstar" "Expert" "Polyglot" 
                   "Hacker" "Programmer" "Developer" "Designer"]
        langs ["Frontend" "Ruby" "Rails" "Clojure" "Haskell" 
               "Brainfuck" "D" "Julia" "OCaml" "Scala" "Arc" "Go"]
        places [["San Francisco, CA" 37.7750 -122.4183]
                ["Raleigh, NC" 35.7719 -78.6389]
                ["Boston, MA" 42.3583 -71.0603]
                ["New York City" 40.7142 -74.0064]]
        location (rand-nth places)
        start (t/date-time 2013 (inc (rand-int 11)) (inc (rand-int 27)))
        email "ethan@startlabs.org"]
    (letfn [(rand-id [n] (apply str (map #(rand-int %) (repeat n 10))))
            (rand-job [] (apply str (interpose " " 
                           (map rand-nth [adjectives langs positions]))))]
      {:id (rand-id 10)
       :secret (rand-id 10)
       :position (rand-job)
       :company (str (rand-nth adjectives) (rand-nth nouns))
       :location (first location)
       :latitude (+ (nth location 1) (rand))
       :longitude (+ (nth location 2) (rand))
       :website "http://www.startlabs.org"
       :start_date (to-date start)
       :end_date (to-date (t/plus start (t/months (inc (rand-int 6)))))
       :description (apply str (repeatedly 10 rand-job))
       :email email
       :contact_info email
       :confirmed? true
       :removed? false
       :fulltime (zero? (rand-int 2))
       :company_size (+ (rand-int 100) 10)
       :tags (repeatedly 3 #(rand-nth positions))
       })))
