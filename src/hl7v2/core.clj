(ns hl7v2.core
  (:require
   [clojure.java.io :as io]
   [clojure.set :refer [map-invert]])
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

(defn hl7-items [rdr]
  (let [msh (seg-id rdr)
        fld (next-char rdr)
        encoding (until rdr #{fld})
        enc (apply str (butlast encoding))
        full-enc (concat encoding [\return \newline])
        enc-set (set full-enc)
        delim-map (zipmap [:cmp :rep :esc :sub :fld :ret :nli] full-enc)
        enc->delim (map-invert delim-map)]
    (concat [[msh :fld] [fld :fld] [enc :fld]]
            (->> (range)
                 (map (fn [_]
                        (let [chars (until rdr enc-set)
                              value (apply str (butlast chars))
                              delim (enc->delim (last chars))]
                          [value delim])))
                 (take-while #(some? (second %)))))))

(with-open [rdr (io/reader "test/data/sample.hl7")]
  (into [] (hl7-items rdr)))
