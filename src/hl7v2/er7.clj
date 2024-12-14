(ns hl7v2.er7
  (:require
   [clojure.java.io :as io])
  (:import
   (java.io Reader)))

(defn char-seq [^Reader rx]
  (let [ch (.read rx)]
    (when-not (= ch -1)
      (cons (char ch) (lazy-seq (char-seq rx))))))

(defn parse-encoding [cseq]
  (let [[m s h fld cmp rep esc sub & more] cseq
        [ret nli] [\return \newline]]
    (when-not (= [\M \S \H] [m s h])
      (throw (ex-info "Unexpected segment"
                      {:expected "MSH"
                       :actual (str m s h)})))
    (let [kind [:fld :cmp :rep :esc :seg :seg]
          sep [fld  cmp  rep  esc  ret  nli]]
      {:separators #{fld cmp sub esc ret nli}
       :index {:ks (zipmap kind sep)
               :sk (zipmap sep kind)}
       :remaining (into [] (concat (take-while (partial not= fld) more) [fld]))})))

(defn format-encoding [m]
  (apply str
         "MSH|"
         (get-in m [:index :ks :cmp])
         (get-in m [:index :ks :rep])
         (get-in m [:index :ks :esc])
         (get-in m [:index :ks :sub])
         (:remaining m)))

(defn escape [esc cseq]
  (let [[curr next & more] cseq]
    (when curr
      (if (= esc curr)
        (cons (str curr next)
              (lazy-seq (escape esc more)))
        (cons curr
              (lazy-seq (escape esc (cons next more))))))))

(defn tokens
  ([encoding cseq]
   (tokens encoding [1 0] cseq))
  ([encoding [line col :as from] cseq]
   (let [sep? (if (= from [1 4])
                #{(get-in encoding [:index :ks :fld])}
                (:separators encoding))]
     (when cseq
       (let [token (take-while (complement sep?) cseq)
             move-count (count token)
             [sep next & more] (drop move-count cseq)
             value (apply str token)
             kind (get-in encoding [:index :sk sep])
             to [line (+ col move-count)]
             seg-x2? (= :seg kind (get-in encoding [:index :sk next]))
             sep-to [line (+ (second to) (if seg-x2? 2 1))]]
         (concat
          [{:value value
            :kind :data
            :loc {:from from :to to}}
           {:value (if seg-x2? (str sep next) (str sep))
            :kind kind
            :loc {:from to :to sep-to}}]
          (lazy-seq
           (tokens encoding
                   (if (= :seg kind) [(inc line) 0] sep-to)
                   (if (and (not seg-x2?) next) (cons next more) more)))))))))

(comment

  (with-open [rx (io/reader (.getBytes "MSH|^~\\&|ULTRA|TML|OLIS"))]
    (parse-encoding (char-seq rx)))
  ;;=> {:separators #{\& \newline \return \\ \| \^},
  ;;    :index
  ;;    {:ks {:fld \|, :cmp \^, :rep \~, :esc \\, :seg \newline},
  ;;     :sk {\| :fld, \^ :cmp, \~ :rep, \\ :esc, \return :seg, \newline :seg}},
  ;;    :remaining [\|]}

  (format-encoding {:separators #{\& \newline \return \\ \| \^},
                    :index {:ks {:fld \|, :cmp \^, :rep \~, :esc \\, :seg \newline},
                            :sk {\| :fld, \^ :cmp, \~ :rep, \\ :esc, \return :seg, \newline :seg}},
                    :remaining [\|]})
  ;;=> "MSH|^~\\|"

  (with-open [rx (io/reader (.getBytes "testing\\&escape\\^chars"))]
    (into [] (escape \\ (char-seq rx))))
  ;;=> [\t \e \s \t \i \n \g "\\&" \e \s \c \a \p \e "\\^" \c \h \a \r \s]

  (with-open [rx (io/reader (io/file "test/hl7v2/data/oru-r01.hl7"))]
    (time
     (into []
           (tokens {:separators #{\& \newline \return \\ \| \^},
                    :index
                    {:ks {:fld \|, :cmp \^, :rep \~, :esc \\, :seg \newline},
                     :sk {\| :fld, \^ :cmp, \~ :rep, \\ :esc, \return :seg, \newline :seg}},
                    :remaining [\|]}
                   (char-seq rx)))))

  :.)