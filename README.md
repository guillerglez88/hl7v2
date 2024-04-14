# hl7v2

Hl7v2 Clojure library.

```
com.github.guillerglez88/hl7v2 {:git/tag "0.0.1-SNAPSHOT", :git/sha "17efce5"}
```

```clojure
(require '[hl7v2.core :as hl7])
```

## parse

Parse hl7 message, any `io/reader` input is allowed.

``` clojure
(def str-hl7 (str "MSH|^~\\&|ULTRA|TML|OLIS|OLIS|200905011130||ORU^R01|20169838-v25|T|2.5\r\n"
                  "PID|||7005728^^^TML^MR||TEST^RACHEL^DIAMOND||19310313|F|||200 ANYWHERE ST^^TORONTO^ON^M6G 2T9||(416)888-8888||||||1014071185^KR\r\n"
                  "PV1|1||OLIS||||OLIST^BLAKE^DONALD^THOR^^^^^921379^^^^OLIST\r\n"
                  "ORC|RE||T09-100442-RET-0^^OLIS_Site_ID^ISO|||||||||OLIST^BLAKE^DONALD^THOR^^^^L^921379\r\n"
                  "OBR|0||T09-100442-RET-0^^OLIS_Site_ID^ISO|RET^RETICULOCYTE COUNT^HL79901 literal|||200905011106|||||||200905011106||OLIST^BLAKE^DONALD^THOR^^^^L^921379||7870279|7870279|T09-100442|MOHLTC|200905011130||B7|F||1^^^200905011106^^R\r\n"
                  "OBX|1|ST|||Test Value"))

(hl7/parse (.getBytes str-hl7))
;; => [{:MSH
;;      {1 "|",
;;       2 "^~\\&",
;;       3 "ULTRA",
;;       4 "TML",
;;       5 "OLIS",
;;       6 "OLIS",
;;       7 "200905011130",
;;       9 {1 "ORU", 2 "R01"},
;;       10 "20169838-v25",
;;       11 "T",
;;       12 "2.5"}}
;;     {:PID
;;      {3 {1 "7005728", 4 "TML", 5 "MR"},
;;       5 {1 "TEST", 2 "RACHEL", 3 "DIAMOND"},
;;       7 "19310313",
;;       8 "F",
;;       11 {1 "200 ANYWHERE ST", 3 "TORONTO", 4 "ON", 5 "M6G 2T9"},
;;       13 "(416)888-8888",
;;       19 {1 "1014071185", 2 "KR"}}}
;;     {:PV1
;;      {1 "1",
;;       3 "OLIS",
;;       7 {1 "OLIST", 2 "BLAKE", 3 "DONALD", 4 "THOR", 9 "921379", 13 "OLIST"}}}
;;     {:ORC
;;      {1 "RE",
;;       3 {1 "T09-100442-RET-0", 3 "OLIS_Site_ID", 4 "ISO"},
;;       12 {1 "OLIST", 2 "BLAKE", 3 "DONALD", 4 "THOR", 8 "L", 9 "921379"}}}
;;     {:OBR
;;      {1 "0",
;;       3 {1 "T09-100442-RET-0", 3 "OLIS_Site_ID", 4 "ISO"},
;;       4 {1 "RET", 2 "RETICULOCYTE COUNT", 3 "HL79901 literal"},
;;       7 "200905011106",
;;       14 "200905011106",
;;       16 {1 "OLIST", 2 "BLAKE", 3 "DONALD", 4 "THOR", 8 "L", 9 "921379"},
;;       18 "7870279",
;;       19 "7870279",
;;       20 "T09-100442",
;;       21 "MOHLTC",
;;       22 "200905011130",
;;       24 "B7",
;;       25 "F",
;;       27 {1 "1", 4 "200905011106", 6 "R"}}}
;;     {:OBX {1 "1", 2 "ST", 5 "Test Value"}}]
```

Ability to output token data. Sample token:

```clojure
{:value "ADT"
 :location {:from [1 31]
            :to   [1 34]}}
```

