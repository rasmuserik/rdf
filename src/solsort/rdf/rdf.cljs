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

(def process js/process)
(def require js/require)
(def http (require "http"))

(defn handler [req res]
  (js/console.log req)
  (.setHeader res "type" "text/html")
  (.end res (reagent/render-to-static-markup [:h1 "Hello from cljs - docker"])))

(defonce server
  (.listen (.createServer http
                  #(handler %1 %2)) 8888 #(js/console.log "listening")))

(js/console.log process)
