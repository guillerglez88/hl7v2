# hl7v2

Hl7v2 Clojure lib. Only vectors and map.


## parse

Parse hl7 message, any `io/reader` input is allowed (byte-array, file-path, etc), returns a vector of tuples `[seg map]` where `seg` is the three letters segment code and `map` is a Clojure map with the segment data with a vector as the key. The vector key may contain in this order: `[:field :repetition :component :subcomponent]`.

``` clojure
(parse (.getBytes (str "MSH|^~\\&|TestSendingSystem||||200701011539||ADT^A01^ADT A01||||123\r\n"
                       "PID|||123456||Doe^John")))
;; => [["MSH"
;;      {[1] \|,
;;       [2] "^~\\&",
;;       [3] "TestSendingSystem",
;;       [7] "200701011539",
;;       [9] "ADT",
;;       [9 0 1] "A01",
;;       [9 0 2] "ADT A01",
;;       [13] "123"}]
;;     ["PID"
;;      {[4] "123456",
;;       [6] "Doe",
;;       [6 0 1] "John"}]]
```

``` clojure
(parse (.getBytes (str "MSH|^~\\&|ULTRA|TML|OLIS|OLIS|200905011130||ORU^R01|20169838-v25|T|2.5\r\n"
                       "PID|||7005728^^^TML^MR||TEST^RACHEL^DIAMOND||19310313|F|||200 ANYWHERE ST^^TORONTO^ON^M6G 2T9||(416)888-8888||||||1014071185^KR\r\n"
                       "PV1|1||OLIS||||OLIST^BLAKE^DONALD^THOR^^^^^921379^^^^OLIST\r\n"
                       "ORC|RE||T09-100442-RET-0^^OLIS_Site_ID^ISO|||||||||OLIST^BLAKE^DONALD^THOR^^^^L^921379\r\n"
                       "OBR|0||T09-100442-RET-0^^OLIS_Site_ID^ISO|RET^RETICULOCYTE COUNT^HL79901 literal|||200905011106|||||||200905011106||OLIST^BLAKE^DONALD^THOR^^^^L^921379||7870279|7870279|T09-100442|MOHLTC|200905011130||B7|F||1^^^200905011106^^R\r\n"
                       "OBX|1|ST|||Test Value")))
;; => [["MSH"
;;      {[9 0 1] "R01",
;;       [4] "TML",
;;       [7] "200905011130",
;;       [10] "20169838-v25",
;;       [6] "OLIS",
;;       [12] "2.5",
;;       [9] "ORU",
;;       [3] "ULTRA",
;;       [5] "OLIS",
;;       [11] "T",
;;       [2] "^~\\&",
;;       [1] \|}]
;;     ["PID"
;;      {[20 0 1] "KR",
;;       [4] "7005728",
;;       [12 0 2] "TORONTO",
;;       [6 0 2] "DIAMOND",
;;       [20] "1014071185",
;;       [6] "TEST",
;;       [12] "200 ANYWHERE ST",
;;       [9] "F",
;;       [12 0 4] "M6G 2T9",
;;       [4 0 4] "MR",
;;       [8] "19310313",
;;       [14] "(416)888-8888",
;;       [6 0 1] "RACHEL",
;;       [12 0 3] "ON",
;;       [4 0 3] "TML"}]
;;     ["PV1"
;;      {[2] "1",
;;       [4] "OLIS",
;;       [8] "OLIST",
;;       [8 0 1] "BLAKE",
;;       [8 0 2] "DONALD",
;;       [8 0 3] "THOR",
;;       [8 0 8] "921379",
;;       [8 0 12] "OLIST"}]
;;     ["ORC"
;;      {[4] "T09-100442-RET-0",
;;       [13 0 8] "921379",
;;       [13 0 2] "DONALD",
;;       [13] "OLIST",
;;       [13 0 3] "THOR",
;;       [13 0 7] "L",
;;       [4 0 2] "OLIS_Site_ID",
;;       [2] "RE",
;;       [13 0 1] "BLAKE",
;;       [4 0 3] "ISO"}]
;;     ["OBR"
;;      {[4] "T09-100442-RET-0",
;;       [17 0 1] "BLAKE",
;;       [28 0 3] "200905011106",
;;       [17 0 8] "921379",
;;       [20] "7870279",
;;       [22] "MOHLTC",
;;       [28] "1",
;;       [28 0 5] "R",
;;       [23] "200905011130",
;;       [26] "F",
;;       [5 0 2] "HL79901 literal",
;;       [17 0 2] "DONALD",
;;       [8] "200905011106",
;;       [17] "OLIST",
;;       [17 0 3] "THOR",
;;       [5] "RET",
;;       [5 0 1] "RETICULOCYTE COUNT",
;;       [21] "T09-100442",
;;       [19] "7870279",
;;       [4 0 2] "OLIS_Site_ID",
;;       [15] "200905011106",
;;       [17 0 7] "L",
;;       [2] "0",
;;       [25] "B7",
;;       [4 0 3] "ISO"}]
;;     ["OBX" 
;;      {[2] "1", 
;;       [3] "ST", 
;;       [6] "Test Value"}]]
```

