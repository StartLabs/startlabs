(ns startlabs.views.jobs
  (:require [clojure.string :as str]
            [cheshire.core :as json]
            [clj-rss.core :as rss]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [noir.response :as response]
            [noir.validation :as vali]
            [postal.core :as postal]
            [ring.util.codec :as c]
            [ring.util.response :as rr]
            [sandbar.stateful-session :as session]
            [startlabs.models.analytics :as analytics]
            [startlabs.models.job :as job]
            [startlabs.models.user :as user]
            [startlabs.models.util :as mu]
            [startlabs.util :as u]
            [startlabs.views.common :as common])

  (:use [compojure.response :only [render]]
        [clojure.core.incubator]
        [clojure.math.numeric-tower :only [abs ceil]]
        [clojure.tools.logging :only [info]]
        [clj-time.coerce :only [to-long]]
        [environ.core :only [env]]
        [hiccup.core :only [html]]
        [hiccup.def :only [defhtml]]
        [markdown.core :only [md-to-html-string]]
        [startlabs.views.job-list 
         :only [job-card job-list ordered-job-keys required-job-keys]])

  (:import java.net.URI))

(defn edit-link [job-map]
  (u/home-uri (str "/job/" (:id job-map) "?secret=" (:secret job-map))))

(defhtml job-email-body [job-map]
  (let [conf-link     (u/home-uri (str "/job/" (:id job-map) "/confirm"))
        the-edit-link (edit-link job-map)]
    [:div
      [:p "Hey there,"]
      [:p "Thanks for submitting to the StartLabs jobs list."]
      [:p "To confirm your listing, " [:strong (:position job-map)]
          ", please visit this link:"]
      [:p [:a {:href conf-link} conf-link]]

      [:p "If you ever need to edit the listing, use this link:"]
      [:p [:a {:href the-edit-link} the-edit-link]]

      [:p "If this email was sent in error, feel free to ignore it or contact "
       (common/webmaster-link "our webmaster") "."]]))

(defn send-confirmation-email [job-map]
  (postal/send-message ^{:host (env :email-host)
                         :user (env :email-user)
                         :pass (env :email-pass)
                         :ssl  :yes}
    {:from    "jobs@startlabs.org"
     :to      (:email job-map)
     :subject "Confirm your StartLabs Job Listing"
     :body [{:type    "text/html; charset=utf-8"
             :content (job-email-body job-map)}]}))

(def hidden-job-keys [:longitude :latitude])

(defmulti input-for-field (fn [field type docs v]
  (keyword (name type))) :default :string)

(defmethod input-for-field :string [field type docs v]
  (let [input-type (if (= field :description) :textarea :input)]
    [input-type {:type "text" :id field :name field :value v
                 :rows 6 :placeholder docs :class "span12"}
      (if (= input-type :textarea) v)]))

(defhtml datepicker [name value date-format]
  [:input.datepicker {:type "text" :data-date-format date-format :value value
                      :id name :name name :placeholder date-format}])

(def jobs-date-fmt   (str/lower-case u/default-date-format))
(def google-date-fmt (str/lower-case analytics/google-date-format))

(defmethod input-for-field :instant [field type docs v]
  (datepicker field v jobs-date-fmt))

;; should generalize this to support arbitrary enum fields
(defmethod input-for-field :ref [field type docs v]
  (let [choices (mu/get-enum-vals :job/role)]
    [:div.btn-group {:data-toggle-name field :data-toggle "buttons-radio"}
     (for [choice choices]
       (let [val (u/phrasify choice)]
         [:button.btn {:value choice :data-toggle "button"} val]))
     [:input {:type "hidden" :name field :id field 
              :value (if (empty? v) 
                       (first choices)
                       v)}]]))

(defhtml error-item [[first-error]]
  [:span.help-block first-error])

