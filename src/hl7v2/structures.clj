(ns hl7v2.structures
  (:require
   [clojure.zip :as zip]
   [hl7v2.primitives :refer [slug]]
   [hl7v2.complex :refer [clean]]
   [hl7v2.schema :as sch]
   [clojure.string :as str]))

(defn schema-zip [schema]
  (zip/zipper (comp seq sch/node-children)
              sch/node-children
              (fn [n c]
                (->> [[(sch/node-tag n) (sch/node-attrs n)] c]
                     (apply concat)
                     (remove nil?)
                     (into [])))
              schema))

(defn process-annotation [loc & {:keys [lang] :or {lang "en"}}]
  (->> (zip/down loc)
       (iterate zip/right)
       (take-while (complement nil?))
       (some (fn [loc]
               (let [node (zip/node loc)]
                 (when (and (= "documentation" (sch/node-tag node))
                            (= lang (-> node sch/node-attrs :lang)))
                   (if-let [text-loc (zip/down loc)]
                     (-> text-loc zip/node sch/node-tag slug keyword)
                     (-> loc zip/node sch/node-tag keyword))))))))

(defn leaf [loc & {:keys [lang] :or {lang "en"}}]
  (when (and (= (some-> loc zip/node sch/node-tag)
                (some-> loc zip/down zip/node sch/node-tag))
             (= (some-> loc zip/down zip/node sch/node-attrs :type)
                (some-> loc zip/down zip/down zip/node sch/node-tag))
             (= (some-> loc zip/down zip/down zip/down zip/node sch/node-tag) "annotation")
             (or (= (some-> loc zip/down zip/down zip/down zip/right zip/node sch/node-tag) "complexContent")
                 (= (some-> loc zip/down zip/down zip/down zip/right zip/node sch/node-tag) "simpleContent"))
             (or (= (some-> loc zip/down zip/down zip/down zip/right zip/down zip/down zip/node sch/node-tag) "restriction")
                 (= (some-> loc zip/down zip/down zip/down zip/right zip/down zip/node sch/node-tag) "extension")
                 (= (some-> loc zip/down zip/down zip/down zip/right zip/down zip/node sch/node-tag) "varies")))
    [(-> loc zip/down zip/down zip/down (process-annotation :lang lang))
     (let [{:keys [minOccurs maxOccurs]} (-> loc zip/node sch/node-attrs)]
       (clean
        {:field (-> loc zip/node sch/node-tag)
         :required (when minOccurs
                     (pos? (parse-long minOccurs)))
         :repeats (when maxOccurs
                    (or (= "unbounded" maxOccurs)
                        (> 1 (parse-long maxOccurs))))
         :type (or (-> loc zip/down zip/down zip/down zip/right zip/down zip/node sch/node-attrs :base)
                   (-> loc zip/down zip/down zip/down zip/right zip/down zip/node sch/node-tag))}))]))

(defn complex [loc & {:keys [lang] :or {lang "en"}}]
  (when (and (= (some-> loc zip/node sch/node-tag)
                (some-> loc zip/down zip/node sch/node-tag))
             (= (some-> loc zip/down zip/node sch/node-attrs :type)
                (some-> loc zip/down zip/down zip/node sch/node-tag))
             (= (some-> loc zip/down zip/down zip/down zip/node sch/node-tag) "annotation")
             (= (some-> loc zip/down zip/down zip/down zip/right zip/node sch/node-tag) "complexContent")
             (= (some-> loc zip/down zip/down zip/down zip/right zip/down zip/down zip/node sch/node-tag) "sequence"))
    (->> [[(-> loc zip/down zip/down zip/down (process-annotation :lang lang))
           (let [{:keys [minOccurs maxOccurs]} (-> loc zip/node sch/node-attrs)]
             (clean
              {:field (-> loc zip/node sch/node-tag)
               :required (when minOccurs
                           (pos? (parse-long minOccurs)))
               :repeats (when maxOccurs
                          (or (= "unbounded" maxOccurs)
                              (> 1 (parse-long maxOccurs))))
               :type (-> loc zip/down zip/down zip/down zip/right zip/down zip/node sch/node-tag)}))]
          (-> loc zip/down zip/down zip/down zip/right zip/down zip/down zip/node sch/node-children)]
         (apply concat)
         (into []))))

