(ns hl7v2.zipper)

(defn match-components [spec data]
  (if-let [components (seq (drop 2 spec))]
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
                                           (drop 2 spec))
             :let [attr (first field-spec)
                   field-data (get-in data [(first spec) idx])]
             :when field-data]
         (if (:repeats (second field-spec))
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
  (let [id (first spec)]
    (when (= id (key (first data)))
      {id (match-fields spec data)})))

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

  (match-segment (->> spec (drop 1) (first))
                 (->> er7 (first)))

  :.)