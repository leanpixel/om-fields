(ns om-fields.editable
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros]
            [om.dom :as dom :include-macros]
            [om-fields.util :refer [debounce]]
            [cljs.core.async :refer [chan put! <! alts!]]
            [clojure.string :as string]))

(defn- auto-resize [el]
  (set! (.. el -style -height) "auto")
  (set! (.. el -style -height)
        (str (min 300 (.-scrollHeight el)) "px")))

(defn editable
  "editable text field that shows and allows editing of a value that is actually different in reality
   useful when state value is not a string
   ex. dates: 'Monday' vs Date('2014-10-24 9:00:00')"
  [cursor owner {:keys [update-fn on-focus on-blur id class type force? disabled placeholder edit-key value-to-string wait string-to-value value-validate multi-line transact-tag delay-save?] :as opts}]
  (let [update-fn (or update-fn #(om/update! cursor edit-key % transact-tag))
        string-to-value (or string-to-value identity)
        value-valid? (or value-validate (constantly true))
        value-to-string (or value-to-string identity)]
    (reify
      om/IInitState
      (init-state [_]
        {:change-chan (chan)
         :display-value (value-to-string (get-in cursor edit-key))
         :kill-chan (chan)
         :state "start"})

      om/IWillMount
      (will-mount [_]
        (let [debounced-value-chan (debounce (om/get-state owner :change-chan) (or wait 400))
              kill-chan (om/get-state owner :kill-chan)]
          (go (loop []
                (let [[v ch] (alts! [debounced-value-chan kill-chan])]
                  (when (= ch debounced-value-chan)
                    (let [value (string-to-value v)]
                      (if (value-valid? value)
                        (do (om/set-state! owner :state "saved")
                            (update-fn value))
                        (om/set-state! owner :state "invalid"))
                      (recur))))))))

      ; update textarea size initially
      om/IDidMount
      (did-mount [_]
        (let [textarea (om/get-node owner "textarea")]
          (when multi-line
            (auto-resize textarea)
            (.. js/window (addEventListener "resize" (fn [_] (auto-resize textarea)))))))

      om/IWillUnmount
      (will-unmount [_]
        (put! (om/get-state owner :kill-chan) true))

      ; update display-value if the actual value is changed elsewhere
      om/IWillReceiveProps
      (will-receive-props [_ next-props]
        (when (or force? (not= (get-in next-props edit-key) (get-in (om/get-props owner) edit-key)))
          (om/set-state! owner :state "start")
          (om/set-state! owner :display-value (value-to-string (get-in next-props edit-key)))))

      om/IRenderState
      (render-state [_ state]
        ((if multi-line dom/textarea dom/input)
           #js {:value (state :display-value)
                :placeholder placeholder
                :disabled disabled
                :ref "textarea"
                :id id
                :rows 1
                :type (name type)
                :className (str "input" " " class " " (state :state))
                :onBlur (fn [e]
                          (when delay-save?
                            (put! (state :change-chan) (.. e -target -value)))
                          (when on-blur (on-blur e)))
                :onFocus on-focus
                :onChange (fn [e]
                            (let [el (.. e -target)]
                              (when multi-line (auto-resize el))
                              (om/set-state! owner :state "editing")
                              (when-not delay-save? (put! (state :change-chan) (.-value el)))
                              (om/set-state! owner :display-value (.-value el))))})))))
