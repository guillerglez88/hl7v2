(ns hl7v2.decode
  (:import
   (java.io Reader))
  (:require [clojure.java.io :as io]))

(defn char-seq [^Reader rdr]
  "Char sequence from rdr"
  (let [ch (.read rdr)]
    (when-not (= ch -1)
      (cons (char ch) (lazy-seq (char-seq rdr))))))

(defn tokenize
  "Split content into tokens sequence based on encoding separators"
  [^Reader rdr]
  (let [[m s h fld cmp rep esc sub & rest] (char-seq rdr)
        seg (str m s h)
        encoding (str cmp rep esc sub)
        separators #{fld cmp rep sub \return \newline}]
    (->> (concat [nil] rest [nil])
         (partition-all 2 1)
         (map (fn [[fst snd]]
                (cond
                  (= esc fst) {:val snd, :escaped true}
                  (= esc snd) nil
                  :else       {:val snd})))
         (remove nil?)
         (partition-by (fn [item]
                         (when (and (separators (:val item))
                                    (not (:escaped item)))
                           (gensym))))
         (map #(apply str (map :val %)))
         (cons encoding)
         (cons (str fld))
         (cons seg))))

(defn chunks-by [pred coll]
  (->> coll
       (partition-by #(when (pred %)
                        (gensym)))
       (partition-all 2 1)
       (map (fn [[fst snd]]
              (when (and (pred (first fst))
                         (not (pred (first snd))))
                snd)))))

(defn nest [separators coll]
  (if (seq separators)
    (let [{t :tag, s :val} (first separators)
          chunks (->> (cons s coll)
                      (chunks-by #{s})
                      (map #(when (seq? %)
                              (nest (next separators) %))))]
      (cond
        (#{:fld :cmp :sub} t) (->> chunks
                                   (zipmap (map inc (range)))
                                   (remove #(nil? (second %)))
                                   (into {}))
        (= :rep t) (into [] chunks)
        :else chunks))
    (apply str coll)))

(defn parse
  "Parse a HL7v2 message."
  [x]
  (with-open [rdr (io/reader x)]
    (let [tokens (tokenize rdr)
          [seg fld [cmp rep esc sub]] tokens
          separators (map (fn [t s] {:tag t, :val (str s)})
                          [:fld :rep :cmp :sub]
                          [fld rep cmp sub])]
      (when (not= "MSH" seg)
        (throw (ex-info "Message should start with MSH" {:seg seg})))
      (->> tokens
           (partition-by #{"\r" "\n"})
           (remove #{'("\r") '("\n")})
           (map (fn [[seg _fld & data]]
                  (nest separators data)))
           (into [])))))

(comment
  (time
   (parse (.getBytes (str "MSH|^~\\&|Regional MPI||Master MPI|Alpha Hospital|20060501140010||ADT^A28|3948375|P^T|2.4|||ER\r\n"
                          "EVN|A28|20060501140008|||000338475^Author^Arthur^^^^^^Regional MPI&2.16.840.1.113883.19.201&ISO^L|20060501140008\r\n"
                          "PID|||000197245^^^NationalPN&2.16.840.1.113883.19.3&ISO^PN~4532^^^Careful\\&CareClinic&2.16.840.1.113883.19.2.400566&ISO^PI~3242346^^^GoodmanGP&2.16.840.1.113883.19.2.450998&ISO^PI||Patient^Particia^^^^^L||19750103|F|||Randomroad 23a&Randomroad&23a^^Anytown^^1200^^H||555 3542557^ORN^PH~555 3542558^ORN^FX|555 5557865^WPN^PH\r\n"
                          "PV1||N|")))))
