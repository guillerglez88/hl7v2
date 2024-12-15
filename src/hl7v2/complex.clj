(ns hl7v2.complex
  (:require
   [clojure.walk :refer [postwalk]]))

(defn clean [m]
  (postwalk (fn [v]
              (cond
                (map-entry? v) (when-not (nil? (val v)) v)
                (map? v) (when-let [entries (seq (remove #(-> % second nil?) v))] (into {} entries))
                (vector? v) (when-let [coll (seq (remove nil? v))] (into [] coll))
                (seq? v) (remove nil? v)
                :else v)) m))