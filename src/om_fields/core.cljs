(ns om-fields.core
  (:require [om-fields.interface :refer [field]]
            [om-fields.fields.text]
            [om-fields.fields.thing]
            [om-fields.fields.file]
            [om-fields.fields.date]
            [om-fields.fields.multiselect]
            [om-fields.fields.checkbox]))

(def ^:export input-available
  (keys (methods field)))

(def ^:export input field)
