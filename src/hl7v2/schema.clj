(ns hl7v2.schema
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.walk :refer [postwalk]]
   [hl7v2.complex :refer [clean]]
   [hl7v2.config :refer [config]])
  (:import
   (java.text Normalizer Normalizer$Form)))

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
  (when (and (vector? node)
             (string? (first node)))
    (first node)))

(defn node-attrs [node]
  (when (and (vector? node)
             (map? (second node)))
    (second node)))

(defn node-children [node]
  (when (vector? node)
    (->> node
         (drop (if (node-attrs node) 2 1))
         (into []))))

(defn node-annotation [node]
  (letfn [(rm-diacritics [s]
            (when s
              (-> s
                  (Normalizer/normalize Normalizer$Form/NFD)
                  (str/replace #"[\u0300-\u036f]" ""))))]
    (->> (node-children node)
         (some #(when (and (= "documentation" (first %))
                           (= "en" (:lang (second %))))
                  (->> (str/split (last %) #"\s+")
                       (map (fn [s]
                              (-> (rm-diacritics s)
                                  (str/replace #"/" "Or")
                                  (str/replace #"-" ""))))
                       (str/join "")))))))

(defn type-base [node]
  (->> (node-children node)
       (filter (comp #{"extension"} node-tag))
       (some (comp :base node-attrs))))

(defn content-node? [node]
  (when-let [attrs (node-attrs node)]
    (= (str (node-tag node) ".CONTENT")
       (:type attrs))))

(defn sequence-node? [node]
  (when-let [tag (node-tag node)]
    (= "sequence" tag)))

(defn group-node? [node]
  (when-let [tag (node-tag node)]
    (and (> (count (str/split tag #"\.")) 1)
         (when-let [attrs (node-attrs node)]
           (some? (:minOccurs attrs))))))

(defn segment-node? [node]
  (when-let [tag (node-tag node)]
    (and (= 1 (count (str/split tag #"\.")))
         (when-let [attrs (node-attrs node)]
           (some? (:minOccurs attrs))))))

(defn annotation-node? [node]
  (when-let [tag (node-tag node)]
    (= "annotation" tag)))

(defn type-node? [node]
  (when-let [tag (node-tag node)]
    (#{"complexContent" "simpleContent"} tag)))

(defn fill-node [node index]
  (let [tag (node-tag node)
        attrs (node-attrs node)
        children (node-children node)]
    (cond
      (= "any" tag) nil
      (content-node? node) (let [content (get index (:type attrs))
                                 node (fill-node content index)]
                             (->> (node-children node)
                                  (mapcat (fn [n]
                                            (if (sequence-node? n)
                                              (fill-node n index)
                                              [(fill-node n index)])))
                                  (concat [tag (merge attrs (node-attrs node))])
                                  (into [])))
      (sequence-node? node) (->> (for [node children]
                                   (fill-node node index))
                                 (into []))
      (group-node? node) (let [group (get index tag)
                               node (fill-node group index)
                               tag (last (str/split tag #"\."))
                               children (node-children node)]
                           (->> children
                                (drop-while string?)
                                (concat [tag (merge attrs (node-attrs node) {:name (first (filter string? children))})])
                                (into [])))
      (segment-node? node) (let [node (get index tag)]
                             (fill-node (->> (node-children node)
                                             (concat [tag (merge attrs (node-attrs node))])
                                             (into []))
                                        index))
      (annotation-node? node) (node-annotation node)
      (type-node? node) (let [node (get index (type-base node))]
                          (->> (node-children node)
                               (mapcat (fn [n]
                                         (if (sequence-node? n)
                                           (fill-node n index)
                                           [(fill-node n index)])))
                               (concat [(node-tag node)])
                               (into [])))
      :else node)))

(defn gen-structure [trigger-event]
  (letfn [(load-schema [tgr-evt]
            (let [dir (:standard-dir @config)
                  files [(format "%s/%s.xsd" dir tgr-evt)
                         (format "%s/segments.xsd" dir)
                         (format "%s/datatypes.xsd" dir)
                         (format "%s/fields.xsd" dir)]]
              (->> (for [f files]
                     (-> (io/file f)
                         (parse-xsd)
                         (index-schema)))
                   (apply merge))))]
    (let [index (load-schema trigger-event)
          root (get index trigger-event)]
      (fill-node root index))))

(comment

  (gen-structure "ORU_R01")

  (-> (io/file "test/hl7v2/data/ACK.xsd")
      (parse-xsd)
      (index-schema))

  (-> (io/file "tmp/HL7-xml v2.5.1/segments.xsd")
      (parse-xsd)
      (index-schema))

  (-> (io/file "tmp/HL7-xml v2.5.1/datatypes.xsd")
      (parse-xsd)
      (index-schema))

  (-> (io/file "tmp/HL7-xml v2.5.1/fields.xsd")
      (parse-xsd)
      (index-schema))

  :.)