```clojure
  (parse (.getBytes (str "MSH|^~\\&|Test||||200701011539||ADT^A01^ADT A01||||123\r\n"
                         "PID|||123456||Doe^John"))
         :val-fn (juxt :value :location))
  ;; => [{:MSH
  ;;      {1 ["|" {:from [1 3], :to [1 4]}],
  ;;       2 ["^~\\&" {:from [1 4], :to [1 8]}],
  ;;       3 ["Test" {:from [1 9], :to [1 13]}],
  ;;       7 ["200701011539" {:from [1 17], :to [1 29]}],
  ;;       9
  ;;       {1 ["ADT" {:from [1 31], :to [1 34]}],
  ;;        2 ["A01" {:from [1 35], :to [1 38]}],
  ;;        3 ["ADT A01" {:from [1 39], :to [1 46]}]},
  ;;       13 ["123" {:from [1 50], :to [1 53]}]}}
  ;;     {:PID
  ;;      {3 ["123456" {:from [2 6], :to [2 12]}],
  ;;       5
  ;;       {1 ["Doe" {:from [2 14], :to [2 17]}],
  ;;        2 ["John" {:from [2 18], :to [2 22]}]}}}]
```

## format

Encode data into hl7 string.

``` clojure
(hl7/format [{:MSH
              {1 "|",
               2 "^~\\&",
               3 "ULTRA",
               4 "TML",
               5 "OLIS",
               6 "OLIS",
               7 "200905011130",
               9 {1 "ORU", 2 "R01"},
               10 "20169838-v25",
               11 "T",
               12 "2.5"}}
             {:PID
              {3 {1 "7005728", 4 "TML", 5 "MR"},
               5 {1 "TEST", 2 "RACHEL", 3 "DIAMOND"},
               7 "19310313",
               8 "F",
               11 {1 "200 ANYWHERE ST", 3 "TORONTO", 4 "ON", 5 "M6G 2T9"},
               13 "(416)888-8888",
               19 {1 "1014071185", 2 "KR"}}}
             {:PV1
              {1 "1",
               3 "OLIS",
               7 {1 "OLIST", 2 "BLAKE", 3 "DONALD", 4 "THOR", 9 "921379", 13 "OLIST"}}}
             {:ORC
              {1 "RE",
               3 {1 "T09-100442-RET-0", 3 "OLIS_Site_ID", 4 "ISO"},
               12 {1 "OLIST", 2 "BLAKE", 3 "DONALD", 4 "THOR", 8 "L", 9 "921379"}}}
             {:OBR
              {1 "0",
               3 {1 "T09-100442-RET-0", 3 "OLIS_Site_ID", 4 "ISO"},
               4 {1 "RET", 2 "RETICULOCYTE COUNT", 3 "HL79901 literal"},
               7 "200905011106",
               14 "200905011106",
               16 {1 "OLIST", 2 "BLAKE", 3 "DONALD", 4 "THOR", 8 "L", 9 "921379"},
               18 "7870279",
               19 "7870279",
               20 "T09-100442",
               21 "MOHLTC",
               22 "200905011130",
               24 "B7",
               25 "F",
               27 {1 "1", 4 "200905011106", 6 "R"}}}
             {:OBX {1 "1", 2 "ST", 5 "Test Value"}}])
;; => "MSH|^~\\&|ULTRA|TML|OLIS|OLIS|200905011130||ORU^R01|20169838-v25|T|2.5\r\n
;;     PID|||7005728^^^TML^MR||TEST^RACHEL^DIAMOND||19310313|F|||200 ANYWHERE ST^^TORONTO^ON^M6G 2T9||(416)888-8888||||||1014071185^KR\r\n
;;     PV1|1||OLIS||||OLIST^BLAKE^DONALD^THOR^^^^^921379^^^^OLIST\r\n
;;     ORC|RE||T09-100442-RET-0^^OLIS_Site_ID^ISO|||||||||OLIST^BLAKE^DONALD^THOR^^^^L^921379\r\n
;;     OBR|0||T09-100442-RET-0^^OLIS_Site_ID^ISO|RET^RETICULOCYTE COUNT^HL79901 literal|||200905011106|||||||200905011106||OLIST^BLAKE^DONALD^THOR^^^^L^921379||7870279|7870279|T09-100442|MOHLTC|200905011130||B7|F||1^^^200905011106^^R\r\n
;;     OBX|1|ST|||Test Value"
```

