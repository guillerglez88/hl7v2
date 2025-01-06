(ns hl7v2.er7
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hl7v2.complex :refer [clean]]
   [clojure.zip :as zip])
  (:import
   (java.io Reader)))

(defn char-seq [^Reader rx]
  (let [ch (.read rx)]
    (when-not (= ch -1)
      (cons (char ch) (lazy-seq (char-seq rx))))))

(defn parse-encoding [cseq]
  (let [[m s h fld cmp rep esc sub & more] cseq
        [ret nli] [\return \newline]]
    (when-not (= [\M \S \H] [m s h])
      (throw (ex-info "Unexpected segment"
                      {:expected "MSH"
                       :actual (str m s h)})))
    (let [kind [:fld :cmp :rep :sub :esc :seg :seg]
          sep [fld cmp rep sub esc ret nli]]
      {:separators #{fld cmp rep sub esc ret nli}
       :index {:ks (zipmap kind sep)
               :sk (zipmap sep kind)}
       :remaining (into [] (concat (take-while (partial not= fld) more) [fld]))})))

(defn format-encoding [m]
  (apply str
         "MSH|"
         (get-in m [:index :ks :cmp])
         (get-in m [:index :ks :rep])
         (get-in m [:index :ks :esc])
         (get-in m [:index :ks :sub])
         (:remaining m)))

(defn escape [esc cseq]
  (let [[curr next & more] cseq]
    (when curr
      (if (= esc curr)
        (cons (str curr next)
              (lazy-seq (escape esc more)))
        (cons curr
              (lazy-seq (escape esc (cons next more))))))))

(defn tokens
  ([encoding cseq]
   (tokens encoding [1 0] cseq))
  ([encoding [line col :as from] cseq]
   (let [sep? (if (= from [1 4])
                #{(get-in encoding [:index :ks :fld])}
                (:separators encoding))]
     (when cseq
       (let [token (take-while (complement sep?) cseq)
             move-count (count token)
             [sep next & more] (drop move-count cseq)
             value (apply str token)
             kind (get-in encoding [:index :sk sep])
             to [line (+ col (count value))]
             seg-x2? (= :seg kind (get-in encoding [:index :sk next]))
             sep-to [line (+ (second to) (if seg-x2? 2 1))]]
         (concat
          [{:value value, :kind :data, :loc {:from from :to to}}
           {:value (if seg-x2? (str sep next) (str sep)), :kind kind, :loc {:from to :to sep-to}}]
          (lazy-seq
           (tokens encoding
                   (if (= :seg kind) [(inc line) 0] sep-to)
                   (if (and (not seg-x2?) next) (cons next more) more)))))))))

