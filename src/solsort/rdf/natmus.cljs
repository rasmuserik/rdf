(ns solsort.rdf.natmus
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

(defn transform [obj]
  (let [obj (into obj
                  {"@context" ["http://rdf.solsort.com/schema/solsort.jsonld"
                               "http://rdf.solsort.com/schema/natmus.jsonld"]
                   :_id (str "natmus:" (:collection obj) ":" (:sourceId obj))
                   :_title (or (:workDescription obj))})]
    obj))

(defn <search [q limit page]
  (go
    ;(js/console.log "natmus-search" q)
    (->>
     (clojure.walk/keywordize-keys
      (<! (<ajax (str "http://samlinger.natmus.dk/api/all/_search"
                      "?q=" q
                      "&from=" (* limit page)
                      "&size=" limit)
                 :credentials false)))
     (:hits) (:hits) (map #(get % :_source {})) (map transform))))

(defn <obj [id]
  (go
    (let [[_ col src] (clojure.string/split id #":")
          [obj] (<! (<search (str "collection:" col " AND " "sourceId:" src) 1 0))]
      obj)))

(defn <search-ids [q]
  (go (map :_id (log (<! (<search (str
                                   ""
                                   (clojure.string/join
                                    " OR "
                                    (map #(str "_all:" %)
                                         (clojure.string/split q #" +")))
                                   "")
                                  10 0))))))
#_(go (log (<! (<search "hest" 5 0)))
      (log (<! (<obj "natmus:DO:59102"))))

