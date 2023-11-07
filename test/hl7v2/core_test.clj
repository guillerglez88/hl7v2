(ns hl7v2.core-test
  (:require
   [hl7v2.core :as sut]
   [clojure.test :as t]))

(t/deftest parse-test
  (t/testing "Parse hl7 string"
    (t/is (= [["MSH"
               {[9 0 1] "R01",
                [4] "TML",
                [7] "200905011130",
                [10] "20169838-v25",
                [6] "OLIS",
                [12] "2.5",
                [9] "ORU",
                [3] "ULTRA",
                [5] "OLIS",
                [11] "T",
                [2] "^~\\&",
                [1] \|}]
              ["PID"
               {[3 0 4] "MR",
                [7] "19310313",
                [11 0 4] "M6G 2T9",
                [11 0 2] "TORONTO",
                [13] "(416)888-8888",
                [3] "7005728",
                [5 0 2] "DIAMOND",
                [8] "F",
                [5] "TEST",
                [5 0 1] "RACHEL",
                [3 0 3] "TML",
                [19] "1014071185",
                [11] "200 ANYWHERE ST",
                [11 0 3] "ON",
                [19 0 1] "KR"}]
              ["PV1"
               {[1] "1",
                [3] "OLIS",
                [7] "OLIST",
                [7 0 1] "BLAKE",
                [7 0 2] "DONALD",
                [7 0 3] "THOR",
                [7 0 8] "921379",
                [7 0 12] "OLIST"}]
              ["ORC"
               {[12 0 2] "DONALD",
                [12] "OLIST",
                [12 0 7] "L",
                [3 0 2] "OLIS_Site_ID",
                [3] "T09-100442-RET-0",
                [12 0 8] "921379",
                [3 0 3] "ISO",
                [1] "RE",
                [12 0 3] "THOR",
                [12 0 1] "BLAKE"}]
              ["OBR"
               {[4] "RET",
                [7] "200905011106",
                [16 0 7] "L",
                [20] "T09-100442",
                [22] "200905011130",
                [3 0 2] "OLIS_Site_ID",
                [3] "T09-100442-RET-0",
                [4 0 1] "RETICULOCYTE COUNT",
                [24] "B7",
                [16 0 8] "921379",
                [16] "OLIST",
                [16 0 3] "THOR",
                [16 0 1] "BLAKE",
                [14] "200905011106",
                [27] "1",
                [3 0 3] "ISO",
                [21] "MOHLTC",
                [19] "7870279",
                [16 0 2] "DONALD",
                [27 0 3] "200905011106",
                [4 0 2] "HL79901 literal",
                [1] "0",
                [27 0 5] "R",
                [25] "F",
                [18] "7870279"}]
              ["OBX"
               {[1] "1",
                [2] "ST",
                [5] "Test Value"}]]
             (sut/parse (.getBytes (str "MSH|^~\\&|ULTRA|TML|OLIS|OLIS|200905011130||ORU^R01|20169838-v25|T|2.5\r\n"
                                        "PID|||7005728^^^TML^MR||TEST^RACHEL^DIAMOND||19310313|F|||200 ANYWHERE ST^^TORONTO^ON^M6G 2T9||(416)888-8888||||||1014071185^KR\r\n"
                                        "PV1|1||OLIS||||OLIST^BLAKE^DONALD^THOR^^^^^921379^^^^OLIST\r\n"
                                        "ORC|RE||T09-100442-RET-0^^OLIS_Site_ID^ISO|||||||||OLIST^BLAKE^DONALD^THOR^^^^L^921379\r\n"
                                        "OBR|0||T09-100442-RET-0^^OLIS_Site_ID^ISO|RET^RETICULOCYTE COUNT^HL79901 literal|||200905011106|||||||200905011106||OLIST^BLAKE^DONALD^THOR^^^^L^921379||7870279|7870279|T09-100442|MOHLTC|200905011130||B7|F||1^^^200905011106^^R\r\n"
                                        "OBX|1|ST|||Test Value")))))
    (t/is (= [["MSH"
               {[1] \|,
                [2] "^~\\&",
                [3] "TestSendingSystem",
                [7] "200701011539",
                [9] "ADT",
                [9 0 1] "A01",
                [9 0 2] "ADT A01",
                [13] "123"}]
              ["PID"
               {[3] "123456",
                [5] "Doe",
                [5 0 1] "John"}]]
             (sut/parse (.getBytes (str "MSH|^~\\&|TestSendingSystem||||200701011539||ADT^A01^ADT A01||||123\r\n"
                                        "PID|||123456||Doe^John")))))))

(t/deftest format-test
  (t/testing "Format hl7 message"
    (t/is (= (str "MSH|^~\\&|TestSendingSystem||||200701011539||ADT^A01^ADT A01||||123\r\n"
                  "PID|||123456||Doe^John")
             (sut/format [["MSH"
                           {[1] "|"
                            [2] "^~\\&"
                            [3] "TestSendingSystem"
                            [7] "200701011539"
                            [9] "ADT"
                            [9 0 1] "A01"
                            [9 0 2] "ADT A01"
                            [13] "123"}]
                          ["PID"
                           {[3] "123456"
                            [5] "Doe"
                            [5 0 1] "John"}]]))))
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
