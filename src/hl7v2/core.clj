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

(defn encoding [^Reader rdr]
  (let [cseq (char-seq rdr)
        [m s h fld cmp rep esc sub] cseq
        [ret nli] [\return \newline]]
    (when-not (= [\M \S \H] [m s h])
      (throw (ex-info "Unexpected segment"
                      {:expected "MSH"
                       :actual (str m s h)})))
    {:segment [ret nli]
     :field fld
     :component cmp
     :repetition rep
     :subcomponent sub
     :escape esc}))

(defn tokenize [^Reader rdr encoding]
  (letfn [(escape [esc start cseq]
            (let [[curr next & rest] cseq]
              (when curr
                (if (and (= esc curr)
                         (<= start 0))
                  (cons (str curr next)
                        (lazy-seq (escape esc (dec start) rest)))
                  (cons curr
                        (lazy-seq (escape esc (dec start) (cons next rest))))))))
          (tokens
            ([encoding cseq]
             (let [separators (->> (seq encoding)
                                   (mapcat (fn [[k v]]
                                             (if (vector? v)
                                               (map vector v (repeat k))
                                               [[v k]])))
                                   (into {}))]
               (tokens separators [1 0] cseq)))
            ([separators [line column :as from] cseq]
             (when cseq
               (let [token (take-while (comp not separators) cseq)
                     move-count (count token)
                     [separator & rest] (drop move-count cseq)
                     str-token (apply str token)
                     kind (separators separator)
                     to [line (+ column (count str-token))]
                     double-segment? (= :segment kind (separators (first rest)))
                     sep-token (if double-segment?
                                 {:value (str separator (first rest))
                                  :kind kind
                                  :location {:from to :to [line (+ 2 (second to))]}}
                                 {:value (str separator)
                                  :kind kind
                                  :location {:from to :to [line (inc (second to))]}})
                     val-token {:value str-token
                                :kind :data
                                :location {:from from :to to}}
                     head (if (= :segment kind)
                            [(inc line) 0]
                            (get-in sep-token [:location :to]))
                     cseq (if double-segment?
                            (drop 1 rest)
                            rest)]
                 (->> (lazy-seq (tokens separators head cseq))
                      (cons sep-token)
                      (cons (when-not (= from to)
                              val-token))
                      (remove nil?))))))
          (merge-encoding [tokens]
            (let [[seg fld & tseq] tokens
                  merge-tokens (fn [t1 t2]
                                 {:value (str (:value t1) (:value t2))
                                  :kind :data
                                  :location {:from (get-in t1 [:location :from])
                                             :to (get-in t2 [:location :to])}})]
              (when-not (= "MSH" (:value seg))
                (throw (ex-info "Unexpected token" seg)))
              (let [encoding (take-while (comp not #{:field} :kind) tseq)]
                (cons seg
                      (cons fld
                            (cons (reduce merge-tokens encoding)
                                  (drop (count encoding) tseq)))))))]
    (->> (char-seq rdr)
         (escape (:escape encoding) 8)
         (tokens encoding)
         (merge-encoding))))

(defn parse [x & {:keys [val-fn] :or {val-fn :value}}]
  (with-open [rdr (io/reader x)]
    (letfn [(nest [sep [h & t :as tokens]]
              (let [next-level (fn [sep offset tokens]
                                 (let [data (->> tokens
                                                 (partition-by (comp #{sep} :kind))
                                                 (mapcat (fn [p]
                                                           (if (= #{sep} (set (map :kind p)))
                                                             (repeat (dec (count p)) (list))
                                                             [p])))
                                                 (map (partial nest sep))
                                                 (concat (repeat offset nil))
                                                 (into []))]
                                   (if (= (inc offset) (count data))
                                     (->> data
                                          (drop offset)
                                          (map (fn [d]
                                                 (if (map? d)
                                                   (or (->> d (into {}) val-fn) d)
                                                   d)))
                                          (first))
                                     (->> data
                                          (map vector (range))
                                          (remove (comp nil? second))
                                          (into (sorted-map))))))]
                (when (seq tokens)
                  (case sep
                    :message (vals (next-level :segment 0 tokens))
                    :segment (let [seg (keyword (:value h))]
                               (if (= :MSH seg)
                                 (let [data (next-level :field 2 (rest t))]
                                   (if (map? data)
                                     {seg (assoc data 1 (val-fn (first t)))}
                                     {seg {1 (str (val-fn (first t)))
                                           2 data}}))
                                 {seg (next-level :field 1 t)}))
                    :field (next-level :repetition 0 tokens)
                    :repetition (next-level :component 1 tokens)
                    :component (next-level :subcomponent 1 tokens)
                    :subcomponent (val-fn h)))))]

      (.mark rdr 32)
      (let [encoding (encoding rdr)]
        (.reset rdr)
        (->> (tokenize rdr encoding)
             (nest :message)
             (into []))))))

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
