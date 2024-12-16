(ns hl7v2.triger-events
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.walk :refer [postwalk]]
   [hl7v2.complex :refer [clean]]
   [clojure.string :as str]))

(defn parse-xsd [x]
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

(defn xsd-schema-root [msg]
  (->> (drop 1 msg)
       (remove (comp #{"include"} first))
       (filter #(= 1 (count (str/split (first %) #"\."))))
       (first)))

(defn full-node [node index]
  (let [[tag attrs] node]
    (cond
      (:type attrs) (->> (for [node (get index (:type attrs))]
                           (full-node node index))
                         (remove string?)
                         (concat [(last (str/split tag #"\.")) attrs])
                         (into []))
      (get index tag) (let [[t a & more] (get index tag)]
                        (full-node (concat [t (merge attrs a)] more) index))
      :else node)))

(defn parse-schema [x]
  (let [[_root & content :as msg] (parse-xsd x)
        index (into {} (map (juxt first identity) content))]
    (-> (xsd-schema-root msg)
        (full-node index))))

(comment

  (parse-xsd (io/file "test/hl7v2/data/ACK.xsd"))
  ;;=> ["schema"
  ;;    ["include" {:schema "segments.xsd"}]
  ;;    ["ACK.CONTENT"
  ;;     ["MSH" {:min "1", :max "1"}]
  ;;     ["SFT" {:min "0", :max "unbounded"}]
  ;;     ["MSA" {:min "1", :max "1"}]
  ;;     ["ERR" {:min "0", :max "unbounded"}]]
  ;;    ["ACK" {:type "ACK.CONTENT"}]]

  (parse-schema (io/file "test/hl7v2/data/ACK.xsd"))
  ;;=> ["ACK"
  ;;    {:type "ACK.CONTENT"}
  ;;    ["MSH" {:min "1", :max "1"}]
  ;;    ["SFT" {:min "0", :max "unbounded"}]
  ;;    ["MSA" {:min "1", :max "1"}]
  ;;    ["ERR" {:min "0", :max "unbounded"}]]

  (parse-schema (io/file "test/hl7v2/data/ORU_R01.xsd"))

  :.)