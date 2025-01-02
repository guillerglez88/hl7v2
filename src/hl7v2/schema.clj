(ns hl7v2.schema
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.walk :refer [postwalk]]
   [clojure.zip :as zip]
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
    (drop (if (node-attrs node) 2 1) node)))

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
         (not= "anyHL7Segment" tag)
         (when-let [attrs (node-attrs node)]
           (some? (:minOccurs attrs))))))

(defn annotation-node? [node]
  (when-let [tag (node-tag node)]
    (= "annotation" tag)))

(defn type-node? [node]
  (when-let [tag (node-tag node)]
    (#{"complexContent" "simpleContent"} tag)))

(defn annotated-type-node? [node]
  (and (some annotation-node? (node-children node))
       (some type-node? (node-children node))
       true))

(defn restricted-node? [node]
  (when-let [children (seq (node-children node))]
    (and (first children)
         (= "restriction" (node-tag (first children))))))

(defn xsd-zip [trigger-event]
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
      (zip/zipper (some-fn content-node?
                           sequence-node?
                           group-node?
                           segment-node?
                           annotated-type-node?
                           (comp seq node-children))
                  (fn [node]
                    (cond
                      (content-node? node) [(get index (:type (node-attrs node)))]
                      (segment-node? node) [(get index (node-tag node))]
                      (sequence-node? node) (node-children node)
                      (group-node? node) [(get index (node-tag node))]
                      (annotated-type-node? node) (node-children node)
                      :else (node-children node)))
                  (fn [node children]
                    (->> [[(node-tag node)
                           (node-attrs node)]
                          children]
                         (apply concat)
                         (remove nil?)
                         (into [])))
                  root))))

(defn zip-tree [zipper]
  (when-not (zip/end? zipper)
    (let [node (zip/node zipper)]
      (->> [[(node-tag node) (node-attrs node)]
            (or (seq
                 (for [child (->> (zip/down zipper)
                                  (iterate zip/right)
                                  (take-while (complement nil?)))]
                   (zip-tree child)))
                [node])]
           (apply concat)
           (remove nil?)
           (into [])))))

(defn attr-kw [node]
  (letfn [(rm-diacritics [s]
            (when s
              (-> s
                  (Normalizer/normalize Normalizer$Form/NFD)
                  (str/replace #"[\u0300-\u036f]" ""))))
          (sanitize [s]
            (-> s
                (str/replace #"/" "or")
                (str/replace #"&" "and")
                (str/replace #"-" "")
                (str/replace #"'" "")
                (str/replace #"\)" "")
                (str/replace #"\." "")))]
    (->> (node-children node)
         (some (fn [child]
                 (when (and (= "documentation" (first child))
                            (= (:lang @config) (:lang (second child))))
                   (->> (str/split (last child) #"\(")
                        (take 1)
                        (mapcat #(str/split % #"\s+"))
                        (map (fn [s]
                               (-> (rm-diacritics s)
                                   (sanitize)
                                   (str/trim)
                                   (str/lower-case))))
                        (remove str/blank?)
                        (str/join "-")
                        (keyword))))))))

(defn type-base [node]
  (->> (node-children node)
       (filter (comp #{"extension"} node-tag))
       (some (comp :base node-attrs))))

(defn fill-node [node index max-depth]
  (when (pos? max-depth)
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
                                              (fill-node index (dec max-depth)))]
                                 (->> (node-children node)
                                      (mapcat (fn [n]
                                                (if (sequence-node? n)
                                                  (fill-node n index (dec max-depth))
                                                  [(fill-node n index (dec max-depth))])))
                                      (concat [(keyword tag) (interpret-attrs attrs (node-attrs node))])
                                      (remove nil?)
                                      (into [])))
          (sequence-node? node) (->> (for [node children
                                           :let [item (fill-node node index (dec max-depth))]]
                                       (if-let [tag (node-tag item)]
                                         (if (parse-long tag)
                                           (let [child (first (node-children item))]
                                             (->> (node-children child)
                                                  (concat [(:name (node-attrs item))
                                                           (-> (node-attrs item)
                                                               (dissoc :name)
                                                               (interpret-attrs (node-attrs child)))])
                                                  (into [])))
                                           (->> (node-children item)
                                                (concat [(keyword tag)
                                                         (interpret-attrs (node-attrs item))])
                                                (remove nil?)
                                                (into [])))
                                         item))
                                     (into []))
          (group-node? node) (let [group (get index tag)
                                   node (fill-node group index (dec max-depth))
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
                                            index
                                            (dec max-depth)))
          (annotation-node? node) (attr-kw node)
          (type-node? node) (let [base-node (get index (type-base node))]
                              (if (restricted-node? base-node)
                                [(keyword (node-tag base-node))
                                 (let [child (first (node-children base-node))
                                       base (:base (node-attrs child))]
                                   {:data-type (->> (re-seq #"^xsd:(.+)$" base)
                                                    (map (comp keyword second))
                                                    (first))})]
                                (when base-node
                                  (->> (node-children base-node)
                                       (mapcat (fn [n]
                                                 (if (sequence-node? n)
                                                   (fill-node n index (dec max-depth))
                                                   [(fill-node n index (dec max-depth))])))
                                       (concat [(keyword (node-tag base-node))])
                                       (into [])))))
          #_#_(annotated-type-node? node) (->> (node-children node)
                                               (map #(fill-node % index (dec max-depth)))
                                               (remove nil?)
                                               (into []))
          :else node)))))

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
      (fill-node root index 20))))

(comment

  (-> (io/file "structures/v2.5.1/ACK.edn")
      (parse-xsd)
      (index-schema))

  (-> (io/file "structures/v2.5.1/segments.xsd")
      (parse-xsd)
      (index-schema))

  (-> (io/file "tmp/datatypes.xsd")
      (parse-xsd)
      (index-schema)
      (get "HD.1.CONTENT"))

  (-> (io/file "tmp/fields.xsd")
      (parse-xsd)
      (index-schema)
      (get "ABS.11.CONTENT"))

  (parse-xsd (io/file "tmp/ORU_R01.xsd"))
  (parse-xsd (io/file "tmp/fields.xsd"))

  (content-node? ["ORU_R01" {:type "ORU_R01.CONTENT"}])
  ;;=> true

  (annotation-node? ["HD.1.CONTENT"
                     ["annotation"
                      ["documentation" {:lang "en"} "Namespace ID"]
                      ["documentation" {:lang "de"} "Identifikator"]
                      ["appinfo" ["Type" "HD.1"] ["LongName" "Namespace ID"]]]
                     ["complexContent" ["extension" {:base "IS"} ["HD.1.ATTRIBUTES"]]]])

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

  (type-base ["complexContent"
              ["extension" {:base "IS"}
               ["HD.1.ATTRIBUTES"]]])
  ;;=> "IS"

  (restricted-node? ["ID"
                     ["restriction" {:base "xsd:string"}]])
  ;;=> true

  (annotated-type-node? ["HD.1.CONTENT"
                         ["annotation"
                          ["documentation" {:lang "en"} "Namespace ID"]
                          ["documentation" {:lang "de"} "Identifikator"]
                          ["appinfo" ["Type" "HD.1"] ["LongName" "Namespace ID"]]]
                         ["complexContent" ["extension" {:base "IS"} ["HD.1.ATTRIBUTES"]]]])

  (xsd-zip "ACK")

  (->> (xsd-zip "ACK")
       (iterate zip/next)
       (take-while (complement zip/end?))
       (map zip/node))

  (zip-tree (xsd-zip "ACK"))

  (zip-tree
   (->> (xsd-zip "ACK")
        (iterate zip/next)
        (take-while (complement zip/end?))
        (filter (comp annotation-node? zip/node))
        (map (fn [loc]
               (zip/edit loc attr-kw)))
        (first)
        (zip/up)
        (zip/root)))

  :.)