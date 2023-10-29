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

(defn seg-id [rdr]
  (apply str (take 3 (char-seq rdr))))

(defn next-char [rdr]
  (first (char-seq rdr)))

(defn until
  ([rdr pred esc]
   (reduce (fn [acc curr]
             (let [items (conj acc curr)]
               (if (and (not (esc (last acc)))
                        (pred curr))
                 (reduced items)
                 items))) [] (char-seq rdr)))
  ([rdr pred]
   (until rdr pred #{})))

(defn next-token [rdr delim-set esc-set enc->tag]
  (let [chars (until rdr delim-set esc-set)
        value (apply str (butlast chars))
        tag (enc->tag (last chars))]
    [value tag]))

(defn tokenize [rdr]
  (let [msh (seg-id rdr)
        fld (next-char rdr)
        encoding (until rdr  #{fld})
        enc (apply str (butlast encoding))
        full-enc (concat encoding [\return \newline])
        enc-map (zipmap [:cmp :rep :esc :sub :fld :ret :nli] full-enc)
        enc->tag (map-invert enc-map)
        delim-set (set (vals (select-keys enc-map [:cmp :rep :sub :fld :ret :nli])))
        esc-set #{(:esc enc-map)}
        token (fn [_] (next-token rdr delim-set esc-set enc->tag))]
    (apply concat
           (concat [[msh nil] [fld nil] [enc :fld]]
                   (take-while second (map token (range)))))))

(with-open [rdr (io/reader "test/data/sample.hl7")]
  (into [] (->> (tokenize rdr)
                (partition-by #{:ret :nli})
                (remove #{'(:ret) '(:nli) '("")}))))