(defhtml fields-from-schema [schema ordered-keys params]
  [:table.table
    [:tbody
      (for [field ordered-keys]
        (let [field-name  (name field)
              field-kw    (mu/namespace-key :job (name field))
              [type docs] (field-kw schema)
              error?      (vali/on-error field (fn [_] true))
              v           (or (field params) "")]
          [:tr {:class (if error? "error")}
            [:td [:label {:for field} (u/phrasify field-name)]]
            [:td
              ; dispatch input based on type
              (input-for-field field type docs v)
              (vali/on-error field error-item)]]))
      [:tr
        [:td]
        [:td
          [:input.btn.btn-primary {:type "submit" :value "Submit"}]]]]])

(def sample-job-fields
  {:position "Lab Assistant" :company "StartLabs"
   :company-size 20
   :role "internship"
   :location "Cambridge, Massachusetts"
   :website "http://www.startlabs.org" 
   :start-date "May 30, 2013" :end-date "August 30, 2013"
   :description "Smart people tackling difficult problems at a great location with *nice perks*.

Must have **4+ years** of lab experience.

We prefer candidates who wear green clothing."
   :contact-info "contact@startlabs.org"})

(def job-list-email-body
  (c/url-encode
   (str "Please fill out all of the following details for consideration:\n\n"
        "Startup Name: \n"
        "Website URL: \n"
        "Brief Description of your Company: \n\n"
        "Years since Incorporation: \n"
        "Number of Employees: \n"
        "Funding Received (Optional):")))

