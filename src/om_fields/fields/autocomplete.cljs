(ns om-fields.fields.autocomplete
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros]
            [om.dom :as dom :include-macros]
            [clojure.string :refer [join]]
            [om-fields.interface :refer [field]]
            [om-fields.editable :refer [editable]]
            [cljs.core.async :refer [put! chan <! alts!]]))


(defn gen-substring-search [source]
  (fn [query return-chan]
    (put! return-chan (->> source
                          (filter (fn [item]
                                    (re-find (re-pattern (str "(?i)" query)) (join " " (vals item)))))))))


(comment
 "
  :update-fn - optional
  function called when field is ready to persist back to parent, by default: (fn [value] (om/update! cursor edit-key value))

  :source - optional

  :edit-key - required

  :result-key - required
  key to get-in result to pass to update-fn, i.e. if you want the :id of the result returned, pass [:id]

  :result-display-key - required
  key used to display the selected item, ex. [:name]

  :search-fn - optional

  :placeholder - optional

  :result-render-fn - optional
  function called to render each item in search results, by default: :result-key

  ")
(defmethod field :autocomplete
  [cursor owner {:keys [update-fn source edit-key result-key result-display-key search-fn placeholder result-render-fn] :as opts}]
  (let [update-fn (or update-fn #(om/update! cursor edit-key %))
        result-render-fn (or result-render-fn result-key)
        search-fn (or search-fn (gen-substring-search source))]
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
          (search-fn (get-in cursor edit-key) thing-result-chan)
          (go (loop []
                (let [[v ch] (alts! [select-chan
                                     thing-result-chan
                                     search-result-chan
                                     input-chan])]
                  (condp = ch
                    select-chan (do (om/set-state! owner :thing v)
                                    (om/set-state! owner :results [])
                                    (update-fn (get-in v result-key)))
                    thing-result-chan (om/set-state! owner :thing (first v))
                    search-result-chan (om/set-state! owner :results v)
                    input-chan (if (empty? v)
                                 (do (update-fn nil)
                                     (om/set-state! owner :results []))
                                 (search-fn v search-result-chan)))
                  (recur))))))

      om/IRenderState
      (render-state [_ state]
        (dom/div #js {:className "autocomplete"}
          (om/build editable (state :thing) {:opts {:type :text
                                                    :placeholder placeholder
                                                    :edit-key result-display-key
                                                    :wait 50
                                                    :update-fn #(put! (state :input-chan) %)}})
          (when (seq (state :results))
            (apply dom/ul #js {:className "results"}
              (map (fn [result]
                     (dom/li #js {:onClick (fn [e] (put! (state :select-chan) result))
                                  :style #js {:cursor "pointer"}
                                  :className "result"}
                       (result-render-fn result)))
                   (state :results)))))))))

