(ns hl7v2.schema
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.walk :refer [postwalk]]
   [clojure.zip :as zip]
   [hl7v2.complex :refer [clean]]))

(defn parse-xsd [x]
  (with-open [rx (io/reader x)]
    (postwalk (fn [x]
                (if (xml/element? x)
                  (->> (remove #(and (string? %)
                                     (-> % str/trim str/trim-newline str/blank?))
                               (:content x))
                       (concat [(or (get-in x [:attrs :name])
                                    (get-in x [:attrs :ref])
                                    (name (:tag x)))
                                (clean
                                 (-> (:attrs x)
                                     (update-keys (comp keyword name))
                                     (dissoc :name :ref)))])
                       (remove nil?)
                       (into []))
                  x))
              (xml/parse rx))))

(defn index-schema [schema]
  (->> schema
       (filter vector?)
       (map (juxt first identity))
       (into {})))

(defn node-tag [node]
  (when (vector? node)
    (first node)))

(defn node-attrs [node]
  (when (and (vector? node)
             (map? (second node)))
    (second node)))

(defn node-children [node]
  (when (vector? node)
    (drop (if (node-attrs node) 2 1) node)))

(defn content-node? [node]
  (when-let [attrs (node-attrs node)]
    (= (str (name (node-tag node)) ".CONTENT")
       (:type attrs))))

(defn sequence-node? [node]
  (when-let [tag (node-tag node)]
    (= "sequence" tag)))

(defn group-node? [node]
  (when-let [tag (node-tag node)]
    (and (> (count (str/split (name tag) #"\.")) 1)
         (when-let [attrs (node-attrs node)]
           (some? (:minOccurs attrs))))))

(defn segment-node? [node]
  (when-let [tag (node-tag node)]
    (and (= 1 (count (str/split (name tag) #"\.")))
         (not= "anyHL7Segment" tag)
         (when-let [attrs (node-attrs node)]
           (some? (:minOccurs attrs))))))

(defn annotation-node? [node]
  (when-let [tag (node-tag node)]
    (= "annotation" tag)))

(defn type-node? [node]
  (when-let [tag (node-tag node)]
    (#{"complexContent" "simpleContent"} tag)))

(defn complex-type-node? [node]
  (= "complexContent" (type-node? node)))

(defn annotated-type-node? [node]
  (and (some annotation-node? (node-children node))
       (some type-node? (node-children node))
       true))

(defn xsd-zip [root index]
  (zip/zipper (some-fn content-node?
                       sequence-node?
                       group-node?
                       segment-node?
                       annotated-type-node?
                       complex-type-node?
                       (comp seq node-children))
              (fn [node]
                (cond
                  (content-node? node) [(get index (:type (node-attrs node)))]
                  (segment-node? node) [(get index (node-tag node))]
                  (sequence-node? node) (node-children node)
                  (group-node? node) [(get index (node-tag node))]
                  (annotated-type-node? node) (node-children node)
                  (complex-type-node? node) (let [child (first (node-children node))]
                                              [(get index (:base (node-attrs child)))])
                  :else (node-children node)))
              (fn [node children]
                (->> [[(node-tag node)
                       (node-attrs node)]
                      children]
                     (apply concat)
                     (remove nil?)
                     (into [])))
              root))

(defn materialize-schema
  ([loc]
   (materialize-schema loc 100))
  ([loc max-depth]
   (when (and (pos? max-depth) (not (zip/end? loc)))
     (let [node (zip/node loc)]
       (->> [[(node-tag node) (node-attrs node)]
             (or (seq
                  (for [child (->> (zip/down loc)
                                   (iterate zip/right)
                                   (take-while (complement nil?)))]
                    (materialize-schema child (dec max-depth))))
                 [node])]
            (apply concat)
            (remove nil?)
            (into []))))))

(defn load-schema [trigger-event standard-dir]
  (let [index (->> [(format "%s/%s.xsd" standard-dir trigger-event)
                    (format "%s/segments.xsd" standard-dir)
                    (format "%s/datatypes.xsd" standard-dir)
                    (format "%s/fields.xsd" standard-dir)]
                   (map #(-> % io/file parse-xsd index-schema))
                   (apply merge))]
    (-> (get index trigger-event)
        (xsd-zip index)
        (materialize-schema))))

(defn schema-zip [schema]
  (zip/zipper (comp seq node-children)
              node-children
              (fn [n c]
                (->> [[(node-tag n) (node-attrs n)] c]
                     (apply concat)
                     (remove nil?)
                     (into [])))
              schema))