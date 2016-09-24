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

(when (and js/window.process js/window.process.versions js/window.process.versions.electron)
  (.push (.-globalPaths (js/require "module")) (str (js/process.cwd) "/node_modules")))

(def wskey "zdanGC4Wc")
(defn transform [obj]
  (into
   obj
   {:_id
    (str "europeana" (string/replace (get obj "id") (js/RegExp. "/" "g") ":"))
    :_title (-> obj (get "dcTitleLangAware" {}) (first) (second) (first))
    :_creators (distinct (concat (get obj "dcCreator" [])))
    :_description  (get obj "type")
    :_source "Europeana"}))
(when js/window.process
  (def request (js/require "request"))
  (def access_token "a4516e74f16b7b2d3f7f3eb6cac35b2b07575345")
  (defn <http [url]
    (let [c (chan)]
      (request url
               (fn [err res data]
                 (if err (do (log err data) (close! c)) (put! c data))))
      c))

  (defn <europeana [action & args]
    (go
      (log args)
        (let [url (if (= :search action)
                    (log (str "http://www.europeana.eu/api/v2/search.json"
                          "?wskey=" wskey
                          "&start=" (inc (* 12 (second args)))
                          "&query=" (first args)
                          ))
                    (str "http://www.europeana.eu/api/v2/record/"
                         (first args); "2058618/object_KUAS_22340808"
                         ".jsonld?wskey=zdanGC4Wc&query=blicher"))
              text (<! (<http url))
              obj (js->clj (js/JSON.parse text))
              ]
          (log obj)
          obj)))
  (defn <search [q page]
    (go
     (log (map
           transform
           (get (<! (<europeana
                     :search
                     (str
                      "\""
                      (string/join "\" AND \""
                                   (string/split q #" +"))
                      "\"") page))
                "items")))))
  (<search "Søren Kierkegaard" 0)
  )