(defn parse-er7 [x & {:keys [val-fn] :or {val-fn :value}}]
  (letfn [(kind-groups [kind seg-id tokens]
            (->> (partition-by (comp #{kind} :kind) tokens)
                 (map-indexed vector)
                 (filter (fn [[idx tokens]]
                           (or (and (= :MSH seg-id)
                                    (= 0 idx))
                               (> (count tokens) 1)
                               (not= kind (:kind (first tokens))))))
                 (map second)
                 (map-indexed vector)))]
    (with-open [rx (io/reader x)]
      (let [cseq (char-seq rx)
            enc (parse-encoding cseq)
            esc (get-in enc [:index :ks :esc])
            tokens (->> cseq (escape esc) (tokens enc))]
        (->> (for [segment (partition-by (comp #{:seg} :kind) tokens)
                   :when (not= :seg (:kind (first segment)))
                   :let [[seg-id-token & data] segment
                         seg-id (keyword (:value seg-id-token))]]
               {seg-id
                (into
                 (sorted-map)
                 (for [[idx field] (kind-groups :fld seg-id data)
                       :let [fld-idx (inc idx)]]
                   [fld-idx
                    (if (= 1 (count field))
                      (val-fn (first field))
                      (into
                       (sorted-map)
                       (for [[idx repetition] (kind-groups :rep seg-id field)
                             :let [rep-idx (inc idx)]]
                         [rep-idx
                          (into
                           (sorted-map)
                           (for [[idx component] (kind-groups :cmp seg-id repetition)
                                 :let [cmp-idx (inc idx)]]
                             [cmp-idx
                              (if (= 1 (count component))
                                (val-fn (first component))
                                (into
                                 (sorted-map)
                                 (for [[idx subcomponent] (kind-groups :sub seg-id component)
                                       :let [sub-idx (inc idx)]]
                                   [sub-idx (val-fn (first subcomponent))])))]))])))]))})
             (into [])
             (clean))))))

(defn format-er7 [msg & {:keys [line-break] :or {line-break "\n"}}]
  (let [enc (parse-encoding (str "MSH"
                                 (get-in msg [0 :MSH 1])
                                 (get-in msg [0 :MSH 2])))]
    (str/join
     line-break
     (for [seg msg
           [seg-id data] seg]
       (str (name seg-id)
            (get-in enc [:index :ks :fld])
            (str/join
             (get-in enc [:index :ks :fld])
             (for [idx (range (if (= :MSH seg-id) 2 1)
                              (inc (apply max (keys data))))
                   :let [fld (get data idx)]]
               (if-not (map? fld)
                 fld
                 (str/join
                  (get-in enc [:index :ks :rep])
                  (for [idx (range 1 (inc (apply max (keys fld))))
                        :let [rep (get fld idx)]]
                    (if-not (map? rep)
                      rep
                      (str/join
                       (get-in enc [:index :ks :cmp])
                       (for [idx (range 1 (inc (apply max (keys rep))))
                             :let [cmp (get rep idx)]]
                         (if-not (map? cmp)
                           cmp
                           (str/join
                            (get-in enc [:index :ks :sub])
                            (for [idx (range 1 (inc (apply max (keys cmp))))]
                              (get cmp idx)))))))))))))))))

(defn er7-zip [er7]
  (letfn [(root? [n]
            (and (vector? n)
                 (map? (first n))
                 (keyword? (ffirst (first n)))))
          (seg? [n]
            (and (vector? n)
                 (keyword? (first n))))
          (complex? [n]
            (and (vector? n)
                 (number? (first n))
                 (map? (second n))))]
    (zip/zipper (some-fn root? seg? complex?)
                (fn [n]
                  (cond
                    (root? n) (apply concat n)
                    (seg? n) (sort-by first < (second n))
                    (complex? n) (sort-by first < (second n))))
                nil
                er7)))

(comment

  (with-open [rx (io/reader (.getBytes "MSH|^~\\&|ULTRA|TML|OLIS"))]
    (parse-encoding (char-seq rx)))
  ;;=> {:separators #{\& \newline \return \\ \| \^},
  ;;    :index
  ;;    {:ks {:fld \|, :cmp \^, :rep \~, :sub \&, :esc \\, :seg \newline},
  ;;     :sk {\| :fld, \^ :cmp, \~ :rep, \& :sub, \\ :esc, \return :seg, \newline :seg}},
  ;;    :remaining [\|]}

  (format-encoding {:separators #{\& \newline \return \\ \| \^},
                    :index
                    {:ks {:fld \|, :cmp \^, :rep \~, :sub \&, :esc \\, :seg \newline},
                     :sk {\| :fld, \^ :cmp, \~ :rep, \& :sub, \\ :esc, \return :seg, \newline :seg}},
                    :remaining [\|]})
  ;;=> "MSH|^~\\&|"

  (with-open [rx (io/reader (.getBytes "testing\\&escape\\^chars"))]
    (into [] (escape \\ (char-seq rx))))
  ;;=> [\t \e \s \t \i \n \g "\\&" \e \s \c \a \p \e "\\^" \c \h \a \r \s]

  (with-open [rx (io/reader (io/file "test/hl7v2/data/oru-r01.hl7"))]
    (time
     (into []
           (tokens {:separators #{\& \newline \return \\ \| \^},
                    :index
                    {:ks {:fld \|, :cmp \^, :rep \~, :esc \\, :seg \newline},
                     :sk {\| :fld, \^ :cmp, \~ :rep, \\ :esc, \return :seg, \newline :seg}},
                    :remaining [\|]}
                   (char-seq rx)))))

  (let [f (io/file "test/hl7v2/data/oru-r01.hl7")]
    (= (str/trim-newline (slurp f))
       (format-er7 (parse-er7 f))))
  ;;=> false

  (parse-er7 (.getBytes "MSH|^~\\&|ULTRA|TML|OLIS|OLIS")
             :val-fn (juxt :value :loc))
  ;;=> [{:MSH
  ;;     {1 ["|" {:from [1 3], :to [1 4]}],
  ;;      2 ["^~\\&" {:from [1 4], :to [1 8]}],
  ;;      3 ["ULTRA" {:from [1 9], :to [1 14]}],
  ;;      4 ["TML" {:from [1 15], :to [1 18]}],
  ;;      5 ["OLIS" {:from [1 19], :to [1 23]}],
  ;;      6 {1 {1 ["OLIS" {:from [1 24], :to [1 28]}]}}}}]

  (with-open [rx (io/reader (io/file "test/hl7v2/data/oru-r01.hl7"))]
    (let [cs (char-seq rx)
          enc (parse-encoding cs)]
      (->> (tokens enc cs)
           (into [])
           (drop-while #(not= "PID" (:value %))))))

  (->> (er7-zip (parse-er7 (io/file "test/hl7v2/data/oru-r01.hl7")))
       (iterate zip/next)
       (take-while (complement zip/end?))
       (map zip/node)
       (drop 1))

  :.)