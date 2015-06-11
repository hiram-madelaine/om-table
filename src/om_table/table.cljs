(ns om-table.table
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [schema.core :as s :include-macros true]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [>! put! <! chan timeout]]
            [clojure.string :as str]
            [clojure.set :refer [index]]))



;__________________________________________________________
;                                                          |
;             Display Util                                 |
;__________________________________________________________|


(defn display [show]
  (if show
    "active"
    "inactive"))

(defn hide-nil [cursor]
  (display (not (nil? cursor))))



;_________________________________________________
;                                                 |
;          Events Utils                           |
;_________________________________________________|


(defn e-value
  "Get value from an event"
  [e]
  (-> e .-target .-value))

;_________________________________________________
;                                                 |
;          Component Utils                        |
;_________________________________________________|

(defn styles
  [& args]
  (str/join " " args))


;__________________________________________________________
;                                                          |
;             Schemas                                      |
;__________________________________________________________|


(def sch-field {:field                          s/Keyword
                (s/optional-key :filter-select) s/Bool
                (s/optional-key :label)         s/Str
                (s/optional-key :render-fn)     s/Any
                (s/optional-key :classFn)       s/Any})

(def sch-options {:fields                        [sch-field]
                  (s/optional-key :no-filter)    s/Bool
                  (s/optional-key :no-nbr-items) s/Bool})



;__________________________________________________________
;                                                          |
;             Internal                                     |
;__________________________________________________________|

(defn render-one-line
  [app owner {:keys [fields action]  :as opts}]
  (om/component
    (let [actFn (when action #(action app))]
      (apply dom/tr #js {:onClick actFn}
            (for [{:keys [field classFn render-fn] :or {render-fn str}} fields]
              (dom/td #js {:className (when classFn (classFn app))}
                      (render-fn (field app))))))))


(defn desc-comp
  [e1 e2]
  (compare e2 e1))


(def sort-dir
  {:asc :desc
   :desc :asc})


(defn sort-by-key
  [k s]
  {k (sort-dir (get s k :desc))})

(defn sortable-header-view
  [o k l s]
  (let []
    (let [style (name (get s k :no-sort))]
      (dom/th #js {:className (styles "sortable-header " style)}
              (dom/button #js {:className (styles "btn btn-default" style)
                               :onClick   #(om/update-state! o :sort-items (partial sort-by-key k))} l
                          (dom/div #js {:className (styles "arrow" style)}))))))

(defmulti filter-by-view (fn [_ _ field-spec _] (:select-filter field-spec)))


(s/defmethod filter-by-view :default
  [items o
   {k :field}
   filters]
  (dom/th #js {}
          (dom/input #js {:value     (k filters)
                          :type      "search"
                          :results   5
                          :className "form-control input-sm"
                          :onChange  #(do
                                       (om/set-state-nr! o :current-page 0)
                                       (om/update-state! o :filter-items (fn [s] (assoc s k (e-value %)))))})))
(defn unique-values
  [items k]
  (mapcat vals (keys (index items [k]))))

(def unique-values-mem (memoize unique-values))


(s/defmethod filter-by-view true
  [items o
   {:keys [field classFn render-fn] :or {render-fn str}}
   filters :- [s/Keyword]]
  (dom/th #js {}
          (let [values (unique-values-mem items field)]
            (apply dom/select #js {:value     (field filters)
                            :type      "select"
                            :results   5
                            :className "form-control input-sm"
                            :onChange  #(do
                                         (om/set-state-nr! o :current-page 0)
                                         (om/update-state! o :filter-items (fn [s] (assoc s field (e-value %)))))}
                   (into [(dom/option #js{:value ""} "")]
                         (for [v values]
                           (dom/option #js {:value v} (render-fn v))))))))


(s/defn filter-header-view
  [items o
   filters :- [s/Keyword]
   fields :- sch-field]
  (apply dom/tr #js {:className "form-group"}
         (for [field fields]
           (filter-by-view items o field filters))))

(defn matches
  "détermine si une chaîne de caractère est contenue dans une autre et renvoie true ou false selon le cas de figure."
  [s sb]
  (not= -1 (.indexOf (str s) sb)))


(defn filter-items
  "Filtre les elements du tableau selon plusieurs colonnes"
  [filters items]
  (if-not filters
    items
    (let [preds (map (fn [[kf vf]]
                       #(when-let [v (kf %)]
                         (matches v vf)))
                     filters)
          all-preds (apply every-pred preds)]
      (filter all-preds items))))

(defn sort-items
  "Trie les éléments du tableau selon une colonne"
  [sort items]
  (let [[k s] (first sort)]
    (if (= :asc s) (sort-by k items)
                   (sort-by k desc-comp items))))

(defn paginate-items
  [n items]
  (vec (partition-all n items)))

(defn pagination-view
  [o n c]
  (let [style (if (= n c) "btn-primary" "btn-default")]
    (dom/button #js {:className (styles "btn" style)
                     :onClick   #(om/set-state! o :current-page n)}
                (inc n))))

;__________________________________________________________
;                                                          |
;             Public                                       |
;__________________________________________________________|

(s/defn table-view
  [items
   o
   {:keys [fields no-filter no-nbr-items] :as opts} :- sch-options]
  (reify om/IRenderState
    (render-state [_ state]
      (let [{sort :sort-items filters :filter-items nb-lines :nb-lines current-page :current-page} state
            filtered (filter-items filters items)
            sorted (sort-items sort filtered)
            partitioned (paginate-items nb-lines sorted)
            current-view (get partitioned current-page)
            filter-headers (filter-header-view items o filters fields)]
        (dom/div #js{:className (styles "panel panel-default" (hide-nil filtered))}
                 (dom/table #js {:className "table table-bordered table-condensed table-hover"}
                            (dom/thead #js {}
                                       (apply dom/tr #js {}
                                              (for [{:keys [field label]} fields]
                                                (sortable-header-view o field label sort)))
                                       (when-not no-filter filter-headers))
                            (dom/tfoot #js {}
                                       (when-not no-filter filter-headers)
                                       (dom/tr #js {}
                                               (dom/th #js {:colSpan 7}
                                                       (dom/div #js {:className "table-footer"}
                                                                (apply dom/div #js {:className "btn-group btn-group-lg"}
                                                                       (for [n (range (count partitioned))]
                                                                         (pagination-view o n current-page)))
                                                                (when-not no-nbr-items
                                                                  (apply dom/div #js {:className "btn-group btn-group-lg"}
                                                                        (for [n (take 3 (iterate #(* 5 %) 10))]
                                                                          (let [style (if (= n nb-lines) "btn-primary" "btn-default")]
                                                                            (dom/button #js {:className (styles "btn" style)
                                                                                             :onClick   #(do
                                                                                                          (om/set-state-nr! o :current-page 0)
                                                                                                          (om/set-state! o :nb-lines n))}
                                                                                        n)))))))))
                            (apply dom/tbody #js {}
                                   (om/build-all render-one-line current-view {:opts opts}))))))))

