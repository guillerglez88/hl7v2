(ns hl7v2.core-test
  (:require
   [hl7v2.core :as sut]
   [clojure.test :as t]))

(def files
  {:sample-1-hl7 "test/data/sample-1.hl7"
   :sample-1-edn "test/data/sample-1.edn"
   :sample-2-hl7 "test/data/sample-2.hl7"
   :sample-2-edn "test/data/sample-2.edn"})

(def data (update-vals files slurp))

(t/deftest parse-test
  (t/testing "Parse hl7 string"
    (t/is (= (read-string (:sample-1-edn data))
             (sut/parse (.getBytes (:sample-1-hl7 data)))))
    (t/is (= (read-string (:sample-2-edn data))
             (sut/parse (.getBytes (:sample-2-hl7 data))))))
  (t/testing "Parse file"
    (t/is (= (read-string (:sample-1-edn data))
             (sut/parse (:sample-1-hl7 files))))))

(t/deftest format-test
  (t/testing "Format hl7 message"
    (t/is (= (str "MSH|^~\\&|TestSendingSystem||||200701011539||ADT^A01^ADT A01||||123\r\n"
                  "PID|||123456||Doe^John")
             (sut/format [{:id "MSH",
                           :data
                           {[1] "|"
                            [2] "^~\\&"
                            [3] "TestSendingSystem"
                            [4] ""
                            [5] ""
                            [6] ""
                            [7] "200701011539"
                            [8] ""
                            [9 0 0] "ADT"
                            [9 0 1] "A01"
                            [9 0 2] "ADT A01"
                            [10] ""
                            [11] ""
                            [12] ""
                            [13] "123"}}
                          {:id "PID",
                           :data
                           {[1] "",
                            [2] "",
                            [3] "123456",
                            [4] "",
                            [5 0 0] "Doe",
                            [5 0 1] "John"}}]))))
  (t/testing "Format segment"
    (t/is (= (str "MSH|^~\\&\r\n"
                  "PID|||123456||Doe^John")
             (sut/format [{:id "MSH",
                           :data
                           {[1] "|"
                            [2] "^~\\&"}}
                          {:id "PID",
                           :data
                           {[1] "",
                            [2] "",
                            [3] "123456",
                            [4] "",
                            [5 0 0] "Doe",
                            [5 0 1] "John"}}])))))
