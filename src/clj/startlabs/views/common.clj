(ns startlabs.views.common
  (:use [noir.core :only [defpartial]]
        [hiccup.page :only [include-css include-js html5]]))

(defpartial layout [& content]
            (html5
              [:head
               [:title "startlabs"]]
              [:body
               [:div#wrapper
                content]
               (include-js "/jquery.js")
               (include-js "/client.js")]))