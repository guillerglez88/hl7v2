(ns hl7v2.core
  (:require
   [clojure.java.io :as io]
   [clojure.set :refer [map-invert]]
   [clojure.string :as str])
  (:refer-clojure :exclude [format])
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
           (concat [[msh :fld] [fld :fld] [enc :fld]]
                   (take-while second (map token (range)))))))

(defn seg-tokens-seq [tokens]
  (remove #{'(:ret) '(:nli) '("")}
          (partition-by #{:ret :nli} tokens)))

(defn segment [tokens]
  (let [[id & tail] tokens]
    (loop [[h & t] tail
           head {:fld 0, :rep 0, :cmp 0, :sub 0}
           data {}]
      (cond
        (= h :fld) (recur t (merge head {:fld (inc (:fld head)), :rep 0, :cmp 0, :sub 0}) data)
        (= h :rep) (recur t (merge head {:rep (inc (:rep head)), :cmp 0, :sub 0}) data)
        (= h :cmp) (recur t (merge head {:cmp (inc (:cmp head)), :sub 0}) data)
        (= h :sub) (recur t (update head h inc) data)
        (some? h) (recur t head (assoc data (mapv head [:fld :rep :cmp :sub]) h))
        :else {:id id, :data data}))))

(defn fill-segment [seg]
  (->> (:data seg)
       (sort-by first)
       (partition 2 1)
       (mapcat (fn [[prev next]]
                 (concat [prev]
                         (map (fn [fld] [[fld 0 0 0] ""])
                              (range (inc (get-in prev [0 0]))
                                     (get-in next [0 0])))
                         [next])))
       (distinct)))



(defn head->tag [head]
  (let [tags (reverse [:fld :rep :cmp :sub])]
    (->> head
         (partition 4 4 [0 0 0 0])
         (first)
         (reverse)
         (map vector tags)
         (filter #(not= 0 (second %)))
         (map first)
         (first))))

(defn format-segment [seg opts]
  (let [id (:id seg)
        msh-1? (fn [head]
                 (and (= id "MSH")
                      (= head [1 0 0 0])))]
    (->> (fill-segment seg)
         (remove (comp msh-1? first))
         (mapcat (juxt (comp head->tag first) second))
         (map #(opts % %))
         (apply str)
         (str (:id seg)))))

(defn parse [x]
  (with-open [rdr (io/reader x)]
    (->> (tokenize rdr)
         (seg-tokens-seq)
         (mapv segment))))

(defn format [hl7 & {:keys [line-break] :or {line-break "\r\n"}}]
  (let [msh (some #(when (= (:id %) "MSH") (:data %)) hl7)
        fld (msh [1 0 0 0])
        [cmp rep _esc sub] (seq (msh [2 0 0 0]))
        opts (zipmap [:fld :rep :cmp :sub] [fld rep cmp sub])]
    (->> hl7
         (map #(format-segment % opts))
         (str/join line-break))))

(comment
  (spit "output/sample.hl7" (format (parse "test/data/sample.hl7"))))
