(ns ^:figwheel-always om-table.core
    (:require[om.core :as om :include-macros true]
              [om.dom :as dom :include-macros true]
             [om-table.table :refer [table-view]]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:data [{:country :country/FRA :count 23}
                                 {:country :country/BEL :count 2}
                                 {:country :country/POL :count 12}
                                 {:country :country/USA :count 45}
                                 {:country :country/CHE :count 8}
                                 {:country :country/GBR :count 78}
                                 {:country :country/BGR :count 90}
                                 {:country :country/DNK :count 3}
                                 {:country :country/EGY :count 1}
                                 {:country :country/FIN :count 67}
                                 {:country :country/GEO :count 23}
                                 {:country :country/HUN :count 45}
                                 {:country :country/IRL :count 12}
                                 {:country :country/KAZ :count 23}
                                 {:country :country/LTU :count 34}
                                 {:country :country/MAR :count 45}
                                 {:country :country/NLD :count 46}
                                 {:country :country/PRT :count 56}
                                 {:country :country/ROM :count 78}
                                 {:country :country/SRB :count 76}
                                 {:country :country/TUR :count 54}
                                 {:country :country/UKR :count 32}]}))




(om/root
  (fn [app owner]
    (reify om/IRender
      (render [_]
        (dom/div #js {:className "container"}
          (dom/div #js {:id "example"}
                  (om/build table-view (:data app)
                            {:opts       {:fields [{:field         :country
                                                    :label         "Country"
                                                    :select-filter true
                                                    :render-fn     name}
                                                   {:field   :count
                                                    :label   "Number"
                                                    :classFn #(let [n (:count %)]
                                                               (cond
                                                                 (< n 10) "low"
                                                                 (< n 20) "mid"
                                                                 (< n 50) "high"
                                                                 (< n 100) "very-high"
                                                                 ))}]}
                             :init-state {:sort-items   {:count :desc}
                                          :current-page 0
                                          :nb-lines     10
                                          }}))))))
  app-state
  {:target (. js/document (getElementById "app"))})


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
) 

