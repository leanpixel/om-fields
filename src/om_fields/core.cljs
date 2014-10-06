(ns om-fields.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros]
            [om.dom :as dom :include-macros]
            [clojure.string :refer [join blank? split]]
            [cljs.core.async :refer [put! chan <! alts!]]
            [om-fields.util :refer [debounce]]))

(declare editable)

(defn auto-resize [el]
  (set! (.. el -style -height) "auto")
  (set! (.. el -style -height) (str (.-scrollHeight el) "px")))

(defn -human-friendly-editable
  "editable text field that shows and allows editing of a value that is actually different in reality
   useful when state value is not a string
   ex. dates: 'Monday' vs Date('2014-10-24 9:00:00')"
  [data owner {:keys [update-fn edit-key value-to-string string-to-value value-validate multi-line] :as opts}]
  (let [string-to-value (or string-to-value identity)
        value-valid? (or value-validate (constantly true))
        value-to-string (or value-to-string identity)]
    (reify
      om/IInitState
      (init-state [_]
        {:change-chan (chan)
         :display-value (value-to-string (get-in data edit-key))
         :state "start"})

      om/IWillMount
      (will-mount [_]
        (let [debounced-value-chan (debounce (om/get-state owner :change-chan) 1000) ]
          (go (loop []
                (let [string (<! debounced-value-chan)
                      value (string-to-value string)]
                  (if (value-valid? value)
                    (do (om/set-state! owner :state "saved")
                        (update-fn value))
                    (om/set-state! owner :state "invalid"))
                  (recur))))))

      ; update textarea size initially
      om/IDidMount
      (did-mount [_]
        (when multi-line (auto-resize (om/get-node owner "textarea"))))

      ; update display-value if the actual value is changed by something else
      om/IWillReceiveProps
      (will-receive-props [_ next-props]
        (om/set-state! owner :display-value (value-to-string (get-in next-props edit-key))))

      om/IRenderState
      (render-state [_ state]
        ((if multi-line dom/textarea dom/input)
         #js {:value (state :display-value)
              :ref "textarea"
              :rows 1
              :className (str "input " (state :state) (when (empty? (state :display-value)) "" ))
              :onChange (fn [e]
                          (let [el (.. e -target)]
                            (when multi-line (auto-resize el))
                            (om/set-state! owner :state "editing")
                            (put! (state :change-chan) (.-value el))
                            (om/set-state! owner :display-value (.-value el))))})))))

