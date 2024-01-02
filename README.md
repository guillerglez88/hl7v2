# hl7v2

Hl7v2 Clojure library.

## parse

Parse hl7 message, any `io/reader` input is allowed.

``` clojure
  (parse (.getBytes (str "MSH|^~\\&|Test||||200701011539||ADT^A01^ADT A01||||123\r\n"
                         "PID|||123456||Doe^John")))

  ;; => [{:MSH
  ;;      {1 "|"
  ;;       2 "^~\\&"
  ;;       3 "Test"
  ::       7 "200701011539"
  ;;       9 {1 "ADT" 
  ;;          2 "A01" 
  ;;          3 "ADT A01"}
  ;;       13 "123"}}
  ;;     {:PID {3 "123456"
  ;;            5 {1 "Doe"
  ;;               2 "John"}}}]
```

## format

Encode data into hl7 string.

``` clojure
  (format [{:MSH
            {7 "200701011539",
             13 "123",
             3 "Test",
             2 "^~\\&",
             9 {1 "ADT", 2 "A01", 3 "ADT A01"},
             1 "|"}}
           {:PID {3 "123456", 5 {1 "Doe", 2 "John"}}}])

  ;; => "MSH|^~\\&|Test||||200701011539||ADT^A01^ADT A01||||123\r\n
  ;;     PID|||123456||Doe^John"
```

---

Standard: https://www.hl7.org/implement/standards/product_brief.cfm?product_id=185
Schema: https://www.hl7.org/documentcenter/private/standards/V251/HL7-xml%20v2.5.1.zip
