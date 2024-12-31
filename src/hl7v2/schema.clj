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

(defn attr-kw [node]
  (letfn [(rm-diacritics [s]
            (when s
              (-> s
                  (Normalizer/normalize Normalizer$Form/NFD)
                  (str/replace #"[\u0300-\u036f]" ""))))]
    (->> (node-children node)
         (some #(when (and (= "documentation" (first %))
                           (= (:lang @config) (:lang (second %))))
                  (->> (str/split (str/replace (last %) #"\(.+" "") #"\s+")
                       (map (fn [s]
                              (-> (rm-diacritics s)
                                  (str/replace #"/" "Or")
                                  (str/replace #"-" "")
                                  (str/replace #"'" "")
                                  (str/trim)
                                  (str/lower-case))))
                       (remove str/blank?)
                       (str/join "-")
                       (keyword)))))))

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

(defn restricted-node? [node]
  (when-let [children (seq (node-children node))]
    (and (first children)
         (= "restriction" (node-tag (first children))))))

(defn fill-node [node index]
  (letfn [(interpret-attrs [attrs-m & more]
            (let [{:keys [minOccurs maxOccurs name data-type segment]} (apply merge attrs-m more)]
              (clean
               {:required (and minOccurs
                               (> (parse-long minOccurs) 0))
                :repeats (and maxOccurs
                              (or (= "unbounded" maxOccurs)
                                  (> (parse-long maxOccurs) 1)))
                :name name
                :data-type data-type
                :segment segment})))]
    (let [tag (node-tag node)
          attrs (node-attrs node)
          children (node-children node)]
      (cond
        (= "any" tag) nil
        (content-node? node) (let [node (-> (get index (:type attrs))
                                            (fill-node index))]
                               (->> (node-children node)
                                    (mapcat (fn [n]
                                              (if (sequence-node? n)
                                                (fill-node n index)
                                                [(fill-node n index)])))
                                    (concat [(keyword tag) (interpret-attrs attrs (node-attrs node))])
                                    (remove nil?)
                                    (into [])))
        (sequence-node? node) (->> (for [node children
                                         :let [item (fill-node node index)]]
                                     (if-let [tag (node-tag item)]
                                       (if (parse-long tag)
                                         (let [child (first (node-children item))]
                                           (->> (node-children child)
                                                (concat [(:name (node-attrs item))
                                                         (-> (node-attrs item)
                                                             (dissoc :name)
                                                             (interpret-attrs (node-attrs child)))])
                                                (remove nil?)
                                                (into [])))
                                         (->> (node-children item)
                                              (concat [(keyword tag)
                                                       (-> (node-attrs item)
                                                           (interpret-attrs))])
                                              (remove nil?)
                                              (into [])))
                                       item))
                                   (into []))
        (group-node? node) (let [group (get index tag)
                                 node (fill-node group index)
                                 tag (last (str/split tag #"\."))
                                 children (node-children node)]
                             (->> children
                                  (drop-while keyword?)
                                  (concat [tag (merge attrs
                                                      (node-attrs node)
                                                      {:name (first (filter keyword children))})])
                                  (into [])))
        (segment-node? node) (let [node (get index tag)]
                               (fill-node (->> (node-children node)
                                               (concat [tag (merge attrs (node-attrs node) {:segment true})])
                                               (into []))
                                          index))
        (annotation-node? node) (attr-kw node)
        (type-node? node) (let [node (get index (type-base node))]
                            (if (restricted-node? node)
                              [(keyword (node-tag node))
                               (let [child (first (node-children node))
                                     base (:base (node-attrs child))]
                                 {:data-type (->> (re-seq #"^xsd:(.+)$" base)
                                                  (map (comp keyword second))
                                                  (first))})]
                              (when node
                                (->> (node-children node)
                                     (mapcat (fn [n]
                                               (if (sequence-node? n)
                                                 (fill-node n index)
                                                 [(fill-node n index)])))
                                     (concat [(keyword (node-tag node))])
                                     (into [])))))
        :else node))))

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

  (require '[clojure.pprint :as pp])

  (spit
   "test/hl7v2/data/ORU_R01.edn"
   (with-out-str
     (pp/pprint (gen-structure "ORU_R01"))))

  (spit
   "test/hl7v2/data/ACK.edn"
   (with-out-str
     (pp/pprint (gen-structure "ACK"))))

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


  (parse-xsd (io/file "tmp/ORU_R01.xsd"))
  (parse-xsd (io/file "tmp/fields.xsd"))

  (content-node? ["ORU_R01" {:type "ORU_R01.CONTENT"}])
  ;;=> true

  (sequence-node? ["sequence"
                   ["OBX" {:minOccurs "1", :maxOccurs "1"}]
                   ["NTE" {:minOccurs "0", :maxOccurs "unbounded"}]])
  ;;=> true

  (group-node? ["ORU_R01.VISIT" {:minOccurs "0", :maxOccurs "1"}])
  ;;=> true

  (segment-node? ["PID" {:minOccurs "1", :maxOccurs "1"}])
  ;;=> true

  (annotation-node? ["annotation"
                     ["documentation" {:lang "en"} "Accident Code"]
                     ["documentation" {:lang "de"} "Art des Unfalls"]
                     ["appinfo" ["Item" "528"] ["Type" "CE"] ["Table" "HL70050"] ["LongName" "HL7Accident Code"]]])
  ;;=> true

  (type-node? ["complexContent"
               ["extension" {:base "CE"}
                ["ACC.2.ATTRIBUTES"]]])
  ;;=> "complexContent"

  (type-node? ["simpleContent"
               ["extension" {:base "ID"}
                ["ABS.14.ATTRIBUTES"]]])
  ;;=> "simpleContent"

  (attr-kw ["annotation"
            ["documentation" {:lang "en"} "Accident Code"]
            ["documentation" {:lang "de"} "Art des Unfalls"]
            ["appinfo"
             ["Item" "528"]
             ["Type" "CE"]
             ["Table" "HL70050"]
             ["LongName" "HL7Accident Code"]]])
  ;;=> :AccidentCode

  (attr-kw ["annotation"
            ["documentation" {:lang "en"} "Gestation Category Code"]
            ["documentation" {:lang "de"} "Kategorisierung der Schwangerschaftsdauer"]
            ["appinfo"
             ["Item" "1524"]
             ["Type" "CE"]
             ["Table" "HL70424"]
             ["LongName" "HL7Gestation Category Code"]]])
  ;;=> :GestationCategoryCode

  (with-redefs [config (delay {:lang "de"})]
    (attr-kw ["annotation"
              ["documentation" {:lang "en"} "Gestation Category Code"]
              ["documentation" {:lang "de"} "Kategorisierung der Schwangerschaftsdauer"]
              ["appinfo"
               ["Item" "1524"]
               ["Type" "CE"]
               ["Table" "HL70424"]
               ["LongName" "HL7Gestation Category Code"]]]))
  ;;=> :KategorisierungDerSchwangerschaftsdauer

  (type-base ["simpleContent"
              ["extension" {:base "NM"}
               ["ABS.12.ATTRIBUTES"]]])
  ;;=> "NM"

  (restricted-node? ["ID"
                     ["restriction" {:base "xsd:string"}]])
  ;;=> true

  :.)