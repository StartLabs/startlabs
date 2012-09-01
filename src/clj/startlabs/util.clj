(ns startlabs.util)

(defn stringify-value [val]
  (if (keyword? val)
    (name val)
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