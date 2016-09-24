(ns solsort.rdf.rdf
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [cljs.reader]
   [solsort.rdf.natmus :as natmus]
   [solsort.rdf.ting :as ting]
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
             [:small (first prop) ": "]
             " \u00a0 "
             (let [val (second prop)]
               (if (coll? val)
                 (string/join ", \u00a0 " val)
                 (str val))
               )
             ])))

  (defn render-object [obj]
    (log obj)
    [:div
     [:h1 (:_title obj)]
     [:p [:em (string/join " & " (:_creators obj))]]
     [:p (:_description obj)]
     [render-properties obj [:_id :_creators :_description :_title "@context"]]
     ]

    )
  (defn show-object [req res]
    (go
      (js/console.log req)
      (let [accept (aget (aget req "headers") "accept")
            type 
            (or (aget (log (.-params req)) "type")
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
            kind (log (first (clojure.string/split id #":")))
            obj (case kind
                  "natmus" (<! (natmus/<obj id))
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
            natmus (log (<! (natmus/<search-ids query)))
            ting (log (<! (ting/<search-ids query)))
            results (sort-by hash (concat natmus ting))]
        (.end res
              (reagent/render-to-static-markup
               [:html
                [:head
                 [:meta {:charset "utf-8"}]]
                [:body
                 [:p "\"" query "\" search results:"]
                 (into [:ul]
                       (for [id results]
                         [:li [:a {:href (str "/object/" id)}
                               id]]))]])))))
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
  [:h1
   [:img {:src "assets/icon.png" :style {:height "1em" :margin-right "1ex"}}]
   "Linked Data Endpoint"]
  [:p
   "Made during " [:a {:href "http://hack4.dk"} "Hack4DK"]]
  [:div
   "Sample pages:"
   (into
    [:ul]
    (map
     (fn [s] [:li [:a {:href s} s]])
     ["search/nefertiti"
      "search/blicher"
      "search/ærø"]))]])
