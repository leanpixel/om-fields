(ns om-fields.fields.select
  (:require [om.core :as om :include-macros]
            [om.dom :as dom :include-macros]
            [om-fields.interface :refer [field]]))

(defmethod field :select [cursor owner {:keys [edit-key update-fn choices transact-tag]}]
  (let [update-fn (or (update-fn #(om/update! cursor edit-key % transact-tag)))]
    (reify
      om/IRender
      (render [_]
        (apply dom/select #js {:value (get-in cursor edit-key)
                               :onChange (fn [e] (update-fn (.. e -target -value)))}
          (map (fn [{:keys [label value]}]
                 (dom/option #js {:value value} label))
               choices))))))
