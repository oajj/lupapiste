(ns lupapalvelu.attachment.ram-test
  (:require [sade.schema-generators :as ssg]
            [sade.schemas :as ssc]
            [schema.core :as sc]
            [clojure.test.check.generators :as gen]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [lupapalvelu.attachment :refer [Attachment]]
            [lupapalvelu.attachment.ram :refer :all]))

(testable-privates lupapalvelu.attachment.ram make-ram-attachment)

(facts make-ram-attachment

       (let [application {:tosFunction  nil
                          :verdicts     []
                          :organization "753-R"
                          :state        :verdictGiven}
             base-attachment (ssg/generate Attachment)
             ram-attachment (make-ram-attachment base-attachment application 12345)]
         (fact "attachment type is copied"
               (:type ram-attachment) => (:type base-attachment))

         (fact "ram-link is created"
               (:ramLink ram-attachment) => (:id base-attachment))

         (fact "ram-attachment is valid"
               (sc/check Attachment ram-attachment) => nil))

       (let [application {:tosFunction  nil
                          :verdicts     []
                          :organization "753-R"
                          :state        :constructionStarted}
             base-attachment (assoc (ssg/generate Attachment) :contents "foo content" :size :A0 :scale :1:10)
             ram-attachment (make-ram-attachment base-attachment application 12345)]
         (fact "attachment meta is copied"
               (:contents ram-attachment) => "foo content"
               (:size ram-attachment) =>  :A0
               (:scale ram-attachment) => :1:10)))

(facts resolve-ram-linked-attachments
       (fact "backward ram link"
             (let [attachment1 (assoc (ssg/generate Attachment) :ramLink nil)
                   attachment2 (assoc (ssg/generate Attachment) :ramLink (:id attachment1))]
               (resolve-ram-links [attachment1 attachment2] (:id attachment2)) => [attachment1 attachment2]))

       (fact "forward ram link"
             (let [attachment1 (assoc (ssg/generate Attachment) :ramLink nil)
                   attachment2 (assoc (ssg/generate Attachment) :ramLink (:id attachment1))]
               (resolve-ram-links [attachment1 attachment2] (:id attachment1)) => [attachment1 attachment2]))

       (fact "no linking"
             (let [ids         (gen/sample ssg/object-id 5)
                   attachments (mapv (fn [id] {:id id :ramLink nil}) ids)]
               (resolve-ram-links attachments (:id (nth attachments 2))) => [(nth attachments 2)]))

       (fact "complete ram link chain"
             (let [ids         (gen/sample ssg/object-id 5)
                   attachments (map (fn [[link id]] {:id id :ramLink link}) (partition 2 1 ids))]
               (resolve-ram-links attachments (nth ids 2)) => attachments))

       (fact "inclomplete ram link chain"
             (let [ids         (gen/sample ssg/object-id 5)
                   attachments (-> (mapv (fn [[link id]] {:id id :ramLink link}) (partition 2 1 ids))
                                   (assoc-in [3 :ramLink] nil))]
               (resolve-ram-links attachments (nth ids 1)) => (take 3 attachments)))

       (fact "self-linked - should not get in endless loop"
             (let [id          (ssg/generate ssc/ObjectIdStr)
                   attachment  {:id id :ramLink id}]
               (resolve-ram-links [attachment] id) => [attachment]))

       (fact "link ring - should not get in endless loop"
             (let [ids         (gen/sample ssg/object-id 5)
                   attachments (map (fn [[link id]] {:id id :ramLink link}) (partition 2 1 ids ids)) ; padded with first id
                   result      (resolve-ram-links attachments (nth ids 2))]
               (count result) => (count attachments)
               (set result)   => (just attachments))))

(facts "RAM pre-checkers"
       (defn ram-fail [code] {:ok false :text (name code)})

       (let [att1 (dissoc (ssg/generate Attachment) :ramLink)
             att2 (assoc (ssg/generate Attachment) :ramLink (:id att1))
             att3 (assoc (ssg/generate Attachment) :ramLink (:id att2))]
         (fact "ram-status-ok"
               (ram-status-ok {:data {:attachmentId (:id att1)}
                               :application {:attachments [att1 att2]}}
                              ) => nil?
               (ram-status-ok {:data {:attachmentId (:id att2)}
                               :application {:attachments [att1 (dissoc att2 :state)]}}
                              ) => (contains (ram-fail :error.ram-not-approved))
               (ram-status-ok {:data {:attachmentId (:id att2)}
                               :application {:attachments [att1 (assoc att2 :state :ok)]}}
                              ) => nil?)

         (fact "ram-status-not-ok"
               (ram-status-not-ok {:data {:attachmentId (:id att1)}
                                   :application {:attachments [att1 att2]}}
                                  ) => nil?
               (ram-status-not-ok {:data {:attachmentId (:id att1)}
                                   :application {:attachments [(assoc att1 :state :ok) att2]}}
                                  ) => nil?
               (ram-status-not-ok {:data {:attachmentId (:id att2)}
                                   :application {:attachments [att1 (dissoc att2 :state)]}}
                                  ) => nil?
               (ram-status-not-ok {:data {:attachmentId (:id att2)}
                                   :application {:attachments [att1 (assoc att2 :state :ok)]}}
                                  ) => (contains (ram-fail :error.ram-approved)))

         (fact "ram-not-root-attachment"
               (ram-not-root-attachment {:user {:role :authority}
                                         :data {:attachmentId (:id att1)}
                                         :application {:attachments [att1]}}
                                        ) => nil?
               (ram-not-root-attachment  {:user {:role :applicant}
                                          :data {:attachmentId (:id att1)}
                                          :application {:attachments [att1]}}
                                         ) => nil?
               (ram-not-root-attachment  {:user {:role :applicant}
                                          :data {:attachmentId (:id att2)}
                                          :application {:attachments [att1]}}
                                         ) => nil?
               (ram-not-root-attachment {:user {:role :authority}
                                         :data {:attachmentId (:id att2)}
                                         :application {:attachments [att1]}}
                                        ) => nil?
               (ram-not-root-attachment  {:user {:role :authority}
                                          :data {:attachmentId (:id att1)}
                                          :application {:attachments [att1 att2]}}
                                         ) => nil?
               (ram-not-root-attachment  {:user {:role :applicant}
                                          :data {:attachmentId (:id att1)}
                                          :application {:attachments [att1 att2]}}
                                         )=> (ram-fail :error.ram-cannot-delete-root)
               (ram-not-root-attachment  {:user {:role :applicant}
                                          :data {:attachmentId (:id att2)}
                                          :application {:attachments [att1 att2]}}
                                         )= nil?
               (ram-not-root-attachment  {:user {:role :applicant}
                                          :data {:attachmentId (:id att2)}
                                          :application {:attachments [att1 att2 att3]}}
                                         ))))