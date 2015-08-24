(ns om-fields.core
  (:require [cljsjs.sugar]
            [om-fields.interface :refer [field]]
            [om-fields.fields.text]
            [om-fields.fields.thing]
            [om-fields.fields.autocomplete]
            [om-fields.fields.file]
            [om-fields.fields.date]
            [om-fields.fields.multiselect]
            [om-fields.fields.checkbox]
            [om-fields.fields.select]))

(def ^:export input-available
  (keys (methods field)))

(def ^:export input field)
