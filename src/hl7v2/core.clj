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

(defn encoding
  ([input]
   (letfn [(from-reader [^Reader rx]
             (let [cseq (char-seq rx)
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
           (from-msh [msh]
             (with-open [rx (io/reader
                             (.getBytes
                              (str "MSH"
                                   (msh 1)
                                   (msh 2)
                                   (msh 1))))]
               (encoding rx)))]
     (cond
       (instance? Reader input) (from-reader input)
       (vector? input) (from-msh (some :MSH input))
       (map? input) (from-msh (:MSH input))
       (string? input) (with-open [rx (io/reader (.getBytes input))]
                         (from-reader rx))
       :else (throw (ex-info "Unknown input type" (type input)))))))

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
    (letfn [(seq-data->map [offset val-fn data]
              (if (= (inc offset) (count data))
                (->> data
                     (drop offset)
                     (map (fn [d]
                            (if (map? d)
                              (let [{k :kind :as d} (into {} d)]
                                (if k
                                  (or (val-fn d) d)
                                  d))
                              d)))
                     (first))
                (->> data
                     (map vector (range))
                     (remove (comp nil? second))
                     (into (sorted-map)))))
            (dive [sep offset nest-fn tokens]
              (->> tokens
                   (partition-by (comp #{sep} :kind))
                   (mapcat (fn [p]
                             (if (= #{sep} (set (map :kind p)))
                               (repeat (dec (count p)) (list))
                               [p])))
                   (map (partial nest-fn sep))
                   (concat (repeat offset nil))
                   (seq-data->map offset val-fn)))
            (nest [sep [h & t :as tokens]]
              (when (seq tokens)
                (case sep
                  :message (vals (dive :segment 0 nest tokens))
                  :segment (let [seg (keyword (:value h))]
                             (if (= :MSH seg)
                               (let [data (dive :field 2 nest (rest t))]
                                 (if (map? data)
                                   {seg (assoc data 1 (val-fn (first t)))}
                                   {seg {1 (str (val-fn (first t)))
                                         2 data}}))
                               {seg (dive :field 1 nest t)}))
                  :field (dive :repetition 0 nest tokens)
                  :repetition (dive :component 1 nest tokens)
                  :component (dive :subcomponent 1 nest tokens)
                  :subcomponent (val-fn h))))]
      (.mark rdr 32)
      (let [encoding (encoding rdr)]
        (.reset rdr)
        (->> (tokenize rdr encoding)
             (nest :message)
             (into []))))))

(defn format [hl7 & {:keys [line-break] :or {line-break "\r\n"}}]
  (letfn [(encode [data separators]
            (let [[t s] (first separators)]
              (cond
                (vector? data) (->> data
                                    (map #(encode % (next separators)))
                                    (str/join s))
                (map? data) (if (= :repetition t)
                              (encode data (next separators))
                              (let [last (->> (keys data) (apply max))]
                                (->> (range 1 (inc last))
                                     (map #(encode (get data %) (next separators)))
                                     (str/join s))))
                :else data)))]
    (let [encoding (encoding hl7)
          separators (->> [:field :repetition :component :subcomponent]
                          (map (fn [sep]
                                 [sep (encoding sep)])))]
      (str/join line-break
                (for [seg hl7
                      :let [[id data] (first seg)]]
                  (if (= :MSH id)
                    (str (name id)
                         (encode (dissoc data 1) separators))
                    (str (name id)
                         (:field encoding)
                         (encode data separators))))))))

(defn structure [trigger-event hl7]
  (letfn [(extract [[h & t]]
            (if (map? (first t))
              (let [[attrs & children] t]
                {:tag h
                 :attrs attrs
                 :group? (seq children)
                 :children children})
              {:tag h
               :group? (seq t)
               :children t}))
          (match [[item & items] [seg & segments]]
            (if (and item seg)
              (let [{:keys [tag attrs group? children]} (extract item)]
                (if group?
                  (let [[data segs] (reduce (fn [acc curr]
                                              (let [[data segs] (match [curr] (second acc))]
                                                [(concat (first acc) data) segs]))
                                            [nil (cons seg segments)]
                                            children)]
                    (if (seq data)
                      [[{tag data}] segs]
                      [nil segs]))
                  (if (get seg tag)
                    [[seg] segments]
                    (match items (cons seg segments)))))
              (when-not item
                [nil (cons seg segments)])))]
    (ffirst (match [trigger-event] hl7))))

(comment
  (let [str-msg (str "MSH|^~\\&|ULTRA|TML|OLIS|OLIS|200905011130||ORU^R01|20169838-v25|T|2.5\r\n"
                     "PID|||7005728^^^TML^MR||TEST^RACHEL^DIAMOND||19310313|F|||200 ANYWHERE ST^^TORONTO^ON^M6G 2T9||(416)888-8888||||||1014071185^KR\r\n"
                     "PV1|1||OLIS||||OLIST^BLAKE^DONALD^THOR^^^^^921379^^^^OLIST\r\n"
                     "ORC|RE||T09-100442-RET-0^^OLIS_Site_ID^ISO|||||||||OLIST^BLAKE^DONALD^THOR^^^^L^921379\r\n"
                     "OBR|0||T09-100442-RET-0^^OLIS_Site_ID^ISO|RET^RETICULOCYTE COUNT^HL79901 literal|||200905011106|||||||200905011106||OLIST^BLAKE^DONALD^THOR^^^^L^921379||7870279|7870279|T09-100442|MOHLTC|200905011130||B7|F||1^^^200905011106^^R\r\n"
                     "OBX|1|ST|||Test Value")
        hl7 (parse (.getBytes str-msg))]
    (structure [:ORU_R01 {:version "2.5.1"}
                [:MSH]
                [:SFT]
                [:PATIENT-RESULTS
                 [:PATIENT
                  [:PID]
                  [:PD1]
                  [:NTE]
                  [:NK1]
                  [:VISIT
                   [:PV1]
                   [:PV2]]]
                 [:ORDER-OBSERVATION
                  [:ORC]
                  [:OBR]
                  [:NTE]
                  [:TIMING-QTY
                   [:TQ1]
                   [:TQ2]]
                  [:CTD]
                  [:OBSERVATION
                   [:OBX]
                   [:NTE]]
                  [:FT1]
                  [:CTI]
                  [:SPECIMEN
                   [:SPM]
                   [:OBX]]]]
                [:DSC]] hl7))
  ;; => {:ORU_R01
  ;;     ({:MSH
  ;;       {1 "|",
  ;;        2 "^~\\&",
  ;;        3 "ULTRA",
  ;;        4 "TML",
  ;;        5 "OLIS",
  ;;        6 "OLIS",
  ;;        7 "200905011130",
  ;;        9 {1 "ORU", 2 "R01"},
  ;;        10 "20169838-v25",
  ;;        11 "T",
  ;;        12 "2.5"}}
  ;;      {:PATIENT-RESULTS
  ;;       ({:PATIENT
  ;;         ({:PID
  ;;           {3 {1 "7005728", 4 "TML", 5 "MR"},
  ;;            5 {1 "TEST", 2 "RACHEL", 3 "DIAMOND"},
  ;;            7 "19310313",
  ;;            8 "F",
  ;;            11 {1 "200 ANYWHERE ST", 3 "TORONTO", 4 "ON", 5 "M6G 2T9"},
  ;;            13 "(416)888-8888",
  ;;            19 {1 "1014071185", 2 "KR"}}}
  ;;          {:VISIT
  ;;           ({:PV1
  ;;             {1 "1",
  ;;              3 "OLIS",
  ;;              7
  ;;              {1 "OLIST",
  ;;               2 "BLAKE",
  ;;               3 "DONALD",
  ;;               4 "THOR",
  ;;               9 "921379",
  ;;               13 "OLIST"}}})})}
  ;;        {:ORDER-OBSERVATION
  ;;         ({:ORC
  ;;           {1 "RE",
  ;;            3 {1 "T09-100442-RET-0", 3 "OLIS_Site_ID", 4 "ISO"},
  ;;            12 {1 "OLIST", 2 "BLAKE", 3 "DONALD", 4 "THOR", 8 "L", 9 "921379"}}}
  ;;          {:OBR
  ;;           {1 "0",
  ;;            3 {1 "T09-100442-RET-0", 3 "OLIS_Site_ID", 4 "ISO"},
  ;;            4 {1 "RET", 2 "RETICULOCYTE COUNT", 3 "HL79901 literal"},
  ;;            7 "200905011106",
  ;;            14 "200905011106",
  ;;            16 {1 "OLIST", 2 "BLAKE", 3 "DONALD", 4 "THOR", 8 "L", 9 "921379"},
  ;;            18 "7870279",
  ;;            19 "7870279",
  ;;            20 "T09-100442",
  ;;            21 "MOHLTC",
  ;;            22 "200905011130",
  ;;            24 "B7",
  ;;            25 "F",
  ;;            27 {1 "1", 4 "200905011106", 6 "R"}}}
  ;;          {:OBSERVATION ({:OBX {1 "1", 2 "ST", 5 "Test Value"}})})})})}

  :.)
