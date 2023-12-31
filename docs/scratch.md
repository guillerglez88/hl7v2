```hl7
 PID|||000197245^^^NationalPN&2.16.840.1.113883.19.3&ISO^PN~4532^^^Careful\\&CareClinic&2.16.840.1.113883.19.2.400566&ISO^PI~3242346^^^GoodmanGP&2.16.840.1.113883.19.2.450998&ISO^PI||Patient^Particia^^^^^L||19750103|F|||Randomroad 23a&Randomroad&23a^^Anytown^^1200^^H||555 3542557^ORN^PH~555 3542558^ORN^FX|555 5557865^WPN^PH\r\n
```

```
PID.3 |000197245
         ^
         ^
         ^NationalPN
             &2.16.840.1.113883.19.3
             &ISO
         ^PN
      ~4532
         ^
         ^
         ^Careful\\&CareClinic
             &2.16.840.1.113883.19.2.400566
             &ISO
         ^PI
      ~3242346
         ^
         ^
         ^GoodmanGP
             &2.16.840.1.113883.19.2.450998
             &ISO
         ^PI
```

```clj
{"PID" {3 [{1 "000197245"
            4 {1 "NationalPN"
               2 "2.16.840.1.113883.19.3"
               3 "ISO"}
            5 "PN"}
           {1 "4532"
            4 {1 "Careful\\&CareClinic"
               2 "2.16.840.1.113883.19.2.400566"
               3 "ISO"}}]}}
```