## structure

Structure parsed hl7 according to a message structure. Structure is a trigger-event, declared using hiccup like format. The 

```clojure
(def ORU_R01 [:ORU_R01 {:version "2.5.1"}
              [:MSH]
              [:SFT]
              [:PATIENT-RESULTS
               [:PATIENT
                [:PID]
                [:PD1]
                [:NTE]
                [:NK1]
                [:VISIT
                 [:PV1]
                 [:PV2]]]
               [:ORDER-OBSERVATION
                [:ORC]
                [:OBR]
                [:NTE]
                [:TIMING-QTY
                 [:TQ1]
                 [:TQ2]]
                [:CTD]
                [:OBSERVATION
                 [:OBX]
                 [:NTE]]
                [:FT1]
                [:CTI]
                [:SPECIMEN
                 [:SPM]
                 [:OBX]]]]
              [:DSC]])

(hl7/structure ORU_R01 (hl7/parse (.getBytes str-hl7)))
;; => {:ORU_R01
;;     [{:MSH
;;       {1 "|",
;;        2 "^~\\&",
;;        3 "ULTRA",
;;        4 "TML",
;;        5 "OLIS",
;;        6 "OLIS",
;;        7 "200905011130",
;;        9 {1 "ORU", 2 "R01"},
;;        10 "20169838-v25",
;;        11 "T",
;;        12 "2.5"}}
;;      {:PATIENT-RESULTS
;;       [{:PATIENT
;;         [{:PID
;;           {3 {1 "7005728", 4 "TML", 5 "MR"},
;;            5 {1 "TEST", 2 "RACHEL", 3 "DIAMOND"},
;;            7 "19310313",
;;            8 "F",
;;            11 {1 "200 ANYWHERE ST", 3 "TORONTO", 4 "ON", 5 "M6G 2T9"},
;;            13 "(416)888-8888",
;;            19 {1 "1014071185", 2 "KR"}}}
;;          {:VISIT
;;           [{:PV1
;;             {1 "1",
;;              3 "OLIS",
;;              7
;;              {1 "OLIST",
;;               2 "BLAKE",
;;               3 "DONALD",
;;               4 "THOR",
;;               9 "921379",
;;               13 "OLIST"}}}]}]}
;;        {:ORDER-OBSERVATION
;;         [{:ORC
;;           {1 "RE",
;;            3 {1 "T09-100442-RET-0", 3 "OLIS_Site_ID", 4 "ISO"},
;;            12 {1 "OLIST", 2 "BLAKE", 3 "DONALD", 4 "THOR", 8 "L", 9 "921379"}}}
;;          {:OBR
;;           {1 "0",
;;            3 {1 "T09-100442-RET-0", 3 "OLIS_Site_ID", 4 "ISO"},
;;            4 {1 "RET", 2 "RETICULOCYTE COUNT", 3 "HL79901 literal"},
;;            7 "200905011106",
;;            14 "200905011106",
;;            16 {1 "OLIST", 2 "BLAKE", 3 "DONALD", 4 "THOR", 8 "L", 9 "921379"},
;;            18 "7870279",
;;            19 "7870279",
;;            20 "T09-100442",
;;            21 "MOHLTC",
;;            22 "200905011130",
;;            24 "B7",
;;            25 "F",
;;            27 {1 "1", 4 "200905011106", 6 "R"}}}
;;          {:OBSERVATION [{:OBX {1 "1", 2 "ST", 5 "Test Value"}}]}]}]}]}
```

---

Standard: https://www.hl7.org/implement/standards/product_brief.cfm?product_id=185   
Schema: https://www.hl7.org/documentcenter/private/standards/V251/HL7-xml%20v2.5.1.zip
