(ns hl7v2.core
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io])
  (:refer-clojure :exclude [format])
  (:import
   (java.io Reader)))

(defn- char-seq [^Reader rdr]
  (let [ch (.read rdr)]
    (when-not (= ch -1)
      (cons (char ch) (lazy-seq (char-seq rdr))))))

(defn- tokenize [^Reader rdr]
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

(defn- chunks-by [pred coll]
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

(defn- nest [separators offset coll]
  (if (seq separators)
    (let [{t :tag, s :val} (first separators)
          chunks (->> (cons s coll)
                      (chunks-by #{s})
                      (map #(when (seq? %)
                              (nest (next separators) 1 %))))]
      (cond
        (= :fld t) (->> chunks
                        (zipmap (drop offset (range)))
                        (remove (comp nil? second))
                        (into {}))
        (#{:cmp :sub} t) (let [entries (->> chunks
                                            (zipmap (drop offset (range)))
                                            (remove (comp nil? second)))]
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

(defn parse [x]
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
                  {(keyword seg) (if (= "MSH" seg)
                                   (assoc (nest separators 2 data)
                                          1 fld)
                                   (nest separators 1 data))}))
           (into [])))))

(defn format [hl7 & {:keys [line-break] :or {line-break "\r\n"}}]
  (letfn [(encode [data separators]
            (let [{t :tag, s :val} (first separators)]
              (cond
                (vector? data) (->> data
                                    (map #(encode % (next separators)))
                                    (str/join s))
                (map? data) (if (= :rep t)
                              (encode data (next separators))
                              (let [last (->> (keys data) (apply max))]
                                (->> (range 1 (inc last))
                                     (map #(encode (get data %) (next separators)))
                                     (str/join s))))
                :else data)))]
    (let [msh (some :MSH hl7)
          fld (get msh 1)
          [cmp rep _esc sub] (seq (get msh 2))
          separators (map (fn [t s] {:tag t, :val (str s)})
                          [:fld :rep :cmp :sub]
                          [fld rep cmp sub])]
      (str/join line-break
                (for [seg hl7
                      :let [[id data] (first seg)]]
                  (if (= :MSH id)
                    (str (name id)
                         (encode (dissoc data 1) separators))
                    (str (name id)
                         fld
                         (encode data separators))))))))

(comment
  (format [{:MSH {1 "|", 2 "^~\\&"}}
           {:PID {3 [{1 "000197245",
                      4 {1 "NationalPN", 2 "2.16.840.1.113883.19.3", 3 "ISO"},
                      5 "PN"}
                     {1 "4532",
                      4 {1 "Careful&CareClinic", 2 "2.16.840.1.113883.19.2.400566", 3 "ISO"},
                      5 "PI"}
                     {1 "3242346",
                      4 {1 "GoodmanGP", 2 "2.16.840.1.113883.19.2.450998", 3 "ISO"},
                      5 "PI"}],
                  5 {1 "Patient", 2 "Particia", 7 "L"},
                  7 "19750103",
                  8 "F"
                  11 {1 {1 "Randomroad 23a", 2 "Randomroad", 3 "23a"},
                      3 "Anytown",
                      5 "1200",
                      7 "H"},
                  13 [{1 "555 3542557", 2 "ORN", 3 "PH"}
                      {1 "555 3542558", 2 "ORN", 3 "FX"}],
                  14 {1 "555 5557865", 2 "WPN", 3 "PH"}}}])

  (format [{:MSH {2 "^~\\&", 1 "|"}}
           {:PID {3 "123456", 5 {1 "Doe", 2 "John"}}}]))