(defhtml submit-job [params]
  ;; make the distinction between editing existing values vs. creating a new job
  (let [has-params? (not (empty? params))
        editing?    (not (empty? (:id params)))
        heading     (if editing? "Edit Job" "Submit a Job")
        action      (if editing? (str "/job/" (:id params)) "/job/new")]

    [:div#submit
     [:h1 heading]
     [:form#job-form.row-fluid {:method "post" :action action}
      [:div.span6
       (fields-from-schema (job/job-fields) ordered-job-keys params)]

      (if editing?
        [:input {:type "hidden" :name "secret" :value (:secret params)}])

      (for [hidden-key hidden-job-keys]
        (let [hname (name hidden-key)]
          [:input {:type "hidden" :name hname :id hname 
                   :value (hidden-key params)}]))

      [:div.span6.clearfix.thumbnail
       [:div#job-preview
        (job-card (if has-params? params sample-job-fields) false)]
       [:h3.centered "Job Location Preview (Drag Pin to Relocate)"]
       [:div#job-location]]]]))

(defn parse-job-filters [filters]
  (apply merge (for [[k v] filters]
                 (u/re-case (name k)
                            #"date" {k (u/parse-date v)}
                            #"size" {k (u/inty v)}
                            #"show" {k (or (empty? v)
                                           (Boolean. v))}))))

;; [:post /jobs/filters]
(defn post-job-filters [params]
  (let [parsed-filters (parse-job-filters params)]
    (session/session-put! :filters parsed-filters)
    params))

(defn jobs-on-page [jobs page page-size]
  (take page-size (drop (* (dec page) page-size) jobs)))

(defhtml input-range [field classes filters]
  (let [field-name  (name field)
        min-field   (str "min-" field-name)
        max-field   (str "max-" field-name)
        field-label (u/phrasify field)
        class-str   (apply str (interpose " " classes))
        min-val     (or ((keyword min-field) filters) "")
        max-val     (or ((keyword max-field) filters) "")]
    [:div.control-group
     [:label.control-label {:for min-field} field-label]
     [:div.controls
      (for [[elem val placeholder] [[min-field min-val "Min"] 
                                    [max-field max-val "Max"]]]
        [:input
         {:type "text" :id elem :name elem :class class-str
          :placeholder placeholder :value (u/stringify-value val)}])]]))

(defhtml job-filters [filters]
  [:div#filter.modal.hide.fade {:tabindex "-1" :role "dialog" 
                                :aria-labelledby "filter-label"
                                :aria-hidden "true"}
   [:div.modal-header
    [:button.close {:type"button" :data-dismiss "modal" 
                    :aria-hidden "true"} "x"]
    [:h2#filter-label "Filtering Options"]]

   [:form.form-horizontal {:action "/jobs/filters" :method "POST"}
    [:div.modal-body
     [:div.control-group
      [:div.controls
       [:div.btn-group {:data-toggle "buttons-checkbox"}
        (for [kw [:show-fulltime :show-internships]]
          (let [id  (name kw)
                val (kw filters)]
            [:a {:href "#" :id id
                 ;; kw could be nil, in which case, resort to true
                 :class (u/cond-class "btn" [(not= val false) "active"])}
             (u/phrasify kw)]))]]]

     (for [kw [:show-fulltime :show-internships]]
       [:input {:name (name kw) :type "hidden" 
                :value (str (or (nil? filters)
                                (true? (kw filters))))}])

     (input-range :company-size ["input-small"] filters)
     (input-range :start-date   ["datepicker"] filters)
     (input-range :end-date     ["datepicker"] filters)]
    
    [:div.modal-footer
     [:button.btn {:data-dismiss "modal" :aria-hidden "true"} "Close"]
     [:button.btn.btn-primary "Save Changes"]]]])

(def sort-field-choices 
  [:post-date :company :company-size 
   :start-date :end-date
   :longitude :latitude])

;; job filters should be specifiable as query
;; params too. Otherwise you can't, say, send a friend
;; a link of the filtered listing, which is a shame.
(defhtml browse-jobs [q sort-field page-jobs]
  (let [filters    (session/session-get :filters)
        sort-order (or (session/session-get :sort-order) 1)]
    [:div#browse
     ;; sort by date and location.
     ;; search descriptions and company names
     [:h1 "Browse Startup Jobs"]

     (job-filters filters)
     
     [:div#map-box.row-fluid
      [:div#map.thumbnail]
      [:div.navbar
       [:div.navbar-inner
        [:form.navbar-search.pull-left {:method "GET"}
         [:input#job-search.search-query
          {:type "text" :placeholder "Search" :name "q" :value q}]]

        [:div.btn.btn-navbar {:data-toggle "collapse" 
                              :data-target "#job-toggles"}
         (repeat 3 [:span.icon-bar])]

        [:div#job-toggles.nav-collapse.collapse
         [:ul.nav.pull-right
          [:li [:a#filter-toggle {:href "#filter" :data-toggle "modal"} 
                [:i.icopn-filter] "Filter"]]

          [:li.dropdown
           [:a#sort-toggle.dropdown-toggle
            {:href "#" :data-toggle "dropdown"}
            [:i.icon-list] "Sort" [:b.caret]]
           [:ul#sort.dropdown-menu
            {:role "menu" :arial-labelledby "sort-toggle"}

            [:li [:button#sort-order.asc-desc.btn.btn-primary
                  {:type "button" :data-order sort-order}
                  [:span {:class (if (= sort-order 1) "hidden")}
                   "Ascending"]
                  [:span {:class (if (= sort-order 0) "hidden")}
                   "Descending"]]]

            (for [field sort-field-choices]
              [:li {:class (if (= (keyword sort-field) field) "active")}
               [:a {:href "#" :data-field (name field)}
                (u/phrasify field)]])]]

          [:li [:a#map-toggle {:href "#map"}
                [:i.icon-map-marker] "Toggle Map"]]]]]]]

     [:div#job-container.row-fluid
      (if (empty? (:jobs page-jobs))
        [:h1 "Sorry, no jobs posted currently. Come back later."])
      ;; eventually pass entire argument map rather than
      ;; separate args for q, page, etc.
      (job-list page-jobs)]
     
     [:script#job-data
      (str "window.job_data = " 
           (json/generate-string (:jobs page-jobs)) ";")]]))

(defn intify [n fallback]
  (if (vali/valid-number? n)
    (Integer. n)
    fallback))

(defn get-sort-field [sort-field]
  (or
   sort-field
   (session/session-get :sort-field)
   ;; default to post date
   :post-date))

;; returns a hash-map containing a list of jobs for the current page
;; filtered and sorted based on the inputs
(defn jobs-map
  [{:keys [q page page-size sort-field sort-order]
    :or {q          "" 
         page       1 
         page-size  20
         sort-field (get-sort-field nil)
         sort-order 1}}]

  (session/session-put! :sort-field sort-field)
  (session/session-put! :sort-order sort-order)

  (let [page         (intify page 1)
        page-size    (intify page-size 20)
        sort-order   (intify sort-order 1)
        filters      (session/session-get :filters)
        jobs         (job/filtered-jobs q sort-field sort-order filters)
        page-jobs    (jobs-on-page jobs page page-size)
        page-count   (ceil (/ (count jobs) page-size))
        editable?    (user/logged-in?)]

    {:jobs page-jobs 
     :q q
     :page page
     :page-count page-count
     :editable? editable?}))

(defn job-rss [job-map]
  (apply rss/channel-xml
         (cons {:title "StartLabs Jobs List"
                :link (u/home-uri "/jobs")
                :description "A continually updated listing of Startup job and internship offerings geared towards MIT students"
                :language "en-us" :webMaster "ethan@startlabs.org"
                :image (u/home-uri "/img/logo_small.png")}
               (for [job (:jobs job-map)]
                 (let [post-date (tc/to-date 
                                  (or (u/parse-date (:post-date job))
                                      ;; random date
                                      (t/date-time 2013 1 01)))]
                   {:title       (str (:company job) ": " (:position job))

                    :description (md-to-html-string 
                                  (str
                                   "**Location:** " (:location job) ", "
                                   "**Start Date:** " (:start-date job) "\n\n"
                                   (:description job)))

                    :author      (:contact-info job)
                    :category    (:role job)
                    :guid        (:id job)
                    ;; hack, parsing stingified date. Should really
                    ;; delay formatting of values to the view rather
                    ;; than doing it in the model...
                    :pubDate     post-date})))))


(defn job-format-fn [fmt]
  (condp = fmt
    :edn  pr-str
    :json #(json/generate-string % {:pretty true})
    :xml  job-rss))

;; [:get /jobs.(edn|json|xml)?q=...]
;; see jobs-and-list-html for all possible arguments
(defn job-search [fmt params]
  (let [body ((job-format-fn fmt) 
              (jobs-map params))]
    (-> (rr/response body)
        (rr/content-type (str "text/" (name fmt))))))

(defn split-sites [sitelist]
  (str/split sitelist #"\s+"))

(defhtml nav-buttons [active-tab]
  [:div#job-toggle.btn-group.pull-right
   [:a {:class (u/cond-class "btn" [(= active-tab :jobs) "active"]) 
        :href "/jobs"} "Browse Available"]
   (if (user/logged-in?)
     [:a {:class (u/cond-class "btn" [(= active-tab :whitelist) "active"]) 
          :href "/whitelist"} "Whitelist"])
   [:a {:class (u/cond-class "btn" [(= active-tab :new-job) "active"]) 
        :href "/job/new"} "Submit a Job"]])

;; [:get /jobs]
(defn get-jobs [{:keys [q sort-field] :as params}]
  (let [page-jobs  (jobs-map params)
        sort-field (or sort-field
                       (session/session-get :sort-field))]
    (common/layout
     (nav-buttons :jobs)
     [:div (browse-jobs q sort-field page-jobs)])))


(defn get-hostname [url]
  (try
    (let [site-uri    (URI. (or url ""))
          replace-www (fn [x] (str/replace x "www."  ""))
          site-host   (-?> site-uri .getHost replace-www)]
      site-host)
    (catch Exception e
      "")))

(defn abs<=
  "Verify that (abs num) is <= max"
  [num-str max]
  (<= (abs (if (number? num-str)
               num-str
               (Double/parseDouble num-str))) max))
;; (abs<= 60 50) => false
;; (abs<= "10" 50) => true

(defn whitelist-contains? [whitelist site-host]
  (re-find (re-pattern (str "\\b" site-host "\\b")) whitelist))

(defn email-domain [email]
  (second (str/split email #"@")))

(defn valid-job? [job-params]
  (let [site-host       (get-hostname (:website job-params))
        fulltime?       (= "fulltime" (:role job-params))
        required-params (required-job-keys (keyword (:role job-params)))]

    (doseq [param required-params]
      (u/empty-rule param (job-params param)))
    
    (vali/rule 
     (contains? (set (mu/get-enum-vals :job/role)) (keyword (:role job-params)))
     [:role "Invalid role. Please choose one of the listed options."])

    (if (not (empty? (:website job-params)))
      (vali/rule (not (or (nil? site-host) (= "" site-host)))
                 [:website "Must be a valid website." site-host]))

    (if (not (empty? (:company-size job-params)))
      (vali/rule (vali/valid-number? (:company-size job-params))
                 [:company-size "The company size must be a valid number."])

      (vali/rule (vali/greater-than? (:company-size job-params) 0)
                 [:company-size "Surely you have at least one employee.
                  If not, then who's filling out this form?"]))

    (if (not (empty? (:location job-params)))
      (let [err [:location 
                 "The latitude/longitude of the location are invalid."]]
        (try
          (vali/rule (and (abs<= (:latitude job-params) 90)
                          (abs<= (:longitude job-params) 180))
                     err)
          (catch Exception e
            (vali/rule false err)))))

    (doseq [date [:start-date :end-date]]
      (if (contains? required-params date)
        (vali/rule
         (u/parse-date (date job-params))
         [date "Invalid date."])))

    ; make sure the end date comes after the start
    (if (contains? required-params :start-date)
      (vali/rule
       (let [[start end] (map #(u/parse-date (% job-params)) 
                              [:start-date :end-date])]
         (and (and start end) 
              (= -1 (apply compare (map to-long [start end])))))
       [:end-date "The end date must come after the start date."]))

    (not (apply vali/errors? ordered-job-keys))))


(defn fix-job-params [params]
  (let [website     (:website params)
        internship? (= (:role params) "internship")
        start-date  (u/parse-date (:start-date params))
        end-date    (if start-date
                      (t/plus start-date (t/months 6)) 
                      (t/plus (t/now) (t/months 6)))]
    (conj params
      {:website (if (not (empty? website)) 
                  (u/httpify-url website) 
                  "")
       :end-date (if internship?
                   (:end-date params)
                   (u/unparse-date end-date))})))

(def job-error  "Please correct the form and resubmit.")
(defn flash-job-error []
  (u/flash-message! :error job-error))

(defn trim-and-fix-params [params]
  (let [trimmed-params (u/trim-vals params)
        fixed-params   (fix-job-params trimmed-params)]
    fixed-params))

;; [:get /job/new]
(defn get-new-job [& [params]]
  (common/layout
   (nav-buttons :new-job)
   (submit-job params)))

;; [:post /job/new]
(defn post-new-job [params]
  (let [fixed-params (trim-and-fix-params params)]
    (if (valid-job? fixed-params)
      (try
        (let [job-info  (job/create-job fixed-params)
              email-res (send-confirmation-email job-info)]
          (if (= (:error email-res) :SUCCESS)
            (do
              (response/redirect "/job/success"))
            (do
              (u/flash-message!
               :error (str "Trouble sending confirmation email:" 
                           (:message email-res)))
              (render get-new-job fixed-params))))

        (catch Exception e
          (u/flash-message!
           :error (str "Trouble connecting to the database:" e))
          (render get-new-job fixed-params)))

      (do ;invalid job, flash an error message
        (flash-job-error)
        (render get-new-job fixed-params)))))

;; [:get /job/success]
(defn job-success []
  (common/layout
    [:h1 "Submission Received"]
    [:p "Please check your email for a confirmation link."]
    [:p (str "Once you've confirmed the listing, it may take a few business days "
             "until your post is approved by the StartLabs team.")]
    [:p "We'll send you an email as soon as your submission has been approved."]
    [:p "If you have any concerns, feel free to contact us by email at: "
     [:a {:href "mailto:jobs@startlabs.org"} "jobs@startlabs.org"] "."]))



;; individual job editing

(defhtml unexpected-error [& [error]]
  [:div
    [:h1 "Something went wrong."]
    (if error
      [:p "Here's the problem: " error])
    [:p  "Sorry for the inconvenience. Please contact the "
      (common/webmaster-link "webmaster") "."]])

(defhtml job-not-found []
  [:div
    [:h1 "Job not found"]

    [:p "Sorry, we couldn't find any jobs with that ID."]
    [:p "If you think this is a mistake, feel free to contact the "
      (common/webmaster-link "webmaster") "."]])


(defhtml job-edit-email-body [job-map]
  (let
      [the-link (edit-link job-map)]
    [:div
      [:p "Hey there,"]
      [:p "To edit your StartLabs jobs posting, please visit this link:"]
      [:p [:a {:href the-link} the-link]]
      [:p "Don't share it with anyone!"]
      [:p "If this email was sent in error, feel free to ignore it or contact "
          (common/webmaster-link "our webmaster") "."]]))

(defn send-edit-email [job-map]
  (postal/send-message ^{:host (env :email-host)
                         :user (env :email-user)
                         :pass (env :email-pass)
                         :ssl  :yes}
    {:from    "jobs@startlabs.org"
     :to      (:email job-map)
     :subject "Edit your StartLabs Job Listing"
     :body [{:type    "text/html; charset=utf-8"
             :content (job-edit-email-body job-map)}]}))


;; going here triggers an edit link to be sent to the author of the listing
;; [:get "/job/:id/edit"] 
(defn resend-edit-link [id]
  (if (user/logged-in?)
    (common/layout
     (if-let [job-map (job/job-map id)]
       ;; here we find the existing secret or create a new one
       (let [map-secret (:secret job-map)
             secret     (if (empty? map-secret) 
                          (job/update-job-fields id {:job/secret (u/uuid)})
                          map-secret)
             secret-map (assoc job-map :secret secret)]
         (send-edit-email secret-map)
         [:div
          [:h1 "Edit Link Sent"]
          [:p "The author can now check their email for a link to edit the job listing."]])
       (job-not-found)))
    ;;else
    (response/redirect "/jobs")))

(defn can-edit-job? [id & candidates]
  (let [job-secret (job/job-secret id)]
    (or (user/logged-in?)
        (some true? (map #(= % job-secret) candidates)))))

(defn analytics-data [job-id start end]
  (try
    (let [session-secret (session/session-get :job-secret)
          data (if (can-edit-job? job-id session-secret)
                 (analytics/google-chart-map job-id start end)
                 {:error "You do not have permissions to view this job's analytics data."})]
      data)
    (catch Exception e
      {:error (.getMessage e)})))

(defn job-edit-tabs [id active]
  [:div.btn-group.pull-right
   [:a {:href (str "/job/" id) 
        :class (u/cond-class "btn" [(= active :edit) "active"])} "Edit Job"]
   [:a {:href (str "/job/" id "/analytics")
        :class (u/cond-class "btn" [(= active :analytics) "active"])} "Analytics"]])

(defhtml job-analytics-view [id data]
  (let [start-date (:start-date data)
        end-date   (:end-date data)]
    (common/layout
     [:div#analytics
      (job-edit-tabs id :analytics)
      [:h1 "Job Analytics"]

      [:div.row-fluid
       [:form.form-horizontal.span6 
        {:method "GET" :action (str "/job/" id "/analytics.edn")}
        (for [[n v l] [["a-start-date" start-date "Start Date"] 
                       ["a-end-date" end-date "End Date"]]]
          [:div.control-group
           [:label.control-label {:for n} l]
           [:div.controls
            (datepicker n v google-date-fmt)]])]

       [:div.span6
        [:div.row-fluid
         [:div.span6.thumbnail
          [:h1#unique-events.centered (:unique-events data)]
          [:h2.centered "Unique Events"]]
         [:div.span6.thumbnail
          [:h1#total-events.centered (:total-events data)]
          [:h2.centered "Total Events"]]]]]

      [:div.row-fluid
       [:div#analytics-chart.span12]]

      (if (not (:error data))
        [:script#analytics-data {:type "text/edn"}
         (str data)])])))

;; [:get /job/:id/analytics.edn?a-start-date=...&a-end-date=...
(defn analytics-search [id & [start end]]
  (let [session-secret (session/session-get :job-secret)
        data (analytics-data id start end)]
    (-> (rr/response (str data))
        (rr/content-type "text/edn")
        (rr/status (if (:error data) 400 200)))))

;; [:get /job/:id/analytics]
(defn get-job-analytics [id & [start end]]
  (let [data (analytics-data id start end)]
    (if (:error data)
      (do
        (u/flash-message! :error (:error data))
        (response/redirect "/jobs"))

    (job-analytics-view id data))))

;; [:get "/job/:id"]
(defn get-edit-job [{:keys [id secret] :as params}]
  (if secret (session/session-put! :job-secret secret))
  (let [secret (or secret (session/session-get :job-secret))]
    (if (can-edit-job? id secret)
      (common/layout
       ;; params either contains previously submitted (invalid) params that require editing
       ;; or it only contains {:id id :secret secret} if the user just arrived at the edit page.
       [:div#edit
        (job-edit-tabs id :edit)
        (if-let [job-map (if (> (count params) 2)
                           params
                           (job/job-map id))]
          (let [secret-map (assoc job-map :secret (:secret params))]
            (submit-job secret-map))
          ;; else
          (job-not-found))])
      ;else
      (do
        (u/flash-message! :error "You cannot edit this job.")
        (response/redirect "/jobs")))))

(defn flash-error-and-render [error job-id params]
  (u/flash-message! :error error)
  (render get-edit-job params))

;;[:post "/job/:id"]
;; Check for job secret in: session and query params.
;; Also check if user is a team member (logged-in?).
;; If none of these conditions are met, the poster should not be
;; allowed to update the job.
(defn post-edit-job [{:keys [id secret] :as params}]
  (let [session-secret (session/session-get :job-secret)
        params         (if (not (empty? (:secret params)))
                         params
                         (assoc params :secret session-secret))
        fixed-params   (trim-and-fix-params params)]
    (if (can-edit-job? id (:secret fixed-params))
      (if (valid-job? fixed-params)
        (if (job/update-job id params)
          (do
            (u/flash-message! 
             :success "Your job has been updated successfully.")
            (response/redirect "/jobs"))
            ; else
          (flash-error-and-render
           "Unable to update job. Sorry for the inconvenience." 
           id fixed-params))

        ; invalid job, flash an error message, return to edit page
        (flash-error-and-render job-error id fixed-params))

      ; secret does not match job secret
      (flash-error-and-render "Invalid job secret." id fixed-params))))


;; [:post "/job/:id/delete"] 
(defn delete-job [id]
  (if (user/logged-in?)
    (if (job/remove-job id)
      (u/flash-message! :success "The job has been removed."))
    ;; else
    (u/flash-message! :error "You cannot delete that job!"))
  (response/redirect "/jobs"))


;; "/job/:id/confirm" {:keys [id]}
(defn confirm-job [id]
  (common/layout
   (if (job/confirm-job id)
     [:div
      [:h1 "Thanks for Confirming"]
      [:p "Your listing should now be posted."]
      [:p "Check it out "
       [:a {:href (str "/jobs#" id)} "on the jobs page"] "."]]
     ;; else
     (unexpected-error))))



;; [:get /whitelist]
(defn get-whitelist []
  (common/layout
   (nav-buttons :whitelist)
   (let [whitelist (second (job/get-whitelist))]
     [:div#whitelist
      [:h2 "Company Whitelist"]

      [:div.well
       [:p "Just put each company domain name on a new line."]
       [:p "Do not include the http:// or the www."]]

      [:form {:action "/whitelist" :method "post"}
       [:div.span6.pull-left
        [:textarea#the-list.span6 {:name "the-list" :rows 20 
                                   :placeholder "google.com"}
         whitelist]
        [:input.btn.btn-primary.span3 {:type "submit"}]]

       [:div.span5
        [:input.btn.btn-primary.span3 {:type "submit"}]]]])))

;; [:post "/whitelist"]
(defn post-whitelist [the-list]
  (if (user/logged-in?)
    (do
      (job/update-whitelist the-list)
      (u/flash-message!
       :success "The whitelist has been updated successfully.")
      (response/redirect "/whitelist"))
    ;else
    (do
      (u/flash-message! 
       :error "You must be logged in to change the whitelist.")
      (response/redirect "/jobs"))))
