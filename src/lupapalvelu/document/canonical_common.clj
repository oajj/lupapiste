(ns lupapalvelu.document.canonical-common
  (:require [clojure.string :as s]
            [clojure.walk :as walk]
            [swiss.arrows :refer [-<>]]
            [sade.strings :as ss]
            [sade.util :refer :all]
            [lupapalvelu.core :refer [now]]
            [lupapalvelu.i18n :refer [with-lang loc]]
            [cljts.geom :as geo]
            [cljts.io :as jts]))


; Empty String will be rendered as empty XML element
(def empty-tag "")

; State of the content when it is send over KRYSP
; NOT the same as the state of the application!
(def toimituksenTiedot-tila "keskener\u00e4inen")

(def application-state-to-krysp-state
  {:open "uusi lupa, ei k\u00e4sittelyss\u00e4"
   :submitted "vireill\u00e4"
   :sent "vireill\u00e4"
   :complement-needed "odottaa asiakkaan toimenpiteit\u00e4"
   :verdictGiven "p\u00e4\u00e4t\u00f6s toimitettu"
   :constructionStarted "rakennusty\u00f6t aloitettu"
   :closed "valmis"})

(def ymp-application-state-to-krysp-state
  {:open "1 Vireill\u00e4"
   :sent "1 Vireill\u00e4"
   :submitted "1 Vireill\u00e4"
   :complement-needed "1 Vireill\u00e4"
   :verdictGiven "ei tiedossa"
   :constructionStarted "ei tiedossa"
   :closed "13 P\u00e4\u00e4t\u00f6s lainvoimainen"})

(def ^:private state-timestamp-fn
  {:open #(or (:opened %) (:created %))
   :submitted :submitted
   :sent :submitted ; Enables XML to be formed from sent applications
   :complement-needed :complementNeeded
   :verdictGiven (fn [app] (->> (:verdicts app) (map :timestamp) sort first))
   :constructionStarted :started
   :closed :closed})

(defn state-timestamp [{state :state :as application}]
  ((state-timestamp-fn (keyword state)) application))

(defn all-state-timestamps [application]
  (into {}
    (map
      (fn [state] [state (state-timestamp (assoc application :state state))])
      (keys state-timestamp-fn))))

(defn by-type [documents]
  (group-by (comp keyword :name :schema-info) documents))

(defn empty-strings-to-nil [v]
  (when-not (and (string? v) (s/blank? v)) v))

(defn documents-by-type-without-blanks
  "Converts blank strings to nils and groups documents by schema name"
  [{documents :documents}]
  (by-type (walk/postwalk empty-strings-to-nil documents)))

(def ^:private puolto-mapping {:condition "ehdoilla"
                               :no "ei puolla"
                               :yes "puoltaa"})

(defn- get-statement [statement]
  (let [lausunto {:Lausunto
                  {:id (:id statement)
                   :viranomainen (get-in statement [:person :text])
                   :pyyntoPvm (to-xml-date (:requested statement))}}]
    (if-not (:status statement)
      lausunto
      (assoc-in lausunto [:Lausunto :lausuntotieto] {:Lausunto
                                                     {:viranomainen (get-in statement [:person :text])
                                                      :lausunto (:text statement)
                                                      :lausuntoPvm (to-xml-date (:given statement))
                                                      :puoltotieto
                                                      {:Puolto
                                                       {:puolto ((keyword (:status statement)) puolto-mapping)}}}}))))

(defn get-statements [statements]
  ;Returing vector because this element to be Associative
  (vec (map get-statement statements)))


