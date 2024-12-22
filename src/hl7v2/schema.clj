(ns hl7v2.schema
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.walk :refer [postwalk]]
   [hl7v2.complex :refer [clean]])
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

(letfn [(load-schema []
          (let [files ["tmp/HL7-xml v2.5.1/ORU_R01.xsd"
                       "tmp/HL7-xml v2.5.1/segments.xsd"
                       "tmp/HL7-xml v2.5.1/datatypes.xsd"
                       "tmp/HL7-xml v2.5.1/fields.xsd"]]
            (->> (for [f files]
                   (-> (io/file f)
                       (parse-xsd)
                       (index-schema)))
                 (apply merge))))
        (fill-node [node index]
          (let [tag (node-tag node)
                attrs (node-attrs node)
                children (node-children node)]
            (cond
              (content-node? node) (let [content (get index (:type attrs))
                                         node (fill-node content index)]
                                     (->> (node-children node)
                                          (mapcat (fn [n]
                                                    (if (sequence-node? n)
                                                      (fill-node n index)
                                                      [(fill-node n index)])))
                                          (concat [tag (merge attrs (dissoc (node-attrs node) :type))])
                                          (into [])))
              (sequence-node? node) (->> (for [node children]
                                           (fill-node node index))
                                         (into []))
              (group-node? node) (let [group (get index tag)
                                       node (fill-node group index)
                                       tag (last (str/split tag #"\."))]
                                   (->> (node-children node)
                                        (concat [tag (merge attrs (dissoc (node-attrs node) :type))])
                                        (into [])))

              :else node)))]
  (let [tgr-evt "ORU_R01"
        index (load-schema)
        root (get index tgr-evt)]
    (fill-node root index)
    #_(get index "ORU_R01.CONTENT")))

(defn sequence-item [sequence]
  [(first (str/split (first sequence) #"\."))
   (->> sequence
        (filter vector?)
        (filter (comp #{"sequence"} first))
        (mapcat (partial drop 1))
        (remove (comp #{"any"} first))
        (map (juxt #(-> % first (str/split #"\.") last parse-long) second))
        (sort-by (fnil first 0))
        (into (sorted-map)))])

(defn content-item [content]
  (letfn [(rm-diacritics [s]
            (when s
              (-> s
                  (Normalizer/normalize Normalizer$Form/NFD)
                  (str/replace #"[\u0300-\u036f]" ""))))]
    [(->> content
          (filter vector?)
          (filter (comp #{"annotation"} first))
          (mapcat (partial drop 1))
          (some #(when (and (= "documentation" (first %))
                            (= "en" (:lang (second %))))
                   (->> (str/split (last %) #"\s+")
                        (map rm-diacritics)
                        (str/join "")))))
     {:type (->> content
                 (filter vector?)
                 (filter (comp #{"complexContent"} first))
                 (mapcat (partial drop 1))
                 (some #(when (= "extension" (first %))
                          (:base (second %)))))}]))

(comment

  (parse-xsd (io/file "test/hl7v2/data/ACK.xsd"))
  ;;=> ["schema"
  ;;    {:targetNamespace "urn:hl7-org:v2xml", :version "1.1"}
  ;;    ["include" {:schemaLocation "segments.xsd"}]
  ;;    ["ACK.CONTENT"
  ;;     ["sequence"
  ;;      ["MSH" {:minOccurs "1", :maxOccurs "1"}]
  ;;      ["SFT" {:minOccurs "0", :maxOccurs "unbounded"}]
  ;;      ["MSA" {:minOccurs "1", :maxOccurs "1"}]
  ;;      ["ERR" {:minOccurs "0", :maxOccurs "unbounded"}]]]
  ;;    ["ACK" {:type "ACK.CONTENT"}]]

  (-> (io/file "test/hl7v2/data/ACK.xsd")
      (parse-xsd)
      (index-schema))

  (-> (io/file "tmp/HL7-xml v2.5.1/segments.xsd")
      (parse-xsd)
      (index-schema)
      (get "PID.CONTENT")
      #_(sequence-item))

  (-> (io/file "tmp/HL7-xml v2.5.1/datatypes.xsd")
      (parse-xsd)
      (index-schema)
      (get "XPN")
      #_(sequence-item))

  (-> (io/file "tmp/HL7-xml v2.5.1/datatypes.xsd")
      (parse-xsd)
      (index-schema)
      (get "FN")
      #_(sequence-item))

  (-> (io/file "tmp/HL7-xml v2.5.1/fields.xsd")
      (parse-xsd)
      (index-schema)
      (get "PID.5.CONTENT")
      #_(content-item))
  ;;=> ["PID.5.CONTENT"
  ;;    ["annotation"
  ;;     ["documentation" {:lang "en"} "Patient Name"]
  ;;     ["documentation" {:lang "de"} "Patientenname"]
  ;;     ["appinfo" ["Item" "108"] ["Type" "XPN"] ["LongName" "HL7Patient\n                Name"]]]
  ;;    ["complexContent" ["extension" {:base "XPN"} ["PID.5.ATTRIBUTES"]]]]

  (-> (io/file "tmp/HL7-xml v2.5.1/datatypes.xsd")
      (parse-xsd)
      (index-schema)
      (get "XPN.1.CONTENT")
      #_(content-item))
  ;;=> ["XPN.1.CONTENT"
  ;;    ["annotation"
  ;;     ["documentation" {:lang "en"} "Family Name"]
  ;;     ["documentation" {:lang "de"} "Familienname"]
  ;;     ["appinfo" ["Type" "XPN.1"] ["LongName" "Family Name"]]]
  ;;    ["complexContent" ["extension" {:base "FN"} ["XPN.1.ATTRIBUTES"]]]]

  (-> (io/file "tmp/HL7-xml v2.5.1/datatypes.xsd")
      (parse-xsd)
      (index-schema)
      (get "FN.1.CONTENT")
      #_(content-item))
  ;;=> ["FN.1.CONTENT"
  ;;    ["annotation"
  ;;     ["documentation" {:lang "en"} "Surname"]
  ;;     ["documentation" {:lang "de"} "gesamter Nachname"]
  ;;     ["appinfo" ["Type" "FN.1"] ["LongName" "Surname"]]]
  ;;    ["complexContent" ["extension" {:base "ST"} ["FN.1.ATTRIBUTES"]]]]

  (-> (io/file "tmp/HL7-xml v2.5.1/datatypes.xsd")
      (parse-xsd)
      (index-schema)
      (get "ST"))
  ;;=> ["ST" ["restriction" {:base "xsd:string"}]]

  :.)