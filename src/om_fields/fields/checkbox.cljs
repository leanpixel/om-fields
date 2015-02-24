(ns om-fields.fields.checkbox
  (:require [om.core :as om :include-macros]
            [om.dom :as dom :include-macros]
            [om-fields.interface :refer [field]]))

(defmethod field :checkbox [cursor owner {:keys [update-fn edit-key label transact-tag] :as opts}]
  (let [update-fn (or update-fn #(om/update! cursor edit-key % transact-tag))]
    (reify
      om/IRender
      (render [_]
        (dom/label #js {:style #js {:cursor "pointer"}}
          (dom/input #js {:type "checkbox"
                          :style #js {:cursor "pointer"}
                          :onClick (fn [e]
                                      (update-fn (.. e -target -checked)))
                          :checked (get-in cursor edit-key) })
          label)))))

