(ns hl7v2.zipper
  (:require
   [clojure.string :as str]
   [clojure.zip :as zip]
   [hl7v2.structures :refer [struc-tag struc-attrs struc-children struc-zip]]))

(defn hl7-seg? [n]
  (and (map? n)
       (= 1 (count n))
       (let [id (name (ffirst n))]
         (and (= 3 (count id))
              (= id (str/upper-case id))))))

(defn match-components [spec data]
  (if-let [components (seq (struc-children spec))]
    (->> (for [[idx cmp-spec] (map-indexed (fn [idx cmp]
                                             [(inc idx) cmp])
                                           components)
               :let [data (if (string? data)
                            (when (= 1 idx)
                              data)
                            (get data idx))]
               :when data]
           [(first cmp-spec) data])
         (into {}))
    data))

(defn match-fields [spec data]
  (->> (for [[idx field-spec] (map-indexed (fn [idx field]
                                             [(inc idx) field])
                                           (struc-children spec))
             :let [field-data (get-in data [(struc-tag spec) idx])]
             :when field-data]
         [(struc-tag field-spec)
          (if (:repeats (struc-attrs field-spec))
            (mapv (partial match-components field-spec)
                  (if (map? field-data)
                    (vals field-data)
                    [field-data]))
            (match-components field-spec
                              (if (map? field-data)
                                (val (first field-data))
                                field-data)))])
       (into {})))

(defn match-segment [spec data]
  (letfn [(seg-id [seg]
            (when (and seg (map? seg))
              (-> seg first key)))]
    (let [id (struc-tag spec)
          {:keys [repeats]} (struc-attrs spec)]
      (loop [[seg & more :as segs] data
             acc []]
        (if (or (nil? seg)
                (not= id (seg-id seg))
                (and (not repeats)
                     (seq acc)))
          [(when (seq acc)
             (if repeats
               {id (mapv (comp val first) acc)}
               (first acc)))
           segs]
          (recur more (conj acc {id (match-fields spec seg)})))))))

(defn match-group [spec data]
  (letfn [(seg? [spec]
            (let [tag-name (name (struc-tag spec))]
              (and (= 3 (count tag-name))
                   (= tag-name (str/upper-case tag-name)))))]
    (loop [[curr & more] (struc-children spec)
           [seg :as segs] data
           acc []]
      (let [{:keys [required]} (struc-attrs curr)]
        (if (and curr seg)
          (let [[result segs] (if (seg? curr)
                                (match-segment curr segs)
                                (match-group curr segs))]
            (if (and required (nil? result))
              (recur nil segs acc)
              (recur more segs (conj acc result))))
          (if-let [items (->> acc (remove nil?) seq)]
            (let [group-id (struc-tag spec)
                  group-data (apply merge items)]
              (if (-> spec struc-attrs :repeats)
                (let [[result segs] (match-group spec segs)]
                  [{group-id (->> (get result group-id)
                                  (cons group-data)
                                  (into []))}
                   segs])
                [{group-id group-data} segs]))
            [nil segs]))))))

(defn hl7 [er7 struc]
  (let [spec-tgr-evt (struc-tag struc)
        msh (first (filter (comp #{:MSH} key first) er7))
        er7-tgr-evt (format "%s_%s" (get-in msh [:MSH 9 1 1]) (get-in msh [:MSH 9 1 2]))]
    (when-not (= (name spec-tgr-evt) er7-tgr-evt)
      (throw (ex-info "Trigger event mismatch"
                      {:spec spec-tgr-evt, :er7 er7-tgr-evt})))
    (let [[matched rem-segs] (match-group struc er7)]
      (when (seq rem-segs)
        (throw (ex-info "Incomplete trigger-event or incorrect er7"
                        {:matched matched, :remaining-segments rem-segs})))
      (get matched spec-tgr-evt))))

(defn hl7-zip [hl7]
  (letfn [(group-child? [n]
            (and (map? n)
                 (let [child (val (first n))]
                   (and (map? child)
                        (not (hl7-seg? child))))))
          (branch? [n]
            (and (map? n)
                 (not (hl7-seg? n))
                 (or (> (count n) 1)
                     (vector? (val (first n)))
                     (group-child? n))))
          (children [n]
            (cond
              (> (count n) 1) (for [[k v] n] {k v})
              (vector? (val (first n))) (-> n first val seq)
              (group-child? n) (for [[k v] (val (first n))] {k v})
              :else n))
          (edit [node children]
            (cond
              (> (count node) 1) (apply merge children)
              (vector? (val (first node))) {(ffirst node) (into [] children)}
              (group-child? node) {(ffirst node) (apply merge children)}
              :else (throw (ex-info "unknown node kind" {:node node, :children children}))))]
    (zip/zipper branch?
                children
                edit
                hl7)))

(comment

  (require '[clojure.java.io :as io])
  (require '[clojure.edn :as edn])
  (require '[hl7v2.er7 :refer [parse-er7]])

  (def struc
    (-> (slurp "structures/v2.5.1/ORU_R01.edn")
        (edn/read-string)))

  (def er7
    (-> (io/file "test/hl7v2/data/oru-r01.hl7")
        (parse-er7)))

  (def hl7 (hl7 er7 struc))

  (time
   (try
     (hl7 (parse-er7 (io/file "test/hl7v2/data/oru-r01.hl7"))
          (edn/read-string (slurp "structures/v2.5.1/ORU_R01.edn")))
     (catch Exception ex
       (ex-data ex))))

  (-> (hl7-zip hl7)
      (zip/down)
      (zip/edit assoc-in [:MSH :field-separator] "=")
      (zip/up)
      (zip/node))

  (->> (hl7-zip hl7)
       (iterate zip/next)
       (take-while (complement zip/end?))
       (filter (comp hl7-seg? zip/node))
       (map zip/node)
       (first))

  (->> (struc-zip struc)
       (iterate zip/next)
       (take-while (complement zip/end?))
       (filter (fn [loc]
                 (let [tag (some-> loc zip/node struc-tag name)]
                   (and (some? tag)
                        (= 3 (count tag))
                        (= tag (str/upper-case tag))))))
       (map (juxt (comp struc-tag zip/node) zip/node))
       (into {}))

  :.)