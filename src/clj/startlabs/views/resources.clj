(ns startlabs.views.resources
  (:require [startlabs.views.common :as common])
  (:use [hiccup.def :only [defhtml]]))

(defhtml ablank [site url]
  [:a {:href (str "http://" url)} url])

(defhtml ablank-tr [site url]
  [:tr [:td site] [:td (ablank site url)]])

(defhtml ablank-li [url site]
  [:li [:a {:href url} site]])

(defhtml site-twitter [person site twitter]
  [:tr [:td person] 
       [:td (if site    [:a {:href site} "Site"])]
       [:td (if twitter [:a {:href (str "https://twitter.com/#!/" twitter)} (str "@" twitter)])]])

;; :get "/resources"
;; put this in a separate file, jesus. resources.clj
(defn resources []
  (common/layout
   [:h1.affix "Resources"]
   [:ul#left-nav.nav.nav-tabs.nav-stacked.span3 {:data-spy "affix"}
    [:li [:a {:href "#design-marketing"} "Design and Marketing"]]
    [:li [:a {:href "#people-blogs"} "People and Blogs"]]
    [:li [:a {:href "#university-resources"} "University Resources"]]
    [:li [:a {:href "#legal"} "Legal"]]
    [:li [:a {:href "#financing"} "Financing"]]
    [:li [:a {:href "#incubators"} "Incubators, Spaces, & Events"]]]

   [:div.offset3.span8
    [:div#design-marketing
     [:h1 "Design & Marketing"]
     [:p "Design and marketing are two very valuable aspects of a founding venture.
          A company must have a product with great design and a plan to market that
          product effectively. Marketing relies on having a well designed product to sell
          and Design relies on having an abled marketing plan to sell it."]
     [:table.table
      [:tbody
       (map #(apply ablank-tr %)
            [["Build a Twitter presence" "www.oneforty.com"]
             ["Fast Company Design" "www.fastcodesign.com"]
             ["Core 77" "www.core77.com"]
             ["From Up North" "www.fromupnorth.com"]
             ["Make a quick site" "www.flavors.me"]
             ["Sell things online" "www.shopify.com"]
             ["Crowdsource funding your idea" "www.kickstarter.com"]])]]]

    [:div#people-blogs
     [:h1 "People & Blogs"]
     [:p "Experience is key. Read about the advice and experiences "
      "that many knowledgeable and successful entrepreneurs have to offer."]

     [:table.table
      [:tbody
       (map #(apply site-twitter %)
            [
             ["Brad Feld" "http://www.feld.com/wp/" "bfeld"]
             ["Paul Graham" "http://www.paulgraham.com" "paulg"]
             ["Fred Wilson" "http://www.avc.com"  "fredwilson"]
             ["Scott Kirsner" "http://www.boston.com/business/technology/innoeco/" "scottkirsner"]
             ["Bob Metcalfe" nil "BobMetcalfe"]
             ["Dharmesh Shah" "http://www.onstartups.com" "dharmesh"]
             ["Katie Ray" "http://thehumansideofstartups.wordpress.com/" "ktrae"]
             ["David Cohen" "http://www.davidgcohen.com" "davidcohen"]
             ["Bill Warner" "http://www.billwarner.posterous.com" "billwarner"]
             ["Karl Buttner" nil "carlbuttner"]
             ["David Skok" "http://www.forentrepreneurs.com" "bostonvc"]
             ["Paul English" "http://www.paulenglish.com" "englishpaulM"]
             ["Bilal Zuberi" "http://bznotes.wordpress.com" "bznotes"]
             ["Ric Fulop" "http://ricfulop.tumblr.com" "ricfulop"]])]]]

    [:div#university-resources.clearfix
     [:h1 "University Resources"]
     [:p "A number of universities are beginning to grasp the entrepreneurial fervor. "
      "With a rise in entrepreneurship on these campuses, many of these universities "
      "are growing a number of resources for their students to help, guide, and advise them."]

     [:div.pull-left.span4
      [:h2 "MIT"]
      [:ul
       (map #(apply ablank-li %)
            [["http://entrepreneurship.mit.edu" "E-Center"]
             ["http://web.mit.edu/e-club/" "E-Club"]
             ["http://www.mit100k.org/" "MIT 100K"]
             ["http://cep.mit.edu/" "MIT Clean Energy Prize"]
             ["http://web.mit.edu/mitpsc/whatwedo/ideas-competition/" "IDEAS Competition"]
             ["http://web.mit.edu/invent/a-main.html" "Lemelson-MIT Competition"]
             ["http://web.mit.edu/deshpandecenter/" "MIT Deshpande Center"]
             ["http://web.mit.edu/vms/" "Venture Mentoring Service"]
             ["http://web.mit.edu/tlo/www/" "Technology Licensing Office"]
             ["http://legatum.mit.edu/" "Legatum Center"]])]]

     [:div.pull-left.span3
      [:h2 "Stanford"]
      [:ul
       (map #(apply ablank-li %)
            [["http://e145.stanford.edu/" "E145:Technology Entrepreneurship"]
             ["http://ecorner.stanford.edu/" "E-Corner"]
             ["http://www.gsbeclub.org/" "GSB Entrepreneur Club"]
             ["http://sselabs.stanford.edu/" "StartX / SSE Labs"]
             ["http://svihackspace.com/" "SVI Hackspace"]])]]]

    [:div#legal
     [:h1 "Legal"]
     [:p "Legally incorporating is quite simple once you get into it "
      "– Goodwin Procter, a national law firm, has a tool called "
      [:a {:href "http://www.goodwinfoundersworkbench.com/"} "Founders Workbench"]
      " that should be able to answer most of your legal questions."]

     [:div.clearfix  
      [:div.pull-left.span4
       [:h2 "Blogs"]
       [:ul (map #(apply ablank-li %)
                 [["http://www.startupcompanylawyer.com/" "Startup Company Lawyer"]
                  ["http://startuplawyer.com/" "The Startup Lawyer"]
                  ["http://www.globalstartupblog.com/" "Global Startup Blog"]
                  ["http://www.venturelaw.blogspot.com/" "Venture Law Lines"]
                  ["http://freelandbenevich.blogspot.com/" "Freeland Benevich"]
                  ["http://www.jasonmendelson.com/wp/" "Jason Mendelson"]
                  ["http://www.startuplawblog.com/" "Startup Law Blog"]
                  ["http://www.iplawforstartups.com/" "IP Law for Startups"]])]

       [:h2 "Law Firms and Lawyers"]
       [:ul (map #(apply ablank-li %)
                 [["http://www.goodwinprocter.com/" "Goodwin Procter"]
                  ["http://www.siliconlegal.com/" "Silicon Legal"]
                  ["http://www.grellas.com/" "Grellas Shah, LLP"]
                  ["http://walkercorporatelaw.com/" "Walker Corporate Law Group"]])]]

      [:div.pull-left.span3
       [:h2 "Legal Tools"]
       [:ul (map #(apply ablank-li %)
                 [["http://www.legalriver.com/" "Legal River"]
                  ["http://www.nvca.org/index.php?option=com_content&view=article&id=108&Itemid=136" 
                   "Model Legal Documents"]
                  ["http://www.legalzoom.com/" "Legal Zoom"]
                  ["http://www.wsgr.com/wsgr/Display.aspx?SectionName=practice/termsheet.htm" 
                   "Term Sheet Generator"]])]

       [:h2 "Tips from VCs"]
       [:ul (map #(apply ablank-li %)
                 [["http://blog.guykawasaki.com/2007/09/the-top-ten-six.html#axzz0VM3lKUJm" 
                   "Guy Kawasaki on \"The Top Ten (Sixteen) Lies of Lawyers\""]
                  ["http://www.bothsidesofthetable.com/2010/01/21/how-to-work-with-lawyers-at-a-startup/"
                   "Mark Suster: \"How to Work with Lawyers at a Startup\""]])]]

      ]]

    [:div#financing
     [:h1 "Financing"]
     [:p "At some point your company is going to need money. Yes, it is.
                 One way or another. This can be a pretty touchy topic that a lot of
                 people have their own ideas on, but to start – take 10 minutes and
                 read this summary of "
      [:a {:href "http://www.paulgraham.com/fundraising.html"}
       "Fundraising"] " by Paul Graham."]
     [:p "There are a few common ways of financing
                 (in order of the amount of money they will invest):"]
     [:ul
      [:li "Friends and Family"]
      [:li "Angel Investors - such as "
       [:a {:href "http://blog.jonpierce.com/post/520863618/bostons-best-angel-investors" 
            } "Boston's Best Angel Investors"]]
      [:li "VCs"]]

     [:p "The details of term sheets (the things that you use to issue stock and such)
                 are explained quite well by Brad Feld's "
      [:a {:href "http://www.feld.com/wp/archives/category/term-sheet"}
       "article"]
      ". You should also have a good idea of what your financials of your company should
                look like – something else that Brad Feld addresses well in another "
      [:a {:href "http://www.feld.com/wp/archives/2011/07/financial-literacy.html"}
       "article"]"."]]

    [:div#incubators
     [:h1 "Incubators, Spaces, & Events"]
     [:p [:strong "Greenhorn Connect"] 
      " is one of the best resources in finding out the when, where, and who of what's
              going on in Boston. They have great blogs, calendars, resources, and job opportunities."
      [:a {:href "http://www.greenhornconnect.com/"} "Check them out."]]

     [:table#incubator-table.table
      [:thead
       [:tr [:td "Incubator"] [:td "Description"] [:td "Funding/Costs"]]]

      [:tbody
       [:tr
        [:td [:a {:href "http://ycombinator.com/apply.html"} "Y-Combinator"]]
        [:td [:p
              "YC is one of the most popular incubators in the U.S.
                      It is run by Paul Graham since 2005.  It is located in Mountain View, CA
                      and focuses mainly on web startups."]
         [:p "Summer and Winter (3 months)"]]
        [:td "$18K for 7%"]]

       [:tr
        [:td [:a {:href "http://www.techstars.org/apply/"} "Techstars"]]
        [:td [:p "Probably the 2nd most popular incubator in the U.S.
                            It is run by David Cohen and has input from Brad Feld, 
                            with locations all over the country."]
         [:p "Boston – Spring, Boulder – Summer,
                             NYC- Summer/Winter, Seattle – Fall"]]
        [:td "$18K for 6%"]]

       [:tr
        [:td [:a {:href "http://mit100k.org/"} "MIT 100K"]]
        [:td [:p
              "One of the oldest entrepreneurial competitons around.
                    It is run by MIT and thus one member of your team must be from MIT, 
                    but they have all sorts of competitions ranging from pitches to business plans.
                    They also have an option for non-MIT students called YouPitch."]
         [:p "Runs all year long, final show takes place in May"]]
        [:td "Prizes up to $100K (they take 0 equity)"]]

       [:tr 
        [:td [:a {:href "http://dogpatchlabs.com"} "DogPatch Labs"]]
        [:td [:p "A working space run by Polaris Ventures that offers free working "
              "spaces for budding entrepreneurs.  They have locations in Cambridge, NYC, "
              "and San Francisco."]]
        [:td "Free"]]

       [:tr
        [:td [:a {:href "http://cictr.com/"} "Cambridge Innovation Center (CIC)"]]
        [:td [:p "The CIC is a co-working space for startups.
                            It is located right in Kendall square and houses nearly 400 companies.
                            The CIC and it’s venture café are the location for many entrepreneurial
                            events around the Boston area."]]
        [:td "$500/person/month"]]

       ]
      ]
     ]
    ]))
