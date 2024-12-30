(ns hl7v2.zipper)

(defn spec-tag [spec]
  (when (vector? spec)
    (first spec)))

(defn spec-attrs [spec]
  (when (vector? spec)
    (when-let [snd (second spec)]
      (when (map? snd)
        snd))))

(defn spec-children [spec]
  (if (spec-attrs spec)
    (drop 2 spec)
    (drop 1 spec)))

(defn seg-id [seg]
  (when (and seg (map? seg))
    (-> seg first key)))

(defn match-components [spec data]
  (if-let [components (seq (spec-children spec))]
    (->> (for [[idx cmp-spec] (map-indexed (fn [idx cmp]
                                             [(inc idx) cmp])
                                           components)
               :let [attr (first cmp-spec)
                     data (if (string? data)
                            (when (= 1 idx)
                              data)
                            (get data idx))]
               :when data]
           [attr data])
         (into {}))
    data))

(defn match-fields [spec data]
  (->> (for [[idx field-spec] (map-indexed (fn [idx field]
                                             [(inc idx) field])
                                           (spec-children spec))
             :let [attr (spec-tag field-spec)
                   field-data (get-in data [(spec-tag spec) idx])]
             :when field-data]
         (if (:repeats (spec-attrs field-spec))
           [attr (->> (for [[idx rep-data] (if (map? field-data)
                                             field-data
                                             {1 field-data})]
                        [idx (match-components field-spec rep-data)])
                      (into {}))]
           [attr (match-components
                  field-spec
                  (if (map? field-data)
                    (val (first field-data))
                    field-data))]))
       (into {})))

(defn match-segment [spec data]
  (let [id (spec-tag spec)]
    (loop [[seg & more :as segs] data
           acc []]
      (if (or (nil? seg) (not= id (seg-id seg)))
        [(when (seq acc)
           (if (-> spec spec-attrs :repeats)
             {id (mapv (comp val first) acc)}
             (first acc)))
         segs]
        (recur more (conj acc {id (match-fields spec seg)}))))))

(defn match-group [spec data]
  (loop [[curr & more] (spec-children spec)
         [seg :as segs] data
         acc []]
    (let [{:keys [required segment]} (spec-attrs curr)]
      (if (and curr seg)
        (let [[result segs] (if segment
                              (match-segment curr segs)
                              (match-group curr segs))]
          (if (and required (nil? result))
            (recur nil segs acc)
            (recur more segs (conj acc result))))
        (if-let [items (->> acc (remove nil?) seq)]
          (let [group-id (spec-tag spec)
                group-data (apply merge items)]
            (if (-> spec spec-attrs :repeats)
              (let [[result segs] (match-group spec segs)]
                [{group-id (->> (get result group-id)
                                (cons group-data)
                                (into []))}
                 segs])
              [{group-id group-data} segs]))
          [nil segs])))))

(comment

  (require '[clojure.java.io :as io])
  (require '[clojure.edn :as edn])
  (require '[hl7v2.er7 :refer [parse-er7]])

  (def spec
    (-> (slurp "test/hl7v2/data/ORU_R01.edn")
        (edn/read-string)))

  (def er7
    (-> (io/file "test/hl7v2/data/oru-r01.hl7")
        (parse-er7)))

  (match-group spec er7)

  :.)