(defn muu-select-map
  "If 'sel-val' is \"other\" considers 'muu-key' and 'muu-val', else considers 'sel-key' and 'sel-val'.
   If value (either 'muu-val' or 'sel-val' is blank, return nil, else return map with
   considered key mapped to considered value."
  [muu-key muu-val sel-key sel-val]
  (let [muu (= "other" sel-val)
        k   (if muu muu-key sel-key)
        v   (if muu muu-val sel-val)]
    (when-not (s/blank? v)
      {k v})))

(def ya-operation-type-to-usage-description
  {:ya-kayttolupa-tapahtumat "erilaiset messujen ja tapahtumien aikaiset alueiden k\u00e4yt\u00f6t"
   :ya-kayttolupa-mainostus-ja-viitoitus "mainoslaitteiden ja opasteviittojen sijoittaminen"
   :ya-kayttolupa-harrastustoiminnan-jarjestaminen "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-metsastys "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-vesistoluvat "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-terassit "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-kioskit "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-kayttolupa-muu-kayttolupa "muu kaytt\u00f6lupa"
   :ya-kayttolupa-nostotyot "kadulta tapahtuvat nostot"
   :ya-kayttolupa-vaihtolavat "muu kaytt\u00f6lupa"
   :ya-kayttolupa-kattolumien-pudotustyot "muu kaytt\u00f6lupa"
   :ya-kayttolupa-muu-liikennealuetyo "muu kaytt\u00f6lupa"
   :ya-kayttolupa-talon-julkisivutyot "kadulle pystytett\u00e4v\u00e4t rakennustelineet"
   :ya-kayttolupa-talon-rakennustyot "kiinteist\u00f6n rakentamis- ja korjaamisty\u00f6t, joiden suorittamiseksi rajataan osa kadusta tai yleisest\u00e4 alueesta ty\u00f6maaksi (ei kaivut\u00f6it\u00e4)"
   :ya-kayttolupa-muu-tyomaakaytto "muut yleiselle alueelle kohdistuvat tilan k\u00e4yt\u00f6t"
   :ya-katulupa-vesi-ja-viemarityot "vesihuoltoverkostoty\u00f6"
   :ya-katulupa-maalampotyot "muu"
   :ya-katulupa-kaukolampotyot "kaukol\u00e4mp\u00f6verkostoty\u00f6"
   :ya-katulupa-kaapelityot "tietoliikenneverkostoty\u00f6"
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat "verkoston liitosty\u00f6"
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-maalampoputkien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-kaukolampoputkien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen "pysyvien maanalaisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-muuntamoiden-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-jatekatoksien-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen "pysyvien maanp\u00e4\u00e4llisten rakenteiden sijoittaminen"
   :ya-sijoituslupa-muu-sijoituslupa "muu sijoituslupa"})

(def ya-operation-type-to-additional-usage-description
  {;; Muu kayttolupa
   :ya-kayttolupa-vaihtolavat                      "vaihtolavat"
   :ya-kayttolupa-kattolumien-pudotustyot          "kattolumien pudotusty\u00f6t"
   :ya-kayttolupa-muu-liikennealuetyo              "muu liikennealuety\u00f6"
   :ya-kayttolupa-muu-kayttolupa                   "muu k\u00e4ytt\u00f6lupa"
   ;; Muut yleiselle alueelle kohdistuvat tilan kaytot
   :ya-kayttolupa-harrastustoiminnan-jarjestaminen "harrastustoiminnan j\u00e4rjest\u00e4minen"
   :ya-kayttolupa-metsastys                        "mets\u00e4stys"
   :ya-kayttolupa-vesistoluvat                     "vesistoluvat"
   :ya-kayttolupa-terassit                         "terassit"
   :ya-kayttolupa-kioskit                          "kioskit"
   :ya-kayttolupa-muu-tyomaakaytto                 "muu ty\u00f6maak\u00e4ytt\u00f6"
   ;; Kaivu- tai katutyolupa
   :ya-katulupa-vesi-ja-viemarityot                "vesi-ja-viem\u00e4rity\u00f6t"
   :ya-katulupa-maalampotyot                     "maal\u00e4mp\u00f6ty\u00f6t"
   :ya-katulupa-kaukolampotyot                     "kaukol\u00e4mp\u00f6ty\u00f6t"
   :ya-katulupa-kaapelityot                        "kaapelity\u00f6t"
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat      "kiinteist\u00f6n johto-, kaapeli- ja putkiliitynn\u00e4t"
   ;; Pysyvien maanalaisten rakenteiden sijoittaminen
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen        "vesi- ja viem\u00e4rijohtojen sijoittaminen"
   :ya-sijoituslupa-maalampoputkien-sijoittaminen                "maal\u00e4mp\u00f6putkien sijoittaminen"
   :ya-sijoituslupa-kaukolampoputkien-sijoittaminen                "kaukol\u00e4mp\u00f6putkien sijoittaminen"
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen "s\u00e4hk\u00f6-, data- ja muiden kaapelien sijoittaminen"
   :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen       "rakennuksen tai sen osan sijoittaminen"
   ;; pysyvien maanpaallisten rakenteiden sijoittaminen
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen                   "ilmajohtojen sijoittaminen"
   :ya-sijoituslupa-muuntamoiden-sijoittaminen                   "muuntamoiden sijoittaminen"
   :ya-sijoituslupa-jatekatoksien-sijoittaminen                  "j\u00e4tekatoksien sijoittaminen"
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen   "leikkipaikan tai koiratarhan sijoittaminen"
   :ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen     "rakennuksen pelastuspaikan sijoittaminen"
   })

(def ya-operation-type-to-schema-name-key
  {:ya-kayttolupa-tapahtumat                                     :Kayttolupa
   :ya-kayttolupa-mainostus-ja-viitoitus                         :Kayttolupa
   :ya-kayttolupa-harrastustoiminnan-jarjestaminen               :Kayttolupa
   :ya-kayttolupa-metsastys                                      :Kayttolupa
   :ya-kayttolupa-vesistoluvat                                   :Kayttolupa
   :ya-kayttolupa-terassit                                       :Kayttolupa
   :ya-kayttolupa-kioskit                                        :Kayttolupa
   :ya-kayttolupa-muu-kayttolupa                                 :Kayttolupa
   :ya-kayttolupa-nostotyot                                      :Kayttolupa
   :ya-kayttolupa-vaihtolavat                                    :Kayttolupa
   :ya-kayttolupa-kattolumien-pudotustyot                        :Kayttolupa
   :ya-kayttolupa-muu-liikennealuetyo                            :Kayttolupa
   :ya-kayttolupa-talon-julkisivutyot                            :Kayttolupa
   :ya-kayttolupa-talon-rakennustyot                             :Kayttolupa
   :ya-kayttolupa-muu-tyomaakaytto                               :Kayttolupa
   :ya-katulupa-vesi-ja-viemarityot                              :Tyolupa
   :ya-katulupa-maalampotyot                                     :Tyolupa
   :ya-katulupa-kaukolampotyot                                   :Tyolupa
   :ya-katulupa-kaapelityot                                      :Tyolupa
   :ya-katulupa-kiinteiston-johto-kaapeli-ja-putkiliitynnat      :Tyolupa
   :ya-sijoituslupa-vesi-ja-viemarijohtojen-sijoittaminen        :Sijoituslupa
   :ya-sijoituslupa-maalampoputkien-sijoittaminen                :Sijoituslupa
   :ya-sijoituslupa-kaukolampoputkien-sijoittaminen              :Sijoituslupa
   :ya-sijoituslupa-sahko-data-ja-muiden-kaapelien-sijoittaminen :Sijoituslupa
   :ya-sijoituslupa-rakennuksen-tai-sen-osan-sijoittaminen       :Sijoituslupa
   :ya-sijoituslupa-ilmajohtojen-sijoittaminen                   :Sijoituslupa
   :ya-sijoituslupa-muuntamoiden-sijoittaminen                   :Sijoituslupa
   :ya-sijoituslupa-jatekatoksien-sijoittaminen                  :Sijoituslupa
   :ya-sijoituslupa-leikkipaikan-tai-koiratarhan-sijoittaminen   :Sijoituslupa
   :ya-sijoituslupa-rakennuksen-pelastuspaikan-sijoittaminen     :Sijoituslupa
   :ya-sijoituslupa-muu-sijoituslupa                             :Sijoituslupa})

(defn toimituksen-tiedot [{:keys [title municipality]} lang]
  {:aineistonnimi title
   :aineistotoimittaja "lupapiste@solita.fi"
   :tila toimituksenTiedot-tila
   :toimitusPvm (to-xml-date (now))
   :kuntakoodi municipality
   :kielitieto lang})

(defn- get-handler [{handler :authority}]
  (if (seq handler)
    {:henkilo {:nimi {:etunimi (:firstName handler) :sukunimi (:lastName handler)}}}
    empty-tag))


(defn get-state [application]
  (let [state-timestamps (-<> (all-state-timestamps application)
                           (dissoc :sent :closed) ; sent date will be returned from toimituksen-tiedot function, closed has no valid KRYSP enumeration
                           strip-nils
                           (sort-by second <>))]
    (mapv
      (fn [[state ts]]
        {:Tilamuutos
         {:tila (application-state-to-krysp-state state)
          :pvm (to-xml-date ts)
          :kasittelija (get-handler application)}})
      state-timestamps)))


(defn lupatunnus [{:keys [id submitted] :as application}]
  {:pre [id]}
  {:LupaTunnus
   (assoc-when
     {:muuTunnustieto {:MuuTunnus {:tunnus id, :sovellus "Lupapiste"}}}
     :saapumisPvm (to-xml-date submitted)
     :kuntalupatunnus (-> application :verdicts first :kuntalupatunnus))})

(def kuntaRoolikoodi-to-vrkRooliKoodi
  {"Rakennusvalvonta-asian hakija"  "hakija"
   "Rakennusvalvonta-asian laskun maksaja"  "maksaja"
   "p\u00e4\u00e4suunnittelija"     "p\u00e4\u00e4suunnittelija"
   "GEO-suunnittelija"              "erityissuunnittelija"
   "LVI-suunnittelija" `            "erityissuunnittelija"
   "RAK-rakennesuunnittelija"       "erityissuunnittelija"
   "ARK-rakennussuunnittelija"      "rakennussuunnittelija"
   "KVV-ty\u00F6njohtaja"           "ty\u00f6njohtaja"
   "IV-ty\u00F6njohtaja"            "ty\u00f6njohtaja"
   "erityisalojen ty\u00F6njohtaja" "ty\u00f6njohtaja"
   "vastaava ty\u00F6njohtaja"      "ty\u00f6njohtaja"
   "ty\u00F6njohtaja"               "ty\u00f6njohtaja"
   "ei tiedossa"                    "ei tiedossa"
   "Rakennuksen omistaja"           "rakennuksen omistaja"

   ; TODO mappings for the rest
   :rakennuspaikanomistaja          "rakennuspaikan omistaja"
   :lupapaatoksentoimittaminen      "lupap\u00e4\u00e4t\u00f6ksen toimittaminen"
   :naapuri                         "naapuri"
   :lisatietojenantaja              "lis\u00e4tietojen antaja"
   :muu                             "muu osapuoli"})

(def kuntaRoolikoodit
  {:paasuunnittelija       "p\u00e4\u00e4suunnittelija"
   :hakija                 "Rakennusvalvonta-asian hakija"
   :maksaja                "Rakennusvalvonta-asian laskun maksaja"
   :rakennuksenomistaja    "Rakennuksen omistaja"})

(defn get-simple-osoite [{:keys [katu postinumero postitoimipaikannimi] :as osoite}]
  (when katu  ;; required field in krysp (i.e. "osoitenimi")
    {:osoitenimi {:teksti katu}
     :postitoimipaikannimi postitoimipaikannimi
     :postinumero postinumero}))

(defn- get-name [henkilotiedot]
  {:nimi (select-keys henkilotiedot [:etunimi :sukunimi])})

(defn- get-yhteystiedot-data [yhteystiedot]
  {:sahkopostiosoite (-> yhteystiedot :email)
   :puhelin (-> yhteystiedot :puhelin)})

(defn- get-simple-yritys [{:keys [yritysnimi liikeJaYhteisoTunnus] :as yritys}]
  {:nimi yritysnimi, :liikeJaYhteisotunnus liikeJaYhteisoTunnus})

(defn- get-yritys-data [{:keys [osoite yhteyshenkilo verkkolaskutustiedot] :as yritys}]
  (let [yhteystiedot (:yhteystiedot yhteyshenkilo)
        postiosoite (get-simple-osoite osoite)]
    (merge (get-simple-yritys yritys)
           {:postiosoite postiosoite ; - 2.1.4
            :postiosoitetieto {:postiosoite postiosoite} ; 2.1.5+
            :puhelin (:puhelin yhteystiedot)
            :sahkopostiosoite (:email yhteystiedot)}
           (when verkkolaskutustiedot
             {:verkkolaskutustieto {:Verkkolaskutus {:ovtTunnus (:ovtTunnus verkkolaskutustiedot)
                                                    :verkkolaskuTunnus (:verkkolaskuTunnus verkkolaskutustiedot)
                                                    :valittajaTunnus (:valittajaTunnus verkkolaskutustiedot)}}}))))

(def ^:private default-role "ei tiedossa")
(defn- get-kuntaRooliKoodi [party party-type]
  (if (contains? kuntaRoolikoodit party-type)
    (kuntaRoolikoodit party-type)
    (let [code (or (get-in party [:kuntaRoolikoodi])
                   ; Old applications have kuntaRoolikoodi under patevyys group (LUPA-771)
                   (get-in party [:patevyys :kuntaRoolikoodi])
                   default-role)]
      (if (s/blank? code) default-role code))))


(defn- get-roolikoodit [kuntaRoolikoodi]
  {:kuntaRooliKoodi kuntaRoolikoodi ; Note the upper case 'Koodi'
   :VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolikoodi)})

(defn get-osapuoli-data [osapuoli party-type]
  (let [selected-value (or (-> osapuoli :_selected) (-> osapuoli first key))
        yritys-type-osapuoli? (= "yritys" selected-value)
        henkilo        (if yritys-type-osapuoli?
                         (get-in osapuoli [:yritys :yhteyshenkilo])
                         (:henkilo osapuoli))]
    (when (-> henkilo :henkilotiedot :sukunimi)
      (let [kuntaRoolicode (get-kuntaRooliKoodi osapuoli party-type)
            omistajalaji   (muu-select-map
                             :muu (-> osapuoli :muu-omistajalaji)
                             :omistajalaji (-> osapuoli :omistajalaji))]
        (merge
          {:VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolicode)
           :kuntaRooliKoodi kuntaRoolicode
           :turvakieltoKytkin (true? (-> henkilo :henkilotiedot :turvakieltoKytkin))
           :henkilo (merge
                      (get-name (:henkilotiedot henkilo))
                      (get-yhteystiedot-data (:yhteystiedot henkilo))
                      (when-not yritys-type-osapuoli?
                        {:henkilotunnus (-> (:henkilotiedot henkilo) :hetu)
                         :osoite (get-simple-osoite (:osoite henkilo))}))}
          (when yritys-type-osapuoli?
            {:yritys  (get-yritys-data (:yritys osapuoli))})
          (when omistajalaji {:omistajalaji omistajalaji}))))))

(defn get-parties-by-type [documents tag-name party-type doc-transformer]
  (for [doc (documents party-type)
        :let [osapuoli (:data doc)]
        :when (seq osapuoli)]
    {tag-name (doc-transformer osapuoli party-type)}))

(defn get-parties [documents]
  (filter #(seq (:Osapuoli %))
    (into
      (get-parties-by-type documents :Osapuoli :hakija get-osapuoli-data)
      (get-parties-by-type documents :Osapuoli :maksaja get-osapuoli-data))))

(defn get-suunnittelija-data [suunnittelija party-type]
  (when (-> suunnittelija :henkilotiedot :sukunimi)
    (let [kuntaRoolikoodi (get-kuntaRooliKoodi suunnittelija party-type)
          codes {:suunnittelijaRoolikoodi kuntaRoolikoodi ; Note the lower case 'koodi'
                 :VRKrooliKoodi (kuntaRoolikoodi-to-vrkRooliKoodi kuntaRoolikoodi)}
          patevyys (:patevyys suunnittelija)
          osoite (get-simple-osoite (:osoite suunnittelija))
          henkilo (merge (get-name (:henkilotiedot suunnittelija))
                    {:osoite osoite}
                    {:henkilotunnus (-> suunnittelija :henkilotiedot :hetu)}
                    (get-yhteystiedot-data (:yhteystiedot suunnittelija)))]
      (merge codes
        {:koulutus (:koulutusvalinta patevyys)
         :patevyysvaatimusluokka (:patevyysluokka patevyys)
         :valmistumisvuosi (:valmistumisvuosi patevyys)
         :kokemusvuodet (:kokemus patevyys)}
        (when (-> henkilo :nimi :sukunimi)
          {:henkilo henkilo})
        (when (-> suunnittelija :yritys :yritysnimi s/blank? not)
          {:yritys (merge
                     (get-simple-yritys (:yritys suunnittelija))
                     {:postiosoite osoite ; - 2.1.4
                      ; 2.1.5+
                      :postiosoitetieto {:postiosoite osoite}})})))))

(defn- get-designers [documents]
  (filter #(seq (:Suunnittelija %))
    (into
      (get-parties-by-type documents :Suunnittelija :paasuunnittelija get-suunnittelija-data)
      (get-parties-by-type documents :Suunnittelija :suunnittelija get-suunnittelija-data))))

(defn- concat-tyotehtavat-to-string [selections]
  (let [joined (clojure.string/join ","
                 (reduce
                   (fn [r [k v]]
                     (if (true? v)
                       (conj r (name k))
                       r))
                   []
                   (-> (dissoc selections :muuMika))))]
    (if (-> selections :muuMika s/blank? not)
      (str joined "," (-> selections :muuMika))
      joined)))

(defn- get-sijaistustieto [{:keys [sijaistettavaHloEtunimi sijaistettavaHloSukunimi alkamisPvm paattymisPvm] :as sijaistus} sijaistettavaRooli]
  (when (or sijaistettavaHloEtunimi sijaistettavaHloSukunimi)
    {:Sijaistus (assoc-when {}
                  :sijaistettavaHlo (s/trim (str sijaistettavaHloEtunimi " " sijaistettavaHloSukunimi))
                  :sijaistettavaRooli sijaistettavaRooli
                  :alkamisPvm (when-not (s/blank? alkamisPvm) (to-xml-date-from-string alkamisPvm))
                  :paattymisPvm (when-not (s/blank? paattymisPvm) (to-xml-date-from-string paattymisPvm)))}))

(defn- get-sijaistettava-hlo-214 [{:keys [sijaistettavaHloEtunimi sijaistettavaHloSukunimi] :as sijaistus}]
  (when (or sijaistettavaHloEtunimi sijaistettavaHloSukunimi)
    (s/trim (str sijaistettavaHloEtunimi " " sijaistettavaHloSukunimi))))

(defn- get-vastattava-tyotieto [{tyotehtavat :vastattavatTyotehtavat} lang]
  (with-lang lang
    (strip-nils
      (when (seq tyotehtavat)
        {:vastattavaTyotieto
         (remove nil?
           (map (fn [[k v]]
                  (when v
                    {:VastattavaTyo
                     {:vastattavaTyo
                      (if (= k :muuMika)
                        v
                        (let [loc-s (loc (str "osapuoli.tyonjohtaja.vastattavatTyotehtavat." (name k)))]
                          (assert (not (re-matches #"^\?\?\?.*" loc-s)))
                          loc-s))}}))
             tyotehtavat))}))))

(defn get-tyonjohtaja-data [lang tyonjohtaja party-type]
  (let [foremans (dissoc (get-suunnittelija-data tyonjohtaja party-type) :suunnittelijaRoolikoodi)
        patevyys (:patevyys tyonjohtaja)
        {:keys [alkamisPvm paattymisPvm] :as sijaistus} (:sijaistus tyonjohtaja)
        rooli    (get-kuntaRooliKoodi tyonjohtaja :tyonjohtaja)]
    (merge
      foremans
      {:tyonjohtajaRooliKoodi rooli
       :vastattavatTyotehtavat (concat-tyotehtavat-to-string (:vastattavatTyotehtavat tyonjohtaja))
       :patevyysvaatimusluokka (:patevyysvaatimusluokka patevyys)
       :valmistumisvuosi (:valmistumisvuosi patevyys)
       :kokemusvuodet (:kokemusvuodet patevyys)
       :valvottavienKohteidenMaara (:valvottavienKohteidenMaara patevyys)
       :tyonjohtajaHakemusKytkin (= "hakemus" (:tyonjohtajaHakemusKytkin patevyys))
       :sijaistustieto (get-sijaistustieto sijaistus rooli)}
      (when-not (s/blank? alkamisPvm) {:alkamisPvm (to-xml-date-from-string alkamisPvm)})
      (when-not (s/blank? paattymisPvm) {:paattymisPvm (to-xml-date-from-string paattymisPvm)})
      (get-vastattava-tyotieto tyonjohtaja lang)
      (let [sijaistettava-hlo (get-sijaistettava-hlo-214 sijaistus)]
        (when-not (ss/blank? sijaistettava-hlo)
          {:sijaistettavaHlo sijaistettava-hlo})))))

(defn- get-foremans [documents lang]
  (get-parties-by-type documents :Tyonjohtaja :tyonjohtaja (partial get-tyonjohtaja-data lang)))

(defn- get-neighbor [neighbor-name property-id]
  {:Naapuri {:henkilo neighbor-name
             :kiinteistotunnus property-id
             :hallintasuhde "Ei tiedossa"}})

(defn- get-neighbors [neighbors]
  (remove nil? (for [neighbor neighbors]
                   (let [status (last (:status neighbor))
                         propertyId (:propertyId neighbor)]
                     (case (:state status)
                       "response-given-ok" (get-neighbor (str (-> status :vetuma :firstName) " " (-> status :vetuma :lastName)) propertyId)
                       "mark-done" (get-neighbor (-> neighbor :owner :name) propertyId)
                       nil)))))

(defn osapuolet
  ([documents-by-types lang]
    (osapuolet documents-by-types nil))
  ([documents-by-types neighbors lang]
    {:Osapuolet
     {:osapuolitieto (get-parties documents-by-types)
      :suunnittelijatieto (get-designers documents-by-types)
      :tyonjohtajatieto (get-foremans documents-by-types lang)
      :naapuritieto (get-neighbors neighbors)}}))

(defn change-value-to-when [value to_compare new_val]
  (if (= value to_compare) new_val value))


(defn get-bulding-places [docs application]
  (for [doc docs
        :let [rakennuspaikka (:data doc)
              kiinteisto (:kiinteisto rakennuspaikka)
              id (:id doc)]]
    {:Rakennuspaikka
     {:yksilointitieto id
      :alkuHetki (to-xml-datetime (now))
      :kaavanaste (change-value-to-when (-> rakennuspaikka :kaavanaste) "eiKaavaa" "ei kaavaa")
      :rakennuspaikanKiinteistotieto {:RakennuspaikanKiinteisto
                                      {:kokotilaKytkin (s/blank? (-> kiinteisto :maaraalaTunnus))
                                       :hallintaperuste (-> rakennuspaikka :hallintaperuste)
                                       :kiinteistotieto {:Kiinteisto (merge {:tilannimi (-> kiinteisto :tilanNimi)
                                                                             :kiinteistotunnus (:propertyId application)
                                                                             :rantaKytkin (true? (-> kiinteisto :rantaKytkin))}
                                                         (when (-> kiinteisto :maaraalaTunnus)
                                                           {:maaraAlaTunnus (str "M" (-> kiinteisto :maaraalaTunnus))}))}}}}}))

(defn get-viitelupatieto [link-permit-data]
  (when link-permit-data
    (assoc-in
      (if (= (:type link-permit-data) "kuntalupatunnus")
        {:LupaTunnus {:kuntalupatunnus (:id link-permit-data)}}
        (lupatunnus link-permit-data))
      [:LupaTunnus :viittaus] "edellinen rakennusvalvonta-asia")))

(defn get-kasittelytieto-ymp [application kt-key]
  {kt-key {:muutosHetki (to-xml-datetime (:modified application))
           :hakemuksenTila (ymp-application-state-to-krysp-state (keyword (:state application)))
           :asiatunnus (:id application)
           :paivaysPvm (to-xml-date (state-timestamp application))
           :kasittelija (let [handler (:authority application)]
                          (if (seq handler)
                            {:henkilo
                             {:nimi {:etunimi  (:firstName handler)
                                     :sukunimi (:lastName handler)}}}
                            empty-tag))}})

(defn get-henkilo [henkilo]
  (let [nimi (assoc-when {}
                         :etunimi (-> henkilo :henkilotiedot :etunimi)
                         :sukunimi (-> henkilo :henkilotiedot :sukunimi))
        teksti (assoc-when {} :teksti (-> henkilo :osoite :katu))
        osoite (assoc-when {}
                           :osoitenimi teksti
                           :postinumero (-> henkilo :osoite :postinumero)
                           :postitoimipaikannimi (-> henkilo :osoite :postitoimipaikannimi))]
    (not-empty
      (assoc-when {}
                  :nimi nimi
                  :osoite osoite
                  :sahkopostiosoite (-> henkilo :yhteystiedot :email)
                  :puhelin (-> henkilo :yhteystiedot :puhelin)
                   :henkilotunnus (-> henkilo :henkilotiedot :hetu)))))

(defn get-yhteystiedot [unwrapped-party-doc]
  (if (= (-> unwrapped-party-doc :data :_selected) "yritys")
    (let [yritys (-> unwrapped-party-doc :data :yritys)
          {:keys [yhteyshenkilo osoite]} yritys
          {:keys [etunimi sukunimi]} (:henkilotiedot yhteyshenkilo)
          {:keys [puhelin email]} (:yhteystiedot yhteyshenkilo)
          yhteyshenkilon-nimi (s/trim (str etunimi " " sukunimi))
          osoite (get-simple-osoite (:osoite yritys))]
      (not-empty
        (assoc-when {}
          :yTunnus (:liikeJaYhteisoTunnus yritys)
          :yrityksenNimi (:yritysnimi yritys)
          :yhteyshenkilonNimi (when-not (ss/blank? yhteyshenkilon-nimi) yhteyshenkilon-nimi)
          :osoitetieto (when (seq osoite) {:Osoite osoite})
          :puhelinnumero puhelin
          :sahkopostiosoite email)))
    (when-let [henkilo (-> unwrapped-party-doc :data :henkilo)]
      (let [{:keys [henkilotiedot osoite yhteystiedot]} henkilo
            teksti (assoc-when {} :teksti (:katu osoite))
            osoite (assoc-when {}
                     :osoitenimi teksti
                     :postinumero (:postinumero osoite)
                     :postitoimipaikannimi (:postitoimipaikannimi osoite))]
        (not-empty
         (assoc-when {}
           :henkilotunnus (:hetu henkilotiedot)
           :sukunimi (:sukunimi henkilotiedot)
           :etunimi (:etunimi henkilotiedot)
           :osoitetieto (when (seq osoite) {:Osoite osoite})
           :puhelinnumero (:puhelin yhteystiedot)
           :sahkopostiosoite (:email yhteystiedot))))
      )))

(defn get-verkkolaskutustieto [unwrapped-party-doc]
  {:Verkkolaskutus (get-in unwrapped-party-doc [:data :yritys :verkkolaskutustiedot])})

(defn get-maksajatiedot [unwrapped-party-doc]
  (merge
    (get-yhteystiedot unwrapped-party-doc)
    (not-empty
      (assoc-when {}
        :laskuviite (get-in unwrapped-party-doc [:data :laskuviite])
        ; TODO
        :verkkolaskutustieto (get-verkkolaskutustieto unwrapped-party-doc)))))

(defn- get-pos [coordinates]
  {:pos (map #(str (-> % .x) " " (-> % .y)) coordinates)})

(defn- point-drawing [drawing]
  (let  [geometry (:geometry drawing)
         p (jts/read-wkt-str geometry)
         cord (.getCoordinate p)]
    {:Sijainti
     {:piste {:Point {:pos (str (-> cord .x) " " (-> cord .y))}}}}))

(defn- linestring-drawing [drawing]
  (let  [geometry (:geometry drawing)
         ls (jts/read-wkt-str geometry)]
    {:Sijainti
     {:viiva {:LineString (get-pos (-> ls .getCoordinates))}}}))

(defn- polygon-drawing [drawing]
  (let  [geometry (:geometry drawing)
         polygon (jts/read-wkt-str geometry)]
    {:Sijainti
     {:alue {:Polygon {:exterior {:LinearRing (get-pos (-> polygon .getCoordinates))}}}}}))

(defn- drawing-type? [t drawing]
  (.startsWith (:geometry drawing) t))

(defn- drawings-as-krysp [drawings]
   (concat (map point-drawing (filter (partial drawing-type? "POINT") drawings))
           (map linestring-drawing (filter (partial drawing-type? "LINESTRING") drawings))
           (map polygon-drawing (filter (partial drawing-type? "POLYGON") drawings))))


(defn get-sijaintitieto [application]
  (let [drawings (drawings-as-krysp (:drawings application))]
    (cons {:Sijainti {:osoite {:yksilointitieto (:id application)
                               :alkuHetki (to-xml-datetime (now))
                               :osoitenimi {:teksti (:address application)}}
                      :piste {:Point {:pos (str (:x (:location application)) " " (:y (:location application)))}}}}
      drawings)))