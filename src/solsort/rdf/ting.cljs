(ns solsort.rdf.ting
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
    [<ajax <seq<! js-seq load-style! put!close! <p
     parse-json-or-nil log page-ready render dom->clj]]
   [reagent.core :as reagent :refer []]
   [clojure.string :as string :refer [replace split blank?]]
   [cljs.core.async :refer [>! <! chan put! take! timeout close! pipe]]))

(when (and js/window.process js/window.process.versions js/window.process.versions.electron)
  (.push (.-globalPaths (js/require "module")) (str (js/process.cwd) "/node_modules")))

(defn transform [obj]
  (into obj
        {"@context"
         ["http://rdf.solsort.com/schema/solsort.jsonld"
          "http://rdf.solsort.com/schema/ting.jsonld"]
         :_id (str "ting:" (get obj "pid"))
         :_title (or (first (get obj "dcTitle" []))
                     (first (get obj "title" [])))
         :_description
         (or (first (get obj "abstract" []))
             (first (get obj "description" [])))
         :_creators
         (distinct (concat
                    (get obj "dcCreator" [])
                    (get obj "creator" [])
                    (get obj "contributor" [])))})
  )
(when js/window.process
  (def request (js/require "request"))
  (def access_token "a4516e74f16b7b2d3f7f3eb6cac35b2b07575345")
  (defn <http [url]
    (let [c (chan)]
      (request url
               (fn [err res data]
                 (if err (do (log err data) (close! c)) (put! c data))))
      c))

  (defn <ting [endpoint o]
    (go
      (let [result
            (js->clj
             (js/JSON.parse
              (<!
               (<http
                (str "https://openplatform.dbc.dk/v1/" (name endpoint)
                     "?"
                     (clojure.string/join
                      "&"
                      (map #(str
                             (js/encodeURIComponent
                              (name (first %)))
                             "="
                             (js/encodeURIComponent
                              (second %)))

                           (into o {:access_token access_token}))))))))]
        (get result "data")))))

(defn <obj [id]
  (go
    (let [pid (.slice id 5)
          [[obj] [collection] recommend]
          (<! (<seq<!
               [(<ting :work {:pids [pid]})
                (<ting :work {:pids [pid] :fields "collection"})
                (<ting :recommend {:like [pid]
                                   :limit 20})]))
          obj (into obj {"tingRelated" (map #(get % "pid") recommend)
                         "collection" (get collection "collection")})
          obj (transform obj)]
      obj)))

(defn <search-ids [q]
  (go (map #(str "ting:" (first (get % "pid")))
           (<! (<ting
                :search
                {:q
                 (str
                  "\""
                  (string/join "\" AND \"" (string/split q #" +"))
                  "\"")
                 :fields ["pid"]})))))
