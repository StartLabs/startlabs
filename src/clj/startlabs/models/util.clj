(ns startlabs.models.util
  (:require [aws.sdk.s3 :as s3]
            [clojure.string :as str]
            [clj-time.format :as t]
            [sandbar.stateful-session :as session]
            [startlabs.util :as u])
  (:use [clj-time.core :only [after? now]]
        [clj-time.coerce :only [to-long to-date]]
        [clojure.java.io :only [input-stream]]
        [datomic.api :only [q db ident] :as d]
        [environ.core :only [env]]
        [startlabs.models.database :only [*conn*]])
  (:import java.net.URI))

; http://www.filepicker.io/api/file/l2qAORqsTSaNAfNB6uP1
(defn save-file-to-s3 
  "takes a file from a temporary url, downloads it, and saves to s3, returning
   the url of the file on s3."
  [temp-url file-name]
  (let [aws-creds   {:access-key (env :aws-key) :secret-key (env :aws-secret)}
        bucket-name "startlabs"]
    (with-open [picture-file (input-stream temp-url)]
      (s3/put-object aws-creds bucket-name file-name picture-file)
      (s3/update-object-acl aws-creds bucket-name file-name 
                            (s3/grant :all-users :read))
      (str "https://s3.amazonaws.com/" bucket-name "/" file-name))))

; the transforming and de/namespacing functions should be their own helper library...
(defmulti 
  transform-attr 
  "Transforms an attribute to the proper database type,
   dispatching based on the attribute's current type and 
   the desired datomic type.
   Example: (transform-attr 'www.google.com' :db.type/uri)
   will yield <URI 'www.google.com'>"
  (fn [attr db-type] [(type attr) db-type])
  :default [String :db.type/string])

(defmethod transform-attr [String :db.type/string] [attr _] attr)

(defmethod transform-attr [String :db.type/uri] [attr _]
  (URI. attr))

(defmethod transform-attr [String :db.type/long] [attr _]
  (Integer/parseInt attr))

(defmethod transform-attr [String :db.type/float] [attr _]
  (Float/parseFloat attr))

(defmethod transform-attr [String :db.type/double] [attr _]
  (Double/parseDouble attr))

(defmethod transform-attr [String :db.type/instant] [attr _]
  (to-date (u/parse-date attr)))

(defmethod transform-attr [org.joda.time.DateTime :db.type/instant] [attr _]
  (to-date attr))

(defmethod transform-attr [String :db.type/boolean] [attr _]
  (if (= (str/lower-case attr) "true") 
    true 
    false))

; Special case for startlabs. 
; Maybe could rely on convention in the general case?
; Any refs of the form :a/b could be transformed to (str "a.b/" attr)
(defmethod transform-attr [String :user/gender] [attr _]
  (keyword (str "user.gender/" attr)))



(defn transform-tx-values
  "Takes a map for a pending transaction and transforms the values, 
   dispatching based on current and desired :db/valueType,
   e.g. transforms the string 'http://www.google.com'
   to a proper URI if its corresponding :db/valueType is :db.type/uri.
   If the type is :db.type/ref, dispatches based on the key instead, 
   eg: :user/gender"
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

; note: you must pass a function: ns-matches, which evaluates the namespace for a match
(def q-schema-attrs
  '[:find ?name ?val-type ?doc
    :in $ ?desired-ns
    :where [_ :db.install/attribute ?a]
           [?a :db/ident ?name]
           [(namespace ?name) ?ns]
           [(= ?desired-ns ?ns)]
           [?a :db/valueType ?val-type]
           [?a :db/doc ?doc]])

(defn map-of-entities
  "Converts the set of [:attr-name valueType-entid] pairs returned by the query
   into a single map of {:attr-name :valueType-ident ...} pairs"
  [desired-ns]
  (let [conn-db (db *conn*)
        schema  (q q-schema-attrs conn-db (name desired-ns))]
    (into {} (for [[k v] schema]
                {k (ident conn-db v)}))))

(defn map-of-entity-tuples
  "Like map of entities, but returns a tuple containing the valueType and docstring"
  [desired-ns]
  (let [conn-db (db *conn*)
        schema (q q-schema-attrs conn-db (name desired-ns))]
    (into {} (for [[k & vs] schema]
                {k (cons (ident conn-db (first vs)) (rest vs))}))))

(defn temp-identify [tx-map]
  (assoc tx-map :db/id (d/tempid :db.part/user)))

(defn entity-map-with-nil-vals [desired-ns]
  (zipmap (keys (map-of-entities desired-ns)) (repeat nil)))

(defn namespace-and-transform 
  "Prepends the-ns to each key in the tx-data map. Also strips out any keys not present
   in the schema (derived from the-ns)."
  [the-ns tx-data]
  (let [entity-map          (map-of-entities the-ns)
        tranny-entity-data  (transform-tx-values (namespace-keys the-ns tx-data) 
                                                  entity-map)
        ; only add attributes that are present in the schema
        clean-entity-data   (into {} (map (fn [[k v]] 
                              (if (k entity-map) {k v})) tranny-entity-data))]
    clean-entity-data))

(defn txify-new-entity
  "Converts a map representation of an entity into a database transaction, with the-ns
   prepended to each key."
  [the-ns entity-data]
  (let [clean-entity-data (namespace-and-transform the-ns entity-data)
        tx-map            (temp-identify clean-entity-data)]
    [tx-map]))


(defn map-for-datom
  "Takes as input a single datomic entity id (the output of ffirsting query)"

  ([datom-id desired-ns]
    (map-for-datom datom-id desired-ns (entity-map-with-nil-vals desired-ns)))

  ([datom-id desired-ns ent-map]
    ; we don't actually use inputs here
     (let [datom-entity (d/entity (db *conn*) datom-id)]
       (denamespace-keys (conj ent-map
                               (into {} datom-entity))))))

(defn maps-for-datoms
  "Expects that all datoms have the same schema"
  [datom-ids desired-ns]
  (let [ent-map (entity-map-with-nil-vals desired-ns)]
    (for [id datom-ids] (map-for-datom id desired-ns ent-map))))


(defn elem-with-attr
  ([k v] (elem-with-attr k v (db *conn*)))
  ([k v conn-db]
    (ffirst (q '[:find ?e
                 :in $ ?v ?ns-key
                 :where [?e ?ns-key ?v]] conn-db v k))))


(defn create-or-update [entity ns ent-map]
  (try
    (if entity
      ;; if the event has been set previously
      (let [fact         (namespace-and-transform ns ent-map)
            idented-fact (assoc fact :db/id entity)]
        @(d/transact *conn* [idented-fact]))
      ;; otherwise, create a new entity
      (let [new-fact (txify-new-entity ns ent-map)]
        @(d/transact *conn* new-fact)))
    (catch Exception e
      (u/flash-message! :error (str "Trouble setting " (name ns) ": " e)))))
