(ns solsort.rdf.rdf
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [cljs.reader]
   [solsort.rdf.natmus :as natmus]
   [solsort.rdf.ting :as ting]
   [solsort.rdf.europeana :as europeana]
   [solsort.toolbox.setup]
   [solsort.toolbox.appdb :refer [db db! db-async!]]
   [solsort.toolbox.ui :refer [input select]]
   [solsort.util
    :refer
    [<ajax <seq<! js-seq load-style! put!close!
     parse-json-or-nil log page-ready render dom->clj]]
   [reagent.core :as reagent :refer []]
   [clojure.string :as string :refer [replace split blank?]]
   [cljs.core.async :refer [>! <! chan put! take! timeout close! pipe]]))

; nb: https://github.com/rasmuserik/solsort-util/blob/master/src/solsort/apps/hack4dk.cljs
(defn header []
  [:h1
   [:img {:src "/assets/icon.png" :style {:margin-top "1ex"
                                          :height "1.5em"
                                          :margin-right "1ex"}}]
   "Federated Linked Data Endpoint"]
  )
(when (and js/window.process js/window.process.versions js/window.process.versions.electron)
  (.push (.-globalPaths (js/require "module")) (str (js/process.cwd) "/node_modules")))

(when js/window.process
  (defn minpos [a b]
    (bit-and 0x7fffff (.indexOf a b)))

  (def process js/process)
  (def require js/require)
  (def http (require "http"))

  (defn html-doc [o]
    (reagent/render-to-static-markup
     [:html
      [:head
       [:link {:rel :stylesheet
               :href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.2.2/semantic.css"}]
      [:meta {:charset "utf-8"}]]
      [:body
       [:div.ui.container
        o]]]))

  (defn render-properties [obj ignore]
    (into [:div]
          (for [prop (->> obj
                      (remove #((into #{} ignore) (first %)))
                      (sort-by #(str (first %))))]
            [:div
             [:small (str (first prop)) ": "]
             " \u00a0 "
             (let [val (second prop)]
               (if (coll? val)
                 (string/join ", \u00a0 " val)
                 (str val))
               )
             ])))

  (defn render-object [obj]
    [:div
     [:h1 (:_title obj)]
     [:p [:em (string/join " & " (:_creators obj))]]
     [:p (:_description obj)]
     [render-properties obj [:_id :_creators :_description :_title "@context"]]
     ]

    )
  (defn show-object [req res]
    (go
      (let [accept (aget (aget req "headers") "accept")
            type 
            (or (aget (.-params req) "type")
                (second
                 (first
                  (sort
                    [[(minpos accept "text/html")
                      "html"]
                     [(min
                       (minpos accept "application/rdf+xml")
                       (minpos accept "application/cml"))
                      "xml"]
                     [(min
                       (minpos accept "application/json")
                       (minpos accept "application/ld+json"))
                      "json"]]))))
            id (.-id (.-params req))
            kind (first (clojure.string/split id #":"))
            obj (case kind
                  "natmus" (<! (natmus/<obj id))
                  "europeana" (<! (europeana/<obj id))
                  "ting" (<! (ting/<obj id))
                  {:_id id})
            result
            (case type
              "json" (js/JSON.stringify (clj->js obj) nil 2)
              "html" (html-doc [render-object obj])
              "not implemented")]
        (.end res result))))

  (defn search [req res]
    (go
      (let [query (.-query (.-params req))
            page (js/parseInt (or (aget (.-params req) "page") 0) 10)
            natmus (<! (natmus/<search query page))
            ting (<! (ting/<search query page))
            europeana (<! (europeana/<search query page))
            results (sort-by hash (concat natmus ting europeana))
            first-link (str "/search/" query)
            next-link (str "/search/" query "/" (inc page))
            prev-link (if (pos? page) (str "/search/" query "/" (dec page)) nil)
            ]
        (.end res
              ;(reagent/render-to-static-markup
              (html-doc
                [:div
                 [header]
                 [:div.ui.grid
                  [:div.ui.form.twelve.wide.column
                   [:input {:id "query"}]
                   ]
                  [:div.four.wide.column
                   {"dangerouslySetInnerHTML"
                    {:__html"<button class=\"fluid ui primary button\" onclick=\"location.href='/search/'+query.value;\">Search</button>"}}]
                  ]

                 [:hr]
                 [:p "\"" query "\" results:"]
                 (into [:ul]
                       (for [obj results]
                         [:li
                          [:a {:href (str "/object/" (:_id obj))
                               :style {:text-decoration :none}}
                           [:strong (:_title obj)]
                           " \u00a0 "
                           [:em (string/join " & "(:_creators obj))]
                           " \u00a0 "
                           [:small (:_description obj)]]
                          " (" (:_source obj) ")"]
                         ))
                 [:div
                  (if prev-link
                    [:span
                     (if (< 1 page )
                       [:span  [:a {:href first-link} "First"]
                        " \u00a0 "
                        ]
                       "")
                     " << "
                     [:a {:href prev-link} "Previous"]
                     " \u00a0 "
                     ]
                    "")
                  (if (empty? results)
                    ""
                    [:span  [:a {:href next-link} "Next"]
                     " >>"])]
                 ])))))
  (defonce server
    (let [express (require "express")
          app (express)]
      (.get app "/object/:id.:type" #(show-object %1 %2))
      (.get app "/object/:id" #(show-object %1 %2))
      (.get app "/search/:query/:page*?" #(search %1 %2))
      (.use app (.static express "../"))
      (.listen app 8888 #(js/console.log "started express on port 8888")))))

(render
 [:div.ui.container
  [header]
  [:div
   "Sample searches:"
   (into
    [:ul]
    (map
     (fn [s] [:li [:a {:href s} s]])
     ["search/Nefertiti"
      "search/Blicher"
      "search/sværd"
      "search/Pink Floyd"
      "search/ost"
      "search/Frogner"
      "search/Søren Kierkegaard"
      "search/Tollund"
      "search/Eckersberg"
      "search/værktøj"
      "search/Peder Wessel"
      "search/Astrid Lindgren"
      "search/Solvognen"
      "search/Edvard Munch"
      "search/Ærø"]))]])
