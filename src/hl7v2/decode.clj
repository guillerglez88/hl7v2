(ns hl7v2.decode
  (:import
   (java.io Reader))
  (:require [clojure.java.io :as io]))

(defn char-seq
  "Char sequence from rdr"
  [^Reader rdr]
  (let [ch (.read rdr)]
    (when-not (= ch -1)
      (cons (char ch) (lazy-seq (char-seq rdr))))))

(defn tokenize
  "Split content into tokens sequence based on encoding separators"
  [^Reader rdr]
  (let [[m s h fld cmp rep esc sub & rest] (char-seq rdr)
        seg (str m s h)
        encoding (str cmp rep esc sub)
        mark-escaped (fn [[fst snd]]
                       (cond
                         (= esc fst) {:val snd, :escaped true}
                         (= esc snd) nil
                         :else       {:val snd}))
        separator? (fn [item]
                     (when (and (#{fld cmp rep sub \return \newline} (:val item))
                                (not (:escaped item)))
                       (gensym)))]
    (->> (concat [nil] rest [nil])
         (partition-all 2 1)
         (map mark-escaped)
         (remove nil?)
         (partition-by separator?)
         (map #(apply str (map :val %)))
         (cons encoding)
         (cons (str fld))
         (cons seg))))

(defn chunks-by
  "Split tokens sequence into chunks based on predicate"
  [pred coll]
  (->> coll
       (partition-by #(when (pred %)
                        (gensym)))
       (partition-all 2 1)
       (map (fn [[fst snd]]
              (when (pred (first fst))
                (if-not (pred (first snd))
                  snd
                  (first fst)))))
       (remove nil?)
       (map #(if (pred %) nil %))))

(defn nest
  "Transform tokens sequence into nested structure each
  level corresponding to a separator in the same order"
  [separators offset coll]
  (if (seq separators)
    (let [{t :tag, s :val} (first separators)
          chunks (->> (cons s coll)
                      (chunks-by #{s})
                      (map #(when (seq? %)
                              (nest (next separators) 1 %))))]
      (cond
        (#{:fld :cmp :sub} t) (let [entries (->> chunks
                                                 (zipmap (drop offset (range)))
                                                 (remove #(nil? (second %))))]
                                (case (count entries)
                                  0 nil
                                  1 (second (first entries))
                                  (into {} entries)))
        (= :rep t) (let [entries (->> chunks
                                      (reverse)
                                      (drop-while nil?)
                                      (reverse))]
                     (if (= 1 (count entries))
                       (first entries)
                       (into [] entries)))
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
           (map (fn [[seg fld & data]]
                  {seg (if (= "MSH" seg)
                         (assoc (nest separators 2 data) 1 fld)
                         (nest separators 1 data))}))
           (into [])))))

(comment
  (time
   (parse (.getBytes (str "MSH|^~\\&|Regional MPI||Master MPI|Alpha Hospital|20060501140010||ADT^A28|3948375|P^T|2.4|||ER\r\n"
                          "EVN|A28|20060501140008|||000338475^Author^Arthur^^^^^^Regional MPI&2.16.840.1.113883.19.201&ISO^L|20060501140008\r\n"
                          "PID|||000197245^^^NationalPN&2.16.840.1.113883.19.3&ISO^PN~4532^^^Careful\\&CareClinic&2.16.840.1.113883.19.2.400566&ISO^PI~3242346^^^GoodmanGP&2.16.840.1.113883.19.2.450998&ISO^PI||Patient^Particia^^^^^L||19750103|F|||Randomroad 23a&Randomroad&23a^^Anytown^^1200^^H||555 3542557^ORN^PH~555 3542558^ORN^FX|555 5557865^WPN^PH\r\n"
                          "PV1||N|")))))
