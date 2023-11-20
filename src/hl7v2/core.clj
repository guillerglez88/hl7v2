(ns hl7v2.core
  (:require
   [clojure.java.io :as io]
   [clojure.set :refer [map-invert]]
   [clojure.string :as str])
  (:refer-clojure :exclude [format])
  (:import
   (java.io Reader)))

(defn str-seq [coll]
  (when (seq coll)
    (apply str coll)))

(defn char-seq [^Reader rdr]
  (let [ch (.read rdr)]
    (when-not (= ch -1)
      (cons (char ch) (lazy-seq (char-seq rdr))))))

(defn read-until [pred escape char-seq]
  (loop [buffer []
         esc? false
         [curr & rest] char-seq]
    (cond
      (nil? curr) [buffer rest]
      (and (not esc?) (pred curr)) [(conj buffer curr) rest]
      :else (recur (conj buffer curr) (escape curr) rest))))

(defn read-nstr [n char-seq]
  [(str-seq (take n char-seq))
   (drop n char-seq)])

(defn next-token
  [delimiters escape tag-map char-seq]
  (let [[buffer rest] (read-until delimiters escape char-seq)]
    [(seq (remove nil? (if-let [tag (get tag-map (last buffer))]
                         [(str-seq (butlast buffer)) tag]
                         [(str-seq buffer)]))) rest]))

(defn tokenize [cs]
  (let [[msh [fld & cs]] (read-nstr 3 cs)
        [[cmp rep esc sub] cs] (read-until #{fld \return \newline} #{} cs)
        enc-map {:fld fld
                 :cmp cmp
                 :rep rep
                 :esc esc
                 :sub sub
                 :ret \return
                 :nli \newline}
        tag-map (map-invert enc-map)
        delimiters (set (vals (dissoc enc-map :esc)))
        escape #{(:esc enc-map)}]
    (loop [tokens [msh :fld fld :fld (str-seq [cmp rep esc sub]) :fld]
           cs cs]
      (let [[token cs] (next-token delimiters escape tag-map cs)]
        (if (seq token)
          (recur (concat tokens token) cs)
          tokens)))))

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

(defn parse
  "Parse a HL7v2 message."
  [x]
  (with-open [rdr (io/reader x)]
    (->> (char-seq rdr)
         (tokenize)
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
