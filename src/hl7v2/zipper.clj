(ns hl7v2.zipper
  (:require
   [clojure.zip :as zip]))

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
  (let [id (spec-tag spec)
        attrs (spec-attrs spec)]
    (loop [[seg & more :as segs] data
           result []]
      (if (or (nil? seg) (not= id (seg-id seg)))
        [(if (:repeats attrs)
           (->> result
                (map-indexed (fn [idx item]
                               [(inc idx) item]))
                (into (sorted-map)))
           (first result))
         segs]
        (recur more (concat result [{id (match-fields spec seg)}]))))))

(defn spec-zip [spec]
  (zip/zipper (fn [spec]
                (let [attrs (spec-attrs spec)]
                  (or (nil? attrs)
                      (not (:segment attrs)))))
              spec-children
              (fn [node children]
                (cons (spec-tag node) children))
              spec))

(match-segment (->> (spec-zip spec)
                    (iterate zip/next)
                    (take-while (complement zip/end?))
                    (map zip/node)
                    (drop 14)
                    (first))
               (drop 4 er7))

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

  (loop [loc (spec-zip spec)]
    (if (zip/end? loc)
      (zip/node loc)
      (if (-> loc zip/node spec-attrs :segment)
        (recur (zip/next (zip/replace loc {})))
        (recur (zip/next loc)))))

  (->> (spec-zip spec)
       (iterate zip/next)
       (take-while (complement zip/end?))
       (map zip/node)
       (map (juxt first
                  (comp :segment spec-attrs)
                  (comp :repeats spec-attrs))))

  :.)