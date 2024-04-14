(ns hl7v2.core-test
  (:require
   [hl7v2.core :as sut]
   [clojure.test :as t]))

(def ORU_R01
  (delay
    (read-string
     (slurp "test/hl7v2/data/ORU_R01.edn"))))

(t/deftest parse-test
  (t/testing "Parse hl7 string"
    (t/is (= [{:MSH
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
              {:OBX {1 "1", 2 "ST", 5 "Test Value"}}]
             (sut/parse (.getBytes (str "MSH|^~\\&|ULTRA|TML|OLIS|OLIS|200905011130||ORU^R01|20169838-v25|T|2.5\r\n"
                                        "PID|||7005728^^^TML^MR||TEST^RACHEL^DIAMOND||19310313|F|||200 ANYWHERE ST^^TORONTO^ON^M6G 2T9||(416)888-8888||||||1014071185^KR\r\n"
                                        "PV1|1||OLIS||||OLIST^BLAKE^DONALD^THOR^^^^^921379^^^^OLIST\r\n"
                                        "ORC|RE||T09-100442-RET-0^^OLIS_Site_ID^ISO|||||||||OLIST^BLAKE^DONALD^THOR^^^^L^921379\r\n"
                                        "OBR|0||T09-100442-RET-0^^OLIS_Site_ID^ISO|RET^RETICULOCYTE COUNT^HL79901 literal|||200905011106|||||||200905011106||OLIST^BLAKE^DONALD^THOR^^^^L^921379||7870279|7870279|T09-100442|MOHLTC|200905011130||B7|F||1^^^200905011106^^R\r\n"
                                        "OBX|1|ST|||Test Value")))))
    (t/is (= [{:MSH
               {1 "|",
                2 "^~\\&",
                3 "Regional MPI",
                5 "Master MPI",
                6 "Alpha Hospital",
                7 "20060501140010",
                9 {1 "ADT", 2 "A28"},
                10 "3948375",
                11 {1 "P", 2 "T"},
                12 "2.4",
                15 "ER"}}
              {:EVN
               {1 "A28",
                2 "20060501140008",
                5
                {1 "000338475",
                 2 "Author",
                 3 "Arthur",
                 9 {1 "Regional MPI", 2 "2.16.840.1.113883.19.201", 3 "ISO"},
                 10 "L"},
                6 "20060501140008"}}
              {:PID
               {3
                {0
                 {1 "000197245",
                  4 {1 "NationalPN", 2 "2.16.840.1.113883.19.3", 3 "ISO"},
                  5 "PN"},
                 1
                 {1 "4532",
                  4 {1 "Careful\\&CareClinic", 2 "2.16.840.1.113883.19.2.400566", 3 "ISO"},
                  5 "PI"},
                 2
                 {1 "3242346",
                  4 {1 "GoodmanGP", 2 "2.16.840.1.113883.19.2.450998", 3 "ISO"},
                  5 "PI"}},
                5 {1 "Patient", 2 "Particia", 7 "L"},
                7 "19750103",
                8 "F",
                11
                {1 {1 "Randomroad 23a", 2 "Randomroad", 3 "23a"},
                 3 "Anytown",
                 5 "1200",
                 7 "H"},
                13
                {0 {1 "555 3542557", 2 "ORN", 3 "PH"},
                 1 {1 "555 3542558", 2 "ORN", 3 "FX"}},
                14 {1 "555 5557865", 2 "WPN", 3 "PH"}}}
              {:PV1 {2 "N"}}]
             (sut/parse (.getBytes (str "MSH|^~\\&|Regional MPI||Master MPI|Alpha Hospital|20060501140010||ADT^A28|3948375|P^T|2.4|||ER\r\n"
                                        "EVN|A28|20060501140008|||000338475^Author^Arthur^^^^^^Regional MPI&2.16.840.1.113883.19.201&ISO^L|20060501140008\r\n"
                                        "PID|||000197245^^^NationalPN&2.16.840.1.113883.19.3&ISO^PN~4532^^^Careful\\&CareClinic&2.16.840.1.113883.19.2.400566&ISO^PI~3242346^^^GoodmanGP&2.16.840.1.113883.19.2.450998&ISO^PI||Patient^Particia^^^^^L||19750103|F|||Randomroad 23a&Randomroad&23a^^Anytown^^1200^^H||555 3542557^ORN^PH~555 3542558^ORN^FX|555 5557865^WPN^PH\r\n"
                                        "PV1||N|")))))
    (t/is (= [{:MSH
               {1 "|",
                2 "^~\\&",
                3 "TestSendingSystem",
                7 "200701011539",
                9 {1 "ADT", 2 "A01", 3 "ADT A01"},
                13 "123"}}
              {:PID {3 "123456", 5 {1 "Doe", 2 "John"}}}]
             (sut/parse (.getBytes (str "MSH|^~\\&|TestSendingSystem||||200701011539||ADT^A01^ADT A01||||123\r\n"
                                        "PID|||123456||Doe^John")))))
    (t/is (= [{:MSH {1 "|", 2 "^~\\&"}} {:PID {3 "123456", 5 {1 "Doe", 2 "John"}}}]
             (sut/parse (.getBytes (str "MSH|^~\\&|\r\n"
                                        "PID|||123456||Doe^John")))))))

(t/deftest format-test
  (t/testing "Format hl7 message"
    (t/is (= (str "MSH|^~\\&|TestSendingSystem||||200701011539||ADT^A01^ADT A01||||123\r\n"
                  "PID|||123456||Doe^John")
             (sut/format [{:MSH {1 "|",
                                 2 "^~\\&",
                                 3 "TestSendingSystem",
                                 7 "200701011539",
                                 9 {1 "ADT", 2 "A01", 3 "ADT A01"},
                                 13 "123"}}
                          {:PID {3 "123456", 5 {1 "Doe", 2 "John"}}}]))))
  (t/testing "Format segment"
    (t/is (= (str "MSH|^~\\&\r\n"
                  "PID|||123456||Doe^John")
             (sut/format [{:MSH {2 "^~\\&", 1 "|"}}
                          {:PID {3 "123456", 5 {1 "Doe", 2 "John"}}}])))))

(t/deftest structure-test
  (t/testing "Structure parsed message according to trigger-event"
    (t/is (= {:ORU_R01
              [{:MSH
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
               {:PATIENT-RESULTS
                [{:PATIENT
                  [{:PID
                    {3 {1 "7005728", 4 "TML", 5 "MR"},
                     5 {1 "TEST", 2 "RACHEL", 3 "DIAMOND"},
                     7 "19310313",
                     8 "F",
                     11 {1 "200 ANYWHERE ST", 3 "TORONTO", 4 "ON", 5 "M6G 2T9"},
                     13 "(416)888-8888",
                     19 {1 "1014071185", 2 "KR"}}}
                   {:VISIT
                    [{:PV1
                      {1 "1",
                       3 "OLIS",
                       7
                       {1 "OLIST",
                        2 "BLAKE",
                        3 "DONALD",
                        4 "THOR",
                        9 "921379",
                        13 "OLIST"}}}]}]}
                 {:ORDER-OBSERVATION
                  [{:ORC
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
                   {:OBSERVATION [{:OBX {1 "1", 2 "ST", 5 "Test Value"}}]}]}]}]}
             (sut/structure @ORU_R01 (sut/parse "test/hl7v2/data/oru-r01.hl7"))))))
