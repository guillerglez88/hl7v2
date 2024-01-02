(ns hl7v2.core-test
  (:require
   [hl7v2.core :as sut]
   [clojure.test :as t]))

(t/deftest parse-test
  (t/testing "Parse hl7 string"
    (t/is (= [{:MSH {1 "|",
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
              {:PID {7 "19310313",
                     13 "(416)888-8888",
                     3 {1 "7005728", 4 "TML", 5 "MR"},
                     19 {1 "1014071185", 2 "KR"},
                     11 {1 "200 ANYWHERE ST", 3 "TORONTO", 4 "ON", 5 "M6G 2T9"},
                     5 {1 "TEST", 2 "RACHEL", 3 "DIAMOND"},
                     8 "F"}}
              {:PV1 {1 "1",
                     3 "OLIS",
                     7 {1 "OLIST",
                        2 "BLAKE",
                        3 "DONALD",
                        4 "THOR",
                        9 "921379",
                        13 "OLIST"}}}
              {:ORC {1 "RE",
                     3 {1 "T09-100442-RET-0", 3 "OLIS_Site_ID", 4 "ISO"},
                     12 {1 "OLIST", 4 "THOR", 3 "DONALD", 2 "BLAKE", 9 "921379", 8 "L"}}}
              {:OBR {1 "0",
                     3 {1 "T09-100442-RET-0", 3 "OLIS_Site_ID", 4 "ISO"},
                     4 {1 "RET", 2 "RETICULOCYTE COUNT", 3 "HL79901 literal"},
                     7 "200905011106",
                     14 "200905011106",
                     16 {1 "OLIST", 4 "THOR", 3 "DONALD", 2 "BLAKE", 9 "921379", 8 "L"},
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
    (t/is (= [{:MSH {1 "|",
                     2 "^~\\&",
                     3 "TestSendingSystem",
                     7 "200701011539",
                     9 {1 "ADT", 2 "A01", 3 "ADT A01"},
                     13 "123"}}
              {:PID {3 "123456", 5 {1 "Doe", 2 "John"}}}]
             (sut/parse (.getBytes (str "MSH|^~\\&|TestSendingSystem||||200701011539||ADT^A01^ADT A01||||123\r\n"
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
             (sut/format [["MSH"
                           {[1] "|"
                            [2] "^~\\&"}]
                          ["PID"
                           {[3] "123456"
                            [5] "Doe"
                            [5 0 1] "John"}]])))))
