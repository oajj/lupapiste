(ns lupapalvelu.domain
  (:require [clojure.set :refer [difference]]
            [taoensso.timbre :as timbre :refer [trace debug info warn warnf error fatal]]
            [monger.operators :refer :all]
            [sade.core :refer [unauthorized fail?]]
            [sade.strings :as ss]
            [sade.util :as util]
            [lupapalvelu.attachment.accessibility :as attachment-access]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.user :as user]
            [lupapalvelu.xml.krysp.verdict :as verdict]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]))

;;
;; application mongo querys
;;

(defn basic-application-query-for [user]
  (let [organizations (user/organization-ids-by-roles user #{:authority :reader :approver :commenter})]
    (case (keyword (:role user))
      :applicant    (if-let [company-id (get-in user [:company :id])]
                      {$or [{:auth.id (:id user)} {:auth.id company-id}]}
                      {:auth.id (:id user)})
      :authority    {$or [{:organization {$in organizations}} {:auth.id (:id user)}]}
      :rest-api     {:organization {$in organizations}}
      :oirAuthority {:organization {$in organizations}}
      :trusted-etl {}
      :trusted-salesforce {}
      (do
        (warnf "invalid role to get applications: user-id: %s, role: %s" (:id user) (:role user))
        {:_id nil})))) ; should not yield any results

(defn applications-with-writer-authz-query-for [user]
  {:auth {$elemMatch {:id (:id user) :role {$in [:owner :writer :foreman]}}}})

(defn applications-containing-future-reservations-for [user]
  {:reservations {$elemMatch {$and [{$or [{:from {"$eq" (:id user)}}
                                          {:to {"$eq" (:id user)}}]}
                                    {:endTime {$gte (tc/to-long (t/now))}}]}}})

(defn applications-containing-reservations-requiring-action-query-for [user]
  {:reservations {$elemMatch {:action-required-by {"$eq" (:id user)}}}})

(defn application-query-for [user]
  (merge
    (basic-application-query-for user)
    (case (keyword (:role user))
      :applicant {:state {$nin ["canceled"]}}
      :authority {:state {$nin ["canceled"]}}
      :oirAuthority {:state {$in ["info" "answered"]} :openInfoRequest true}
      {})))

(defn- only-authority-sees [user checker items]
  (filter (fn [m] (or (user/authority? user) (not (checker m)))) items))

(defn- only-authority-sees-drafts [user verdicts]
  (only-authority-sees user :draft verdicts))

(defn- normalize-neighbors [user neighbors]
  (mapv
    (fn [neighbor]
      (-> neighbor
        (update-in [:status]
          #(mapv
             (fn [{vetuma :vetuma :as state}]
               (if (and vetuma (not (user/authority? user)))
                 (assoc-in state [:vetuma :userid] nil)
                 state))
             %))))
    neighbors))

(defn- filter-targeted-attachment-comments
  "If comment target type is attachment, check that attachment exists.
   If not, show only comments with non-blank text related to deleted attachment"
  [application]
  (let [attachments (set (map :id (:attachments application)))]
    (update-in application [:comments]
      #(filter (fn [{:keys [target text]}] (or
                                             (empty? target)
                                             (not= (:type target) "attachment")
                                             (or
                                               (attachments (:id target))
                                               (not (ss/blank? text))))) %))))

(defn- filter-notice-from-application [application user]
  (if (user/authority? user)
    application
    (dissoc application :urgency :authorityNotice)))

(defn- relates-to-draft-verdict? [{verdicts :verdicts} {target :target source :source}]
  (or (and (= (:type target) "verdict") (:draft (util/find-by-id (:id target) verdicts)))
      (and (= (:type source) "verdict") (:draft (util/find-by-id (:id source) verdicts)))))

(defn- authorized-to-statement? [user statement]
  (or (:given statement)
      (user/authority? user)
      (and (user/applicant? user)
           (= (-> statement :person :email ss/canonize-email)
              (-> user :email ss/canonize-email)))))

(defn- statement-attachment-hidden-for-user? [{statements :statements} user {target :target :as attachment}]
  (and (= (:type target) "statement")
       (not (->> (util/find-by-id (:id target) statements) (authorized-to-statement? user)))))

(defn- statement-summary [statement]
  (select-keys statement [:id :person :requested :given :state]))

(defn- can-read-comments? [app user]
  (or (auth/user-authz? roles/comment-user-authz-roles app user)
      (auth/has-organization-authz-roles? roles/commenter-org-authz-roles (:organization app) user)
      (auth/has-auth? app (get-in user [:company :id]))))

