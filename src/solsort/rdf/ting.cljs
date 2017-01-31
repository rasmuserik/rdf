(ns solsort.rdf.ting
  (:require-macros
   [cljs.core.async.macros :refer [go go-loop alt!]]
   [reagent.ratom :as ratom :refer  [reaction]])
  (:require
   [cljs.reader]
   [solsort.mobibl.work :refer [get-work-fn ahref-fn work-view]]
   [solsort.toolbox.setup]
   [solsort.toolbox.appdb :refer [db db! db-async!]]
   [solsort.toolbox.ui :refer [input select]]
   [solsort.util
    :refer
    [<ajax <seq<! js-seq load-style! put!close! <p
     parse-json-or-nil log page-ready dom->clj]]
   [reagent.core :as reagent :refer []]
   [clojure.string :as string :refer [replace split blank?]]
   [cljs.core.async :refer [>! <! chan put! take! timeout close! pipe]]))

(reset!
 ahref-fn
 (fn [route obj]
   (assoc (or obj {}) "href"
          (if (= "search" (:page route))
            (str "/search/"
                 (.trim (string/join
                         " "
                         (concat (if (:q route) [(:q route)] [])
                                 (map :term
                                      (get route :facets []))))))
            (str "http://mobibl.solsort.com/?" (JSON.stringify (clj->js route)))))))
(defn render [obj]
  (reset! get-work-fn
          (fn [id]
            obj))
  [work-view (get obj "pid")])
(defn transform [obj]
  (into obj
        {"@context"
         ["http://rdf.solsort.com/schema/solsort.jsonld"
          "http://rdf.solsort.com/schema/ting.jsonld"]
         "_id" (str "ting:" (first (get obj "pid")))
         "solsortSource" "Biblioteket"
         "solsortTitle" (or (first (get obj "dcTitle" []))
                     (first (get obj "title" [])))
         "solsortDescription"
         (or (first (get obj "abstract" []))
             (first (get obj "description" [])))
         "solsortCreators"
         (distinct (concat
                    (get obj "dcCreator" [])
                    (get obj "creator" [])
                    (get obj "contributor" [])))}))
(def access_token "3fb138a55c0a3faf297cbf66930eb73dc039c236")
(defn <http [url] (<ajax url :result :text))

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
      (get result "data"))))
(defn <obj [id]
  (go
    (let [pid (.slice id 5)
          [[obj] [collection] [cover] recommend]
          (<! (<seq<!
               [(<ting :work {:pids [pid]})
                (<ting :work {:pids [pid] :fields ["collection"]})
                (<ting :work {:pids [pid] :fields ["coverDataUrlThumbnail" ]})
                (<ting :recommend {:like [pid]
                                   :limit 20})]))
          obj (into obj {"tingRelated" (map #(get % "pid") recommend)
                         "collection" (get collection "collection")
                         "coverDataUrlThumbnail" (get cover "coverDataUrlThumbnail")
                         })
          obj (transform obj)]
      obj)))
(defn <search [q page]
  (go (map transform
           (<! (<ting
                :search
                {:q
                 (str
                  "\""
                  (string/join "\" AND \"" (string/split q #" +"))
                  "\"")
                 :offset (* 10 page)})))))
