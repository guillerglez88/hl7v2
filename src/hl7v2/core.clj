(ns hl7v2.core
  (:require
   [hl7v2.er7 :refer [parse-er7 format-er7]]
   [hl7v2.zipper :refer [hl7 er7]]
   [clojure.zip :as zip]))

(defn parse-hl7 [content struc]
  (-> (parse-er7 content)
      (hl7 struc)))

(defn format-hl7 [hl7 struc & {:keys [line-break] :or {line-break "\n"}}]
  (-> (er7 hl7 struc)
      (format-er7 :line-break line-break)))

(comment

  (require '[clojure.edn :as edn])
  (require '[clojure.java.io :as io])
  (require '[clojure.zip :as zip])
  (require '[hl7v2.zipper :as hz])

  (parse-hl7 (io/file "test/hl7v2/data/oru-r01.hl7")
             (edn/read-string (slurp "structures/v2.5.1/ORU_R01.edn")))


  (let [struc (edn/read-string (slurp "structures/v2.5.1/ORU_R01.edn"))]
    (-> (io/file "test/hl7v2/data/oru-r01.hl7")
        (parse-hl7 struc)
        (format-hl7 struc)))

  (let [struc (edn/read-string (slurp "structures/v2.5.1/ORU_R01.edn"))]
    (->> (parse-hl7 (io/file "test/hl7v2/data/oru-r01.hl7") struc)
         (hz/hl7-zip)
         (iterate zip/next)
         (take-while (complement zip/end?))
         (map zip/node)
         (filter (fn [node]
                   (and (= :OBX (hz/hl7-seg-id node))
                        (= "CO2RR" (get-in node [:OBX :observation-identifier :identifier])))))))

  (letfn [(edit-obx [loc]
            (zip/edit loc assoc-in [:OBX :abnormal-flags] ["H"]))]
    (let [struc (edn/read-string (slurp "structures/v2.5.1/ORU_R01.edn"))]
      (->> (parse-hl7 (io/file "test/hl7v2/data/oru-r01.hl7") struc)
           (hz/hl7-zip)
           (iterate zip/next)
           (take-while (complement zip/end?))
           (filter (fn [loc]
                     (let [node (zip/node loc)]
                       (and (= :OBX (hz/hl7-seg-id node))
                            (= "CO2RR" (get-in node [:OBX :observation-identifier :identifier]))))))
           (first)
           (edit-obx)
           (zip/root))))

  :.)