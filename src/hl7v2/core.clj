(ns hl7v2.core
  (:require
   [clojure.string :as str]
   [clojure.java.io :as io])
  (:refer-clojure :exclude [format])
  (:import
   (java.io Reader)))

(defn parse [x]
  (with-open [rdr (io/reader x)]
    (letfn [(char-seq [^Reader rdr]
              (let [ch (.read rdr)]
                (when-not (= ch -1)
                  (cons (char ch) (lazy-seq (char-seq rdr))))))
            (escape [esc cseq]
              (let [[curr next & rest] cseq]
                (when curr
                  (if (= esc curr)
                    (cons (str curr next) (lazy-seq (escape esc rest)))
                    (cons curr (lazy-seq (escape esc (cons next rest))))))))
            (tokens [separators cseq]
              (when cseq
                (let [token (take-while (comp not (set (concat separators [\return \newline]))) cseq)
                      move-count (count token)
                      [separator & rest] (drop move-count cseq)]
                  (->> (lazy-seq (tokens separators rest))
                       (cons separator)
                       (cons (when (seq token)
                               (apply str token)))
                       (remove nil?)))))
            (nest [sep token->sep [h & t :as tokens]]
              (let [next-level (fn [sep offset tokens]
                                 (let [data (->> tokens
                                                 (partition-by (comp #{sep} token->sep))
                                                 (mapcat (fn [p]
                                                           (if (= #{sep} (set (map token->sep p)))
                                                             (repeat (dec (count p)) (list))
                                                             [p])))
                                                 (map (partial nest sep token->sep))
                                                 (concat (repeat offset nil))
                                                 (into []))]
                                   (if (= (inc offset) (count data))
                                     (first (drop offset data))
                                     (->> data
                                          (map vector (range))
                                          (remove (comp nil? second))
                                          (into (sorted-map))))))]
                (when (seq tokens)
                  (case sep
                    ::message (vals (next-level ::segment 0 tokens))
                    ::segment (let [seg (keyword h)]
                                #dbg
                                 (if (= :MSH seg)
                                   (let [data (next-level ::field 2 (rest t))]
                                     (if (map? data)
                                       {seg (assoc data 1 (str (first t)))}
                                       {seg {1 (str (first t))
                                             2 data}}))
                                   {seg (next-level ::field 1 t)}))
                    ::field (next-level ::repetition 0 tokens)
                    ::repetition (next-level ::component 1 tokens)
                    ::component (next-level ::subcomponent 1 tokens)
                    ::subcomponent h))))
            (msh [cseq]
              (let [[m s h & cseq] cseq]
                (when-not (= [\M \S \H] [m s h])
                  (throw (ex-info "Unexpected segment"
                                  {:expected "MSH"
                                   :actual (str m s h)
                                   :head [1 1]})))
                (let [[fld & cseq] cseq
                      encoding (take-while (comp not #{fld \return \newline}) cseq)
                      move-count (count encoding)]
                  [{:MSH [nil (str fld) (apply str encoding)]}
                   (drop move-count cseq)
                   [1 move-count]])))]
      (let [cseq (char-seq rdr)
            [seg cseq _head] (msh cseq)
            [fld] (get-in seg [:MSH 1])
            [cmp rep esc sub :as encoding] (get-in seg [:MSH 2])
            token->sep {\return ::segment
                        \newline ::segment
                        fld ::field
                        rep ::repetition
                        cmp ::component
                        sub ::subcomponent}]
        (->> cseq
             (escape esc)
             (tokens [fld rep cmp sub])
             (concat ["MSH" fld encoding])
             (nest ::message token->sep)
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

(comment
  (parse (.getBytes (str "MSH|^~\\&\r\n"
                         "PID|||123456||Doe^John")))

  :.)
