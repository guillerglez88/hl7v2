(ns hl7v2.core
  (:require
   [hl7v2.er7 :refer [parse-er7 format-er7]]
   [hl7v2.zipper :refer [match-trigger-event]]))

(defn parse-hl7 [content structure]
  (-> (parse-er7 content)
      (match-trigger-event structure)))

(defn format-hl7 [msg & {:keys [line-break] :or {line-break "\n"} :as opt}]
  (format-er7 msg opt))