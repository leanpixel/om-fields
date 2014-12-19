(ns om-fields.editable
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros]
            [om.dom :as dom :include-macros]
            [om-fields.util :refer [debounce]]
            [cljs.core.async :refer [chan put! <!]]
            [clojure.string :as string]))

(defn- auto-resize [el]
  (set! (.. el -style -height) "auto")
  (set! (.. el -style -height) (str (.-scrollHeight el) "px")))

(defn editable
  "editable text field that shows and allows editing of a value that is actually different in reality
   useful when state value is not a string
   ex. dates: 'Monday' vs Date('2014-10-24 9:00:00')"
  [cursor owner {:keys [update-fn id class type disabled placeholder edit-key value-to-string string-to-value value-validate multi-line] :as opts}]
  (let [update-fn (or update-fn #(om/update! cursor edit-key %))
        string-to-value (or string-to-value identity)
        value-valid? (or value-validate (constantly true))
        value-to-string (or value-to-string identity)]
    (reify
      om/IInitState
      (init-state [_]
        {:change-chan (chan)
         :display-value (value-to-string (get-in cursor edit-key))
         :state "start"})

      om/IWillMount
      (will-mount [_]
        (let [debounced-value-chan (debounce (om/get-state owner :change-chan) 400) ]
          (go (loop []
                (let [string (-> (<! debounced-value-chan)
                                 string/trim)
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

      ; update display-value if the actual value is changed elsewhere
      om/IWillReceiveProps
      (will-receive-props [_ next-props]
        (when (not= (get-in next-props edit-key) (get-in (om/get-props owner) edit-key))
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
              :onChange (fn [e]
                          (let [el (.. e -target)]
                            (when multi-line (auto-resize el))
                            (om/set-state! owner :state "editing")
                            (put! (state :change-chan) (.-value el))
                            (om/set-state! owner :display-value (.-value el))))})))))
