# hl7v2

Hl7v2 Clojure library.

## parse

Parse hl7 message, any `io/reader` input is allowed.

``` clojure
  (parse (.getBytes (str "MSH|^~\\&|Test||||200701011539||ADT^A01^ADT A01||||123\r\n"
                         "PID|||123456||Doe^John")))
  ;; => [{:MSH
  ;;      {1 "|",
  ;;       2 "^~\\&",
  ;;       3 "Test",
  ;;       7 "200701011539",
  ;;       9 {1 "ADT", 2 "A01", 3 "ADT A01"},
  ;;       13 "123"}}
  ;;     {:PID {3 "123456", 5 {1 "Doe", 2 "John"}}}]
```

Ability to output token data. Sample token:

```clojure
{:value "ADT"
 :kind :data
 :location {:from [1 31]
            :to   [1 34]}}
```

```clojure
  (parse (.getBytes (str "MSH|^~\\&|Test||||200701011539||ADT^A01^ADT A01||||123\r\n"
                         "PID|||123456||Doe^John"))
         :val-fn (juxt :value :location))
  ;; => [{:MSH
  ;;      {1 ["|" {:from [1 3], :to [1 4]}],
  ;;       2 ["^~\\&" {:from [1 4], :to [1 8]}],
  ;;       3 ["Test" {:from [1 9], :to [1 13]}],
  ;;       7 ["200701011539" {:from [1 17], :to [1 29]}],
  ;;       9
  ;;       {1 ["ADT" {:from [1 31], :to [1 34]}],
  ;;        2 ["A01" {:from [1 35], :to [1 38]}],
  ;;        3 ["ADT A01" {:from [1 39], :to [1 46]}]},
  ;;       13 ["123" {:from [1 50], :to [1 53]}]}}
  ;;     {:PID
  ;;      {3 ["123456" {:from [2 6], :to [2 12]}],
  ;;       5
  ;;       {1 ["Doe" {:from [2 14], :to [2 17]}],
  ;;        2 ["John" {:from [2 18], :to [2 22]}]}}}]
```

## format

Encode data into hl7 string.

``` clojure
  (format [{:MSH
            {1 "|",
             2 "^~\\&",
             3 "Test",
             7 "200701011539",
             9 {1 "ADT", 2 "A01", 3 "ADT A01"},
             13 "123"}}
           {:PID {3 "123456", 5 {1 "Doe", 2 "John"}}}])

  ;; => "MSH|^~\\&|Test||||200701011539||ADT^A01^ADT A01||||123\r\n
  ;;     PID|||123456||Doe^John"
```

---

Standard: https://www.hl7.org/implement/standards/product_brief.cfm?product_id=185   
Schema: https://www.hl7.org/documentcenter/private/standards/V251/HL7-xml%20v2.5.1.zip