(defn group [loc]
  (when (and (= (some-> loc zip/node sch/node-tag)
                (some-> loc zip/down zip/node sch/node-tag))
             (= (some-> loc zip/down zip/node sch/node-attrs :type)
                (some-> loc zip/down zip/down zip/node sch/node-tag))
             (= (some-> loc zip/down zip/down zip/down zip/node sch/node-tag) "sequence"))
    (->> [[(-> loc zip/node sch/node-tag (str/split #"\.") last keyword)
           (let [{:keys [minOccurs maxOccurs]} (-> loc zip/node sch/node-attrs)]
             (clean
              {:required (when minOccurs
                           (> 0 (parse-long minOccurs)))
               :repeats (when maxOccurs
                          (or (= "unbounded" maxOccurs)
                              (> 1 (parse-long maxOccurs))))}))]
          (-> loc zip/down zip/down zip/down zip/node sch/node-children)]
         (apply concat)
         (remove (comp #{"any"} sch/node-tag))
         (into []))))

(defn root [loc & {:keys [version]}]
  (when (and (= (some-> loc zip/node sch/node-attrs :type)
                (some-> loc zip/down zip/node sch/node-tag))
             (= (some-> loc zip/down zip/down zip/node sch/node-tag) "sequence"))
    (->> [[(-> loc zip/node sch/node-tag keyword)
           (when version
             {:version version})]
          (-> loc zip/down zip/down zip/node sch/node-children)]
         (apply concat)
         (remove nil?)
         (into []))))

(defn gen-structure [trigger-event & {:keys [standard-dir version lang]}]
  (loop [loc (schema-zip (sch/load-schema trigger-event standard-dir))
         [process & more :as processors] [#(leaf % :lang lang)
                                          #(complex % :lang lang)
                                          group
                                          #(root % :version version)]]
    (if (zip/end? loc)
      (if (seq more)
        (recur (schema-zip (zip/root loc)) more)
        (zip/root loc))
      (if-let [sub (process loc)]
        (recur (zip/replace loc sub) processors)
        (recur (zip/next loc) processors)))))

(comment

  (-> ["annotation"
       ["documentation" {:lang "en"} ["Namespace ID"]]
       ["documentation" {:lang "de"} ["Identifikator"]]
       ["appinfo" ["Type" ["HD.1"]] ["LongName" ["Namespace ID"]]]]
      (schema-zip)
      (process-annotation))
  ;;=> :namespace-id

  (-> ["HD.1"
       {:minOccurs "0", :maxOccurs "1"}
       ["HD.1"
        {:type "HD.1.CONTENT"}
        ["HD.1.CONTENT"
         ["annotation"
          ["documentation" {:lang "en"} ["Namespace ID"]]
          ["documentation" {:lang "de"} ["Identifikator"]]
          ["appinfo" ["Type" ["HD.1"]] ["LongName" ["Namespace ID"]]]]
         ["complexContent" ["IS" ["restriction" {:base "xsd:string"} ["restriction" {:base "xsd:string"}]]]]]]]
      (schema-zip)
      (leaf))
  ;;=> [:namespace-id {:field "HD.1", :required false, :repeats false, :type "IS"}]

  (-> ["MSH.3"
       {:minOccurs "0", :maxOccurs "1"}
       ["MSH.3"
        {:type "MSH.3.CONTENT"}
        ["MSH.3.CONTENT"
         ["annotation"
          ["documentation" {:lang "en"} ["Sending Application"]]
          ["documentation" {:lang "de"} ["Sendende Anwendung / Sendender Bereich"]]
          ["appinfo" ["Item" ["3"]] ["Type" ["HD"]] ["Table" ["HL70361"]] ["LongName" ["HL7Sending Application"]]]]
         ["complexContent"
          ["HD"
           ["sequence"
            [:namespace-id {:field "HD.1", :required false, :repeats false, :type "IS"}]
            [:universal-id {:field "HD.2", :required false, :repeats false, :type "ST"}]
            [:universal-id-type {:field "HD.3", :required false, :repeats false, :type "ID"}]]]]]]]
      (schema-zip)
      (complex))
  ;;=> [:sending-application
  ;;    {:field "MSH.3", :required false, :repeats false, :type "HD"}
  ;;    [:namespace-id {:field "HD.1", :required false, :repeats false, :type "IS"}]
  ;;    [:universal-id {:field "HD.2", :required false, :repeats false, :type "ST"}]
  ;;    [:universal-id-type {:field "HD.3", :required false, :repeats false, :type "ID"}]]

  (-> ["MSA"
       {:minOccurs "1", :maxOccurs "1"}
       ["MSA"
        {:type "MSA.CONTENT"}
        ["MSA.CONTENT"
         ["sequence"
          [:acknowledgment-code {:field "MSA.1", :required false, :repeats false, :type "ID"}]
          [:message-control-id {:field "MSA.2", :required false, :repeats false, :type "ST"}]
          [:text-message {:field "MSA.3", :required false, :repeats false, :type "ST"}]
          [:expected-sequence-number {:field "MSA.4", :required false, :repeats false, :type "NM"}]
          [:error-condition
           {:field "MSA.6", :required false, :repeats false, :type "CE"}
           [:identifier {:field "CE.1", :required false, :repeats false, :type "ST"}]
           [:text {:field "CE.2", :required false, :repeats false, :type "ST"}]
           [:name-of-coding-system {:field "CE.3", :required false, :repeats false, :type "ID"}]
           [:alternate-identifier {:field "CE.4", :required false, :repeats false, :type "ST"}]
           [:alternate-text {:field "CE.5", :required false, :repeats false, :type "ST"}]
           [:name-of-alternate-coding-system {:field "CE.6", :required false, :repeats false, :type "ID"}]]
          ["any" {:processContents "lax", :namespace "##other", :minOccurs "0"} []]]]]]
      (schema-zip)
      (group))
  ;;=> [:MSA
  ;;    {:required false, :repeats false}
  ;;    [:acknowledgment-code {:field "MSA.1", :required false, :repeats false, :type "ID"}]
  ;;    [:message-control-id {:field "MSA.2", :required false, :repeats false, :type "ST"}]
  ;;    [:text-message {:field "MSA.3", :required false, :repeats false, :type "ST"}]
  ;;    [:expected-sequence-number {:field "MSA.4", :required false, :repeats false, :type "NM"}]
  ;;    [:error-condition
  ;;     {:field "MSA.6", :required false, :repeats false, :type "CE"}
  ;;     [:identifier {:field "CE.1", :required false, :repeats false, :type "ST"}]
  ;;     [:text {:field "CE.2", :required false, :repeats false, :type "ST"}]
  ;;     [:name-of-coding-system {:field "CE.3", :required false, :repeats false, :type "ID"}]
  ;;     [:alternate-identifier {:field "CE.4", :required false, :repeats false, :type "ST"}]
  ;;     [:alternate-text {:field "CE.5", :required false, :repeats false, :type "ST"}]
  ;;     [:name-of-alternate-coding-system {:field "CE.6", :required false, :repeats false, :type "ID"}]]]

  (-> ["ACK"
       {:type "ACK.CONTENT"}
       ["ACK.CONTENT"
        ["sequence"
         [:MSH]
         [:SFT]]]]
      (schema-zip)
      (root))
  ;;=> [:ACK [:MSH] [:SFT]]

  (schema-zip (sch/load-schema "ACK" "/Users/guille/Downloads/HL7-xml-v2.5.1"))

  (gen-structure "ORU_R01"
                 :standard-dir "/Users/guille/Downloads/HL7-xml-v2.5.1"
                 :versiown "2.5.1"
                 :lang "en")

  :.)