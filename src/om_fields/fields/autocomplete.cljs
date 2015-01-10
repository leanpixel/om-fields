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
  (let [init-value-fn (fn [props]
                        (get-in (first (filter (fn [i]
                                                 (= (get-in i result-key) (get-in props edit-key))) source)) result-display-key))
        update-fn (or update-fn #(om/update! cursor edit-key %))
        result-render-fn (or result-render-fn result-key)
        search-fn (or search-fn (gen-substring-search source))]
    (reify
      om/IInitState
      (init-state [_]
        {:query (init-value-fn cursor)
         :input-chan (chan)
         :select-chan (chan)
         :results []
         :active-result-index nil })

      om/IWillMount
      (will-mount [_]
        (let [select-chan (om/get-state owner :select-chan)
              search-result-chan (chan)
              input-chan (om/get-state owner :input-chan)]

          (go (loop []
                (let [[v ch] (alts! [select-chan
                                     search-result-chan
                                     input-chan])]
                  (condp = ch
                    select-chan (do (om/set-state! owner :query (get-in v result-display-key))
                                    (om/set-state! owner :results [])
                                    (om/set-state! owner :active-result-index nil)
                                    (update-fn (get-in v result-key)))
                    search-result-chan (om/set-state! owner :results v)
                    input-chan (if (empty? v)
                                 (do (update-fn nil)
                                     (om/set-state! owner :results []))
                                 (do (om/set-state! owner :query v)
                                     (search-fn v search-result-chan))))
                  (recur))))))

      ; update display-value if the actual value is changed elsewhere
      om/IWillReceiveProps
      (will-receive-props [_ next-props]
        (om/set-state! owner :query (init-value-fn next-props)))

      om/IRenderState
      (render-state [_ state]
        (let [active-result (get (vec (state :results)) (state :active-result-index))
              move-active-result (fn [delta]
                                   (if (seq (state :results))
                                     (om/set-state! owner :active-result-index
                                                    (if (state :active-result-index)
                                                      (mod (+ delta (state :active-result-index)) (count (state :results)))
                                                      0))
                                     (when (state :query)
                                       (put! (state :input-chan) (state :query)))))]
          (dom/div #js {:className "autocomplete"
                        :onKeyDownCapture (fn [e] (case (.-keyCode e)
                                                    38 ; up arrow
                                                    (do (move-active-result -1)
                                                        (.preventDefault e))
                                                    40 ; down arrow
                                                    (do (move-active-result 1)
                                                        (.preventDefault e))
                                                    13 ; enter
                                                    (when active-result
                                                        (put! (state :select-chan) active-result))
                                                    nil))}
            (om/build editable state {:opts {:type :text
                                             :placeholder placeholder
                                             :edit-key [:query]
                                             :force true
                                             :wait 240
                                             :update-fn #(put! (state :input-chan) %)}})
            (when (seq (state :results))
              (apply dom/ul #js {:className "results"
                                 :onMouseOut (fn [e] (om/set-state! owner :active-result-index nil))}
                (map-indexed (fn [idx result]
                               (dom/li #js {:onClick (fn [e] (put! (state :select-chan) result))
                                            :onMouseOver (fn [e]
                                                           (om/set-state! owner :active-result-index idx))
                                            :style #js {:cursor "pointer"}
                                            :className (str "result" " " (when (= idx (state :active-result-index)) "active"))}
                                 (result-render-fn result)))
                             (state :results))))))))))

