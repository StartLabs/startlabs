(ns startlabs.main
  (:use [singult.core :only [render]]
        [jayq.core :only [$]]
        [startlabs.views.job :only [job-card]])
  (:require [clojure.string :as str]
            [fetch.remotes :as remotes]
            [clojure.browser.repl :as repl]
            [jayq.core :as jq]
            [jayq.util :as util])
  (:require-macros [fetch.macros :as fm]
                   [jayq.macros :as jm]))

; browser repl for development
; (repl/connect "http://localhost:9000/repl")

(def location-hash (.-hash js/location))

(defn mapify-hash 
  "convert location.hash into a clojure map"
  []
  (let [hash (.slice location-hash 1)
        split-hash (.split hash #"[=&]")
        keyvals (map-indexed (fn [idx val] (if (even? idx) (keyword val) val)) split-hash)]
    (apply hash-map keyvals)))


(defn handle-hash-change [& e]
  (let [hash-vals (mapify-hash)
        access-token (:access_token hash-vals)
        expiration (:expires_in hash-vals)]
    (if access-token
      (do
        (jq/text ($ "#loading") "Verifying credentials...")
        (fm/remote (token-info access-token) [result]
          (.log js/console (util/clj->js result))
          (set! (.-location js/window) "/")
        )))))

(defn swap-picture-preview []
  (jq/attr ($ "#preview") "src" (jq/val ($ "#picture"))))

(defn update-bio-preview []
  (let [$bio ($ "#bio")]
    (if (not (empty? $bio))
      (jq/inner ($ "#bio-preview") 
        (->> $bio
          .val
          markdown/mdToHtml)))))

(defn form-to-map [$form]
  (into {} (for [field (.serializeArray $form)]
    { (keyword (.-name field)) (str/trim (.-value field))})))

(defn main []
  (if location-hash (handle-hash-change))
  (set! (.-onhashchange js/window) handle-hash-change)

  (.setKey js/filepicker "AuL8SYGe7TXG-aEqBK1S1z")
  (jq/bind ($ "#new-picture") :click (fn [e]
    (.preventDefault e)
    (.getFile js/filepicker "image/*" (fn [url metadata]
      (jq/attr ($ "#picture") "value" url)
      (swap-picture-preview)))))

  (jq/bind ($ "#picture") :keyup swap-picture-preview)
  (jq/bind ($ "#bio") :keyup update-bio-preview)

  (.datepicker ($ ".datepicker"))

  (let [$elems ($ "#job-form input, #job-form textarea")]
    (letfn [(update-job-card [e]
              (.html ($ "#job-preview")
                     (job-card (form-to-map ($ "#job-form")))))]
      (jq/bind $elems :keyup update-job-card)
      (jq/bind $elems :blur update-job-card)))

  (update-bio-preview))

(jm/ready
  (.log js/console "Hello world!")
  (main))

