(ns lupapalvelu.attachment-test
  (:require [clojure.string :as s]
            [midje.sweet :refer :all]
            [midje.util :refer [testable-privates]]
            [sade.strings :refer [encode-filename]]
            [sade.env :as env]
            [monger.operators :refer :all]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.attachment :refer :all]
            [lupapalvelu.attachment.type :as att-type]
            [lupapalvelu.attachment.metadata :refer :all]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen]
            [clojure.test :refer [is]]
            [schema.core :as sc]
            [sade.schemas :as ssc]
            [sade.schema-generators :as ssg]))

(testable-privates lupapalvelu.attachment
                   attachment-file-ids
                   version-number
                   latest-version-after-removing-file
                   build-version-updates
                   default-metadata-for-attachment-type
                   make-ram-attachment)

(def ascii-pattern #"[a-zA-Z0-9\-\.]+")

(defspec make-attachement-spec
  (prop/for-all [attachment-id           ssg/object-id
                 now                     ssg/timestamp
                 target                  (ssg/generator Target)
                 required?               gen/boolean
                 requested-by-authority? gen/boolean
                 locked?                 gen/boolean
                 application-state       (gen/elements states/all-states)
                 operation               (ssg/generator Operation)
                 attachment-type         (ssg/generator Type)
                 metadata                (ssg/generator {sc/Keyword sc/Str})
                 ;; Optional parameters
                 contents                (ssg/generator (sc/maybe sc/Str))
                 read-only?              (ssg/generator (sc/maybe sc/Bool))
                 source                  (ssg/generator (sc/maybe Source))]
                (let [validation-error (->> (make-attachment now target required? requested-by-authority? locked? application-state operation attachment-type metadata attachment-id contents read-only? source)
                                            (sc/check Attachment))]
                  (is (nil? validation-error)))))

(facts "Test file name encoding"
  (fact (encode-filename nil)                                 => nil)
  (fact (encode-filename "foo.txt")                           => "foo.txt")
  (fact (encode-filename (apply str (repeat 255 \x)))         => (apply str (repeat 255 \x)))
  (fact (encode-filename (apply str (repeat 256 \x)))         => (apply str (repeat 255 \x)))
  (fact (encode-filename (apply str (repeat 256 \x) ".txt"))  => (has-suffix ".txt"))
  (fact (encode-filename "\u00c4\u00e4kk\u00f6si\u00e4")      => (just ascii-pattern))
  (fact (encode-filename "/root/secret")                      => (just ascii-pattern))
  (fact (encode-filename "\\Windows\\cmd.exe")                => (just ascii-pattern))
  (fact (encode-filename "12345\t678\t90")                    => (just ascii-pattern))
  (fact (encode-filename "12345\n678\r\n90")                  => (just ascii-pattern)))

(def test-attachments [{:id "1", :latestVersion {:version {:major 9, :minor 7}}}])

(facts "Facts about next-attachment-version"
  (fact (next-attachment-version {:major 1 :minor 1} {:role :authority})  => {:major 1 :minor 2})
  (fact (next-attachment-version {:major 1 :minor 1} {:role :dude})       => {:major 2 :minor 0})
  (fact (next-attachment-version nil {:role :authority})  => {:major 0 :minor 1})
  (fact (next-attachment-version nil {:role :dude})       => {:major 1 :minor 0}))

(fact "version number"
  (version-number {:version {:major 1 :minor 16}}) => 1016)

(fact "Find latest version"
  (let [application {:attachments [{:id :attachment1
                                    :versions []}
                                   {:id :attachment2
                                    :versions [{:version { :major 1 :minor 0 }
                                                :fileId :file1
                                                :originalFileId :originalFileId1}
                                               {:version { :major 1 :minor 1 }
                                                :fileId :file2
                                                :originalFileId :originalFileId2}]}]}
        attachment (last (:attachments application))]
    (latest-version-after-removing-file attachment :file1) => {:version {:major 1 :minor 1}
                                                               :fileId :file2
                                                               :originalFileId :originalFileId2}

    (attachment-file-ids attachment) => (just #{:file1 :originalFileId1 :file2 :originalFileId2})
    (attachment-latest-file-id application :attachment2) => :file2))

(fact "make attachments"
  (let [type1 (ssg/generate Type)
        type2 (ssg/generate Type)]
    (make-attachments 999 :draft [{:type type1} {:type type2}] false true true)
    => (just [{:id                   "5790633c66e8f95ecc4287be"
               :locked               false
               :modified             999
               :op                   nil
               :state                :requires_user_action
               :target               nil
               :type                 type1
               :applicationState     :draft
               :contents             nil
               :signatures           []
               :versions             []
               :auth                 []
               :notNeeded            false
               :required             true
               :requestedByAuthority true
               :forPrinting          false
               :readOnly             false}
              {:id                   "5790633c66e8f95ecc4287be"
               :locked               false
               :modified             999
               :op                   nil
               :state                :requires_user_action
               :target               nil
               :type                 type2
               :applicationState     :draft
               :contents             nil
               :signatures           []
               :versions             []
               :auth                 []
               :notNeeded            false
               :required             true
               :requestedByAuthority true
               :forPrinting          false
               :readOnly             false}])
    (provided
      (mongo/create-id) => "5790633c66e8f95ecc4287be")))

(fact "attachment can be found with file-id"
  (get-attachment-info-by-file-id {:attachments [{:versions [{:fileId "123"}
                                                             {:fileId "234"}]}
                                                 {:versions [{:fileId "345"}
                                                             {:fileId "456"}]}]} "456") => {:versions [{:fileId "345"}
                                                                                                       {:fileId "456"}]})

(let [attachments [{:id 1 :versions [{:fileId "11"} {:fileId "21"}]}
                   {:id 2 :versions [{:fileId "12"} {:fileId "22"}]}
                   {:id 3 :versions [{:fileId "13"} {:fileId "23"}]}]]

  (facts "create-sent-timestamp-update-statements"
    (create-sent-timestamp-update-statements attachments ["12" "23"] 123) => {"attachments.1.sent" 123
                                                                              "attachments.2.sent" 123}))

(fact "All attachments are localized"
  (let [attachment-group-type-paths (->> (vals att-type/attachment-types-by-permit-type)
                                         (apply concat)
                                         (map (juxt :type-group :type-id)))]
    (fact "Meta: collected all types"
      (set (map second attachment-group-type-paths)) => att-type/all-attachment-type-ids)

    (doseq [lang ["fi" "sv"]
            path attachment-group-type-paths
            :let [i18n-path (cons :attachmentType path)
                  args (map name (cons lang i18n-path))
                  info-args (concat args ["info"])]]

      (fact {:midje/description (str lang " " (s/join "." (rest args)))}
        (apply i18n/has-term? args) => true)

      (fact {:midje/description (str lang " " (s/join "." (rest info-args)))}
        (apply i18n/has-term? info-args) => true))))


(fact "make attachments with metadata"
  (let [type1 (ssg/generate Type)
        type2 (ssg/generate Type)
        types-with-metadata [{:type type1 :metadata {:foo "bar"}}
                             {:type type2 :metadata {:bar "baz"}}]]
    (make-attachments 999 :draft types-with-metadata false true true)
    => (just [{:id                   "5790633c66e8f95ecc4287be"
               :locked               false
               :modified             999
               :op                   nil
               :state                :requires_user_action
               :target               nil
               :type                 type1
               :applicationState     :draft
               :contents             nil
               :signatures           []
               :versions             []
               :auth                 []
               :notNeeded            false
               :required             true
               :requestedByAuthority true
               :forPrinting          false
               :metadata             {:foo "bar"}
               :readOnly             false}
              {:id                   "5790633c66e8f95ecc4287be"
               :locked               false
               :modified             999
               :op                   nil
               :state                :requires_user_action
               :target               nil
               :type                 type2
               :applicationState     :draft
               :contents             nil
               :signatures           []
               :versions             []
               :auth                 []
               :notNeeded            false
               :required             true
               :requestedByAuthority true
               :forPrinting          false
               :metadata             {:bar "baz"}
               :readOnly             false}])
    (provided
     (mongo/create-id) => "5790633c66e8f95ecc4287be")))

(facts "facts about attachment metada"
  (fact "visibility"
    (let [no-metadata {}
          no-metadata2 {:metadata {}}
          nakyvyys-not-public {:metadata {:nakyvyys "test"}}
          jluokka-nakyvyys-not-public {:metadata {:nakyvyys "test"
                                                  :julkisuusluokka "test2"}}
          nakyvyys-public {:metadata {:nakyvyys "julkinen"
                                      :julkisuusluokka "test2"}}
          jluokka-public {:metadata {:nakyvyys "test"
                                     :julkisuusluokka "julkinen"}}
          both-public {:metadata {:nakyvyys "test"
                                  :julkisuusluokka "julkinen"}}
          only-julkisuusluokka {:metadata {:julkisuusluokka "julkinen"}}]

      (public-attachment? no-metadata) => true
      (public-attachment? no-metadata2) => true
      (public-attachment? nakyvyys-not-public) => false
      (public-attachment? jluokka-nakyvyys-not-public) => false
      (fact "julkisuusluokka overrules nakyvyys" (public-attachment? nakyvyys-public) => false)
      (public-attachment? jluokka-public) => true
      (public-attachment? both-public) => true
      (public-attachment? only-julkisuusluokka) => true)))


(defspec make-version-new-attachment {:num-tests 20 :max-size 100}
  (prop/for-all [attachment      (ssg/generator Attachment {Version nil  [Version] (gen/elements [[]])})
                 file-id         (ssg/generator ssc/ObjectIdStr)
                 archivability   (ssg/generator (sc/maybe {:archivable sc/Bool
                                                           :archivabilityError (apply sc/enum nil archivability-errors)
                                                           :missing-fonts (sc/eq ["Arial"])}))
                 general-options (ssg/generator {:filename sc/Str
                                                 :contentType sc/Str
                                                 :size sc/Int
                                                 :now ssc/Timestamp
                                                 :user user/SummaryUser
                                                 :stamped (sc/maybe sc/Bool)})]
                (let [options (merge {:fileId file-id :original-file-id file-id} archivability general-options)
                      version (make-version attachment options)]
                  (is (not (nil? (get-in version [:version :minor]))))
                  (is (not (nil? (get-in version [:version :major]))))
                  (is (= (:fileId version)         file-id))
                  (is (= (:originalFileId version) file-id))
                  (is (= (:created version) (:now options)))
                  (is (= (:user version) (:user options)))
                  (is (= (:filename version) (:filename options)))
                  (is (= (:contentType version) (:contentType options)))
                  (is (= (:size version) (:size options)))
                  (is (or (not (:stamped options)) (:stamped version)))
                  (is (or (not (:archivable options)) (:archivable version)))
                  (is (= (:archivabilityError version) (:archivabilityError options)))
                  (is (= (:missing-fonts version) (:missing-fonts options))))))

(defspec make-version-update-existing {:num-tests 20 :max-size 100}
  (prop/for-all [[attachment options] (gen/fmap (fn [[att ver fids opt]] [(-> (update att :versions assoc 0 (assoc ver :originalFileId (first fids)))
                                                                              (assoc :latestVersion (assoc ver :originalFileId (first fids))))
                                                                          (assoc opt :fileId (last fids) :original-file-id (first fids))])
                                                (gen/tuple (ssg/generator Attachment {Version nil [Version] (gen/elements [[]])})
                                                           (ssg/generator Version)
                                                           (gen/vector-distinct ssg/object-id {:num-elements 2})
                                                           (ssg/generator {:filename sc/Str
                                                                           :contentType sc/Str
                                                                           :size sc/Int
                                                                           :now ssc/Timestamp
                                                                           :user user/SummaryUser})))]
                (let [version (make-version attachment options)]
                  (and (= (:version version) (get-in attachment [:latestVersion :version]))
                       (= (:fileId version) (:fileId options))
                       (= (:originalFileId version) (:original-file-id options))
                       (= (:created version) (:now options))
                       (= (:user version) (:user options))
                       (= (:filename version) (:filename options))
                       (= (:contentType version) (:contentType options))
                       (= (:size version) (:size options))))))

(defspec build-version-updates-new-attachment {:num-tests 20 :max-size 100}
  (prop/for-all [application    (ssg/generator {:state sc/Keyword})
                 attachment     (ssg/generator Attachment {Version nil [Version] (gen/elements [[]])})
                 version-model  (ssg/generator Version)
                 options        (ssg/generator {:now ssc/Timestamp
                                                :target (sc/maybe Target)
                                                :user user/SummaryUser
                                                :stamped (sc/maybe sc/Bool)
                                                :comment? sc/Bool
                                                :comment-text sc/Str})]
                (let [updates (build-version-updates application attachment version-model options)]
                  (and (= (get-in updates [$addToSet :attachments.$.auth :role]) (if (:stamped options) :stamper :uploader))
                       (= (get-in updates [$set :modified] (:now options)))
                       (= (get-in updates [$set :attachments.$.modified] (:now options)))
                       (= (get-in updates [$set :attachments.$.state] (:state options)))
                       (if (:target options)
                         (= (get-in updates [$set :attachments.$.target] (:target options)))
                         (not (contains? (get updates $set) :attachments.$.target)))
                       (= (get-in updates [$set :attachments.$.latestVersion] version-model))
                       (= (get-in updates [$set "attachments.$.versions.0"] version-model))))))

(defspec build-version-updates-update-existing-version {:num-tests 20 :max-size 100}
  (prop/for-all [application    (ssg/generator {:state sc/Keyword})
                 [attachment version-model] (gen/fmap (fn [[att fids]]
                                                        (let [ver (assoc (get-in att [:versions 1]) :originalFileId (first fids))]
                                                          [(-> (assoc-in att [:versions 1] ver)
                                                               (assoc :latestVersion ver))
                                                           ver]))
                                                      (gen/tuple (ssg/generator Attachment {Version   nil
                                                                                            [Version] (gen/vector-distinct (ssg/generator Version) {:min-elements 3})})
                                                                 (gen/vector-distinct ssg/object-id {:num-elements 2})))
                 options (ssg/generator {:now ssc/Timestamp
                                         :user user/SummaryUser})]
                (let [updates (build-version-updates application attachment version-model options)]
                  (and (not (contains? (get updates $set) :attachments.$.latestVersion))
                       (= (get-in updates [$set "attachments.$.versions.1"] version-model))))))

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
