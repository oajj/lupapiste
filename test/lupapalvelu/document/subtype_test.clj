(ns lupapalvelu.document.subtype-test
  (:use [lupapalvelu.document.subtype]
        [midje.sweet]))

(facts "Facts about generic subtype validation"
  (fact (subtype-validation {} "what-ever") => nil?)
  (fact (subtype-validation {:subtype :foobar} "what-ever") => [:err "illegal-subtype"]))

(facts "Facts about email validation"
  (fact (subtype-validation {:subtype :email} "") => nil?)
  (fact (subtype-validation {:subtype :email} "a") => [:warn "illegal-email"])
  (fact (subtype-validation {:subtype :email} "a@b.") => [:warn "illegal-email"])
  (fact (subtype-validation {:subtype :email} "a@b.c") => nil?))

(facts "Facts about tel validation"
  (fact (subtype-validation {:subtype :tel} "") => nil?)
  (fact (subtype-validation {:subtype :tel} "fozzaa") => [:warn "illegal-tel"])
  (fact (subtype-validation {:subtype :tel} "1+2") => [:warn "illegal-tel"])
  (fact (subtype-validation {:subtype :tel} "1") => nil?)
  (fact (subtype-validation {:subtype :tel} "123-456") => nil?)
  (fact (subtype-validation {:subtype :tel} "+358-123 456") => nil?))

(facts "Facts about number validation"
  (fact (subtype-validation {:subtype :number} "123") => nil?)
  (fact (subtype-validation {:subtype :number} "abc") => [:warn "illegal-number"])
  (fact (subtype-validation {:subtype :number} " 123 ") => [:warn "illegal-number"]))

(facts "Facts about letter validation"
  (subtype-validation {:subtype :letter} "a") => nil?
  (subtype-validation {:subtype :letter} "A") => nil?
  (subtype-validation {:subtype :letter} "\u00e4") => nil?
  (subtype-validation {:subtype :letter} "1") => [:warn "illegal-letter"]
  (subtype-validation {:subtype :letter} "@") => [:warn "illegal-letter"]
  (subtype-validation {:subtype :letter :case :upper} "A") => nil?
  (subtype-validation {:subtype :letter :case :upper} "a") => [:warn "illegal-letter"]
  (subtype-validation {:subtype :letter :case :lower} "a") => nil?
  (subtype-validation {:subtype :letter :case :lower} "A") => [:warn "illegal-letter"])
