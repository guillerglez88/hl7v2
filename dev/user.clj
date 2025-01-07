(ns user
  (:require
   [clojure.pprint :as pp]
   [hl7v2.structures :refer [gen-structure]]))

(comment

  (let [dir "structures/v2.3.1"]
    (doseq [item (read-string (slurp (str dir "/index.edn")))]
      (println "generating structure for" item)
      (spit
       (format "%s/%s.edn" dir item)
       (with-out-str
         (pp/pprint (gen-structure item
                                   :standard-dir "/Users/guille/Downloads/v2.3.1"
                                   :version "2.3.1"
                                   :lang "en"))))))

  :.)