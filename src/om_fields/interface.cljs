(ns om-fields.interface)

(defmulti ^:export field (fn [data owner opts] (opts :type)))
