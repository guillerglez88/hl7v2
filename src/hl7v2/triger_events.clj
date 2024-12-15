(ns hl7v2.triger-events
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.walk :refer [postwalk]]
   [hl7v2.complex :refer [clean]]
   [clojure.string :as str]))

(defn parse-schema [x]
  (with-open [rx (io/reader x)]
    (letfn [(tag [n]
              (when n
                (or (get-in n [:attrs :name])
                    (get-in n [:attrs :ref])
                    (name (:tag n)))))
            (attrs [n]
              (clean
               {:min (get-in n [:attrs :minOccurs])
                :max (get-in n [:attrs :maxOccurs])
                :type (get-in n [:attrs :type])
                :schema (get-in n [:attrs :schemaLocation])}))]
      (postwalk (fn [n]
                  (if (xml/element? n)
                    (->> (concat
                          [(tag n) (attrs n)]
                          (if (= "complexType" (name (:tag n)))
                            (->> (:content n)
                                 (remove string?)
                                 (mapcat (partial drop 1)))
                            (->> (:content n)
                                 (remove string?))))
                         (remove nil?)
                         (into []))
                    n))
                (xml/parse rx)))))

#_#_#_#_:required (when-let [min (get-in n [:attrs :minOccurs])]
                    (> (parse-long min) 0))
    :repeats (when-let [max (get-in n [:attrs :maxOccurs])]
               (or (= "unbounded" max)
                   (> (parse-long max) 0)))

(fn [[_schema & content]]
  (let [index (->> content
                   (map (juxt first rest))
                   (into {}))]
    (->> (for [[tag attrs] content
               :when (and (map? attrs)
                          (some? (:type attrs)))]
           (->> (get index (:type attrs))
                (cons tag)
                (into [])))
         (into []))))

(comment

  (parse-schema (io/file "test/hl7v2/data/ACK.xsd"))
  ;;=> ["schema"
  ;;    ["include" {:schema "segments.xsd"}]
  ;;    ["ACK.CONTENT"
  ;;     ["MSH" {:min "1", :max "1"}]
  ;;     ["SFT" {:min "0", :max "unbounded"}]
  ;;     ["MSA" {:min "1", :max "1"}]
  ;;     ["ERR" {:min "0", :max "unbounded"}]]
  ;;    ["ACK" {:type "ACK.CONTENT"}]]

  (parse-schema (io/file "test/hl7v2/data/ORU_R01.xsd"))

  :.)