(ns hl7v2.triger-events
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [hl7v2.schema :refer [parse-xsd xsd-schema-root]]))

(defn full-node [node index]
  (let [[tag attrs & content] node]
    (cond
      (:type attrs) (->> (for [node (get index (:type attrs))]
                           (full-node node index))
                         (remove string?)
                         (concat [(keyword (last (str/split tag #"\."))) attrs])
                         (into []))
      (get index tag) (let [[t a & content] (get index tag)]
                        (full-node (concat [t (merge attrs a)] content) index))
      (not (seq content)) [(keyword tag) (assoc attrs :type tag)]
      :else node)))

(defn parse-structure [x]
  (let [[_root & content :as msg] (parse-xsd x)
        index (into {} (map (juxt first identity) content))]
    (-> (xsd-schema-root msg)
        (full-node index))))

(comment

  (parse-structure (io/file "test/hl7v2/data/ACK.xsd"))
  ;;=> ["ACK"
  ;;    {:type "ACK.CONTENT"}
  ;;    ["MSH" {:min "1", :max "1"}]
  ;;    ["SFT" {:min "0", :max "unbounded"}]
  ;;    ["MSA" {:min "1", :max "1"}]
  ;;    ["ERR" {:min "0", :max "unbounded"}]]

  (parse-structure (io/file "test/hl7v2/data/ORU_R01.xsd"))

  :.)