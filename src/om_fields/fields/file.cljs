(ns om-fields.fields.file
  (:require [om.core :as om :include-macros]
            [om.dom :as dom :include-macros]
            [om-fields.interface :refer [field]]))

(defmethod field :file [cursor owner {:keys [update-fn edit-key upload] :as opts}]
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
              (om/build field cursor {:opts (assoc opts :type :url)}))
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

(defmethod field :image [cursor owner {:keys [update-fn edit-key upload] :as opts}]
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
              (om/build field cursor {:opts (assoc opts :type :url)}))
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
