(ns hl7v2.core
  (:require
   [hl7v2.er7 :refer [parse-er7 format-er7]]
   [hl7v2.zipper :refer [hl7 er7]]))

(defn parse-hl7 [content struc]
  (-> (parse-er7 content)
      (hl7 struc)))

(defn format-hl7 [hl7 struc & {:keys [line-break] :or {line-break "\n"}}]
  (-> (er7 hl7 struc)
      (format-er7 :line-break line-break)))

(comment

  (require '[clojure.edn :as edn])
  (require '[clojure.java.io :as io])

  (parse-hl7 (io/file "test/hl7v2/data/oru-r01.hl7")
             (edn/read-string (slurp "structures/v2.5.1/ORU_R01.edn")))

  (let [struc (edn/read-string (slurp "structures/v2.5.1/ORU_R01.edn"))]
    (-> (io/file "test/hl7v2/data/oru-r01.hl7")
        (parse-hl7 struc)
        (format-hl7 struc)))

  :.)