(ns user
  (:require
   [clojure.pprint :as pp]
   [hl7v2.schema :refer [gen-structure]]))

(def trigger-events
  ["ACK" "ADR_A19" "ADT_A01" "ADT_A02" "ADT_A03" "ADT_A05" "ADT_A06" "ADT_A09" "ADT_A12" "ADT_A15" "ADT_A16"
   "ADT_A17" "ADT_A18" "ADT_A20" "ADT_A21" "ADT_A24" "ADT_A30" "ADT_A37" "ADT_A38" "ADT_A39" "ADT_A43" "ADT_A45"
   "ADT_A50" "ADT_A52" "ADT_A54" "ADT_A60" "ADT_A61" "BAR_P01" "BAR_P02" "BAR_P05" "BAR_P06" "BAR_P10" "BAR_P12"
   "BPS_O29" "BRP_O30" "BRT_O32" "BTS_O31" "CRM_C01" "CSU_C09" "DFT_P03" "DFT_P11" "DOC_T12" "EAC_U07" "EAN_U09"
   "EAR_U08" "ESR_U02" "ESU_U01" "INR_U06" "INU_U05" "LSU_U12" "MDM_T01" "MDM_T02" "MFK_M01" "MFN_M01" "MFN_M02"
   "MFN_M03" "MFN_M04" "MFN_M05" "MFN_M06" "MFN_M07" "MFN_M08" "MFN_M09" "MFN_M10" "MFN_M11" "MFN_M12" "MFN_M13"
   "MFN_M15" "MFN_Znn" "MFQ_M01" "MFR_M01" "MFR_M04" "MFR_M05" "MFR_M06" "MFR_M07" "NMD_N02" "NMQ_N01" "NMR_N01"
   "OMB_O27" "OMD_O03" "OMG_O19" "OMI_O23" "OML_O21" "OML_O33" "OML_O35" "OMN_O07" "OMP_O09" "OMS_O05" "ORB_O28"
   "ORD_O04" "ORF_R04" "ORG_O20" "ORI_O24" "ORL_O22" "ORL_O34" "ORL_O36" "ORM_O01" "ORN_O08" "ORP_O10" "ORR_O02"
   "ORS_O06" "ORU_R01" "ORU_R30" "OSQ_Q06" "OSR_Q06" "OUL_R21" "OUL_R22" "OUL_R23" "OUL_R24" "PEX_P07" "PGL_PC6"
   "PMU_B01" "PMU_B03" "PMU_B04" "PMU_B07" "PMU_B08" "PPG_PCG" "PPP_PCB" "PPR_PC1" "PPT_PCL" "PPV_PCA" "PRR_PC5"
   "PTR_PCF" "QBP_Q11" "QBP_Q13" "QBP_Q15" "QBP_Q21" "QBP_Qnn" "QBP_Z73" "QCN_J01" "QRY" "QRY_A19" "QRY_PC4"
   "QRY_Q01" "QRY_R02" "QSB_Q16" "QVR_Q17" "RAR_RAR" "RAS_O17" "RCI_I05" "RCL_I06" "RDE_O11" "RDR_RDR" "RDS_O13"
   "RDY_K15" "REF_I12" "RER_RER" "RGR_RGR" "RGV_O15" "ROR_ROR" "RPA_I08" "RPI_I01" "RPI_I04" "RPL_I02" "RPR_I03"
   "RQA_I08" "RQC_I05" "RQI_I01" "RQP_I04" "RRA_O18" "RRD_O14" "RRE_O12" "RRG_O16" "RRI_I12" "RSP_K11" "RSP_K21"
   "RSP_K23" "RSP_K25" "RSP_K31" "RSP_Q11" "RSP_Z82" "RSP_Z86" "RSP_Z88" "RSP_Z90" "RTB_K13" "RTB_Knn" "RTB_Z74"
   "SIU_S12" "SQM_S25" "SQR_S25" "SRM_S01" "SRR_S01" "SSR_U04" "SSU_U03" "SUR_P09" "TCU_U10" "VXQ_V01" "VXR_V03"
   "VXU_V04" "VXX_V02"])

(comment

  (let [output-dir "structures/v2.5.1"]
    (doseq [item trigger-events
            :when (not (#{"ORL_O34" "SUR_P09"} item))]
      (println "generating structure for" item)
      (spit
       (format "%s/%s.edn" output-dir item)
       (with-out-str
         (pp/pprint (gen-structure item))))))

  (spit
   "test/hl7v2/data/ORL_O34.edn"
   (with-out-str
     (pp/pprint (gen-structure "ORL_O34"))))

  :.)