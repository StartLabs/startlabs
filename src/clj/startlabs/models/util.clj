(ns startlabs.models.util
  (:use [datomic.api :only [q db ident] :as d]
        [startlabs.models.database :only [conn]])
  (:require [clojure.string :as str])
  (:import java.net.URI))

; the transforming and de/namespacing functions should be their own helper library...
(defmulti 
  transform-attr 
  "Transforms an attribute to the proper database type,
   dispatching based on the attribute's current type and 
   the desired datomic type.
   Example: (transform-attr 'www.google.com' :db.type/uri)
   will yield <URI 'www.google.com'>"
  (fn [attr attr-type] [(type attr) attr-type])
  :default [String :db.type/string])

(defmethod transform-attr [String :db.type/string] [attr _] attr)

(defmethod transform-attr [String :db.type/uri] [attr _]
  (URI. attr))

(defmethod transform-attr [String :db.type/long] [attr _]
  (Integer/parseInt attr))

(defmethod transform-attr [String :user/gender] [attr _]
  (keyword (str "user.gender/" attr)))


(defn transform-tx-values
  "Takes a map for a pending transaction and transforms the values, 
   dispatching based on current and desired :db/valueType,
   e.g. transforms the string 'http://www.google.com'
   to a proper URI if its corresponding :db/valueType is :db.type/uri.
   If the type is :db.type/ref, dispatches based on the key instead, eg: :user/gender"
  [tx-map entity-map]
  (into {} (for [[k v] tx-map]
    (let [attr-type (k entity-map)
          dereferenced-type (if (= attr-type :db.type/ref)
                              k
                              attr-type)]
      {k (transform-attr v dereferenced-type)}))))

(defn namespace-key [the-ns k]
  (keyword (str (name the-ns) "/" (name k))))

(defn namespace-keys 
  "Converts the keys of the-map to be prefixed with the-ns/
  (namespace-keys :user {:locale 'en'}) returns {:user/locale 'en'}"
  [the-ns the-map]
  (into {} (for [[k v] the-map] 
    {(namespace-key the-ns k) v})))

(defn denamespace-keys
  "The inverse of namespace-keys. Do not have to specify the-ns"
  [the-map]
  (into {} (for [[k v] the-map] 
    {(keyword (last (str/split (name k) #"/")))
     v})))

(defn map-of-entities 
  "Converts the set of [:attr-name valueType-entid] pairs returned by the query
   into a single map of {:attr-name :valueType-ident ...} pairs"
  [query]
  (let [conn-db (db @conn)
        schema  (q query conn-db)]
    (into {} (for [[k v] schema]
                {k (ident conn-db v)}))))

(defn temp-identify [tx-map]
  (assoc tx-map :db/id (d/tempid :db.part/user)))

(defn entity-map-with-nil-vals [query]
  (zipmap (keys (map-of-entities query)) (repeat nil)))