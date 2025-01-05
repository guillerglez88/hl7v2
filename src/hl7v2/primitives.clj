(ns hl7v2.primitives
  (:require
   [clojure.string :as str])
  (:import
   (java.text Normalizer Normalizer$Form)))

(defn rm-diacritics [s]
  (when s
    (-> (Normalizer/normalize s Normalizer$Form/NFD)
        (str/replace #"[\u0300-\u036f]" ""))))

(defn sanitize [s]
  (-> s
      (str/replace #"/" "or")
      (str/replace #"&" "and")
      (str/replace #"-" "")
      (str/replace #"'" "")
      (str/replace #"\)" "")
      (str/replace #"\." "")))

(defn normalize-text [s]
  (-> (rm-diacritics s)
      (sanitize)
      (str/trim)
      (str/lower-case)))

(defn slug [s]
  (->> (str/split s #"\(")
       (take 1)
       (mapcat #(str/split % #"\s+"))
       (map normalize-text)
       (remove str/blank?)
       (str/join "-")))

(comment

  (slug "Clinic Organization Name")
  ;;=> "clinic-organization-name"

  (slug "Suffix (e.g., JR or III)")
  ;;=> "suffix"

  (slug "Assigning Authority - Namespace ID")
  ;;=> "assigning-authority-namespace-id"

  (slug "Date/Time Certification Required")
  ;;=> "dateortime-certification-required"

  :.)