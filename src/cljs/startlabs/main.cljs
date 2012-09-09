(ns startlabs.main
  (:use [jayq.core :only [$]]
        [singult.core :only [render]]
        [startlabs.views.jobx :only [job-card]]
        [startlabs.maps :only [setup-maps]])
  (:require [clojure.string :as str]
            [fetch.remotes :as remotes]
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

; haven't got redirect uri working yet
(defn handle-hash-change [& e]
  (let [hash-vals (mapify-hash)
        access-token (:access_token hash-vals)
        expiration   (:expires_in hash-vals)
        redirect-uri (or (:state hash-vals) "/")]
    (if access-token
      (do
        (jq/text ($ "#loading") "Verifying credentials...")
        (fm/remote (token-info access-token) [result]
          (.log js/console (util/clj->js result))
          (set! (.-location js/window) redirect-uri)
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

(defn setup-team []
  ;filepicker
  (.setKey js/filepicker "AuL8SYGe7TXG-aEqBK1S1z")

  (jq/bind ($ "#new-picture") :click (fn [e]
    (.preventDefault e)
    (.getFile js/filepicker "image/*" (fn [url metadata]
      (jq/attr ($ "#picture") "value" url)
      (swap-picture-preview)))))

  ; picture and bio live previews
  (jq/bind ($ "#picture") :keyup swap-picture-preview)
  (jq/bind ($ "#bio") :keyup update-bio-preview)

  (update-bio-preview))

(defn form-to-map [$form]
  (into {} (for [field (.serializeArray $form)]
    { (keyword (.-name field)) (str/trim (.-value field))})))

(defn setup-job-submit []
  (.datepicker ($ ".datepicker"))

  (let [$elems ($ "#job-form input, #job-form textarea")]
    (letfn [(update-job-card [e]
              (.html ($ "#job-preview")
                     (render (job-card (form-to-map ($ "#job-form")))))
              ; singult is escaping the generated markdown :(
              (.html ($ "#job-preview .description") 
                     (markdown/mdToHtml (.val ($ "#description")))))]
      (jq/bind $elems :keyup update-job-card)
      (jq/bind $elems :blur update-job-card))))

(defn main []
  (if location-hash (handle-hash-change))
  (set! (.-onhashchange js/window) handle-hash-change)

  (setup-team)
  (setup-job-submit))

(jm/ready
  (.log js/console "Hello world!")
  (main)
  (setup-maps))

