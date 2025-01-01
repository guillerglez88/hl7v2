(ns user
  (:require
   [clojure.pprint :as pp]
   [hl7v2.schema :refer [gen-structure]]
   [hl7v2.config :refer [config]]))

(def trigger-events
  ["ACK"
   "CRM_C01"
   "CSU_C09"
   "MFK_M01"
   "MFN_M01"
   "MFN_M02"
   "MFQ_M01"
   "MFR_M01"
   "NMD_N02"
   "NMQ_N01"
   "NMR_N01"
   "OMD_O01"
   "OMN_O01"
   "OMS_O01"
   "ORD_O02"
   "ORM_O01"
   "ORN_O02"
   "ORR_O02"
   "ORS_O02"
   "PEX_P07"
   "PGL_PC6"
   "PPG_PCG"
   "PPP_PCB"
   "PPR_PC1"
   "RDO_O01"
   "REF_I12"
   "RPA_I08"
   "RQA_I08"
   "RRI_I12"
   "RRO_O02"
   "SIU_S12"
   "SRM_S01"
   "SRR_S01"])

(comment

  (with-redefs [config (delay {:standard-dir "~/Downloads/2.3.1"
                               :lang "en"})]
    (let [output-dir "structures/v2.3.1"]
      (doseq [item trigger-events]
        (println "generating structure for" item)
        (spit
         (format "%s/%s.edn" output-dir item)
         (with-out-str
           (pp/pprint (gen-structure item)))))))

  :.)