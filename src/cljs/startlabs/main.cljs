(ns startlabs.main
  (:use [jayq.core :only [$]]
        [startlabs.views.jobx :only [markdownify]]
        [startlabs.jobs :only [setup-jobs-list setup-job-submit]])

  (:require [jayq.core :as jq]
            [jayq.util :as util]
            [startlabs.util :as u])
  
  (:require-macros [jayq.macros :as jm]))

; browser repl for development
; (repl/connect "http://localhost:9000/repl")

(defn handle-hash-change [& e]
  (let [hash-vals    (u/mapify-hash)]
    (u/log (str "Hash changed: " hash-vals))))

(defn swap-picture-preview []
  (jq/attr ($ "#preview") "src" (jq/val ($ "#picture"))))

(defn update-bio-preview []
  (let [$bio ($ "#bio")]
    (if (not (empty? $bio))
      (jq/inner ($ "#bio-preview") 
        (->> $bio
          .val
          markdownify)))))

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

(defn setup-home []
  (jq/bind ($ "#edit-upcoming") :click (fn [e]
                                         (.preventDefault e)
                                         (.toggleClass ($ "#event-form") "hidden")
                                         (.focus ($ "#event-text"))))

  (let [$event-info ($ "#event-info")
        $event-text ($ "#event-text")]
    (jq/bind $event-text :keyup (fn [e]
                                  (let [new-text (.val $event-text)]
                                    (.html $event-info (markdownify new-text)))))))

(defn main []
  (if u/location-hash (handle-hash-change))
  (set! (.-onhashchange js/window) handle-hash-change)
  (setup-home)
  (setup-team)

  (if (u/exists? "#map")
    (setup-jobs-list))

  (if (u/exists? "#job-form")
    (setup-job-submit)))

 ;; setup hash handling immediately
(main)