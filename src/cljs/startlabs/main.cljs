(ns startlabs.main
  (:use [jayq.core :only [$]]
        [singult.core :only [render]]
        [startlabs.views.jobx :only [job-card]]
        [startlabs.jobs :only [setup-jobs]])

  (:require [fetch.remotes :as remotes]
            [jayq.core :as jq]
            [jayq.util :as util]
            [startlabs.util :as u])
  
  (:require-macros [fetch.macros :as fm]
                   [jayq.macros :as jm]))

; browser repl for development
; (repl/connect "http://localhost:9000/repl")

; haven't got redirect uri working yet
(defn handle-hash-change [& e]
  (let [hash-vals    (u/mapify-hash)
        access-token (:access_token hash-vals)
        expiration   (:expires_in hash-vals)
        redirect-uri (or (:state hash-vals) "/")]
    (if access-token
      (do
        (jq/text ($ "#loading") "Verifying credentials...")
        (fm/remote (token-info access-token) [result]
          (u/log (util/clj->js result))
          (u/redirect! redirect-uri)
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

(defn main []
  (if u/location-hash (handle-hash-change))
  (set! (.-onhashchange js/window) handle-hash-change)

  (setup-team))

(jm/ready
  (main)

  (if (u/exists? "#map")
    (setup-jobs)))

