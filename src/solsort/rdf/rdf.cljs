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
   "Federated Linked Data Endpoint"])
(when (and js/window.process js/window.process.versions js/window.process.versions.electron)
  (.push (.-globalPaths (js/require "module")) (str (js/process.cwd) "/node_modules")))

(when js/window.process
  
  (defn minpos [a b]
    (bit-and 0x7fffff (.indexOf a b)))

  (def process js/process)
  (def require js/require)
  (def fs (require "fs"))
  (def http (require "http"))

  (let [obj (into
             (js->clj (js/JSON.parse (.readFileSync fs "../schema/ting.jsonld" "utf-8")))
             (js->clj (js/JSON.parse (.readFileSync fs "../schema/solsort.jsonld" "utf-8"))))
        prefixes (into {} (filter #(string? (second %)) obj))
        types (into {} (remove #(string? (second %)) obj))
        ]
    (def prefixes prefixes)
    (def types types))

  (defn html-doc [o]
    (str
     "<!DOCTYPE html>
<html>
<head><link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.2.2/semantic.css\"/><meta charset=\"utf-8\"/></head><body>
<div
xmlns:dc=\"http://purl.org/dc/elements/1.1/\"
"
     (string/join "\n"
                  (map #(str "xmlns:" (first %) "=\"" (second %)"\"") prefixes)
      )
     "
>
"
     "</div></body>"
     (reagent/render-to-static-markup

      [:div.ui.container
       o]
      #_[:html {:prefix (string/join " " (map #(str (name (first %)) ":" (second %)) prefixes))}
       [:head
        [:link {:rel :stylesheet
                :href "https://cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.2.2/semantic.css"}]
        [:meta {:charset "utf-8"}]]
       [:body
        ]])))

  (defn render-property [key val]
    (log types key (get types key))
    (if (get types key)
      [:span {:property (get-in types [key "@id"]) } (str val)]
      (str val)
      )
    )
  (defn render-properties [obj ignore]
    (into [:div ]
          (for [prop (->> obj
                          (remove #((into #{} ignore) (first %)))
                          (sort-by #(str (first %))))]
            [:div
             [:small (str (first prop)) ": "]
             " \u00a0 "
             (let [val (second prop)]
               #_(into [:span]
                     (if (coll? val)
                       (map render-property (map (fn [val] [(first prop) val]) val))
                       ([(render-property (first prop) val)])))
               (into [:span]
                     (if (coll? val)
                       (interpose ", \u00a0 " (map #(render-property (first prop) %) val))
                       [(render-property (first prop) val)])))])))

  (defn render-object [obj]
    [:div 
     {:vocab "http://rdf.solsort.com/"
      :typeof "Thing"
      :item-scope :item-scope
     ; :item-type (str "http://schema.org/" (get obj :_type "Thing"))
      }
     [:h1 {:item-prop "title"} (:_title obj)]
     [:p (into [:em ]
               (interpose " & " (map (fn [s] [:a {:href (str "/search/" s)}
                                              [:span {:item-prop "creator"} s]])
                                     (:_creators obj))))]
     [:p {:item-prop "description"} (:_description obj)]
     [render-properties obj [:_id :_creators :_description :_title "@context"]]])
  (defn show-object [req res]
    (go
      (let [accept (or (aget (aget req "headers") "accept") "")
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
              "html" (html-doc
                      (case kind
                        "ting" (ting/render obj)
                        [render-object obj]))
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
            prev-link (if (pos? page) (str "/search/" query "/" (dec page)) nil)]
        (.end res
              ;(reagent/render-to-static-markup
              (html-doc
               [:div
                [header]
                [:div.ui.grid
                 [:div.ui.form.twelve.wide.column
                  [:input {:id "query"}]]
                 [:div.four.wide.column
                  {"dangerouslySetInnerHTML"
                   {:__html "<button class=\"fluid ui primary button\" onclick=\"location.href='/search/'+query.value;\">Search</button>"}}]]

                [:hr]
                [:p "\"" query "\" results:"]
                (into [:ul]
                      (for [obj results]
                        [:li
                         [:a {:href (str "/object/" (:_id obj))
                              :style {:text-decoration :none}}
                          [:strong (:_title obj)]
                          " \u00a0 "
                          [:em (string/join " & " (:_creators obj))]
                          " \u00a0 "
                          [:small (:_description obj)]]
                         " (" (:_source obj) ")"]))
                [:div
                 (if prev-link
                   [:span
                    (if (< 1 page)
                      [:span  [:a {:href first-link} "First"]
                       " \u00a0 "]
                      "")
                    " << "
                    [:a {:href prev-link} "Previous"]
                    " \u00a0 "]
                   "")
                 (if (empty? results)
                   ""
                   [:span  [:a {:href next-link} "Next"]
                    " >>"])]])))))
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
  [:p
   "This site wraps different APIs as " [:a {:href "https://en.wikipedia.org/wiki/Linked_data"} "Linked Data"] ", giving it the following properties:"]
  [:ul
   [:li [:strong "Unique url/name per object."] " Makes it possible to talk about the object, and be certain that it is the same object we talk about, independent of context. Makes a path to discover more information about the object, when you know the dereferential name/url."]
   [:li [:strong "Federated search."] " Makes it possible to search in several datasets at once,"]
   [:li [:strong "Standard vocabularies, and common approach to access data."] " Makes it possible for robots(web-spiders, knowledge networks, etc.) to understand the data from the APIs, without human hand-holding. Makes it easier to use and build upon as a developer."]]
  [:p "It is developed as " [:a {:href "https://github.com/solsort/rdf"} "open source"] " on top of open APIs. "

   "RasmusErik / "
   [:a {:href "http://solsort.com"} "solsort.com"]
   " started the project, and is hosting it. "
   "The first version was made during " [:a {:href "http://hack4.dk"} "Hack4DK 2016"] ", which is the yearly danish cultural heritage hackathon."]
  [:div "It is easy to add more datasets/APIs. Currently the following datasets are exposed as linked data:"
   [:ul
    [:li [:a {:href "https://opendata.dbc.dk"} "Danish Libraries"] " - make library objects more accessible for search engines, and create a single derefentiable url/name per object."]
    [:li [:a {:href "http://europeana.eu"} "Europeana"] " - which aggregates data from many cultural institutions around europe."]
    [:li [:a {:href "http://natmus.dk"} "National Museum of Denmark"] " - this is the first time the full collection is on the web, in a form that is discoverable by search engines and similar."]]]
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