(defn filter-application-content-for [application user]
  (when (seq application)
    (-> application
        (update-in [:comments] (partial filter (fn [comment] (and
                                                               (can-read-comments? application user)
                                                               ((set (:roles comment)) (name (:role user)))))))
        (update-in [:verdicts] (partial only-authority-sees-drafts user))
        (update-in [:statements] (partial map #(if (authorized-to-statement? user %) % (statement-summary %))))
        (update-in [:attachments] (partial remove (partial statement-attachment-hidden-for-user? application user)))
        (update-in [:attachments] (partial only-authority-sees user (partial relates-to-draft-verdict? application)))
        (update-in [:attachments] (partial attachment-access/filter-attachments-for user application))
        (update-in [:neighbors] (partial normalize-neighbors user))
        filter-targeted-attachment-comments
        (update-in [:tasks] (partial only-authority-sees user (partial relates-to-draft-verdict? application)))
        (filter-notice-from-application user))))

(defn with-participants-info
  [reservation]
  (let [ids (flatten (vals (select-keys reservation [:from :to])))]
    (assoc reservation :participants (map user/get-user-by-id (remove nil? ids)))))

(defn enrich-application-handlers
  ([{org-id :organization :as application}]
   (enrich-application-handlers application {:handler-roles (lazy-seq (:handler-roles (mongo/select-one :organizations {:_id org-id} [:handler-roles])))}))
  ([application {roles :handler-roles :as organization}]
   (update application :handlers (partial map #(merge (util/find-by-id (:roleId %) roles) %)))))

(defn- enrich-application [application]
  (some-> application
          (update :documents (partial map schemas/with-current-schema-info))
          (update :tasks (partial map schemas/with-current-schema-info))
          (update :reservations (partial map with-participants-info))
          (enrich-application-handlers)))

(defn get-application-as [query-or-id user & {:keys [include-canceled-apps?] :or {include-canceled-apps? false}}]
  {:pre [query-or-id (map? user)]}
  (let [query-id-part (if (map? query-or-id) query-or-id {:_id query-or-id})
        query-user-part (if include-canceled-apps?
                          (update-in (application-query-for user) [:state $nin] #(difference (set %) #{"canceled"}))
                          (application-query-for user))]

    (some-> (mongo/select-one :applications {$and [query-id-part query-user-part]})
            (enrich-application)
            (filter-application-content-for user))))

(defn get-application-no-access-checking
  ([query-or-id]
   {:pre [query-or-id]}
   (get-application-no-access-checking query-or-id {}))
  ([query-or-id projection]
   (let [query (if (map? query-or-id) query-or-id {:_id query-or-id})]
     (->> (mongo/select-one :applications query projection)
          enrich-application))))

(defn get-multiple-applications-no-access-checking
  ([query-or-ids]
   {:pre [(coll? query-or-ids)]}
   (get-multiple-applications-no-access-checking query-or-ids {}))
  ([query-or-ids projection]
   (let [query (if (map? query-or-ids) query-or-ids {:_id {$in query-or-ids}})]
     (->> (if (seq projection)
            (mongo/select :applications query projection)
            (mongo/select :applications query))
          (map enrich-application)))))

;;
;; authorization
;;

(def owner-or-write-roles ["owner" "writer" "foreman"])

(defn owner-or-write-access? [application user-id]
  (auth/has-some-auth-role? application user-id owner-or-write-roles))

(defn validate-access
  "Command pre-check for validating user roles in application auth array."
  [allowed-roles {:keys [application user] :as command}]
  (when-not (or (auth/has-some-auth-role? application (:id user) allowed-roles)
                (auth/has-some-auth-role? application (get-in user [:company :id]) allowed-roles))
    unauthorized))

(defn validate-owner-or-write-access
  "Validator: current user must be owner or have write access.
   To be used in commands' :pre-checks vector."
  [command]
  (validate-access owner-or-write-roles command))

;;
;; documents
;;

(defn- docs-from [application-or-documents]
  {:post [(sequential? %)]}
  (if (map? application-or-documents) (:documents application-or-documents) application-or-documents))

(defn get-document-by-id
  "returns first document from application with the document-id"
  [application-or-documents document-id]
  (let [documents (docs-from application-or-documents)]
    (util/find-by-id document-id documents)))

(defn- documents-by-schema-info [application-or-documents k v]
  (let [documents (docs-from application-or-documents)]
    (filter (comp (partial = (keyword v)) keyword k :schema-info) documents)))

(defn get-documents-by-name
  "returns document from application by schema name"
  [application-or-documents schema-name]
  (documents-by-schema-info application-or-documents :name schema-name))

(defn get-documents-by-type
  "returns document from application by schema type"
  [application-or-documents schema-type]
  (documents-by-schema-info application-or-documents :type schema-type))

(defn get-document-by-name
  "returns first document from application by schema name"
  [application-or-documents schema-name]
  (first (documents-by-schema-info application-or-documents :name schema-name)))

(defn get-document-by-type
  "returns first document from application by schema type"
  [application-or-documents schema-type]
  (first (documents-by-schema-info application-or-documents :type schema-type)))

(defn get-document-by-operation
  "returns first document from application that is associated with the operation"
  [application-or-documents operation]
  (let [op-id (if (map? operation) (:id operation) operation)
        documents (docs-from application-or-documents)]
    (first (filter #(= op-id (get-in % [:schema-info :op :id])) documents))))

(defn get-subtype [{schema-info :schema-info :as doc}]
  (when (:subtype schema-info)
    (name (:subtype schema-info))))

(defn get-documents-by-subtype
  "Returns documents of given subtype"
  [documents subtype]
  {:pre [(sequential? documents)]}
  (filter (comp (partial = (name subtype)) get-subtype) documents))

(defn get-applicant-documents
  "returns applicant documents from given application documents"
  [documents]
  {:pre [(sequential? documents)]}
  (get-documents-by-subtype documents "hakija"))

(defn get-applicant-document
  "returns first applicant document from given application documents"
  [documents]
  (first (get-applicant-documents documents)))

(defn invites [{auth :auth}]
  (map :invite (filter :invite auth)))

(defn invite [application email]
  (first (filter #(= (ss/lower-case email) (:email %)) (invites application))))

(defn no-pending-invites? [application user-id]
  (not-any? #(= user-id (-> % :user :id)) (invites application)))

;;
;; Verdict model
;;

(defn ->paatos
  "Returns a verdict data structure, compatible with KRYSP schema"
  [{:keys [verdictId backendId timestamp name given status official text section draft agreement metadata paatos-id]}]
  (let [verdict-id (or verdictId (mongo/create-id))]
    {:id verdict-id
    :kuntalupatunnus backendId
    :draft (if (nil? draft) false draft)
    :timestamp timestamp
    :sopimus agreement ; not in KRYSP
    :metadata metadata ; not in KRYSP?
    :paatokset [{:id          (or paatos-id (mongo/create-id))
                 :paivamaarat {:anto             given
                               :lainvoimainen    official}
                 :poytakirjat [{:paatoksentekija name
                                :urlHash         verdict-id
                                :status          status
                                :paatos          text ; Only in rakennusvalvonta KRYSP
                                :paatospvm       given
                                :pykala          section
                                :paatoskoodi     (when status (verdict/verdict-name status))}]}]}))

;;
;; Comment model
;;

(defn ->comment [text target type user to-user timestamp roles]
  {:pre [(or (nil? text) (string? text)) (map? target)
         type (map? user) (or (nil? to-user) (:role to-user))
         (number? timestamp) (or (sequential? roles) (set? roles))]}

  {:text    text
   ; target key order seems to be significant in MongoDB updates
   :target  (if (:id target) {:type (:type target), :id (:id target)} {:type (:type target)})
   :type    type
   :created timestamp
   :roles   (if to-user (conj (set roles) (:role to-user)) roles)
   :to      (user/summary to-user)
   :user    (user/summary user)})

;;
;; Neighbors
;;
(def neighbor-skeleton
  {:id ""
   :propertyId ""
   :owner {:type nil
           :name nil
           :businessID nil
           :nameOfDeceased nil
           :address {:street nil :zip nil :city nil}}
   :status []})

;;
;; Application skeleton with default values
;;

(def application-skeleton
  {:_applicantIndex          []
   :_attachment_indicator_reset nil ; timestamp
   :_comments-seen-by        {}
   :_statements-seen-by      {}
   :_verdicts-seen-by        {}
   :acknowledged             nil ; timestamp
   :address                  ""
   :applicant                ""
   :attachments              []
   :auth                     []
   :authority                {:firstName "", :lastName "", :id nil} ; TODO: Remove from application, use handlers instead
   :authorityNotice          ""
   :buildings                []
   :closed                   nil ; timestamp
   :closedBy                 {}
   :convertedToApplication   nil ; timestamp
   :comments                 []
   :complementNeeded         nil ; timestamp
   :created                  nil ; timestamp
   :documents                []
   :drawings                 []
   :foreman                  ""
   :foremanRole              ""
   :handlers                 []
   :history                  [] ; state transition audit log
   :infoRequest              false
   :location                 {}
   :location-wgs84           {}
   :modified                 nil ; timestamp
   :municipality             ""
   :neighbors                []
   :opened                   nil ; timestamp
   :openInfoRequest          false
   :primaryOperation         nil
   :secondaryOperations      []
   :options                  {}
   :organization             ""
   :propertyId               ""
   :permitSubtype            ""
   :permitType               ""
   :reminder-sent            nil ; timestamp
   :schema-version           nil ; Long
   :sent                     nil ; timestamp
   :started                  nil ; construction started
   :agreementPrepared        nil                            ; agreement prepared YA
   :agreementSigned          nil            ; Agreemetn signed YA, terminal
   :finished                 nil                   ; Finished  YA
   :startedBy                {}
   :state                    ""
   :statements               []
   :submitted                nil ; timestamp
   :suti                     {:id nil :added false}
   :tasks                    []
   :title                    ""
   :transfers                []
   :urgency                  "normal"
   :verdicts                 []
   :tosFunction              nil
   :metadata                 {}
   :processMetadata          {}
   :appeals                  []
   :appealVerdicts           []
   :archived                 {:initial     nil
                              :application nil
                              :completed   nil}
   :reservations             []
   :warrantyStart            nil ; timestamp
   :warrantyEnd              nil
   :inspection-summaries     []})

(def operation-skeleton
  {:name ""
   :description nil
   :created nil})
