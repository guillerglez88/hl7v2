(ns hl7v2.core
  (:require
   [hl7v2.er7 :as er7]))

(defn parse [x & {:keys [val-fn] :or {val-fn :value} :as opt}]
  (er7/parse-er7 x opt))

(defn format [msg & {:keys [line-break] :or {line-break "\n"} :as opt}]
  (er7/format-er7 msg opt))

(defn structure [trigger-event msg]
  (letfn [(extract [[h & t]]
            (if (map? (first t))
              (let [[attrs & children] t]
                {:tag h
                 :attrs attrs
                 :group? (seq children)
                 :children children})
              {:tag h
               :group? (seq t)
               :children t}))
          (match [[item & items] [seg & segments]]
            (if (and item seg)
              (let [{:keys [tag _attrs group? children]} (extract item)]
                (if group?
                  (let [[data segs] (reduce (fn [acc curr]
                                              (let [[data segs] (match [curr] (second acc))]
                                                [(vec (concat (first acc) data)) segs]))
                                            [nil (cons seg segments)]
                                            children)]
                    (if (seq data)
                      [[{tag data}] segs]
                      [nil segs]))
                  (if (get seg tag)
                    [[seg] segments]
                    (match items (cons seg segments)))))
              (when-not item
                [nil (cons seg segments)])))]
    (ffirst (match [trigger-event] msg))))

(comment

  :.)