# hl7v2

Hl7v2 Clojure library.

## Motivation

- Avoid interop necessary with existing java libraries.
- Use clojure data structures and built in tools like the powerful zipper api.
- Control over the spec, field names, data types, additional fields, etc.

```
com.github.guillerglez88/hl7v2 {:git/tag "0.0.1-SNAPSHOT", :git/sha "17efce5"}
```

## Structures

A comprehensive library of trigger-events structures can be found in [structures](./structures) folder. The structure is hiccup data structure describing the trigger-event, you can modify the structure is you want. Currently, only v2.3.1, v2.5.1, and v2.9 structures have been generated. Ask for more versions if needed. The structure management is out of the scope of this lib, the developer is supposed to copy the structure from this repo and version it as part of the client code.

example of structure: [structures/v2.5.1/ORU_R01.edn](./structures/v2.5.1/ORU_R01.edn)

## Parse

Parse er7 formatted hl7 message, any `io/reader` input is allowed.
- [test/hl7v2/data/oru-r01.hl7](./test/hl7v2/data/oru-r01.hl7)
- [structures/v2.5.1/ORU_R01.edn](./structures/v2.5.1/ORU_R01.edn)

```clojure
(require '[hl7v2.core :refer [parse-hl7 format-hl7]])
(require '[clojure.edn :as edn])
(require '[clojure.java.io :as io])

(parse-hl7 (io/file "test/hl7v2/data/oru-r01.hl7")
           (edn/read-string (slurp "structures/v2.5.1/ORU_R01.edn")))
```

<details>
<summary>view result</summary>

