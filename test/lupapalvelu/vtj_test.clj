(ns lupapalvelu.vtj
  (:use lupapalvelu.vtj
        clojure.test
        midje.sweet))

(def data "%3C%3Fxml+version%3D%221.0%22+encoding%3D%22ISO-8859-1%22+standalone%3D%22yes%22%3F%3E%3Cns2%3AVTJHenkiloVastaussanoma+versio%3D%221.0%22+sanomatunnus%3D%22PERUSJHHS2%22+tietojenPoimintaaika%3D%2220121009130453%22+xmlns%3Ans2%3D%22http%3A%2F%2Fxml.vrk.fi%2Fschema%2Fvtjkysely%22+xmlns%3D%22http%3A%2F%2Ftempuri.org%2F%22%3E%3Cns2%3AAsiakasinfo%3E%3Cns2%3AInfoS%3E09.10.2012+13%3A04%3C%2Fns2%3AInfoS%3E%3Cns2%3AInfoR%3E09.10.2012+13%3A04%3C%2Fns2%3AInfoR%3E%3Cns2%3AInfoE%3E09.10.2012+13%3A04%3C%2Fns2%3AInfoE%3E%3C%2Fns2%3AAsiakasinfo%3E%3Cns2%3APaluukoodi+koodi%3D%220000%22%3EHaku+onnistui%3C%2Fns2%3APaluukoodi%3E%3Cns2%3AHakuperusteet%3E%3Cns2%3AHenkilotunnus+hakuperusteTekstiE%3D%22Found%22+hakuperusteTekstiR%3D%22Hittades%22+hakuperusteTekstiS%3D%22L%F6ytyi%22+hakuperustePaluukoodi%3D%221%22%3E081181-9984%3C%2Fns2%3AHenkilotunnus%3E%3Cns2%3ASahkoinenAsiointitunnus+hakuperusteTekstiE%3D%22Not+used%22+hakuperusteTekstiR%3D%22Beteckningen+har+inte+anv%E4ndat%22+hakuperusteTekstiS%3D%22Tunnushakuperustetta+ei+ole+kaytetty%22+hakuperustePaluukoodi%3D%224%22%3E%3C%2Fns2%3ASahkoinenAsiointitunnus%3E%3C%2Fns2%3AHakuperusteet%3E%3Cns2%3AHenkilo%3E%3Cns2%3AHenkilotunnus+voimassaolokoodi%3D%221%22%3E081181-9984%3C%2Fns2%3AHenkilotunnus%3E%3Cns2%3ANykyinenSukunimi%3E%3Cns2%3ASukunimi%3EMarttila%3C%2Fns2%3ASukunimi%3E%3C%2Fns2%3ANykyinenSukunimi%3E%3Cns2%3ANykyisetEtunimet%3E%3Cns2%3AEtunimet%3ESylvi+Sofie%3C%2Fns2%3AEtunimet%3E%3C%2Fns2%3ANykyisetEtunimet%3E%3Cns2%3AVakinainenKotimainenLahiosoite%3E%3Cns2%3ALahiosoiteS%3ESep%E4nkatu+11+A+5%3C%2Fns2%3ALahiosoiteS%3E%3Cns2%3ALahiosoiteR%3E%3C%2Fns2%3ALahiosoiteR%3E%3Cns2%3APostinumero%3E70100%3C%2Fns2%3APostinumero%3E%3Cns2%3APostitoimipaikkaS%3EKUOPIO%3C%2Fns2%3APostitoimipaikkaS%3E%3Cns2%3APostitoimipaikkaR%3EKUOPIO%3C%2Fns2%3APostitoimipaikkaR%3E%3Cns2%3AAsuminenAlkupvm%3E20050525%3C%2Fns2%3AAsuminenAlkupvm%3E%3Cns2%3AAsuminenLoppupvm%3E%3C%2Fns2%3AAsuminenLoppupvm%3E%3C%2Fns2%3AVakinainenKotimainenLahiosoite%3E%3Cns2%3AVakinainenUlkomainenLahiosoite%3E%3Cns2%3AUlkomainenLahiosoite%3E%3C%2Fns2%3AUlkomainenLahiosoite%3E%3Cns2%3AUlkomainenPaikkakuntaJaValtioS%3E%3C%2Fns2%3AUlkomainenPaikkakuntaJaValtioS%3E%3Cns2%3AUlkomainenPaikkakuntaJaValtioR%3E%3C%2Fns2%3AUlkomainenPaikkakuntaJaValtioR%3E%3Cns2%3AUlkomainenPaikkakuntaJaValtioSelvakielinen%3E%3C%2Fns2%3AUlkomainenPaikkakuntaJaValtioSelvakielinen%3E%3Cns2%3AValtiokoodi3%3E%3C%2Fns2%3AValtiokoodi3%3E%3Cns2%3AAsuminenAlkupvm%3E%3C%2Fns2%3AAsuminenAlkupvm%3E%3Cns2%3AAsuminenLoppupvm%3E%3C%2Fns2%3AAsuminenLoppupvm%3E%3C%2Fns2%3AVakinainenUlkomainenLahiosoite%3E%3Cns2%3AKotikunta%3E%3Cns2%3AKuntanumero%3E297%3C%2Fns2%3AKuntanumero%3E%3Cns2%3AKuntaS%3EKuopio%3C%2Fns2%3AKuntaS%3E%3Cns2%3AKuntaR%3EKuopio%3C%2Fns2%3AKuntaR%3E%3Cns2%3AKuntasuhdeAlkupvm%3E20050525%3C%2Fns2%3AKuntasuhdeAlkupvm%3E%3C%2Fns2%3AKotikunta%3E%3Cns2%3AKuolintiedot%3E%3Cns2%3AKuolinpvm%3E%3C%2Fns2%3AKuolinpvm%3E%3C%2Fns2%3AKuolintiedot%3E%3Cns2%3AAidinkieli%3E%3Cns2%3AKielikoodi%3Efi%3C%2Fns2%3AKielikoodi%3E%3Cns2%3AKieliS%3Esuomi%3C%2Fns2%3AKieliS%3E%3Cns2%3AKieliR%3Efinska%3C%2Fns2%3AKieliR%3E%3Cns2%3AKieliSelvakielinen%3E%3C%2Fns2%3AKieliSelvakielinen%3E%3C%2Fns2%3AAidinkieli%3E%3Cns2%3ASuomenKansalaisuusTietokoodi%3E1%3C%2Fns2%3ASuomenKansalaisuusTietokoodi%3E%3C%2Fns2%3AHenkilo%3E%3C%2Fns2%3AVTJHenkiloVastaussanoma%3E")

(facts
  (fact "data can be extracted"
    (parse data) => truthy)
  (fact "select returns right info"
    (-> data parse (select :NykyisetEtunimet :Etunimet)) => "Sylvi Sofie")
  (fact "data is parsed correctly"
     (extract data {:firstName   [:NykyisetEtunimet :Etunimet]
                    :lastName    [:NykyinenSukunimi :Sukunimi]
                    :street      [:VakinainenKotimainenLahiosoite :LahiosoiteS]
                    :zip         [:VakinainenKotimainenLahiosoite :Postinumero]
                    :city        [:VakinainenKotimainenLahiosoite :PostitoimipaikkaS]}) 
     => {:lastName "Marttila"
         :firstName "Sylvi Sofie"
         :city "KUOPIO"
         :street "Sepänkatu 11 A 5"
         :zip "70100"}))