## format

Encode data into hl7 string.

``` clojure
(format [["MSH"
          {[1] \|,
           [2] "^~\\&",
           [3] "TestSendingSystem",
           [7] "200701011539",
           [9] "ADT",
           [9 0 1] "A01",
           [9 0 2] "ADT A01",
           [13] "123"}]
         ["PID"
          {[4] "123456",
           [6] "Doe",
           [6 0 1] "John"}]])
;; => MSH|^~\\&|TestSendingSystem||||200701011539||ADT^A01^ADT A01||||123\r\n
;;    PID||||123456||Doe^John
```

``` clojure
(format [["MSH"
          {[9 0 1] "R01",
           [4] "TML",
           [7] "200905011130",
           [10] "20169838-v25",
           [6] "OLIS",
           [12] "2.5",
           [9] "ORU",
           [3] "ULTRA",
           [5] "OLIS",
           [11] "T",
           [2] "^~\\&",
           [1] \|}]
         ["PID"
          {[20 0 1] "KR",
           [4] "7005728",
           [12 0 2] "TORONTO",
           [6 0 2] "DIAMOND",
           [20] "1014071185",
           [6] "TEST",
           [12] "200 ANYWHERE ST",
           [9] "F",
           [12 0 4] "M6G 2T9",
           [4 0 4] "MR",
           [8] "19310313",
           [14] "(416)888-8888",
           [6 0 1] "RACHEL",
           [12 0 3] "ON",
           [4 0 3] "TML"}]
         ["PV1"
          {[2] "1",
           [4] "OLIS",
           [8] "OLIST",
           [8 0 1] "BLAKE",
           [8 0 2] "DONALD",
           [8 0 3] "THOR",
           [8 0 8] "921379",
           [8 0 12] "OLIST"}]
         ["ORC"
          {[4] "T09-100442-RET-0",
           [13 0 8] "921379",
           [13 0 2] "DONALD",
           [13] "OLIST",
           [13 0 3] "THOR",
           [13 0 7] "L",
           [4 0 2] "OLIS_Site_ID",
           [2] "RE",
           [13 0 1] "BLAKE",
           [4 0 3] "ISO"}]
         ["OBR"
          {[4] "T09-100442-RET-0",
           [17 0 1] "BLAKE",
           [28 0 3] "200905011106",
           [17 0 8] "921379",
           [20] "7870279",
           [22] "MOHLTC",
           [28] "1",
           [28 0 5] "R",
           [23] "200905011130",
           [26] "F",
           [5 0 2] "HL79901 literal",
           [17 0 2] "DONALD",
           [8] "200905011106",
           [17] "OLIST",
           [17 0 3] "THOR",
           [5] "RET",
           [5 0 1] "RETICULOCYTE COUNT",
           [21] "T09-100442",
           [19] "7870279",
           [4 0 2] "OLIS_Site_ID",
           [15] "200905011106",
           [17 0 7] "L",
           [2] "0",
           [25] "B7",
           [4 0 3] "ISO"}]
         ["OBX"
          {[2] "1",
           [3] "ST",
           [6] "Test Value"}]])
;; => MSH|^~\\&|ULTRA|TML|OLIS|OLIS|200905011130||ORU^R01|20169838-v25|T|2.5\r\n
;;    PID||||7005728^TML^MR||TEST^RACHEL^DIAMOND||19310313|F|||200 ANYWHERE ST^TORONTO^ON^M6G 2T9||(416)888-8888||||||1014071185^KR\r\n
;;    PV1||1||OLIS||||OLIST^BLAKE^DONALD^THOR^921379^OLIST\r\n
;;    ORC||RE||T09-100442-RET-0^OLIS_Site_ID^ISO|||||||||OLIST^BLAKE^DONALD^THOR^L^921379\r\n
;;    OBR||0||T09-100442-RET-0^OLIS_Site_ID^ISO|RET^RETICULOCYTE COUNT^HL79901 literal|||200905011106|||||||200905011106||OLIST^BLAKE^DONALD^THOR^L^921379||7870279|7870279|T09-100442|MOHLTC|200905011130||B7|F||1^200905011106^R\r\n
;;    OBX||1|ST|||Test Value
```
