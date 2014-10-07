(ns lupapalvelu.notification-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.itest-util :refer :all]))

(last-email) ; inbox zero

(facts "Subscription"
  (let [{:keys [id]} (create-and-submit-application pena)]

    (fact "applicant gets email"
      (comment-application sonja id false) => ok?
      (:to (last-email)) => (email-for "pena"))

    (fact "pena unsubscribes, no more email"
      (command pena :unsubscribe-notifications :id id :username "pena") => ok?
      (comment-application sonja id false) => ok?
      (last-email) => nil?)

    (fact "sonja resubscribes pena, emails start again"
      (command sonja :subscribe-notifications :id id :username "pena") => ok?
      (comment-application sonja id false) => ok?
      (:to (last-email)) => (email-for "pena"))

    (fact "sonja unsubscribes pena, no more email"
      (command sonja :unsubscribe-notifications :id id :username "pena") => ok?
      (comment-application sonja id false) => ok?
      (last-email) => nil?)

    (fact "pena resubscribes, emails start again"
      (command pena :subscribe-notifications :id id :username "pena") => ok?
      (comment-application sonja id false) => ok?
      (:to (last-email)) => (email-for "pena"))))
