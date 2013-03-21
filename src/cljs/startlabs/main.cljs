(ns startlabs.main
  (:use [jayq.core :only [$]]
        [startlabs.views.job-list :only [markdownify]]
        [startlabs.jobs :only [setup-jobs-list 
                               setup-job-analytics
                               setup-job-container
                               setup-job-submit]]
        [startlabs.visitors :only [setup-visitors]])

  (:require [jayq.core :as jq]
            [startlabs.util :as u])
  
  (:require-macros [jayq.macros :as jm]))

; browser repl for development
; (repl/connect "http://localhost:9000/repl")

(defn handle-hash-change [& e]
  (let [hash-vals    (u/mapify-hash)]))
   
(defn swap-picture-preview []
  (jq/attr ($ "#preview") "src" (jq/val ($ "#picture"))))

(defn update-bio-preview []
  (let [$bio ($ "#bio")]
    (if (not (empty? $bio))
      (jq/inner ($ "#bio-preview") 
        (->> $bio
          .val
          markdownify)))))

(defn setup-me []
  (let [$me ($ "#me")]
    ;filepicker
    (.setKey js/filepicker "AuL8SYGe7TXG-aEqBK1S1z")

    (jq/on $me :click "#new-picture"
           (fn [e]
             (.preventDefault e)
             (.pick js/filepicker 
                    (clj->js {:mimetypes "image/*"})
                    (fn [fp-file]
                      (jq/attr ($ "#picture") "value" (.-url fp-file))
                      (swap-picture-preview)))))

    ; picture and bio live previews
    (jq/on $me :keyup "#picture" swap-picture-preview)
    (jq/on $me :keyup "#bio" update-bio-preview)

    (update-bio-preview)))

(defn setup-home []
  (let [$events     ($ "#upcoming-events")
        $event-text ($ "#event-text")]
    (jq/on $events :click "#edit-upcoming"
           (fn [e]
             (.preventDefault e)
             (.toggleClass ($ "#event-form") "hidden")
             (.focus $event-text)))

    (jq/on $events :keyup "#event-text"
           (fn [e]
             (this-as this
                      (let [new-text (.val ($ this))]
                        (.html ($ "#event-info")
                               (markdownify new-text))))))))

(defn main []
  (if u/location-hash (handle-hash-change))
  (set! (.-onhashchange js/window) handle-hash-change))

;; setup hash handling immediately
(main)

(jm/ready
 (.datepicker ($ ".datepicker"))

 (if (u/exists? "#upcoming-events")
   (setup-home))

 (if (u/exists? "#me")
   (setup-me))

 (if (u/exists? "#map")
   (setup-jobs-list))
 
 (if (u/exists? "#job-container")
   (setup-job-container))

 (if (u/exists? "#job-form")
   (setup-job-submit))

 (if (u/exists? "#analytics")
   (setup-job-analytics))

 (if (u/exists? "#visitors")
   (setup-visitors)))