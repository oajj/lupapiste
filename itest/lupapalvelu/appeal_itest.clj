(ns lupapalvelu.appeal-itest
  (:require [midje.sweet :refer :all]
            [lupapalvelu.factlet :refer :all]
            [lupapalvelu.itest-util :refer :all]
            [sade.core :refer [now]]
            [lupapalvelu.mongo :as mongo]))

(apply-remote-minimal)

(facts "Upserting appeal"
  (let [{app-id :id} (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
        created (now)]
    app-id => string?
    (fact "Can't add appeal before verdict"
      (command sonja :upsert-appeal
               :id app-id
               :verdictId (mongo/create-id)
               :type "appeal"
               :appellant "Pena"
               :datestamp 123456
               :text "foo") => (partial expected-failure? :error.command-illegal-state))

    (let [{vid :verdict-id} (give-verdict sonja app-id :verdictId "321-2016")]
      vid => string?
      (fact "wrong verdict ID"
        (command sonja :upsert-appeal
                 :id app-id
                 :verdictId (mongo/create-id)
                 :type "appeal"
                 :appellant "Pena"
                 :datestamp 123456
                 :text "foo") => (partial expected-failure? :error.verdict-not-found))
      (fact "successful appeal"
        (command sonja :upsert-appeal
                 :id app-id
                 :verdictId vid
                 :type "appeal"
                 :appellant "Pena"
                 :datestamp created
                 :text "foo") => ok?)
      (fact "text is optional"
        (command sonja :upsert-appeal
                 :id app-id
                 :verdictId vid
                 :type "rectification"
                 :appellant "Pena"
                 :datestamp created) => ok?)
      (fact "appeal is saved to application to be viewed"
        (map :type (:appeals (query-application pena app-id))) => (just ["appeal" "rectification"]))
      (fact "appeal query is OK"
        (let [response-data (:data (query pena :appeals :id app-id))
              verdictid-key (keyword vid)]
          (keys response-data) => (just [verdictid-key])
          (count (get response-data verdictid-key)) => 2))

      (fact* "updating appeal when appealId is given"
        (let [appeals-before  (appeals-for-verdict pena app-id vid)
              target-appeal   (first appeals-before)
              _ (command sonja :upsert-appeal :id app-id
                         :verdictId vid
                         :type "rectification"
                         :appellant "Teppo"
                         :datestamp created
                         :appealId (:id target-appeal)) => ok?
              appeals-after   (appeals-for-verdict pena app-id vid)
              updated-target-appeal   (first appeals-after)]

          (fact "Count of appeals is the same"
            (count appeals-before) => (count appeals-after))

          (fact "Appellant has been changed"
            (:appellant target-appeal) => "Pena"
            (:appellant updated-target-appeal) => "Teppo")))

      (fact "Upsert is validated"
        (fact "appealId must be found from application"
          (command sonja :upsert-appeal :id app-id
                   :verdictId vid
                   :type "rectification"
                   :appellant "Teppo"
                   :datestamp created
                   :appealId "foobar") => (partial expected-failure? :error.unknown-appeal))

        (let [appeals (appeals-for-verdict pena app-id vid)
              test-appeal (first appeals)]
          (fact "Appeal must be valid when upserting"
            (command sonja :upsert-appeal :id app-id
                     :verdictId vid
                     :type "trolol"
                     :appellant "Teppo"
                     :datestamp created
                     :appealId (:id test-appeal)) => (partial expected-failure? :error.invalid-appeal))))

      (fact "Delete"
        (let [appeals       (appeals-for-verdict pena app-id vid)
              first-appeal  (first appeals)
              second-appeal (second appeals)]
          (count appeals) => 2

          (command sonja :delete-appeal :id app-id
                   :verdictId vid
                   :appealId (:id first-appeal)) => ok?

          (fact "sane parameters"
            (command sonja :delete-appeal :id app-id
                     :verdictId vid
                     :appealId "foobar") => (partial expected-failure? :error.unknown-appeal)
            (command sonja :delete-appeal :id app-id
                     :verdictId "foofaa"
                     :appealId (:id second-appeal)) => (partial expected-failure? :error.verdict-not-found))

          (fact "one appeal left"
            (count (appeals-for-verdict pena app-id vid)) => 1))))))

(facts "Upserting appeal verdicts"
  (let [{app-id :id} (create-and-submit-application pena :operation "kerrostalo-rivitalo" :propertyId sipoo-property-id)
        created (now)]
    app-id => string?
    (fact "Can't add appeal before verdict"
      (command sonja :upsert-appeal-verdict
               :id app-id
               :verdictId (mongo/create-id)
               :giver "Teppo"
               :datestamp 123456
               :text "foo") => (partial expected-failure? :error.command-illegal-state))

    (let [{vid :verdict-id} (give-verdict sonja app-id :verdictId "321-2016")]
      vid => string?
      (fact "wrong verdict ID"
        (command sonja :upsert-appeal-verdict
                 :id app-id
                 :verdictId (mongo/create-id)
                 :giver "Teppo"
                 :datestamp 123456
                 :text "foo") => (partial expected-failure? :error.verdict-not-found))
      (fact "an appeal must exists before creating verdict appeal"
        (command sonja :upsert-appeal-verdict
                 :id app-id
                 :verdictId vid
                 :giver "Teppo"
                 :datestamp created
                 :text "foo") => (partial expected-failure? :error.appeals-not-found))
      (fact "first create appeal"
        (command sonja :upsert-appeal
                 :id app-id
                 :verdictId vid
                 :type "rectification"
                 :appellant "Pena"
                 :datestamp created
                 :text "rectification 1") => ok?)
      (fact "... then try to create invalid appeal verdict"
        (command sonja :upsert-appeal-verdict
                 :id app-id
                 :verdictId vid
                 :giver "Teppo"
                 :datestamp "18.3.2016"
                 :text "verdict for rectification 1") => (partial expected-failure? :error.invalid-appeal-verdict))
      (fact "... then actually create a valid appeal verdict"
        (command sonja :upsert-appeal-verdict
                 :id app-id
                 :verdictId vid
                 :giver "Teppo"
                 :datestamp (+ created 1)
                 :text "verdict for rectification 1") => ok?)

      (fact "appeal query is OK after giving appeal and appeal verdict"
        (let [response-data (:data (query pena :appeals :id app-id))
              verdictid-key (keyword vid)]
          (keys response-data) => (just [verdictid-key])
          (count (get response-data verdictid-key)) => 2
          (:type (first (get response-data verdictid-key))) => "rectification"
          (:type (second (get response-data verdictid-key))) => "appealVerdict"))

      (fact "appeal can't be edited after appeal verdict is created"
        (let [appeals (appeals-for-verdict pena app-id vid)
              test-appeal (first appeals)]
          (command sonja :upsert-appeal
                   :id app-id
                   :verdictId vid
                   :type "rectification"
                   :appellant "Pena"
                   :datestamp created
                   :text "rectification edition"
                   :appealId (:id test-appeal)) => (partial expected-failure? :error.appeal-verdict-already-exists)

          (fact "Query has editable flag set correctly"
            (:editable test-appeal) => false)))

      (fact "upsert is validated"
        (let [appeals             (appeals-for-verdict pena app-id vid)
              test-appeal-verdict (second appeals)
              appeal              (first appeals)]
          (fact "Can't update appeal with appeal-verdict endpoint"
            (command sonja :upsert-appeal-verdict :id app-id
                     :verdictId vid
                     :giver "Teppo"
                     :datestamp (+ created 1)
                     :appealId (:id appeal)) => (partial expected-failure? :error.unknown-appeal-verdict))))

      (fact* "Upsert updates successfully"
        (let [appeals-before  (appeals-for-verdict pena app-id vid)
              target-appeal-verdict   (second appeals-before)
              _ (:editable target-appeal-verdict) => true
              _ (command sonja :upsert-appeal-verdict :id app-id
                         :verdictId vid
                         :giver "Seppo"
                         :datestamp (+ created 1)
                         :appealId (:id target-appeal-verdict)) => ok?
              appeals-after   (appeals-for-verdict pena app-id vid)
              updated-target-appeal-verdict   (second appeals-after)]

          (fact "Count of appeals is the same"
            (count appeals-before) => (count appeals-after))

          (fact "Appellant has been changed"
            (:giver target-appeal-verdict) => "Teppo"
            (:giver updated-target-appeal-verdict) => "Seppo")))

      (fact "If new appeals have been made, old appeal-verdict can't be edited"
        (command sonja :upsert-appeal
                 :id app-id
                 :verdictId vid
                 :type "appeal"
                 :appellant "Pena again"
                 :datestamp (now)
                 :text "new appeal") => ok?
        (let [appeals (appeals-for-verdict pena app-id vid)
              target-appeal-verdict (second appeals)]
          (fact "upsert"
            (command sonja :upsert-appeal-verdict :id app-id
                     :verdictId vid
                     :giver "Seppo"
                     :datestamp created
                     :appealId (:id target-appeal-verdict)) => (partial expected-failure? :error.appeal-already-exists))

          (fact "delete"
            (command sonja :delete-appeal-verdict :id app-id
                     :verdictId vid
                     :appealId (:id target-appeal-verdict)) => (partial expected-failure? :error.appeal-already-exists))

          (fact "query has editable flag set correctly"
            (:editable target-appeal-verdict) => false)))

      (facts "Deleting"
        (let [appeals (appeals-for-verdict sonja app-id vid)]
          ; types of first second last:
          (map :type appeals) => (just ["rectification" "appealVerdict" "appeal"])
          (fact "Pena can't remove"
            (command pena :delete-appeal :id app-id
                     :verdictId vid
                     :appealId (-> appeals last :id)) => unauthorized?)
          (fact "First remove appeal"
            (command sonja :delete-appeal :id app-id
                     :verdictId vid
                     :appealId (-> appeals last :id)) => ok?)
          (fact "Can't still remove first rectification as verdict exists"
            (command sonja :delete-appeal :id app-id
                     :verdictId vid
                     :appealId (-> appeals first :id)) => (partial expected-failure? :error.appeal-verdict-already-exists))
          (fact "Can't remove verdict using command for appeals"
            (command sonja :delete-appeal :id app-id
                     :verdictId vid
                     :appealId (-> appeals second :id)) => (partial expected-failure? :error.unknown-appeal))
          (fact "Parameters must be sane"
            (command sonja :delete-appeal-verdict :id app-id
                     :verdictId vid
                     :appealId "foobar") => (partial expected-failure? :error.unknown-appeal-verdict))
          (fact "Appeal verdict can now be removed with correct parameters and application state"
            (command sonja :delete-appeal-verdict :id app-id
                     :verdictId vid
                     :appealId (-> appeals second :id)) => ok?)

          (let [appeals-after (appeals-for-verdict sonja app-id vid)]
            (count appeals-after) => 1
            (:editable (last appeals-after)) => true))))))