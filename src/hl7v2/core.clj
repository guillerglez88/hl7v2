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

(defn read-until [rdr pred esc]
  (loop [buffer []
         prev nil
         [curr] (char-seq rdr)]
    (cond
      (nil? curr) buffer
      (and (not (esc prev)) (pred curr)) (conj buffer curr)
      :else (recur (conj buffer curr) curr (char-seq rdr)))))

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
  (let [msh (apply str (take 3 (char-seq rdr)))
        fld (first (char-seq rdr))
        enc (take 4 (char-seq rdr))
        full-enc (concat [fld] enc [\return \newline])
        enc-map (zipmap [:fld :cmp :rep :esc :sub :ret :nli] full-enc)
        enc->tag (map-invert enc-map)
        delim-set (set (vals (dissoc enc-map :esc)))
        esc-set #{(:esc enc-map)}
        _ (read-until rdr delim-set esc-set)]
    (->> (repeatedly #(next-token rdr delim-set esc-set enc->tag))
         (take-while some?)
         (cons [msh :fld fld :fld (apply str enc) :fld])
         (apply concat))))

(defn trim-head [head]
  (->> (reverse head)
       (drop-while (partial = 0))
       (reverse)
       (vec)))

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
        (some? h) (recur t head (assoc data (trim-head (mapv head [:fld :rep :cmp :sub])) h))
        :else [id data]))))

(defn parse [x]
  (with-open [rdr (io/reader x)]
    (->> (tokenize rdr)
         (partition-by #{:ret :nli})
         (remove #{'(:ret) '(:nli)})
         (mapv segment))))

(defn head->tag [head]
  (let [tags (reverse [:fld :rep :cmp :sub])]
    (->> (reverse head)
         (map vector tags)
         (filter #(not= 0 (second %)))
         (map first)
         (first))))

(defn fill-segment [seg]
  (let [[id data] seg
        pad-head (fn [head]
                   (first (partition 4 4 [0 0 0 0] head)))]
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

(defn format [hl7 & {:keys [line-break] :or {line-break "\r\n"}}]
  (let [segments (mapv fill-segment hl7)
        msh (some #(when (= (first %) "MSH") (second %)) segments)
        fld (msh [1 0 0 0])
        [cmp rep _esc sub] (seq (msh [2 0 0 0]))
        opts (zipmap [:fld :rep :cmp :sub] [fld rep cmp sub])]
    (->> segments
         (map #(format-segment % opts))
         (str/join line-break))))

(comment
  (parse (.getBytes (str "MSH|^~\\&\r\n"
                         "PID|||7005728^^^TML^MR"))))
