(ns hl7v2.core-test
  (:require
   [hl7v2.core :as sut]
   [clojure.test :as t]))

(def hl7-str (slurp "test/data/sample-1.hl7"))
(def hl7-msg (read-string (slurp "test/data/sample-1.edn")))

(t/deftest parse-test
  (t/testing "Parse hl7 string"
    (t/is (= hl7-msg
             (sut/parse hl7-str)))))
