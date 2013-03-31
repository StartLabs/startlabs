(ns startlabs.util
  (:require [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.string :as str]
            [noir.validation :as vali]
            [sandbar.stateful-session :as session])
  (:use [clj-time.coerce :only [from-date to-date to-long]]
        [environ.core :only [env]])
  (:import java.util.Date)
  (:import org.joda.time.DateTime))

(def default-date-format "MM/dd/YYYY")
(def default-date-formatter (tf/formatter default-date-format))

(defn inty [n]
  (case n
    nil nil
    ""  nil
    (Integer. n)))

(defn intify [n fallback] ^:clj
  (try 
    (Integer. n)
    (catch Exception e fallback)))

(defn parse-date 
  "Returns the parsed date if valid, otherwise returns false"
  [the-date]
  (try (tf/parse default-date-formatter the-date) 
    (catch Exception e false)))

(defn unparse-date [the-date]
  (tf/unparse default-date-formatter the-date))

(defn now-date []
  (to-date (t/now)))

(defn a-week-ago []
  (t/minus (t/now) (t/weeks 1)))

(defn n-days-ago [n]
  (t/minus (t/now) (t/days n)))

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
    clojure.lang.Keyword   (name val)
    java.util.Date         (unparse-date (from-date val))
    org.joda.time.DateTime (unparse-date val)
    (str val)))

(defn stringify-values [m]
  (into {} (map (fn [[k v]] 
    {k (stringify-value v)}) m)))

(defn trim-vals [m]
  (into {} (map (fn [[k v]] {k (if (string? v)
                                 (str/trim v)
                                 v)}) m)))

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

(defn home-uri [& [route]]
  (str
   (if (env :dev)
     (str "http://localhost:" (or (env :port) "8000"))
     "http://www.startlabs.org") route))
;; (home-uri "/jobs")

(defn empty-rule [k v]
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
               "Smalltalk" "D" "Julia" "OCaml" "Scala" "Arc" "Go"]
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
       :start-date (to-date start)
       :end-date (to-date (t/plus start (t/months (inc (rand-int 6)))))
       :description (apply str (repeatedly 10 rand-job))
       :email email
       :contact-info email
       :confirmed? true
       :removed? false
       :fulltime? (zero? (rand-int 2))
       :company-size (+ (rand-int 100) 10)
       :tags (repeatedly 3 #(rand-nth positions))
       })))


;; from clojure.contrib.fcase

(defmacro fcase
  "Generic switch/case macro.  'fcase' is short for 'function case'.

  The 'compare-fn' is a fn of two arguments.

  The 'test-expr-clauses' are value-expression pairs without
  surrounding parentheses, like in Clojure's 'cond'.

  The 'case-value' is evaluated once and cached.  Then, 'compare-fn'
  is called once for each clause, with the clause's test value as its
  first argument and 'case-value' as its second argument.  If
  'compare-fn' returns logical true, the clause's expression is
  evaluated and returned.  If 'compare-fn' returns false/nil, we go to
  the next test value.

  If 'test-expr-clauses' contains an odd number of items, the last
  item is the default expression evaluated if no case-value matches.
  If there is no default expression and no case-value matches, fcase
  returns nil.

  See specific forms of this macro in 'case' and 're-case'.

  The test expressions in 'fcase' are always evaluated linearly, in
  order.  For a large number of case expressions it may be more
  efficient to use a hash lookup."
  [compare-fn case-value &
   test-expr-clauses]
  (let [test-val-sym (gensym "test_val")
	test-fn-sym (gensym "test_fn")
	cond-loop (fn this [clauses]
		      (cond
		       (>= (count clauses) 2)
		       (list 'if (list test-fn-sym (first clauses) test-val-sym)
			     (second clauses)
			     (this (rest (rest clauses))))
		       (= (count clauses) 1) (first clauses)))]
    (list 'let [test-val-sym case-value, test-fn-sym compare-fn]
	  (cond-loop test-expr-clauses))))

(defmacro re-case
  "Like case, but the test expressions are regular expressions, tested
  with re-find."
  [test-value & clauses]
  `(fcase re-find ~test-value ~@clauses))


;; get rid of session boilerplate
(defn flash-message! [type message]
  (session/flash-put! :message [type message]))
