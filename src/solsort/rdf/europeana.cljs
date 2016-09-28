(ns solsort.rdf.europeana
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

(def wskey "zdanGC4Wc")
(defn uncoll [o]
  (if (coll? o)
    (first o)
    o))
(defn transform [obj]
  (into
   obj
   {:_id
    (str "europeana" (string/replace (get obj "id" "") (js/RegExp. "/" "g") ":"))
    :_title (or (uncoll (get obj "dcTitle" [])) (-> obj (get "dcTitleLangAware" {}) (first) (second) (first)))
    :_creators (distinct (concat (get obj "dcCreator" [])))
    :_description  (or (uncoll (get obj "dcDescription")) (uncoll (get obj "type")))
    :_source "Europeana"}))
  (def access_token "a4516e74f16b7b2d3f7f3eb6cac35b2b07575345")
  (defn <http [url] (<ajax url :result :text))


  (defn <europeana [action & args]
    (go
      (let [url (if (= :search action)
                  (str "http://www.europeana.eu/api/v2/search.json"
                       "?wskey=" wskey
                       "&start=" (inc (* 12 (second args)))
                       "&query=" (first args))

                  (str "http://www.europeana.eu/portal/en/record/"
                       (first args); "2058618/object_KUAS_22340808.json"
                                        ;"?wskey=zdanGC4Wc&query=blicher"
))
            text (<! (<http url))
            obj (js->clj (js/JSON.parse text))]
        obj)))
  (defn mkvec [o]
    (if (vector? o)
      o
      [o]))
  (defn obj-flatten [o]
    (if (map? o)
      (apply concat (map (fn [[k v]] (obj-flatten v)) o))
      (if (coll? o)
        (apply concat (map obj-flatten o))
        [o])))
  (defn merge-objs [& objs]
    (->
     (->> objs
          (apply concat)
          (map (fn [[k v]] [k (mkvec v)]))
          (group-by first)
          (map (fn [[k v]] [k (map second v)]))
          (map (fn [[k v]] [k (distinct (apply obj-flatten v))]))
          (into {}))
     (dissoc "aggregations")
     (dissoc "agents")
     (dissoc "concepts")
     (dissoc "europeanaAggregation")
     (transform)))

  (defn <obj [id]
    (go
      (let [[_ collection id] (string/split id #":")
            obj (get-in (<! (<europeana
                             :record (str collection "/" id ".json")))
                        ["response" "document"])
            obj (apply merge-objs (dissoc obj "proxies")
                       (mkvec (get obj "proxies" [])))]
        (assoc obj :_id id))))
  (defn <search [q page]
    (go
      (map
       transform
       (get (<! (<europeana
                 :search
                 (str
                  "\""
                  (string/join "\" AND \""
                               (string/split q #" +"))
                  "\"") page))
            "items"))))
