(ns hl7v2.core
  (:require
   [clojure.java.io :as io])
  (:import
   (java.io Reader)))

(defn char-seq [^Reader rdr]
  (let [ch (.read rdr)]
    (when-not (= ch -1)
      (cons (char ch) (lazy-seq (char-seq rdr))))))

(defn seg-id [^Reader rdr]
  (apply str (take 3 (char-seq rdr))))

(defn next-char [^Reader rdr]
  (first (char-seq rdr)))

(defn until [^Reader rdr pred]
  (reduce (fn [acc curr]
            (let [items (conj acc curr)]
              (if (pred curr)
                (reduced items)
                items))) [] (char-seq rdr)))

(defn next-item [rdr enc]
  (let [enc-set (set (vals enc))
        enc->tag (into {} (map (fn [k] [(k enc) k]) (keys enc)))
        value (until rdr enc-set)]
    {(enc->tag (last value)) (apply str (butlast value))}))

;; TODO: use (with-open ...)
(def ^Reader rdr (io/reader "test/data/sample.hl7"))

(let [msh (seg-id rdr)
      field (next-char rdr)
      encoding (until rdr #{field})
      [cmp rep esc sub] encoding
      enc {:field field
           :component cmp
           :repetition rep
           :escape esc
           :subcomponent sub
           :return \return
           :newline \newline}]
  (into [] (for [_ (range 10000)]
             (next-item rdr enc))))
