(ns hl7v2.core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.zip :as zip])
  (:refer-clojure :exclude [format])
  (:import
   (java.io Reader)))

(defn tokenize
  "Split content into tokens sequence based on encoding separators"
  [^Reader rdr]
  (letfn [(char-seq [^Reader rdr]
            (let [ch (.read rdr)]
              (when-not (= ch -1)
                (cons (char ch) (lazy-seq (char-seq rdr))))))
          (escape [pred coll]
            (let [[fst snd & rest] coll]
              (when fst
                (if (pred fst)
                  (cons (str fst snd) (lazy-seq (escape pred rest)))
                  (cons (str fst) (lazy-seq (escape pred (cons snd rest))))))))]
    (let [[m s h fld cmp rep esc sub & rest] (char-seq rdr)
          seg (str m s h)
          encoding (str cmp rep esc sub)
          separators (->> [fld cmp rep sub \return \newline]
                          (map str)
                          (set))]
      (concat [seg (str fld) encoding]
              (->> rest
                   (escape #{esc})
                   (partition-by #(when (separators %)
                                    (gensym)))
                   (map (partial apply str)))))))

(defn parse
  "Parse a HL7v2 message."
  [x]
  (with-open [rdr (io/reader x)]
    (->> (tokenize rdr)
         (partition-by #{"\r" "\n"})
         (remove #{'("\r") '("\n")})
         (map (fn [[seg & data]]
                [seg (vec data)]))
         (into [])
         #_(mapv segment))))

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
  (time
   (parse (.getBytes (str "MSH|^~\\&|Regional MPI||Master MPI|Alpha Hospital|20060501140010||ADT^A28|3948375|P^T|2.4|||ER\r\n"
                          "EVN|A28|20060501140008|||000338475^Author^Arthur^^^^^^Regional MPI&2.16.840.1.113883.19.201&ISO^L|20060501140008\r\n"
                          "PID|||000197245^^^NationalPN&2.16.840.1.113883.19.3&ISO^PN~4532^^^Careful\\&CareClinic&2.16.840.1.113883.19.2.400566&ISO^PI~3242346^^^GoodmanGP&2.16.840.1.113883.19.2.450998&ISO^PI||Patient^Particia^^^^^L||19750103|F|||Randomroad 23a&Randomroad&23a^^Anytown^^1200^^H||555 3542557^ORN^PH~555 3542558^ORN^FX|555 5557865^WPN^PH\r\n"
                          "PV1||N|")))))
