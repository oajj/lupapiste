(ns lupapalvelu.archiving-test
  (:require [midje.sweet :refer :all]
            [lupapalvelu.archiving :refer :all]))


(facts "Should archiving valid YA applications"
  (fact "Valid YA application with valid state - should be archived"
    (valid-ya-state? {:id "LP-XXX-2017-00001" :permitType "YA" :state "verdictGiven"}) => true
    (valid-ya-state? {:id "LP-XXX-2017-00002" :permitType "YA" :state "finished"}) => true)
       ;; pitäskö olla kaikki post verdict statet?
  (fact "Valid YA application with invalid state - should not be archived"
    (valid-ya-state? {:id "LP-XXX-2017-00003" :permitType "YA" :state "open"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00004" :permitType "YA" :state "submitted"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00005" :permitType "YA" :state "sent"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00006" :permitType "YA" :state "draft"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00007" :permitType "YA" :state "complementNeeded"}) => false)
  (fact "Valid R application with valid R state - should not be archived"
    (valid-ya-state? {:id "LP-XXX-2017-00008" :permitType "R" :state "verdictGiven"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00009" :permitType "R" :state "closed"}) => false
    (valid-ya-state? {:id "LP-XXX-2017-00009" :permitType "R" :state "foremanVerdictGiven"}) => false)
  (fact "Invalid R application with invalid YA state - should not be archived"
    (valid-ya-state? {:id "LP-XXX-2017-00010" :permitType "R" :state "finished"}) => false))
