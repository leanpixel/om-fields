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
  :edit-key - required
    key to extract out of passed-in cursor to use as initial value (and to update!, if using default update-fn)

  :update-fn - optional
    function called when field is ready to persist back to parent
    default: (fn [value] (om/update! cursor edit-key value))

  :val-to-str - optional
    function that converts the initial value to a string to display in the input
    default: str

  :search-fn - required
    function that returns array of result objects given the input search string
    can use gen-substring-search
    sample:
    (fn [input-string return-chan]
      (let [results [{:foo 'bar'}]
        (put! return-chan results)))

  :choice-render-fn - optional
    function used to display the chosen result, ex. :name
    template: (fn [result-object] (str result-object))
    default: (obj :edit-key)

  :result-render-fn - optional
    function called to render each item in search results
    template: (fn [result-object] (str result-object))
    default: choice-render-fn

  :placeholder - optional
    default: nil

  :class - optional
    default: nil

  ")

(defmethod field :autocomplete
  [cursor owner {:keys [edit-key ; required
                        update-fn
                        val-to-str ; optional
                        search-fn ; required
                        choice-render-fn
                        result-render-fn
                        placeholder] :as opts}]

  (let [update-fn (or update-fn #(om/update! cursor edit-key %))
        val-to-str (or val-to-str str)
        choice-render-fn (or choice-render-fn (fn [o] (o edit-key)))
        result-render-fn (or result-render-fn choice-render-fn)]

    (reify
      om/IInitState
      (init-state [_]
        {:query (val-to-str (get-in cursor edit-key))
         :input-chan (chan)
         :select-chan (chan)
         :search-result-chan (chan)
         :results []
         :active-result-index nil })

      om/IWillMount
      (will-mount [_]
        (let [select-chan (om/get-state owner :select-chan)
              search-result-chan (om/get-state owner :search-result-chan)
              input-chan (om/get-state owner :input-chan)]

          (go (loop []
                (let [[v ch] (alts! [select-chan
                                     search-result-chan
                                     input-chan])]
                  (condp = ch
                    select-chan (do (om/set-state! owner :query (choice-render-fn v))
                                    (om/set-state! owner :results [])
                                    (om/set-state! owner :active-result-index nil)
                                    (update-fn v))
                    search-result-chan (om/set-state! owner :results v)
                    input-chan (if (empty? v)
                                 (do (update-fn nil)
                                     (search-fn v search-result-chan))
                                 (do (om/set-state! owner :query v)
                                     (search-fn v search-result-chan))))
                  (recur))))))

      ; update display-value if the actual value is changed elsewhere
      om/IWillReceiveProps
      (will-receive-props [_ next-props]
        (om/set-state! owner :query (val-to-str (get-in next-props edit-key))))

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
                                                    9 ; tab
                                                    (om/set-state! owner :results [])
                                                    27 ; esc
                                                    (om/set-state! owner :results [])
                                                    13 ; enter
                                                    (when active-result
                                                        (put! (state :select-chan) active-result))
                                                    nil))}
            (om/build editable state {:opts {:type :text
                                             :placeholder placeholder
                                             :edit-key [:query]
                                             :force true
                                             :wait 240
                                             :on-focus (fn [e]
                                                         (search-fn (or (.. e -target -value) "") (state :search-result-chan)))
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

