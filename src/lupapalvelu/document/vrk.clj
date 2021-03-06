(ns lupapalvelu.document.vrk
  (:require [sade.util :refer [->int fn-> ->double]]
            [clojure.string :as s]
            [sade.strings :as ss]
            [lupapalvelu.document.validator :refer :all]))

;;
;; da lib
;;

(defmacro defvalidator-old [validator-name doc bindings & body]
  `(swap! validators assoc (keyword ~validator-name)
     {:doc ~doc
      :fn (fn [~@bindings] (do ~@body))}))

(defn exists? [x] (-> x s/blank? not))

;;
;; Data
;;

(def kayttotarkoitus->tilavuus {:011 10000
                                :012 15000
                                :511 200000
                                :013 9000
                                :021 10000
                                :022 10000
                                :521 250000
                                :032 100000
                                :039 150000
                                :041 2500
                                :111 150000
                                :112 1500000
                                :119 1000000
                                :121 200000
                                :123 200000
                                :124 10000
                                :129 20000
                                :131 100000
                                :139 40000
                                :141 40000
                                :151 500000
                                :161 1000000
                                :162 1100000
                                :163 500000
                                :164 100000
                                :169 100000
                                :211 600000
                                :213 250000
                                :214 100000
                                :215 120000
                                :219 100000
                                :221 100000
                                :222 50000
                                :223 50000
                                :229 100000
                                :231 20000
                                :239 100000
                                :241 50000
                                :311 300000
                                :312 30000
                                :322 200000
                                :323 200000
                                :324 1500000
                                :331 50000
                                :341 50000
                                :342 50000
                                :349 25000
                                :351 200000
                                :352 200000
                                :353 260000
                                :354 800000
                                :359 300000
                                :369 300000
                                :531 700000
                                :532 200000
                                :541 100000
                                :549 100000
                                :611 1200000
                                :613 700000
                                :691 3000000
                                :692 800000
                                :699 2000000
                                :711 1700000
                                :712 1500000
                                :719 1000000
                                :721 50000
                                :722 250000
                                :723 50000
                                :729 100000
                                :811 50000
                                :819 50000
                                :891 500000
                                :892 200000
                                :893 100000
                                :899 40000
                                :931 4000
                                :941 50000
                                :999 50000})


(def rakennus-schemas ["uusiRakennus"
                       "uusi-rakennus-ei-huoneistoa"
                       "rakennuksen-muuttaminen-ei-huoneistoja"
                       "rakennuksen-muuttaminen-ei-huoneistoja-ei-ominaisuuksia"
                       "rakennuksen-muuttaminen"
                       "rakennuksen-laajentaminen"
                       "rakennuksen-laajentaminen-ei-huoneistoja"
                       "purkaminen"])

(def ei-lammitysta "ei l\u00e4mmityst\u00e4")

;;
;; helpers
;;

(defn ->kayttotarkoitus [x]
  (some->> x (re-matches #"(\d+) .*") last keyword ))

(defn ->huoneistoala [huoneistot]
  (apply + (map (fn-> second :huoneistoala (ss/replace "," ".") ->double) huoneistot)))

(defn ->count [m]
  (-> m keys count))

(defn repeating
  ([n]
    (repeating n {:any :any}))
  ([n body]
    (for [i (take n (range))]
      {(-> i str keyword) body})))

;;
;; Validators
;;

(defvalidator :vrk:CR335
  {:doc "Jos lammitystapa ei ole 5 (ei kiinteaa lammitystapaa), on polttoaine ilmoitettava"
   :schemas ["uusiRakennus"]
   :fields [lammitystapa [:lammitys :lammitystapa]
            polttoaine   [:lammitys :lammonlahde]]
   :facts {:ok   [["ei l\u00e4mmityst\u00e4" nil]
                  ["ei l\u00e4mmityst\u00e4" ""]
                  ["suora s\u00e4hk\u00f6" "s\u00e4hk\u00f6"]]
           :fail [["suora s\u00e4hk\u00f6" ""]
                  ["suora s\u00e4hk\u00f6" nil]]}}
  (and lammitystapa (not= lammitystapa ei-lammitysta) (s/blank? polttoaine)))

(defvalidator :vrk:CR336
 {:doc "Jos lammitystapa on 5 (ei kiinteaa lammitystapaa), ei saa olla polttoainetta"
  :schemas ["uusiRakennus"]
  :fields [lammitystapa [:lammitys :lammitystapa]
           polttoaine   [:lammitys :lammonlahde]]
  :facts {:ok   [["ei l\u00e4mmityst\u00e4" nil]
                 ["ei l\u00e4mmityst\u00e4" ""]
                 ["suora s\u00e4hk\u00f6" "s\u00e4hk\u00f6"]]
          :fail [["ei l\u00e4mmityst\u00e4" "s\u00e4hk\u00f6"]
                 ["ei l\u00e4mmityst\u00e4" "ei tiedossa"]]}}
 (and (= lammitystapa ei-lammitysta) (exists? polttoaine)))

(defvalidator :vrk:CR326
  {:doc    "Kokonaisalan oltava vahintaan kerrosala"
   :schemas ["uusiRakennus"]
   :fields [kokonaisala [:mitat :kokonaisala ->int]
            kerrosala   [:mitat :kerrosala ->int]]
   :facts  {:ok   [[10 10]]
            :fail [[10 11]]}}
  (and kokonaisala kerrosala (< kokonaisala kerrosala)))

(defvalidator :vrk:CR324
  {:doc    "Sahko polttoaineena vaatii varusteeksi sahkon"
   :schemas ["uusiRakennus"]
   :fields [polttoaine [:lammitys :lammonlahde]
            sahko      [:varusteet :sahkoKytkin]]
   :facts {:ok   [["s\u00e4hk\u00f6" true]
                  ["raskas poltto\u00f6ljy" false]]
           :fail [["s\u00e4hk\u00f6" false]]}}
  (and (= polttoaine "s\u00e4hk\u00f6") (not= sahko true)))

(defvalidator :vrk:CR322
  {:doc    "Uuden rakennuksen kokonaisalan oltava vahintaan huoneistoala"
   :schemas ["uusiRakennus"]
   :fields [kokonaisala  [:mitat :kokonaisala ->int]
            huoneistoala [:huoneistot ->huoneistoala]]
   :facts  {:ok   [[100 {:0 {:huoneistoala 60}
                         :1 {:huoneistoala 40}}]]
            :fail [[100 {:0 {:huoneistoala 60}
                         :1 {:huoneistoala 50}}]]}}
  (and kokonaisala huoneistoala (< kokonaisala huoneistoala)))

(defvalidator :vrk:BR113
  {:doc    "Pien- tai rivitalossa saa olla korkeintaan 3 kerrosta"
   :schemas ["uusiRakennus"]
   :fields [kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus]
            kerrosluku      [:mitat :kerrosluku ->int]]
   :facts  {:ok   [["011 yhden asunnon talot" 3]]
            :fail [["011 yhden asunnon talot" 4]]}}
  (and (#{:011 :012 :013 :021 :022} kayttotarkoitus) (> kerrosluku 3)))

(defvalidator :vrk:CR328:sahko
  {:doc    "Verkostoliittymat ja rakennuksen varusteet tasmattava: Sahko"
   :schemas ["uusiRakennus"]
   :fields [liittyma [:verkostoliittymat :sahkoKytkin]
            varuste  [:varusteet         :sahkoKytkin]]
   :facts   {:ok   [[true true]]
             :fail [[true false]]}}
  (and liittyma (not varuste)))

(defvalidator :vrk:CR328:viemari
  {:doc    "Verkostoliittymat ja rakennuksen varusteet tasmattava: Viemari"
   :schemas rakennus-schemas
   :level :tip
   :fields [liittyma [:verkostoliittymat :viemariKytkin]
            varuste  [:varusteet         :viemariKytkin]]
   :facts  {:ok   [[true true]]
            :fail [[true false]]}}
  (and liittyma (not varuste)))

(defvalidator :vrk:CR328:vesijohto
  {:doc    "Verkostoliittymat ja rakennuksen varusteet tasmattava: Vesijohto"
   :schemas rakennus-schemas
   :level  :tip
   :fields [liittyma [:verkostoliittymat :vesijohtoKytkin]
            varuste  [:varusteet         :vesijohtoKytkin]]
   :facts   {:ok   [[true true]]
             :fail [[true false]]}}
  (and liittyma (not varuste)))

(defvalidator :vrk:CR312
  {:doc     "Jos rakentamistoimenpide on 691 tai 111, on kerrosluvun oltava 1"
   :schemas rakennus-schemas
   :fields  [toimenpide [:kaytto :kayttotarkoitus ->kayttotarkoitus]
             kerrosluku [:mitat :kerrosluku ->int]]
   :facts   {:ok   [["111 myym\u00e4l\u00e4hallit" 1]]
             :fail [["111 myym\u00e4l\u00e4hallit" 2]]}}
  (and (#{:691 :111} toimenpide) (not= kerrosluku 1)))

(defvalidator :vrk:CR313
  {:doc     "Jos rakentamistoimenpide on 1, niin tilavuuden on oltava 1,5 kertaa kerrosala."
   :schemas ["uusiRakennus"]
   :fields  [tilavuusRaw     [:mitat :tilavuus]  ; eliminate this validation if not a number
                                                 ;    - there will be illegal-number warning from elsewhere in the code
             tilavuus        [:mitat :tilavuus ->int]
             kerrosala       [:mitat :kerrosala ->int]]
   :facts   {:ok   [[6 6 4] ["6,5" 6 4]]
             :fail [[5 5 4]]}}
  (and (number? tilavuusRaw) tilavuus (< tilavuus (* 1.5 kerrosala))))

(defvalidator :vrk:BR407
  {:doc     "Jos rakentamistoimenpide on 1, niin tilavuuden on oltava 1,5 kertaa kokonaisala jo kayttotapaus on ..."
   :schemas ["uusiRakennus"]
   :fields  [tilavuus        [:mitat :tilavuus ->int]
             kokonaisala     [:mitat :kokonaisala ->int]
             kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus]]
   :facts   {:ok   [[5 4 "611 voimalaitosrakennukset"]
                    [5 4 "811 navetat, sikalat, kanalat yms"]
                    [6 4 "032 luhtitalot"]]
             :fail [[5 4 "032 luhtitalot"]]}}
  (and
    tilavuus
    (< tilavuus (* 1.5 kokonaisala))
    (and
      kayttotarkoitus
      (not
        (or
          (> (->int kayttotarkoitus) 799)
          (#{:162 :163 :169 :611 :613 :699 :712 :719 :722} kayttotarkoitus))))))

(defvalidator :vrk:CR314
  {:doc     "Asuinrakennuksessa pitaa olla lammitys"
   :schemas ["uusiRakennus"]
   :fields  [kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus ->int]
             lammitystapa    [:lammitys :lammitystapa]]
   :facts   {:ok   [["032 luhtitalot" "ilmakeskus"]
                    ["011 yhden asunnon talot" "suora s\u00e4hk\u00f6"]]
             :fail [["032 luhtitalot" ei-lammitysta]
                    ["032 luhtitalot" nil]]}}
  (and
    (<= 11 kayttotarkoitus 39)
    (not (#{"vesikeskus" "ilmakeskus" "suora s\u00e4hk\u00f6" "uuni"} lammitystapa))))

(defvalidator :vrk:CR315
  {:doc     "Omakotitalossa pitaa olla huoneisto"
   :schemas ["uusiRakennus"]
   :fields  [kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus]
             huoneistot      [:huoneistot ->count]]
   :facts   {:ok   [["011 yhden asunnon talot" (repeating 1)]]
             :fail [["011 yhden asunnon talot" (repeating 2)]]}}
  (and (= :011 kayttotarkoitus) (not= 1 huoneistot)))

(defvalidator :vrk:CR316
  {:doc     "Paritalossa pitaa olla kaksi uutta huoneistoa"
   :schemas ["uusiRakennus"]
   :fields  [kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus]
             huoneistot      [:huoneistot ->count]]
   :facts   {:ok   [["012 kahden asunnon talot" (repeating 2)]]
             :fail [["012 kahden asunnon talot" (repeating 1)]]}}
  (and (= :012 kayttotarkoitus) (not= 2 huoneistot)))

(defvalidator :vrk:CR317
  {:doc     "Rivi- tai kerrostaloissa tulee olla vahintaan kolme uutta huoneistoa"
   :schemas ["uusiRakennus"]
   :fields  [kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus ->int]
             huoneistot      [:huoneistot ->count]]
   :facts   {:ok   [["032 luhtitalot" (repeating 3)]]
             :fail [["032 luhtitalot" (repeating 1)]]}}
  (and (<= 13 kayttotarkoitus 39) (< huoneistot 3)))

(defvalidator :vrk:CR319
  {:doc     "Jos rakentamistoimenpide on 1 ja kayttotarkoitus on 032 - 039, on kerrosluvun oltava vahintaan 2"
   :schemas ["uusiRakennus"]
   :fields  [kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus ->int]
             kerrosluku      [:mitat :kerrosluku ->int]]
   :facts   {:ok   [["032 luhtitalot" 2]]
             :fail [["032 luhtitalot" 1]]}}
  (and (<= 32 kayttotarkoitus 39) (< kerrosluku 2)))

(defvalidator :vrk:CR340
  {:doc     "Asuinrakennuksessa kerrosalan on oltava vahintaaan 7 neliota"
   :schemas ["uusiRakennus"]
   :fields  [kerrosala       [:mitat :kerrosala ->int]
             kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus ->int]]
   :facts   {:ok   [[7 "032 luhtitalot"]
                    [6 "611 voimalaitosrakennukset"]]
             :fail [[6 "032 luhtitalot"]]}}
  (and (<= 11 kayttotarkoitus 39) (< kerrosala 7)))

(defvalidator :vrk:CR333:tilavuus
  {:doc     "Jos rakentamistoimenpide on 1, ovat tilavuus,kerrosala,kokonaisala ja kerrosluku pakollisia"
   :schemas ["uusiRakennus"]
   :level   :tip
   :fields  [tilavuus [:mitat :tilavuus ->int]]
   :facts   {:ok   [[10]]
             :fail [[0]]}}
  (zero? tilavuus))

(defvalidator :vrk:CR333:kerrosala
  {:doc     "Jos rakentamistoimenpide on 1, ovat tilavuus,kerrosala,kokonaisala ja kerrosluku pakollisia.
             - Kerrosala voi olla 0, jos kayttotarkoitus on 162, 163, 169, 611, 613, 712, 719, 722 tai 941."
   :schemas rakennus-schemas
   :level   :tip
   :fields  [kerrosala       [:mitat :kerrosala ->int]
             kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus ->int]]
   :facts   {:ok   [[9 "032 luhtitalot"]
                    [0 "611 voimalaitosrakennukset"]]
             :fail [[0 "032 luhtitalot"]]}}
  (and
    (zero? kerrosala)
    (not (#{162 163 169 611 613 712 719 722 941} kayttotarkoitus))))

(defvalidator :vrk:CR333:kokonaisala
  {:doc     "Jos rakentamistoimenpide on 1, ovat tilavuus,kerrosala,kokonaisala ja kerrosluku pakollisia.
             Kokonaisala voi olla 0 , jos kayttotarkoitus >729."
   :schemas ["uusiRakennus"]
   :level   :tip
   :fields  [kokonaisala     [:mitat :kokonaisala ->int]
             kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus ->int]]
   :facts   {:ok   [[9 "032 luhtitalot"]
                    [0 "892 kasvihuoneet"]]
             :fail [[0 "032 luhtitalot"]
                    [0 "729 muut palo- ja pelastustoimen rakennukset"]]}}
  (and
    (zero? kokonaisala)
    (<= kayttotarkoitus 729)))

(defvalidator :vrk:CR333:kerrosluku
  {:doc     "Jos rakentamistoimenpide on 1, ovat tilavuus,kerrosala,kokonaisala ja kerrosluku pakollisia.
             Kerrosluku voi olla 0, jos kayttotarkoitus = 162, 163, 169, 611, 613, 712, 719, 722 tai >729."
   :schemas rakennus-schemas
   :level   :tip
   :fields  [kerrosluku      [:mitat :kerrosluku ->int]
             kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus ->int]]
   :facts   {:ok   [[9 "032 luhtitalot"]
                    [0 "892 kasvihuoneet"]
                    [0 "611 voimalaitosrakennukset"]]
             :fail [[0 "729 muut palo- ja pelastustoimen rakennukset"]]}}
  (and
    (zero? kerrosluku)
    (not (#{162 163 169 611 613 712 719 722} kayttotarkoitus))
    (<= kayttotarkoitus 729)))

(defvalidator :vrk:AR307
  {:doc     "Uusien asuntojen lukumaara: sallitut arvot 0 - 300"
   :schemas ["uusiRakennus"]
   :fields  [huoneistot [:huoneistot ->count]]
   :facts   {:ok   [[(repeating 1)]
                    [(repeating 300)]]
             :fail [[(repeating 301)]]}}
  (not (<= 0 huoneistot 300)))

(defvalidator :vrk:CR330
  {:doc    "Rakennus ei saa olla yli 150 metria korkea"
   :schemas ["uusiRakennus"]
   :fields [tilavuus        [:mitat :tilavuus ->int]
            kerrosala       [:mitat :kerrosala ->int]
            kerrosluku      [:mitat :kerrosluku ->int]
            kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus ->int]]
   :facts  {:ok   [[ 570 145  1 "032 luhtitalot"] ; 3.7m (sample case)
                   [ 200   0  0 "032 luhtitalot"] ; kerrosala/kerrosluku not set
                   [3000 800 40 "342 seurakuntatalot"] ; 150m
                   [3300 800 40 "611 voimalaitosrakennukset"] ; 165m
                   [3300 800 40 "931 saunarakennukset"]] ; 165m
            :fail [[3300 800 40 "342 seurakuntatalot"]]}} ; 165m
  (and
    (pos? kerrosluku)
    (pos? kerrosala)
    (not (#{162 163 169 722 611 613 699 712 719} kayttotarkoitus))
    (<= kayttotarkoitus 799)
    (> (/ tilavuus (/ kerrosala kerrosluku)) 150)))

(defvalidator :vrk:BR319:julkisivu
  {:doc "Jos rakentamistoimenpide on 1, ovat kantavien rakenteiden rakennusaine,
         rakennuksen rakentamistapa, julkisivumateriaali ja lammitystapa pakollisia Huom!
         Kuitenkin, jos kayttotarkoitus on > 729, saavat paaasiallinen julkisivumateriaali ja lammitystapa puuttua."
   :schemas ["uusiRakennus"]
   :fields [kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus ->int]
            julkisivu       [:rakenne :julkisivu]]
   :facts  {:ok    [["032 luhtitalot"       "tiili"]
                    ["032 luhtitalot"       "ei tiedossa"]
                    ["931 saunarakennukset" nil]]
            :fail  [["032 luhtitalot"       nil]]}}
  (and (<= kayttotarkoitus 729) (not julkisivu)))

(defvalidator :vrk:BR319:lammitystapa
  {:doc "Jos rakentamistoimenpide on 1, ovat kantavien rakenteiden rakennusaine,
         rakennuksen rakentamistapa, julkisivumateriaali ja lammitystapa pakollisia Huom!
         Kuitenkin, jos kayttotarkoitus on > 729, saavat paaasiallinen julkisivumateriaali ja lammitystapa puuttua."
   :schemas ["uusiRakennus"]
   :fields [kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus ->int]
            lammitystapa    [:lammitys :lammitystapa]]
   :facts  {:ok    [["141 ravintolat yms."  "uuni"]
                    ["141 ravintolat yms."  ei-lammitysta]
                    ["141 ravintolat yms."  "ei tiedossa"]
                    ["931 saunarakennukset" ei-lammitysta]
                    ["931 saunarakennukset" nil]
                    ["931 saunarakennukset" " "]]
            :fail  [["141 ravintolat yms."  nil]]}}
  (and (<= kayttotarkoitus 729) (not lammitystapa)))

(defvalidator :vrk:CR327
  {:doc "k\u00e4ytt\u00f6tarkoituksen mukainen maksimitilavuus"
   :schemas ["uusiRakennus"]
   :fields [kayttotarkoitus [:kaytto :kayttotarkoitus ->kayttotarkoitus]
            tilavuus        [:mitat :tilavuus ->int]]
   :facts {:ok   [["032 luhtitalot" "100000"]]
           :fail [["032 luhtitalot" "100001"]]}}
  (and
    tilavuus
    (kayttotarkoitus->tilavuus kayttotarkoitus)
    (> tilavuus (kayttotarkoitus->tilavuus kayttotarkoitus))))

(defvalidator :vrk:BR106
  {:doc "Puutalossa saa olla korkeintaan 4 kerrosta"
   :schemas ["uusiRakennus"]
   :fields [kantavaRakennusaine [:rakenne :kantavaRakennusaine]
            kerrosluku          [:mitat :kerrosluku ->int]]
   :facts {:ok [["puu" "3"]]
           :fail [["puu" "5"]]}}
  (when (= kantavaRakennusaine "puu") (> kerrosluku 4)))

(defvalidator :vrk:CR343
  {:doc "Jos lammitystapa on 3 (sahkolammitys), on polttoaineen oltava 4 (sahko)"
   :schemas rakennus-schemas
   :fields [lammitystapa [:lammitys :lammitystapa]
            lammonlahde  [:lammitys :lammonlahde]]
   :facts {:ok [["suora s\u00e4hk\u00f6" "s\u00e4hk\u00f6"]]
           :fail [["suora s\u00e4hk\u00f6" "kaasu"]]}}
  (when (= lammitystapa "suora s\u00e4hk\u00f6") (not= "s\u00e4hk\u00f6" lammonlahde)))

(defvalidator :vrk:CR342
  {:doc "Sahko polttoaineena vaatii sahkoliittyman"
   :schemas rakennus-schemas
   :fields [lammonlahde [:lammitys :lammonlahde]
            sahkoliittyma?  [:verkostoliittymat :sahkoKytkin]]
   :facts {:ok [["s\u00e4hk\u00f6" true]]
           :fail [["s\u00e4hk\u00f6" false]]}}
  (when (= lammonlahde "s\u00e4hk\u00f6") (not sahkoliittyma?)))

(defvalidator :vrk:CR341
  {:doc "Sahkolammitys vaatii sahkoliittyman"
   :schemas rakennus-schemas
   :fields [lammitystapa [:lammitys :lammitystapa]
            sahkoliittyma?  [:verkostoliittymat :sahkoKytkin]]
   :facts {:ok [["suora s\u00e4hk\u00f6" true]]
           :fail [["suora s\u00e4hk\u00f6" false]]}}
  (when (= lammitystapa "suora s\u00e4hk\u00f6") (not sahkoliittyma?)))

#_(defvalidator :vrk:BR203
  {:doc "Jos huoneiston jakokirjain on annettu taytyy olla myos porraskirjain tai huoneistonumero"
   :schemas ["uusiRakennus"]
   :childs [:huoneistot]
   :fields [jakokirjain      [:huoneistoTunnus :jakokirjain]
            porraskirjain    [:huoneistoTunnus :porras]
            huoneistonumero  [:huoneistoTunnus :huoneistonumero]]
   :facts  {:ok    [["032 luhtitalot"       "tiili"]
                    ["032 luhtitalot"       "ei tiedossa"]
                    ["931 saunarakennukset" nil]]
            :fail  [["032 luhtitalot"       nil]]}}
  (and
    (<= kayttotarkoitus 729)
    (not julkisivu)))