```clj
;;=> {:MSH
;;    {:field-separator "|",
;;     :application-acknowledgment-type "AL",
;;     :accept-acknowledgment-type "AL",
;;     :dateortime-of-message {:time "20150602100012.43+0100"},
;;     :message-control-id "20251014154001-425",
;;     :security "DEADBEEF",
;;     :version-id {:version-id "2.5.1"},
;;     :sending-application {:namespace-id "LabApp", :universal-id "9.8.7.6.5", :universal-id-type "ISO"},
;;     :message-profile-identifier [{:entity-identifier "LRI..get..."}],
;;     :sending-facility {:namespace-id "LabFac", :universal-id "8.7.6.5.4", :universal-id-type "ISO"},
;;     :sequence-number "",
;;     :country-code "USA",
;;     :receiving-application {:namespace-id "OrdApp", :universal-id "1.2.3.4.5", :universal-id-type "ISO"},
;;     :receiving-facility {:namespace-id "OrdFac", :universal-id "2.3.4.5.6", :universal-id-type "ISO"},
;;     :continuation-pointer "",
;;     :message-type {:message-code "ORU", :trigger-event "R01", :message-structure "ORU_R01"},
;;     :encoding-characters "^~\\&#",
;;     :principal-language-of-message {:identifier "en-US", :text "", :name-of-coding-system "ISO639"},
;;     :character-set ["UNICODE UTF-8"],
;;     :processing-id {:processing-id "P"},
;;     :alternate-character-set-handling-scheme ""},
;;    :PATIENT_RESULT
;;    [{:PATIENT
;;      {:PID
;;       {:religion {:identifier "CHR", :text "Christian", :name-of-coding-system "HL70006"},
;;        :administrative-sex "F",
;;        :mothers-identifier [{:id-number ""}],
;;        :ssn-number-patient "",
;;        :mothers-maiden-name [{:family-name ""}],
;;        :birth-order "2",
;;        :drivers-license-number-patient
;;        {:license-number "12345", :issuing-state-province-country "MI", :expiration-date "20180219"},
;;        :multiple-birth-indicator "Y",
;;        :birth-place {1 "1025 House Lane", 2 "", 3 "Ann Arbor", 4 "MI", 5 "99999", 6 "USA", 7 "H", 8 "", 9 "WA"},
;;        :identity-unknown-indicator "",
;;        :set-id-pid "1",
;;        :race
;;        [{:identifier "1002-5", :text "American Indian or Alaska Native", :name-of-coding-system "HL70005"}
;;         {:identifier "2106-3", :text "White", :name-of-coding-system "HL70005"}],
;;        :patient-death-indicator "N",
;;        :veterans-military-status {:identifier ""},
;;        :nationality {:identifier ""},
;;        :patient-alias [{:family-name ""}],
;;        :county-code "",
;;        :dateortime-of-birth {:time "197006010912"},
;;        :patient-address
;;        [{:city "Ann Arbor",
;;          :countyorparish-code "WA",
;;          :other-designation "Appt 123",
;;          :address-type "H",
;;          :state-or-province "MI",
;;          :street-address "1000 House Lane",
;;          :zip-or-postal-code "99999",
;;          :country "USA",
;;          :other-geographic-designation ""}],
;;        :patient-account-number
;;        {:id-number "12345",
;;         :check-digit "",
;;         :check-digit-scheme "",
;;         :assigning-authority {1 "OrdFac", 2 "2.3.4.5.6", 3 "ISO"},
;;         :identifier-type-code "AN"},
;;        :phone-number-home
;;        [{:telephone-number "",
;;          :telecommunication-use-code "PRN",
;;          :telecommunication-equipment-type "PH",
;;          :email-address "",
;;          :country-code "1",
;;          :areaorcity-code "555",
;;          :local-number "555-8473"}
;;         {:telephone-number "",
;;          :telecommunication-use-code "NET",
;;          :telecommunication-equipment-type "Internet",
;;          :email-address "eve@test.test"}],
;;        :patient-name
;;        [{:suffix "Jr",
;;          :professional-suffix "PhD",
;;          :name-representation-code "",
;;          :name-validity-range "",
;;          :name-type-code "L",
;;          :given-name "Eve",
;;          :prefix "Dr",
;;          :second-and-further-given-names-or-initials-thereof "L",
;;          :degree "",
;;          :effective-date "20000909",
;;          :name-assembly-order "G",
;;          :name-context "",
;;          :expiration-date "20301231",
;;          :family-name "Everywoman"}
;;         {:suffix "Jr",
;;          :name-representation-code "",
;;          :name-validity-range {1 "19700601", 2 "20000908"},
;;          :name-type-code "M",
;;          :given-name "Eve",
;;          :prefix "",
;;          :second-and-further-given-names-or-initials-thereof "L",
;;          :degree "",
;;          :name-assembly-order "G",
;;          :name-context "",
;;          :family-name "Original"}],
;;        :primary-language {:identifier "en-US", :text "", :name-of-coding-system "ISO639"},
;;        :phone-number-business
;;        [{:telephone-number "",
;;          :telecommunication-use-code "WPN",
;;          :telecommunication-equipment-type "PH",
;;          :email-address "",
;;          :country-code "1",
;;          :areaorcity-code "555",
;;          :local-number "555-1126",
;;          :extension "12"}],
;;        :ethnic-group [{:identifier "N", :text {1 "Not Hispanic or Latino", 2 "HL70189"}}],
;;        :citizenship [{:identifier "NL", :text "Netherlands", :name-of-coding-system "ISO3166"}],
;;        :patient-id {:id-number ""},
;;        :patient-identifier-list
;;        [{:id-number "1032702",
;;          :check-digit "",
;;          :check-digit-scheme "",
;;          :assigning-authority {1 "OrdOrg", 2 "3.4.5.6.7", 3 "ISO"},
;;          :identifier-type-code "MR",
;;          :assigning-facility {1 "OrdFac", 2 "2.3.4.5.6", 3 "ISO"},
;;          :effective-date "20190101",
;;          :expiration-date "20290101"}
;;         {:id-number "1032702",
;;          :check-digit "",
;;          :check-digit-scheme "",
;;          :assigning-authority {1 "OrdOrg", 2 "3.4.5.6.7", 3 "ISO"},
;;          :identifier-type-code "MR",
;;          :assigning-facility {1 "OrdFac", 2 "2.3.4.5.6", 3 "ISO"},
;;          :effective-date "20190101",
;;          :expiration-date "20290101"}],
;;        :marital-status {:identifier "M", :text "Married", :name-of-coding-system "HL70002"},
;;        :alternate-patient-id-pid [{:id-number ""}],
;;        :patient-death-date-and-time {:time ""}},
;;       :VISIT
;;       {:PV1
;;        {:prior-patient-location {:point-of-care ""},
;;         :visit-number
;;         {:id-number "81456267",
;;          :check-digit "",
;;          :check-digit-scheme "",
;;          :assigning-authority {1 " AssignAuth", 2 "1.2.3.4.5.6", 3 "ISO"},
;;          :identifier-type-code "VN"},
;;         :patient-class {1 "E", 2 "EMERGENCY", 3 "HL70004"},
;;         :preadmit-number {:id-number ""},
;;         :admitting-doctor [{:id-number ""}],
;;         :hospital-service "",
;;         :temporary-location {:point-of-care ""},
;;         :attending-doctor
;;         [{:suffix "",
;;           :name-representation-code "G",
;;           :name-validity-range "20330101000000",
;;           :check-digit-scheme "DN",
;;           :name-type-code "9",
;;           :assigning-facility "",
;;           :given-name "Emily",
;;           :identifier-type-code {1 "OrdFac", 2 "2.3.4.5.6", 3 "ISO"},
;;           :assigning-authority "L",
;;           :prefix "MD",
;;           :second-and-further-given-names-or-initials-thereof "",
;;           :degree "",
;;           :source-table {1 "OrdOrg", 2 "3.4.5.6.7.8", 3 "ISO"},
;;           :id-number "857432",
;;           :identifier-check-digit "1000",
;;           :name-assembly-order "doctor",
;;           :name-context "20100101000000",
;;           :family-name "Jones"}],
;;         :set-id-pv1 "1",
;;         :preadmit-test-indicator "",
;;         :referring-doctor [{:id-number ""}],
;;         :financial-class [{:financial-class-code "V01", :effective-date "Not VFC Eligible"}],
;;         :admission-type {1 "E", 2 "Emergency", 3 "HL70007"},
;;         :vip-indicator "",
;;         :readmission-indicator "",
;;         :consulting-doctor [{:id-number ""}],
;;         :admit-source "",
;;         :patient-type "",
;;         :assigned-patient-location
;;         {:facility "",
;;          :building "",
;;          :comprehensive-location-identifier "",
;;          :location-description "",
;;          :assigning-authority-for-location "DEPID",
;;          :point-of-care "EMERG",
;;          :location-status "",
;;          :bed "01",
;;          :room "101",
;;          :floor "",
;;          :person-location-type ""},
;;         :ambulatory-status [""]},
;;        :PV2
;;        {:expected-admit-dateortime {:time "201506011609"},
;;         :patient-valuables-location "",
;;         :admission-level-of-care-code {:identifier "AC", :text "Acute", :name-of-coding-system "HL70432"},
;;         :clinic-organization-name [{:organization-name ""}],
;;         :visit-publicity-code "F",
;;         :actual-length-of-inpatient-stay "",
;;         :accommodation-code {:identifier ""},
;;         :military-nonavailability-code "",
;;         :expected-number-of-insurance-plans "",
;;         :visit-description "",
;;         :prior-pending-location {:point-of-care ""},
;;         :first-similar-illness-date "",
;;         :military-partnership-code "",
;;         :purge-status-code "",
;;         :baby-detained-indicator "",
;;         :visit-priority-code {1 "2", 2 "Urgent", 3 "HL72017"},
;;         :transfer-reason {:identifier ""},
;;         :signature-on-file-date "",
;;         :referral-source-code
;;         [{:suffix "Jr",
;;           :name-representation-code "G",
;;           :name-validity-range "20330101000000",
;;           :check-digit-scheme "DN",
;;           :name-type-code "9",
;;           :assigning-facility "",
;;           :given-name "Gordon",
;;           :identifier-type-code {1 "OrdFac", 2 "2.3.4.5.6", 3 "ISO"},
;;           :assigning-authority "L",
;;           :prefix "MD",
;;           :second-and-further-given-names-or-initials-thereof "Denny",
;;           :degree "",
;;           :source-table {1 "OrdOrg", 2 "3.4.5.6.7", 3 "ISO"},
;;           :id-number "23432",
;;           :identifier-check-digit "1000",
;;           :name-assembly-order "doctor",
;;           :name-context "20100101000000",
;;           :family-name "Smith"}],
;;         :patient-charge-adjustment-code {:identifier ""},
;;         :retention-indicator "",
;;         :recurring-service-code "",
;;         :admit-reason {:identifier "", :text "Not feeling well"},
;;         :patient-status-code "",
;;         :previous-treatment-date "",
;;         :patient-valuables [""],
;;         :employment-illness-related-indicator "",
;;         :mode-of-arrival-code {:identifier "A", :text "Ambulance", :name-of-coding-system "HL70430"},
;;         :visit-user-code [""],
;;         :newborn-baby-indicator "",
;;         :special-program-code "",
;;         :visit-protection-indicator "N",
;;         :expected-surgery-date-and-time {:time ""},
;;         :estimated-length-of-inpatient-stay "",
;;         :recreational-drug-use-code [{:identifier ""}],
;;         :purge-status-date "",
;;         :expected-discharge-dateortime {:time ""},
;;         :billing-media-code "",
;;         :expected-discharge-disposition "",
;;         :previous-service-date ""}}},
;;      :ORDER_OBSERVATION
;;      [{:ORC
;;        {:ordering-facility-address [{:street-address "OrdFac", :other-designation "2.3.4.5.6", :city "ISO"}],
;;         :verified-by
;;         [{:suffix "",
;;           :check-digit-scheme "",
;;           :name-type-code "L",
;;           :given-name "Nicholas",
;;           :identifier-type-code "NPI",
;;           :assigning-authority {1 "", 2 "372526", 3 "L"},
;;           :prefix "",
;;           :second-and-further-given-names-or-initials-thereof "",
;;           :degree "",
;;           :source-table "",
;;           :id-number "5742200012",
;;           :identifier-check-digit "",
;;           :family-name "Radon"}],
;;         :filler-order-number
;;         {:entity-identifier "LAB4432", :namespace-id "LabFac", :universal-id "8.7.6.5.4", :universal-id-type "ISO"},
;;         :parent {:placer-assigned-identifier ""},
;;         :enterers-location {:point-of-care ""},
;;         :order-status-modifier
;;         {:name-of-alternate-coding-system "USA",
;;          :coding-system-version-id {1 "S", 2 "Service Location", 3 "HL70190"},
;;          :name-of-coding-system "Ann Arbor",
;;          :original-text "WA",
;;          :alternate-identifier "MI",
;;          :alternate-text "99999",
;;          :identifier {1 "Emergency Lane", 2 "", 3 "912"},
;;          :alternate-coding-system-version-id "",
;;          :text "Medical Building I"},
;;         :advanced-beneficiary-notice-override-reason {:identifier ""},
;;         :order-control "RE",
;;         :order-status "CM",
;;         :placer-group-number
;;         {:entity-identifier "GORD874244", :namespace-id "OrdFac", :universal-id "2.3.4.5.6", :universal-id-type "ISO"},
;;         :ordering-facility-phone-number
;;         [{:telephone-number {1 "Emergency Lane", 2 "", 3 "911"},
;;           :telecommunication-use-code "First Floor",
;;           :local-number {1 "S", 2 "Service Location", 3 "HL70190"},
;;           :country-code "99999",
;;           :speed-dial-code "",
;;           :extension "",
;;           :extension-prefix "9876",
;;           :any-text "WA",
;;           :email-address "MI",
;;           :telecommunication-equipment-type "Ann Arbor",
;;           :unformatted-telephone-number "20100612",
;;           :areaorcity-code "USA"}],
;;         :order-control-code-reason {:identifier ""},
;;         :response-flag "",
;;         :ordering-provider
;;         [{:suffix "",
;;           :check-digit-scheme "",
;;           :name-type-code "L",
;;           :given-name "Nicholas",
;;           :identifier-type-code "NPI",
;;           :assigning-authority {1 "", 2 "372526", 3 "L"},
;;           :prefix "",
;;           :second-and-further-given-names-or-initials-thereof "",
;;           :degree "",
;;           :source-table "",
;;           :id-number "5742200012",
;;           :identifier-check-digit "",
;;           :family-name "Radon"}],
;;         :dateortime-of-transaction {:time "201506011608"},
;;         :ordering-facility-name
;;         [{:organization-name "2",
;;           :organization-name-type-code "Patient has been informed of responsibility, and agrees to pay for service",
;;           :id-number "HL70339"}],
;;         :action-by [{:id-number ""}],
;;         :entering-organization {:identifier ""},
;;         :quantityortiming
;;         [{:quantity "1", :interval "", :duration "", :start-dateortime "20150601", :end-dateortime "", :priority "R "}],
;;         :entered-by
;;         [{:suffix "III",
;;           :name-representation-code "",
;;           :name-validity-range "20140129",
;;           :check-digit-scheme "NPI",
;;           :name-type-code "",
;;           :assigning-facility "",
;;           :given-name "Will",
;;           :identifier-type-code "",
;;           :assigning-authority "L",
;;           :prefix "Mr.",
;;           :second-and-further-given-names-or-initials-thereof "John",
;;           :degree "PA",
;;           :source-table {1 "", 2 "372526", 3 "L"},
;;           :effective-date "FHL7",
;;           :id-number "1234567890",
;;           :identifier-check-digit "",
;;           :name-assembly-order "",
;;           :name-context "G",
;;           :family-name "PhysicianAssistant"}],
;;         :ordering-provider-address [{:street-address "555-555-9110"}],
;;         :advanced-beneficiary-notice-code {:identifier ""},
;;         :entering-device {:identifier "E", :text "Emergency", :name-of-coding-system "HL70007"},
;;         :order-effective-dateortime {:time ""},
;;         :call-back-phone-number {:telephone-number ""},
;;         :placer-order-number
;;         {:entity-identifier "ORD777888", :namespace-id "OrdFac", :universal-id "2.3.4.5.6", :universal-id-type "ISO"}},
;;        :OBR
;;        {:diagnostic-serv-sect-id "",
;;         :order-callback-phone-number {:telephone-number ""},
;;         :placer-field-1 "",
;;         :filler-order-number {:entity-identifier ""},
;;         :transportation-mode "",
;;         :parent {:placer-assigned-identifier ""},
;;         :relevant-clinical-information "",
;;         :principal-result-interpreter {:name ""},
;;         :universal-service-identifier {:identifier ""},
;;         :technician [{:name ""}],
;;         :specimen-received-dateortime {:time ""},
;;         :priority-obr "",
;;         :collection-volume {:quantity ""},
;;         :reason-for-study [{:identifier ""}],
;;         :results-rptorstatus-chng-dateortime {:time ""},
;;         :set-id-obr "1",
;;         :charge-to-practice {:monetary-amount ""},
;;         :transcriptionist [{:name ""}],
;;         :danger-code {:identifier ""},
;;         :placer-field-2 "",
;;         :ordering-provider [{:id-number ""}],
;;         :collector-identifier [{:id-number ""}],
;;         :filler-field-2 "",
;;         :parent-result {:parent-observation-identifier ""},
;;         :observation-dateortime {:time "20251014154001"},
;;         :specimen-source {:specimen-source-name-or-code ""},
;;         :filler-field-1 "",
;;         :quantityortiming [{:quantity "", :interval "", :duration "", :start-dateortime "", :end-dateortime ""}],
;;         :scheduled-dateortime {:time ""},
;;         :result-copies-to [{:id-number ""}],
;;         :assistant-result-interpreter [{:name ""}],
;;         :requested-dateortime {:time ""},
;;         :observation-end-dateortime {:time ""},
;;         :placer-order-number {:entity-identifier ""},
;;         :specimen-action-code "",
;;         :result-status ""}}
;;       {:OBR
;;        {:diagnostic-serv-sect-id "",
;;         :order-callback-phone-number
;;         {:telephone-number "",
;;          :telecommunication-use-code "WPN",
;;          :telecommunication-equipment-type "PH",
;;          :email-address "",
;;          :country-code "1",
;;          :areaorcity-code "555",
;;          :local-number "5559908",
;;          :extension "34"},
;;         :placer-field-1 "",
;;         :filler-order-number
;;         {:entity-identifier "LAB4432", :namespace-id "LabFac", :universal-id "8.7.6.5.4", :universal-id-type "ISO"},
;;         :relevant-clinical-information "",
;;         :universal-service-identifier {:identifier "51523-9", :text "Grass Pollen Mix", :name-of-coding-system "LN"},
;;         :specimen-received-dateortime {:time ""},
;;         :priority-obr "R",
;;         :collection-volume {:quantity ""},
;;         :results-rptorstatus-chng-dateortime {:time "201506011811"},
;;         :set-id-obr "1",
;;         :charge-to-practice {:monetary-amount ""},
;;         :danger-code {:identifier ""},
;;         :placer-field-2 "",
;;         :ordering-provider
;;         [{:suffix "",
;;           :check-digit-scheme "",
;;           :name-type-code "L",
;;           :given-name "Nicholas",
;;           :identifier-type-code "NPI",
;;           :assigning-authority {1 "", 2 "372526", 3 "L"},
;;           :prefix "",
;;           :second-and-further-given-names-or-initials-thereof "",
;;           :degree "",
;;           :source-table "",
;;           :id-number "5742200012",
;;           :identifier-check-digit "",
;;           :family-name "Radon"}],
;;         :collector-identifier [{:id-number ""}],
;;         :filler-field-2 "",
;;         :parent-result {:parent-observation-identifier ""},
;;         :observation-dateortime {:time "201506011608"},
;;         :specimen-source {:specimen-source-name-or-code ""},
;;         :filler-field-1 "",
;;         :quantityortiming
;;         [{:quantity "1", :interval "", :duration "", :start-dateortime "20150601", :end-dateortime "", :priority "R"}],
;;         :result-copies-to
;;         [{:suffix "",
;;           :check-digit-scheme "",
;;           :name-type-code "L",
;;           :given-name "Pafford",
;;           :identifier-type-code "NPI",
;;           :assigning-authority {1 "", 2 "372526", 3 "L"},
;;           :prefix "",
;;           :second-and-further-given-names-or-initials-thereof "",
;;           :degree "",
;;           :source-table "",
;;           :id-number "10092000194",
;;           :identifier-check-digit "",
;;           :family-name "Hamlin"}],
;;         :requested-dateortime {:time "201506011608"},
;;         :observation-end-dateortime {:time ""},
;;         :placer-order-number
;;         {:entity-identifier "ORD777888", :namespace-id "OrdFac", :universal-id "2.3.4.5.6", :universal-id-type "ISO"},
;;         :specimen-action-code "",
;;         :result-status "F"},
;;        :NTE
;;        [{:set-id-nte "1",
;;          :source-of-comment "",
;;          :comment ["Allergy test interpretations are subjective."],
;;          :comment-type {:identifier "RE"}}],
;;        :OBSERVATION
;;        [{:OBX
;;          {:performing-organization-address {:street-address ""},
;;           :observation-value ["3.9"],
;;           :set-id-obx "1",
;;           :observation-result-status "F",
;;           :reserved-for-harmonization-with-v26 "",
;;           :observation-identifier
;;           {:identifier "64991-3", :text "Kentucky blue grass IgE Ab ", :name-of-coding-system "LN"},
;;           :performing-organization-name {:organization-name ""},
;;           :observation-subid "1",
;;           :probability "",
;;           :references-range "<0.10",
;;           :dateortime-of-the-analysis {:time "201506011605"},
;;           :effective-date-of-reference-range-values {:time ""},
;;           :abnormal-flags [{1 "A", 2 "Abnormal", 3 "HL70078"}],
;;           :user-defined-access-checks "",
;;           :equipment-instance-identifier [{:entity-identifier ""}],
;;           :producers-reference {:identifier ""},
;;           :units {:identifier "kU/L"},
;;           :nature-of-abnormal-test [""],
;;           :observation-method [{:identifier ""}],
;;           :responsible-observer [{:id-number ""}],
;;           :value-type "DT",
;;           :dateortime-of-the-observation {:time "201506011608"},
;;           :performing-organization-medical-director {:id-number ""}}}
;;         {:OBX
;;          {:observation-value ["68"],
;;           :set-id-obx "1",
;;           :observation-result-status "R",
;;           :observation-identifier {:identifier "HR"},
;;           :observation-subid "",
;;           :probability "",
;;           :references-range "",
;;           :abnormal-flags [""],
;;           :units {:identifier "/min"},
;;           :nature-of-abnormal-test [""],
;;           :value-type "ST"}}
;;         {:OBX
;;          {:observation-value ["0"],
;;           :set-id-obx "2",
;;           :observation-result-status "R",
;;           :observation-identifier {:identifier "PVC"},
;;           :observation-subid "",
;;           :probability "",
;;           :references-range "",
;;           :abnormal-flags [""],
;;           :units {:identifier "#/min"},
;;           :nature-of-abnormal-test [""],
;;           :value-type "ST"}}
;;         {:OBX
;;          {:observation-value ["14"],
;;           :set-id-obx "3",
;;           :observation-result-status "R",
;;           :observation-identifier {:identifier "RR"},
;;           :observation-subid "",
;;           :probability "",
;;           :references-range "",
;;           :abnormal-flags [""],
;;           :units {:identifier "breaths/min"},
;;           :nature-of-abnormal-test [""],
;;           :value-type "ST"}}
;;         {:OBX
;;          {:observation-value ["28"],
;;           :set-id-obx "4",
;;           :observation-result-status "R",
;;           :observation-identifier {:identifier "CO2EX"},
;;           :observation-subid "",
;;           :probability "",
;;           :references-range "",
;;           :abnormal-flags [""],
;;           :units {:identifier "mm(hg)"},
;;           :nature-of-abnormal-test [""],
;;           :value-type "ST"}}
;;         {:OBX
;;          {:observation-value ["3"],
;;           :set-id-obx "5",
;;           :observation-result-status "R",
;;           :observation-identifier {:identifier "CO2IN"},
;;           :observation-subid "",
;;           :probability "",
;;           :references-range "",
;;           :abnormal-flags [""],
;;           :units {:identifier "mm(hg)"},
;;           :nature-of-abnormal-test [""],
;;           :value-type "ST"}}
;;         {:OBX
;;          {:observation-value ["14"],
;;           :set-id-obx "6",
;;           :observation-result-status "R",
;;           :observation-identifier {:identifier "CO2RR"},
;;           :observation-subid "",
;;           :probability "",
;;           :references-range "",
;;           :abnormal-flags [""],
;;           :units {:identifier "breaths/min"},
;;           :nature-of-abnormal-test [""],
;;           :value-type "ST"}}
;;         {:OBX
;;          {:observation-value ["71"],
;;           :set-id-obx "7",
;;           :observation-result-status "R",
;;           :observation-identifier {:identifier "SPO2R"},
;;           :observation-subid "",
;;           :probability "",
;;           :references-range "",
;;           :abnormal-flags [""],
;;           :units {:identifier "/min"},
;;           :nature-of-abnormal-test [""],
;;           :value-type "ST"}}
;;         {:OBX
;;          {:observation-value ["100"],
;;           :set-id-obx "8",
;;           :observation-result-status {1 {1 "R"}},
;;           :observation-identifier {:identifier "SPO2P"},
;;           :observation-subid "",
;;           :probability "",
;;           :references-range "",
;;           :abnormal-flags [""],
;;           :units {:identifier "%"},
;;           :nature-of-abnormal-test [""],
;;           :value-type "ST"}}]}]}]}
```
</details>