(defn mini-record-view [o]
  (dom/div #js {:className "mini-record"}
    (dom/img #js {:className "image" :src (:entity/image o)})
    (dom/span #js {:className "name"} nil (:entity/name o))
    (dom/span #js {:className "type"} (join ", " (map name (:entity/is-a o))))))

(defn- -search-results [[query-chan selection-chan] owner]
  (reify
    om/IInitState
    (init-state [_]
      {:results []
       :result-chan (chan)})
    om/IWillMount
    (will-mount [_]
      (go (loop []
            (let [result-chan (om/get-state owner :result-chan)
                  [v ch] (alts! [query-chan result-chan])]
              (condp = ch
                query-chan (do (put! (om/get-shared owner :name-search-chan) [v result-chan]))
                result-chan (do (om/set-state! owner :results v)))
              (recur)))))
    om/IRenderState
    (render-state [_ state]
      (apply dom/ul #js {:className "results"}
        (map (fn [o]
               (dom/li #js {:onClick (fn [e] (put! selection-chan (:db/id o)))}
                 (mini-record-view o)))
             (state :results))))))

(defn- editable-date [data owner opts]
  (-human-friendly-editable data owner (assoc opts
                                         :value-to-string (fn [value]
                                                            (when value
                                                              (.long (js/Date.create value))))
                                         :value-validate (fn [value]
                                                           (not (js/isNaN (.getTime value))))
                                         :string-to-value (fn [string]
                                                            (js/Date.create string)))))

(defn- editable-keyword [data owner opts]
  (-human-friendly-editable data owner (assoc opts
                                         :value-to-string (fn [value]
                                                            (when value
                                                              (name value)))
                                         :value-validate (complement nil?)
                                         :string-to-value (fn [string]
                                                            (when-not (blank? string)
                                                              (keyword string))))))


(defn- editable-email [data owner opts]
  (-human-friendly-editable data owner (assoc opts
                                         :value-validate (fn [value]
                                                           (re-matches #"(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,4}" value)))))

(defn- editable-url [data owner opts]
  (-human-friendly-editable data owner (assoc opts
                                         :value-validate (fn [value]
                                                          (re-matches #"(?i)^(https?|ftp)://[^\s/$.?#].[^\s]*$" value)))))

(defn editable-text [data owner opts]
  (-human-friendly-editable data owner (assoc opts :value-to-string identity :string-to-value identity)))

(defn editable-long-text [data owner opts]
  (-human-friendly-editable data owner (assoc opts :value-to-string identity :string-to-value identity :multi-line true)))

(defn editable-integer [data owner opts]
  (-human-friendly-editable data owner (assoc opts :value-to-string str :string-to-value (fn [v] (js/parseInt v 10)))))

(defn editable-thing [data owner {:keys [update-fn edit-key] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:display-value "..."
       :reference nil
       :change-chan (chan)
       :query-chan (chan)
       :selection-chan (chan)
       :id-to-name-result-chan (chan)
       :state "start"
       })

    om/IWillMount
    (will-mount [_]
     (let [selection-chan (om/get-state owner :selection-chan)
            query-chan (om/get-state owner :query-chan)
            id-to-name-search-chan (om/get-shared owner :id-to-name-search-chan)
            id-to-name-result-chan (om/get-state owner :id-to-name-result-chan)
            debounced-value-chan (debounce (om/get-state owner :change-chan) 1000)]
        (put! id-to-name-search-chan [(get-in data edit-key) id-to-name-result-chan])
        (go (loop []
              (let [[v ch] (alts! [debounced-value-chan selection-chan id-to-name-result-chan])]
                (condp = ch
                  debounced-value-chan (do (put! query-chan v))
                  selection-chan (do (om/set-state! owner :state "saved")
                                     (put! id-to-name-search-chan [v id-to-name-result-chan])
                                     (update-fn v))
                  id-to-name-result-chan (do (om/set-state! owner :display-value (:entity/name v))
                                             (om/set-state! owner :reference v)))
                (recur))))))

    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:className "autocomplete"}
        (let [o (when (not= (state :state) "editing") (state :reference))]
          (dom/div #js {:className "mini-record"}
            (dom/img #js {:className "image" :src (:entity/image o)})
            (dom/input
              #js {:value (state :display-value)
                   :className (str "input name " (state :state))
                   :onChange (fn [e]
                               (let [el (.. e -target)
                                     v (.-value el)]
                                 (om/set-state! owner :state "editing")
                                 (put! (state :change-chan) v)
                                 (om/set-state! owner :display-value v)))})
            (dom/span #js {:className "type"} (join ", " (map name (:entity/is-a o))))))
        (when (= "editing" (state :state))
          (om/build -search-results [(state :query-chan) (state :selection-chan)]))))))

(defn- editable-read-only [data owner opts]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:className "readonly"}
        (get-in data (opts :edit-key))))))

(defn editable [field-type]
  (let [f (case field-type
            :read-only editable-read-only
            :datatype/keyword editable-keyword
            :datatype/integer editable-integer
            :datatype/thing editable-thing
            :datatype/date editable-date
            :datatype/text editable-text
            :datatype/long-text editable-long-text
            :datatype/url editable-url
            :datatype/email editable-email)]
    f))

(def editable-available
  #{:read-only :datatype/thing :datatype/text :datatype/long-text :datatype/file :datatype/image :datatype/date :datatype/keyword :datatype/url :datatype/email})
