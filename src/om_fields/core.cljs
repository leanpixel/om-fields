(ns om-fields.core
  (:require [om.core :as om :include-macros]
            [om.dom :as dom :include-macros]
            [om-fields.text :refer [human-friendly-editable]]
            [om-fields.thing :refer [thing]]
            [om-fields.multiselect :refer [multiselect]]
            [clojure.string :refer [blank?]]

            [cljs-time.format :refer [formatter formatters unparse]]
            [cljs-time.core :refer [date-time]]))

(defmulti ^:export input (fn [data owner opts] (opts :type)))

(def ^:export input-available
  (keys (methods input)))

(defn str-or-nil [s]
  (if (blank? s) nil s))

(defmethod input :read-only [_]
  (fn [data owner opts]
    (reify
      om/IRender
      (render [_]
        (dom/div #js {:className "readonly"}
          (get-in data (opts :edit-key)))))))

(defmethod input :text [data owner opts]
  (human-friendly-editable data owner (assoc opts
                                        :value-to-string identity
                                        :string-to-value str-or-nil)))

(defmethod input :password [data owner opts]
  (human-friendly-editable data owner (assoc opts
                                        :type "password"
                                        :value-to-string identity
                                        :string-to-value str-or-nil)))

(defmethod input :url [data owner opts]
  (human-friendly-editable data owner (assoc opts
                                        :string-to-value str-or-nil
                                        :value-validate (fn [value]
                                                          (if value
                                                            (re-matches #"(?i)^(https?|ftp)://[^\s/$.?#].[^\s]*$" value)
                                                            true)))))

(defmethod input :long-text [data owner opts]
  (human-friendly-editable data owner (assoc opts
                                        :value-to-string identity
                                        :string-to-value str-or-nil
                                        :multi-line true)))

(defmethod input :integer [data owner opts]
  (human-friendly-editable data owner (assoc opts
                                        :value-to-string str
                                        :value-validate integer?
                                        :string-to-value (fn [v]
                                                           (js/parseInt (str-or-nil v) 10)))))

(defmethod input :keyword [data owner opts]
  (human-friendly-editable data owner (assoc opts
                                           :value-to-string (fn [value]
                                                              (when value
                                                                (name value)))
                                           :value-validate (complement nil?)
                                           :string-to-value (fn [string]
                                                              (-> string
                                                                  str-or-nil
                                                                  keyword)))))

(defmethod input :date [data owner opts]
  (let [date-format (if (opts :date-format)
                      (formatter (opts :date-format))
                      (formatters :mysql))]
    (human-friendly-editable data owner (assoc opts
                                          :value-to-string (fn [value]
                                                             (when value
                                                               (unparse date-format (date-time value))))
                                          :value-validate (fn [value]
                                                            (not (js/isNaN (.getTime value))))
                                          :string-to-value (fn [string]
                                                             (when-let [s (str-or-nil string)]
                                                               (js/Date.create s)))))))

(defmethod input :email [data owner opts]
  (human-friendly-editable data owner (assoc opts
                                        :value-validate (fn [value]
                                                          (if value
                                                            (re-matches #"(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,4}" value)
                                                            true)))))



(defmethod input :file [cursor owner {:keys [update-fn edit-key upload] :as opts}]
  (let [update-fn (or update-fn #(om/update! cursor edit-key %))]
    (reify
      om/IRender
      (render [_]
        (let [handle-files (fn [file-list]
                             (when (< 0 (.-length file-list))
                               (upload (aget file-list 0) (fn [url] (update-fn url)))))
              url (get-in cursor edit-key)]
          (dom/div #js {:className "file"}
            ; direct url input
            (dom/div #js {:className "input-wrapper"}
              (om/build input cursor {:opts (assoc opts :type :url)}))
            ; hidden file input
            (dom/input #js {:type "file"
                            :ref "file-input"
                            :style #js {:display "none"}
                            :onChange (fn [e]
                                        (handle-files (.. e -target -files)))})
            ; styled input button
            (dom/div #js {:className "action"
                          :onClick (fn [e]
                                     (.click (om/get-node owner "file-input")))}  " Upload a File")))))))

(defmethod input :image [cursor owner {:keys [update-fn edit-key upload] :as opts}]
  (let [update-fn (or update-fn #(om/update! cursor edit-key %))]
    (reify
      om/IInitState
      (init-state [_]
        {:data-url nil})

      om/IRenderState
      (render-state [_ state]
        (let [handle-files (fn [file-list]
                             (when (< 0 (.-length file-list))
                               (let [reader (js/FileReader. )]
                                 (aset reader "onload" (fn [e]
                                                         (om/set-state! owner :data-url (.. e -target -result))))
                                 (upload (aget file-list 0)
                                         (fn [url]
                                           (update-fn url)))
                                 (.. reader (readAsDataURL (aget file-list 0))))))
              url (get-in cursor edit-key)]
          (dom/div #js {:className "image"}
            ; direct url input
            (dom/div #js {:className "input-wrapper"}
              (om/build input cursor {:opts (assoc opts :type :url)}))
            ; hidden file input
            (dom/input #js {:type "file"
                            :ref "file-input"
                            :style #js {:display "none"}
                            :onChange (fn [e]
                                        (handle-files (.. e -target -files)))})
            ; styled input button
            (dom/div #js {:className "action"
                          :style #js {:cursor "pointer"}
                          :onClick (fn [e]
                                     (.click (om/get-node owner "file-input")))}  " Upload an Image")

            ; drop area + image + button
            (dom/div #js {:className "embed"
                          :title "Upload an Image"
                          :style #js {:cursor "pointer"}
                          :onClick (fn [e]
                                     (.click (om/get-node owner "file-input")))
                          :onDragEnter (fn [e] false)
                          :onDragOver (fn [e] false)
                          :onDrop (fn [e]
                                    (.preventDefault e)
                                    (when-let [web-content (.. e -dataTransfer (getData "text/html"))]
                                      (when-let [img-url (get (re-find #"src=\"([^\"]*)\" " web-content) 1)]
                                        (om/set-state! owner :data-url img-url)))
                                    (handle-files (.. e -dataTransfer -files))
                                    )}
              (if-let [src (or (state :data-url) url)]
                (dom/img #js {:src src})
                (dom/span #js {:className "button"} "+")))))))))

(defmethod input :thing [data owner opts]
  (thing data owner opts))

(defmethod input :multiselect [data owner opts]
  (multiselect data owner opts))

(defmethod input :checkbox [cursor owner {:keys [update-fn edit-key label] :as opts}]
  (let [update-fn (or update-fn #(om/update! cursor edit-key %))]
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