## Format

Encode hl7 data into er7 format.
- [test/hl7v2/data/oru-r01.hl7](./test/hl7v2/data/oru-r01.hl7)
- [structures/v2.5.1/ORU_R01.edn](./structures/v2.5.1/ORU_R01.edn)

```clj
(require '[clojure.edn :as edn])
(require '[clojure.java.io :as io])

(let [struc (edn/read-string (slurp "structures/v2.5.1/ORU_R01.edn"))]
  (-> (io/file "test/hl7v2/data/oru-r01.hl7")
      (parse-hl7 struc)
      (format-hl7 struc)))
```

<details>
<summary>view result</summary>

```clj
  ;;=> "MSH|^~\\&#|LabApp^9.8.7.6.5^ISO|LabFac^8.7.6.5.4^ISO|OrdApp^1.2.3.4.5^ISO|OrdFac^2.3.4.5.6^ISO|20150602100012.43+0100|DEADBEEF|ORU^R01^ORU_R01|20251014154001-425|P|2.5.1|||AL|AL|USA|UNICODE UTF-8|en-US^^ISO639||LRI..get...
  ;;    PID|1||1032702^^^OrdOrg&3.4.5.6.7&ISO^MR^OrdFac&2.3.4.5.6&ISO^20190101^20290101~1032702^^^OrdOrg&3.4.5.6.7&ISO^MR^OrdFac&2.3.4.5.6&ISO^20190101^20290101||Everywoman^Eve^L^Jr^Dr^^L^^^^G^20000909^20301231^PhD~Original^Eve^L^Jr^^^M^^^19700601&20000908^G||197006010912|F||1002-5^American Indian or Alaska Native^HL70005~2106-3^White^HL70005|1000 House Lane^Appt 123^Ann Arbor^MI^99999^USA^H^^WA||^PRN^PH^^1^555^555-8473~^NET^Internet^eve@test.test|^WPN^PH^^1^555^555-1126^12|en-US^^ISO639|M^Married^HL70002|CHR^Christian^HL70006|12345^^^OrdFac&2.3.4.5.6&ISO^AN||12345^MI^20180219||N^Not Hispanic or Latino&HL70189|1025 House Lane^^Ann Arbor^MI^99999^USA^H^^WA|Y|2|NL^Netherlands^ISO3166||||N|
  ;;    PV1|1|E^EMERGENCY^HL70004|EMERG^101^01^^^^^^^^DEPID|E^Emergency^HL70007|||857432^Jones^Emily^^^MD^^OrdOrg&3.4.5.6.7.8&ISO^L^9^1000^DN^OrdFac&2.3.4.5.6&ISO^^G^20100101000000^20330101000000^doctor||||||||||||81456267^^^ AssignAuth&1.2.3.4.5.6&ISO^VN|V01^Not VFC Eligible
  ;;    PV2|||^Not feeling well|||||201506011609|||||23432^Smith^Gordon^Denny^Jr^MD^^OrdOrg&3.4.5.6.7&ISO^L^9^1000^DN^OrdFac&2.3.4.5.6&ISO^^G^20100101000000^20330101000000^doctor||||||||F|N|||2^Urgent^HL72017|||||||||||||A^Ambulance^HL70430||AC^Acute^HL70432
  ;;    ORC|RE|ORD777888^OrdFac^2.3.4.5.6^ISO|LAB4432^LabFac^8.7.6.5.4^ISO|GORD874244^OrdFac^2.3.4.5.6^ISO|CM||1^^^20150601^^R ||201506011608|1234567890^PhysicianAssistant^Will^John^III^Mr.^PA^&372526&L^L^^^NPI^^^^G^20140129^^FHL7|5742200012^Radon^Nicholas^^^^^^&372526&L^L^^^NPI|5742200012^Radon^Nicholas^^^^^^&372526&L^L^^^NPI||||||E^Emergency^HL70007|||2^Patient has been informed of responsibility, and agrees to pay for service^HL70339|OrdFac^2.3.4.5.6^ISO|Emergency Lane&&911^First Floor^Ann Arbor^MI^99999^USA^S&Service Location&HL70190^^WA^9876^^20100612|555-555-9110|Emergency Lane&&912^Medical Building I^Ann Arbor^MI^99999^USA^S&Service Location&HL70190^^WA|
  ;;    OBR|1||||||20251014154001||||||||||||||||||||^^^^|||||||||
  ;;    OBR|1|ORD777888^OrdFac^2.3.4.5.6^ISO|LAB4432^LabFac^8.7.6.5.4^ISO|51523-9^Grass Pollen Mix^LN|R|201506011608|201506011608|||||||||5742200012^Radon^Nicholas^^^^^^&372526&L^L^^^NPI|^WPN^PH^^1^555^5559908^34|||||201506011811|||F||1^^^20150601^^R|10092000194^Hamlin^Pafford^^^^^^&372526&L^L^^^NPI
  ;;    NTE|1^^Allergy test interpretations are subjective.^RE
  ;;    OBX|1|DT|64991-3^Kentucky blue grass IgE Ab ^LN|1|3.9|kU/L|<0.10|A^Abnormal^HL70078|||F|||201506011608|||||201506011605||||||
  ;;    OBX|1|ST|HR||68|/min|||||R
  ;;    OBX|2|ST|PVC||0|#/min|||||R
  ;;    OBX|3|ST|RR||14|breaths/min|||||R
  ;;    OBX|4|ST|CO2EX||28|mm(hg)|||||R
  ;;    OBX|5|ST|CO2IN||3|mm(hg)|||||R
  ;;    OBX|6|ST|CO2RR||14|breaths/min|||||R
  ;;    OBX|7|ST|SPO2R||71|/min|||||R
  ;;    OBX|8|ST|SPO2P||100|%|||||R"
```
</details>

## Advanced api

Advanced features can be used from the namespaces:

- [hl7v2.er7](./src/hl7v2/er7.clj)
- [hl7v2.zipper](./src/hl7v2/zipper.clj)

...doc [wip]

---

Standard: https://www.hl7.org/implement/standards/product_brief.cfm?product_id=185   
Schema: https://www.hl7.org/documentcenter/private/standards/V251/HL7-xml%20v2.5.1.zip