(ns hl7v2.core
  (:require
   [clojure.java.io :as io])
  (:import
   (java.io Reader)))

;; TODO: use (with-open ...)
(def ^Reader rdr (io/reader "test/data/sample.hl7"))

(defn char-seq [^Reader rdr]
  (let [ch (.read rdr)]
    (when-not (= ch -1)
      (cons (char ch) (lazy-seq (char-seq rdr))))))

(apply str (char-seq rdr))
