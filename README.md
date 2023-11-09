# hl7v2

Hl7v2 Clojure library.

## parse

Parse hl7 message, any `io/reader` input is allowed. Returns a tuple `[seg map]` coll, `seg` is the three letters segment code and `map` is the segment data. The vector key may contain, in this order: `[:field :repetition :component :subcomponent]`.

``` clojure
(hl7/parse (.getBytes (str "MSH|^~\\&|Test||||200701011539||ADT^A01^ADT A01||||123\r\n"
                           "PID|||123456||Doe^John")))

;; => [["MSH" {[1] \|, [2] "^~\\&", [3] "Test", [7] "200701011539", [9] "ADT", [9 0 1] "A01", [9 0 2] "ADT A01", [13] "123"}]
;;     ["PID" {[4] "123456", [6] "Doe", [6 0 1] "John"}]]
```

## format

Encode data into hl7 string.

``` clojure
(hl7/format [["MSH" {[1] \|, [2] "^~\\&", [3] "TestSendingSystem", [7] "200701011539", [9] "ADT", [9 0 1] "A01", [9 0 2] "ADT A01", [13] "123"}]
             ["PID" {[4] "123456", [6] "Doe", [6 0 1] "John"}]])

;; => MSH|^~\\&|TestSendingSystem||||200701011539||ADT^A01^ADT A01||||123\r\n
;;    PID||||123456||Doe^John
```

---

Standard: https://www.hl7.org/implement/standards/product_brief.cfm?product_id=185
Schema: https://www.hl7.org/documentcenter/private/standards/V251/HL7-xml%20v2.5.1.zip
