(ns om-fields.fields.multiselect
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [om-fields.editable :refer [editable]]
            [om-fields.interface :refer [field]]
            [clojure.set :refer [union]]
            [cljs.core.async :refer [put! chan <! sub pub]]
            [om-fields.util :refer [debounce]]
            [clojure.string :as string]))

(defn- auto-resize-w [el]
  (set! (.. el -style -width) "auto")
  (set! (.. el -style -width) (str (.-scrollWidth el) "px")))

(defmethod field :multiselect [cursor owner {:keys [placeholder update-fn edit-key options] :as opts}]
  (let [options (or options #{})
        edit-key (or edit-key [])
        update-fn (or update-fn #(om/update! cursor edit-key %))]
    (reify
      om/IInitState
      (init-state [_] {:selected (set (get-in cursor edit-key))
                       :new #{}
                       :change-chan (chan)})

      om/IWillMount
      (will-mount [_]
        (let [value-chan (om/get-state owner :change-chan)]
          (go (loop []
                (let [values (<! value-chan)]
                  (update-fn (set (filter (comp not nil?) values)))
                  (recur))))))

      om/IWillUpdate
      (will-update [_ new-props new-state]
        ; put to change-chan whenever state changes
        ; (but NOT when the props change, which happens after all updates)
        (when (= new-props (om/get-props owner))
          (let [interests (union (new-state :selected) (new-state :new))]
            (put! (new-state :change-chan) interests))))

      om/IRenderState
      (render-state [_ state]
        (apply dom/div #js {:ref "select" :className "select multiple"}
          (concat
            (map
              (fn [v]
                (dom/span
                  #js {:className (str "option" " "
                                       (when (contains? (state :selected) v) "selected"))
                       :style #js {:cursor "pointer"}
                       :onClick (fn [e]
                                  (om/update-state! owner :selected (fn [s]
                                                                      (if (contains? s v)
                                                                        (disj s v)
                                                                        (conj s v)))))}
                  v)) (union options (state :selected)))
            (map (fn [v]
                   (dom/span #js {:className (str "option selected new")
                                  :style #js {:cursor "pointer"}
                                  :onClick (fn [e]
                                             (when (not (empty? v))
                                               (om/update-state! owner :new (fn [s] (disj s v)))))}
                     (dom/input #js {:size 1
                                     :className ""
                                     :value v
                                     :placeholder placeholder
                                     :style #js {:cursor "pointer"}
                                     :onChange (fn [e]
                                                 (om/update-state! owner :new (fn [s] (conj (disj s v) (.. e -target -value))))
                                                 (auto-resize-w (.. e -target)))})))
                 (state :new))
            [(dom/span #js {:className (str "option")
                            :style #js {:cursor "pointer"}
                            :onClick (fn [e]
                                       (om/update-state! owner :new (fn [s] (conj s nil))))} "+")])))

      om/IDidUpdate
      (did-update [_ _ _]
        ; set focus on last 'new' option
        (let [opts (.getElementsByClassName (om/get-node owner "select") "new")
              len (.-length opts)]
          (when (< 0 len)
            (.focus (.-firstChild (aget opts (- len 1))))))))))
