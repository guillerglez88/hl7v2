(ns hl7v2.core-test
  (:require
   [hl7v2.core :as sut]
   [clojure.test :as t]))

(def files
  {:sample-1-hl7 "test/data/sample-1.hl7"
   :sample-1-edn "test/data/sample-1.edn"})

(def data (update-vals files slurp))

(t/deftest parse-test
  (t/testing "Parse hl7 string"
    (t/is (= (read-string (:sample-1-edn data))
             (sut/parse (.getBytes (:sample-1-hl7 data)))))
    (t/is (= [["MSH"
               {[1] \|,
                [2] "^~\\&",
                [3] "TestSendingSystem",
                [4] "200701011539",
                [5] "ADT",
                [5 0 1] "A01",
                [5 0 2] "ADT A01",
                [6] "123"}]
              ["PID"
               {[1] "123456",
                [2] "Doe",
                [2 0 1] "John"}]]
             (sut/parse (.getBytes (str "MSH|^~\\&|TestSendingSystem||||200701011539||ADT^A01^ADT A01||||123\r\n"
                                        "PID|||123456||Doe^John\r\n"))))))
  (t/testing "Parse file"
    (t/is (= (read-string (:sample-1-edn data))
             (sut/parse (:sample-1-hl7 files))))))

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
