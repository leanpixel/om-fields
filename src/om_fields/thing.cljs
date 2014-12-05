(ns om-fields.thing
    (:require-macros [cljs.core.async.macros :refer [go]])
    (:require [om.core :as om :include-macros]
              [om.dom :as dom :include-macros]
              [clojure.string :refer [join]]
              [om-fields.text :refer [human-friendly-editable]]
              [cljs.core.async :refer [put! chan <! alts!]]
              [om-fields.util :refer [debounce]]))

(defn thing [cursor owner {:keys [update-fn edit-key search! placeholder] :as opts}]
  (let [update-fn (or update-fn #(om/update! cursor edit-key %))]
    (reify
      om/IInitState
      (init-state [_]
        {:thing nil
         :input-chan (chan)
         :select-chan (chan)
         :results [] })

      om/IWillMount
      (will-mount [_]
        (let [select-chan (om/get-state owner :select-chan)
              thing-result-chan (chan)
              search-result-chan (chan)
              input-chan (om/get-state owner :input-chan)]
          (search! {:id (get-in cursor edit-key)} thing-result-chan)
          (go (loop []
                (let [[v ch] (alts! [select-chan
                                     thing-result-chan
                                     search-result-chan
                                     input-chan])]
                  (condp = ch
                    select-chan (do (om/set-state! owner :thing v)
                                    (om/set-state! owner :results [])
                                    (update-fn (:db/id v)))
                    thing-result-chan  (om/set-state! owner :thing v)
                    search-result-chan (om/set-state! owner :results v)
                    input-chan (if (empty? v)
                                 (do (update-fn nil)
                                     (om/set-state! owner :results []))
                                 (search! {:name v} search-result-chan)))
                  (recur))))))

      om/IRenderState
      (render-state [_ state]
        (dom/div #js {:className "autocomplete"}
          (let [thing (state :thing)]
            (dom/div #js {:className "mini-record"}
              (dom/img #js {:className "image" :src (:entity/image thing)})
              (om/build human-friendly-editable thing
                        {:opts {:placeholder placeholder
                                :edit-key [:entity/name]
                                :update-fn #(put! (state :input-chan) %)}})
              (dom/span #js {:className "type"} (join ", " (map name (:entity/is-a thing))))))
          (apply dom/ul #js {:className "results"}
            (map (fn [o]
                   (dom/li #js {:onClick (fn [e] (put! (state :select-chan) o))
                                :style #js {:cursor "pointer"}}
                     (dom/div #js {:className "mini-record"}
                       (dom/img #js {:className "image" :src (:entity/image o)})
                       (dom/span #js {:className "name"} nil (:entity/name o))
                       (dom/span #js {:className "type"} (join ", " (map name (:entity/is-a o)))))))
                 (state :results))))))))
