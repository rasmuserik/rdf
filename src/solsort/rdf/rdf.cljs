(ns solsort.rdf.rdf
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [cljs.reader]
   [solsort.rdf.natmus :as natmus]
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

(when (and js/window.process js/window.process.versions js/window.process.versions.electron)
  (.push (.-globalPaths (js/require "module")) (str (js/process.cwd) "/node_modules")))

(def process js/process)
(def require js/require)
(def http (require "http"))

(defn show-object [req res]
  (go
    (let [id (.-id (.-params req))
         kind (first (clojure.string/split id #":"))
         obj (case kind
               "natmus" (<! (natmus/<obj id))
               {:_id id})]
     (.end res (prn-str obj)))))

(defn search [req res]
  (go
    (let [query (.-query (.-params req))
          natmus (<! (natmus/<search-ids query))
          results (shuffle (concat natmus))]
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
                       id]])
                    )]])))))
(defonce server
  (let [express (require "express")
        app (express)]
    (.get app "/object/:id.:type" #(show-object %1 %2))
    (.get app "/object/:id" #(show-object %1 %2))
    (.get app "/search/:query/:page*?" #(search %1 %2))
    (.use app (.static express "../"))
    (.listen app 8888 #(js/console.log "started express on port 8888"))))

(render
 [:div.ui.container
  [:h1
   [:img {:src "assets/icon.png" :style {:height "1em" :margin-right "1ex"}}]
   "Linked Data Endpoint" ]
  [:p
   "Made during " [:a {:href "http://hack4.dk"} "Hack4DK"]]
  [:div
   "Sample pages:"
   (into
    [:ul]
    (map
     (fn [s] [:li [:a {:href s} s]])
     ["search/hest"
      "object/natmus:DMR:1850"
      "object/natmus:DMR:1850.json"
      "object/natmus:DMR:1850.html"
      "object/natmus:DMR:1850.rdf"
      ]))
   ]
  ])
