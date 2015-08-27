(ns om-fields.fields.date
  (:require [om-fields.interface :refer [field]]
            [om-fields.editable :refer [editable]]
            [om-fields.util :refer [str-or-nil]]
            [cljs-time.format :refer [formatter formatters unparse]]
            [cljs-time.core :refer [date-time minutes minus]]))

(defmethod field :datetime [data owner opts]
  (let [date-format (if (opts :date-format)
                      (formatter (opts :date-format))
                      (formatters :mysql))]
    (editable data owner (assoc opts
                           :value-to-string (fn [value]
                                              (when value
                                                (unparse date-format (minus (date-time value)
                                                                            (minutes (.getTimezoneOffset (js/Date.)))))))
                           :value-validate (fn [value]
                                             (not (js/isNaN (.getTime value))))
                           :string-to-value (fn [string]
                                              (when-let [s (str-or-nil string)]
                                                (js/Date.create s)))))))



