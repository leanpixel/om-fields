(ns om-fields.fields.text
  (:require [om-fields.interface :refer [field]]
            [om-fields.editable :refer [editable]]
            [om-fields.util :refer [str-or-nil]]
            [clojure.string :as string]))


(defmethod field :text [data owner opts]
  (editable data owner (assoc opts
                         :value-to-string identity
                         :string-to-value str-or-nil)))

(defmethod field :password [data owner opts]
  (editable data owner (assoc opts
                         :type "password"
                         :value-to-string identity
                         :string-to-value str-or-nil)))

(defmethod field :url [data owner opts]
  (editable data owner (assoc opts
                         :string-to-value str-or-nil
                         :value-validate (fn [value]
                                           (if value
                                             (re-matches #"(?i)^(https?|ftp)://[^\s/$.?#].[^\s]*$" value)
                                             true)))))

(defmethod field :long-text [data owner opts]
  (editable data owner (assoc opts
                         :value-to-string identity
                         :string-to-value str-or-nil
                         :multi-line true)))

(defmethod field :integer [data owner opts]
  (editable data owner (assoc opts
                         :value-to-string str
                         :value-validate integer?
                         :string-to-value (fn [v]
                                            (js/parseInt (str-or-nil v) 10)))))

(defn str-insert
  "Insert c in string s at index i."
  [s c i]
  (str (subs s 0 i) c (subs s i)))

(defmethod field :currency [data owner opts]
  (editable data owner (assoc opts
                         :value-to-string (fn [v]
                                            (str-insert (str v) "." (- (count (str v)) 2) ))
                         :value-validate number?
                         :string-to-value (fn [v]
                                            (js/parseInt (string/replace (str-or-nil v) #"\." ""))))))

(defmethod field :keyword [data owner opts]
  (editable data owner (assoc opts
                         :value-to-string (fn [value]
                                            (when value
                                              (name value)))
                         :value-validate (complement nil?)
                         :string-to-value (fn [string]
                                            (-> string
                                                str-or-nil
                                                keyword)))))

(defmethod field :email [data owner opts]
  (editable data owner (assoc opts
                         :value-validate (fn [value]
                                           (if value
                                             (re-matches #"(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,4}" value)
                                             true)))))



