(ns hl7v2.zipper
  (:require
   [clojure.string :as str]
   [clojure.zip :as zip]))

(defn struc-tag [spec]
  (when (vector? spec)
    (first spec)))

(defn struc-attrs [spec]
  (when (vector? spec)
    (when-let [snd (second spec)]
      (when (map? snd)
        snd))))

(defn struc-children [spec]
  (when (vector? spec)
    (if (struc-attrs spec)
      (drop 2 spec)
      (drop 1 spec))))

(defn struc-zip [spec]
  (zip/zipper (comp seq struc-children)
              struc-children
              (fn [node children]
                (->> [[(struc-tag node) (struc-attrs node)] children]
                     (apply concat)
                     (remove nil?)
                     (into [])))
              spec))

(defn match-components [data struc]
  (if-let [components (seq (struc-children struc))]
    (->> (for [[idx cmp-struc] (map-indexed (fn [idx cmp]
                                              [(inc idx) cmp])
                                            components)
               :let [data (if (string? data)
                            (when (= 1 idx)
                              data)
                            (get data idx))]
               :when data]
           [(first cmp-struc) data])
         (into {}))
    data))

(defn match-fields [data struc]
  (->> (for [[idx field-struc] (map-indexed (fn [idx field]
                                              [(inc idx) field])
                                            (struc-children struc))
             :let [field-data (get-in data [(struc-tag struc) idx])]
             :when field-data]
         [(struc-tag field-struc)
          (if (:repeats (struc-attrs field-struc))
            (mapv #(match-components % field-struc)
                  (if (map? field-data)
                    (vals field-data)
                    [field-data]))
            (match-components (if (map? field-data)
                                (val (first field-data))
                                field-data)
                              field-struc))])
       (into {})))

(defn match-segment [data struc]
  (letfn [(seg-id [seg]
            (when (and seg (map? seg))
              (-> seg first key)))]
    (let [id (struc-tag struc)
          {:keys [repeats]} (struc-attrs struc)]
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
          (recur more (conj acc {id (match-fields seg struc)})))))))

(defn match-group [data struc]
  (letfn [(seg? [spec]
            (let [tag-name (name (struc-tag spec))]
              (and (= 3 (count tag-name))
                   (= tag-name (str/upper-case tag-name)))))]
    (loop [[curr & more] (struc-children struc)
           [seg :as segs] data
           acc []]
      (let [{:keys [required]} (struc-attrs curr)]
        (if (and curr seg)
          (let [[result segs] (if (seg? curr)
                                (match-segment segs curr)
                                (match-group segs curr))]
            (if (and required (nil? result))
              (recur nil segs acc)
              (recur more segs (conj acc result))))
          (if-let [items (->> acc (remove nil?) seq)]
            (let [group-id (struc-tag struc)
                  group-data (apply merge items)]
              (if (-> struc struc-attrs :repeats)
                (let [[result segs] (match-group segs struc)]
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
    (let [[matched rem-segs] (match-group er7 struc)]
      (when (seq rem-segs)
        (throw (ex-info "Incomplete trigger-event or incorrect er7"
                        {:matched matched, :remaining-segments rem-segs})))
      (get matched spec-tgr-evt))))

(defn hl7-seg-id [n]
  (when (and (map? n)
             (= 1 (count n))
             (let [id (name (ffirst n))]
               (and (= 3 (count id))
                    (= id (str/upper-case id)))))
    (ffirst n)))

(defn hl7-zip [hl7]
  (letfn [(group-child? [n]
            (and (map? n)
                 (let [child (val (first n))]
                   (and (map? child)
                        (nil? (hl7-seg-id child))))))
          (branch? [n]
            (and (map? n)
                 (nil? (hl7-seg-id n))
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

(defn seg-zip [hl7-seg]
  (zip/zipper
   (fn [node]
     (or (and (map-entry? node)
              (or (vector? (val node))
                  (map? (val node))))
         (and (vector? node)
              (not (map-entry? node)))
         (map? node)))
   (fn [node]
     (cond
       (map-entry? node) (seq (val node))
       (vector? node) (seq node)
       (map? node) (seq node)))
   (fn [node children]
     (cond
       (map-entry? node) [(key node) children]
       (vector? node) (seq children)
       (map? node) (into (sorted-map) children)))
   hl7-seg))

(defn er7 [hl7 struc]
  (letfn [(struc-segments [struc]
            (->> (struc-zip struc)
                 (iterate zip/next)
                 (take-while (complement zip/end?))
                 (keep (fn [loc]
                         (let [node (zip/node loc)
                               tag (some-> node struc-tag name)]
                           (when (and (some? tag)
                                      (= 3 (count tag))
                                      (= tag (str/upper-case tag)))
                             node))))))
          (index-fields [struc]
            (->> (struc-children struc)
                 (map (juxt struc-tag identity))
                 (into {})))
          (field-idx [struc]
            (-> (struc-attrs struc)
                (:field)
                (str/split #"\.")
                (last)
                (parse-long)))
          (unmatch
            ([data struc]
             (unmatch data struc [:fld :rep :cmp :sub]))
            ([data struc [level & more]]
             (cond
               (map? data) (let [fields (index-fields struc)]
                             ((fn [m]
                                (if (= :rep level) {1 m} m))
                              (->> (seq data)
                                   (map (fn [[k v]]
                                          (if-let [field (get fields k)]
                                            [(field-idx field) (unmatch v field more)]
                                            [k v])))
                                   (into (sorted-map)))))
               (vector? data) (->> (seq data)
                                   (map-indexed (fn [idx v]
                                                  [(inc idx) (unmatch v struc more)]))
                                   (into (sorted-map)))
               :else data)))]
    (let [struc-by-seg-id (->> (struc-segments struc)
                               (map (juxt struc-tag identity))
                               (into {}))]
      (->> (hl7-zip hl7)
           (iterate zip/next)
           (take-while (complement zip/end?))
           (filter (comp hl7-seg-id zip/node))
           (mapv (fn [loc]
                   (let [seg (zip/node loc)
                         id (hl7-seg-id seg)]
                     (update seg id unmatch (struc-by-seg-id id)))))))))

(comment

  (->> (struc-zip
        (read-string
         (slurp "structures/v2.5.1/ORU_R01.edn")))
       (iterate zip/next)
       (take-while (complement zip/end?))
       (filter #(-> % zip/node struc-tag (= :PID)))
       (some zip/node))

  (require '[clojure.java.io :as io])
  (require '[clojure.edn :as edn])
  (require '[hl7v2.er7 :refer [parse-er7]])

  (time
   (try
     (hl7 (parse-er7 (io/file "test/hl7v2/data/oru-r01.hl7"))
          (edn/read-string (slurp "structures/v2.5.1/ORU_R01.edn")))
     (catch Exception ex
       (ex-data ex))))

  (let [struc (edn/read-string (slurp "structures/v2.5.1/ORU_R01.edn"))
        oru (-> (io/file "test/hl7v2/data/oru-r01.hl7")
                (parse-er7)
                (hl7 struc))]
    (-> (hl7-zip oru)
        (zip/down)
        (zip/edit assoc-in [:MSH :field-separator] "=")
        (zip/up)
        (zip/node)))

  (let [struc (edn/read-string (slurp "structures/v2.5.1/ORU_R01.edn"))]
    (er7 (-> (io/file "test/hl7v2/data/oru-r01.hl7")
             (parse-er7)
             (hl7 struc))
         struc))

  :.)