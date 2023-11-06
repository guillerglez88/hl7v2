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

(defn read-until
  ([rdr pred esc]
   (reduce (fn [acc curr]
             (let [items (conj acc curr)]
               (if (and (not (esc (last acc)))
                        (pred curr))
                 (reduced items)
                 items))) [] (char-seq rdr)))
  ([rdr pred]
   (read-until rdr pred #{})))

(defn next-token [rdr delim-set esc-set enc->tag]
  (let [buffer (read-until rdr delim-set esc-set)
        str-val (fn [b]
                  (when (seq b)
                    (apply str b)))
        pair (if-let [tag (enc->tag (last buffer))]
               [(str-val (butlast buffer)) tag]
               [(str-val buffer)])]
    (seq (remove nil? pair))))

(defn tokenize [rdr]
  (let [msh (seg-id rdr)
        fld (next-char rdr)
        encoding (read-until rdr  #{fld})
        enc (apply str (butlast encoding))
        full-enc (concat encoding [\return \newline])
        enc-map (zipmap [:cmp :rep :esc :sub :fld :ret :nli] full-enc)
        enc->tag (map-invert enc-map)
        delim-set (set (vals (select-keys enc-map [:cmp :rep :sub :fld :ret :nli])))
        esc-set #{(:esc enc-map)}]
    (->> (repeatedly #(next-token rdr delim-set esc-set enc->tag))
         (take-while some?)
         (cons [msh fld :fld enc :fld])
         (apply concat))))

(defn pad-head [head]
  (first (partition 4 4 [0 0 0 0] head)))

(defn trim-head [head]
  (->> (reverse head)
       (drop-while (partial = 0))
       (reverse)
       (vec)))

(defn segment [tokens]
  (let [[id & tail] tokens]
    (loop [[h & t] tail
           head {:fld 1, :rep 0, :cmp 0, :sub 0}
           data {}]
      (cond
        (= h :fld) (recur t (merge head {:fld (inc (:fld head)), :rep 0, :cmp 0, :sub 0}) data)
        (= h :rep) (recur t (merge head {:rep (inc (:rep head)), :cmp 0, :sub 0}) data)
        (= h :cmp) (recur t (merge head {:cmp (inc (:cmp head)), :sub 0}) data)
        (= h :sub) (recur t (update head h inc) data)
        (some? h) (recur t head (assoc data (trim-head (mapv head [:fld :rep :cmp :sub])) h))
        :else [id data]))))

(defn fill-segment [seg]
  (let [[id data] seg]
    (->> (map (juxt (comp vec pad-head first) second) data)
         (cons [[1 0 0 0] ""])
         (sort-by first)
         (partition 2 1)
         (mapcat (fn [[prev next]]
                   (concat [prev]
                           (map (fn [fld] [[fld 0 0 0] ""])
                                (range (inc (get-in prev [0 0]))
                                       (get-in next [0 0])))
                           [next])))
         (distinct)
         (into {})
         (vector id))))

(defn head->tag [head]
  (let [tags (reverse [:fld :rep :cmp :sub])]
    (->> (reverse head)
         (map vector tags)
         (filter #(not= 0 (second %)))
         (map first)
         (first))))

(defn format-segment [seg opts]
  (let [[id data] seg
        msh-1? (fn [head]
                 (and (= id "MSH")
                      (= head [1 0 0 0])))]
    (->> (sort-by first data)
         (remove (comp msh-1? first))
         (mapcat (juxt (comp head->tag first) second))
         (map #(opts % %))
         (apply str)
         (str id))))

(defn parse [x]
  (with-open [rdr (io/reader x)]
    (->> (tokenize rdr)
         (partition-by #{:ret :nli})
         (remove #{'(:ret) '(:nli) '("")})
         (mapv segment))))

(defn format [hl7 & {:keys [line-break] :or {line-break "\r\n"}}]
  (let [segments (mapv fill-segment hl7)
        msh (some #(when (= (first %) "MSH") (second %)) segments)
        fld (msh [1 0 0 0])
        [cmp rep _esc sub] (seq (msh [2 0 0 0]))
        opts (zipmap [:fld :rep :cmp :sub] [fld rep cmp sub])]
    (->> segments
         (map #(format-segment % opts))
         (str/join line-break))))
