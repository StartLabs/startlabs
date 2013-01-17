(ns startlabs.visitors
  (:use [jayq.core :only [$]])

  (:require [clojure.string :as str]
            [cljs-hash.goog :as gh]
            [jayq.core :as jq]
            [jayq.util :as util]
            [startlabs.util :as u])
  
  (:require-macros [jayq.macros :as jm]))

(defn gravatar-link [email & [size]]
  (let [clean-email (str/lower-case (str/trim email))]
    (str "http://www.gravatar.com/avatar/" 
         (gh/hash :md5 clean-email)
         "?s=" (or size 64))))

(defn setup-visitors []
  (let [$email    ($ "#email")
        $gravatar ($ "#gravatar")]
    (jq/on $email :keyup (fn [e]
      (jq/attr $gravatar "src"
               (gravatar-link (.val $email))))))
  
  (if (some #(u/exists? %) ["#hello" "#goodbye"])
    (js/setTimeout #(set! (.-location js/window) "/signin")
                   5000)))