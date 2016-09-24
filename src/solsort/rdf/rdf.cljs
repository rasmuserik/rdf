(ns solsort.rdf.rdf
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [cljs.reader]
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

(defn handler [req res]
  (.setHeader res "type" "text/html")
  (.end res (reagent/render-to-static-markup [:h1 "Hello from cljs - docker"])))

(defn natmus-id [obj]
  (str "natmus:" (get obj "collection") ":" (get obj "sourceId")))
  (defn obj-ref "return full url for id"
    [id]
    (str
    ; "http://rdf.solsort.com"
         "/object/" id)
    )
(defn natmus-title [obj]
  (get obj "workDescription"))
(defn <natmus-search [q limit from]
  (go
    (->
     (<! (<ajax (str "http://samlinger.natmus.dk/api/all/_search"
                      "?q=" q
                      "&from=" from
                      "&size=" limit)
                :credentials false
                :type :json))
     (get "hits")
     (get "hits")
     (->> (map #(get % "_source"))))))
(defn show-object [req res]
  ; collection:DO AND sourceId:1234
  (.end res (str "show-object" (prn-str (.-params req)))))
(defn natmus-search [req res]
  (go
    (.end res (reagent/render-to-static-markup
               [:html
                [:head
                 [:meta {:charset "utf-8"}]]
                [:body(into [:ul]
                 (map (fn [obj]
                        [:li [:a {:href (obj-ref (natmus-id obj))}
                              (natmus-title obj)]])
                      (<! (<natmus-search
                           (.-query (.-params req))
                           100 0))))]]))))
(defonce server
  (let [express (require "express")
        app (express)]
    (.get app "/object/:id.:type" #(show-object %1 %2))
    (.get app "/search/natmus/:query/:page*?" #(natmus-search %1 %2))
    (.use app (.static express "../"))
    (.listen app 8888 #(js/console.log "started express on port 8888"))
    #_(.listen
     (.createServer
      http
      #(handler %1 %2)) 8888 #(js/console.log "listening"))))
