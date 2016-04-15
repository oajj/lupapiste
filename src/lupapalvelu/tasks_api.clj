(ns lupapalvelu.tasks-api
  (:require [taoensso.timbre :as timbre :refer [trace debug info infof warn warnf error fatal]]
            [monger.operators :refer :all]
            [sade.util :as util]
            [sade.strings :as ss]
            [sade.core :refer :all]
            [sade.env :as env]
            [lupapalvelu.action :refer [defquery defcommand defraw non-blank-parameters update-application]]
            [lupapalvelu.i18n :as i18n]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.document.schemas :as schemas]
            [lupapalvelu.document.persistence :as doc-persistence]
            [lupapalvelu.attachment :as attachment]
            [lupapalvelu.tasks :as tasks]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.states :as states]
            [lupapalvelu.child-to-attachment :as child-to-attachment]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.xml.krysp.application-as-krysp-to-backing-system :as mapping-to-krysp]
            [lupapalvelu.document.model :as model]))

;; Helpers

(defn- task-state-assertion [states]
  (fn [{{task-id :taskId} :data} {tasks :tasks}]
    (if-let [task (util/find-by-id task-id tasks)]
      (when-not ((set states) (keyword (:state task)))
        (fail :error.command-illegal-state))
      (fail :error.task-not-found))))

(defn- set-state [{created :created :as command} task-id state & [updates]]
  (update-application command
    {:tasks {$elemMatch {:id task-id}}}
    (util/deep-merge
      {$set {:tasks.$.state state :modified created}}
      updates)))

(defn- task-is-review? [task]
  (let [task-type (-> task :schema-info :name)
        schema (schemas/get-schema tasks/task-schemas-version task-type)]
    (= :review (-> schema :info :subtype keyword))))

(defn- validate-task-is-not-review [{data :data} {tasks :tasks}]
  (when-let [task-id (:taskId data)]
    (when (task-is-review? (util/find-by-id task-id tasks))
      (fail :error.invalid-task-type))))

;; API
(def valid-source-types #{"verdict" "task"})

(def valid-states states/all-application-states-but-draft-or-terminal)

(defn- valid-source [{:keys [id type]}]
  (when (and (string? id) (valid-source-types type))
    {:id id, :type type}))

(defcommand create-task
  {:parameters [id taskName schemaName]
   :optional-parameters [taskSubtype]
   :input-validators [(partial non-blank-parameters [:id :taskName :schemaName])]
   :user-roles #{:authority}
   :states     valid-states}
  [{:keys [created application user data] :as command}]
  (when-not (some #(let [{:keys [name type]} (:info %)] (and (= name schemaName ) (= type :task))) (tasks/task-schemas application))
    (fail! :illegal-schema))
  (let [meta {:created created :assignee user}
        source (or (valid-source (:source data)) {:type :authority :id (:id user)})
        rakennus-data (when (= "task-katselmus" schemaName)
                        (util/strip-empty-maps {:rakennus (tasks/rakennus-data-from-buildings {} (:buildings application))}))
        task (tasks/new-task schemaName
                             taskName
                             (if-not (ss/blank? taskSubtype)
                               (merge rakennus-data {:katselmuksenLaji taskSubtype})
                               rakennus-data)
                             meta
                             source)
        validation-results (tasks/task-doc-validation schemaName task)
        error (when (seq validation-results)
                (-> (first validation-results)
                    :result
                    last))]
    (if-not error
      (do
        (update-application command
                            {$push {:tasks task}
                             $set {:modified created}})
        (ok :taskId (:id task)))
      (fail (str "error." error)))))

(defcommand delete-task
  {:parameters [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states     valid-states
   :pre-checks [(task-state-assertion (tasks/all-states-but :sent))]}
  [{:keys [application created] :as command}]
  (child-to-attachment/delete-child-attachment application :tasks taskId)
  (update-application command
    {$pull {:tasks {:id taskId}}
     $set  {:modified  created}}))

(defn generate-task-pdfa [application {info :schema-info :as task} user lang]
  (when (= "task-katselmus" (:name info))
    (child-to-attachment/create-attachment-from-children user application :tasks (:id task) lang)))

(defcommand approve-task
  {:description "Authority can approve task, moves to ok"
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states      valid-states
   :pre-checks [(task-state-assertion (tasks/all-states-but :sent :ok))
                validate-task-is-not-review]}
  [{:keys [application user lang] :as command}]
  (generate-task-pdfa application (util/find-by-id taskId (:tasks application)) user lang)
  (set-state command taskId :ok))

(defcommand reject-task
  {:description "Authority can reject task, requires user action."
   :parameters  [id taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles #{:authority}
   :states      valid-states
   :pre-checks  [(task-state-assertion (tasks/all-states-but :sent))
                 validate-task-is-not-review]}
  [{:keys [application] :as command}]
  (set-state command taskId :requires_user_action))

(defn- validate-task-is-review [{{task-id :taskId} :data} {tasks :tasks}]
  (when-not (task-is-review? (util/find-by-id task-id tasks))
    (fail :error.invalid-task-type)))

(defn- validate-review-kind [{{task-id :taskId} :data} {tasks :tasks}]
  (when (ss/blank? (get-in (util/find-by-id task-id tasks) [:data :katselmuksenLaji :value]))
    (fail :error.missing-parameters)))

(defn- validate-required-review-fields [{data :data} {tasks :tasks :as application}]
  (when (= (permit/permit-type application) permit/R)
    (when-let [task-id (:taskId data)]
      (let [task (util/find-by-id task-id tasks)]
        (when (reduce (fn [acc k]
                        (or acc (ss/blank? (get-in task [:data :katselmus k :value]))))
                      false [:pitoPvm :pitaja :tila])
          (fail :error.missing-parameters))))))

(comment ;; deleted after review-done feature is in production, will be used later to send updated data to backing systems
  (defcommand send-task
    {:description "Authority can send task info to municipality backend system."
     :parameters  [id taskId lang]
     :input-validators [(partial non-blank-parameters [:id :taskId :lang])]
     :pre-checks  [validate-task-is-review
                   validate-review-kind
                   (permit/validate-permit-type-is permit/R permit/YA)
                   (task-state-assertion [:ok :sent])] ; KRYPS mapping currently implemented only for R & YA
     :user-roles  #{:authority}
     :states      valid-states}
    [{application :application user :user created :created :as command}]
    (let [task (util/find-by-id taskId (:tasks application))
          all-attachments (:attachments (domain/get-application-no-access-checking id [:attachments]))
          sent-file-ids (mapping-to-krysp/save-review-as-krysp application task user lang)
          set-statement (attachment/create-sent-timestamp-update-statements all-attachments sent-file-ids created)]
      (set-state command taskId :sent (when (seq set-statement) {$set set-statement})))))

(defn- schema-with-type-options
  "Genereate 'subtype' options for readonly elements with sequential body"
  [{info :info body :body}]
  {:schemaName (:name info)
   :types      (some (fn [{:keys [name readonly body]}]
                       (when (and readonly (seq body))
                         (for [option body
                               :let [option-id (:name option)]]
                           {:id   option-id
                            :text (i18n/localize i18n/*lang* (:name info) name option-id)})))
                     body)})

(defquery task-types-for-application
  {:description      "Returns a list of allowed schema maps for current application and user. Map has :schemaName, and :types is list of subtypes for that schema"
   :parameters       [:id :lang]
   :user-roles       #{:authority}
   :input-validators [(partial non-blank-parameters [:lang])]
   :states           states/all-states}
  [{application :application}]
  (ok :schemas (->> application
                 (tasks/task-schemas)
                 (sort-by (comp :order :info))
                 (map schema-with-type-options))))

(defn- root-task [application {source :source :as task}]
  (let [{:keys [id type]} source]
    (if (= "task" type)
      (recur application (util/find-by-id id (:tasks application)))
      task)))

(defquery review-can-be-marked-done
  {:description "Review can be marked done by authorities"
   :parameters  [:id :taskId]
   :input-validators [(partial non-blank-parameters [:id :taskId])]
   :user-roles  #{:authority}
   :states      valid-states
   :pre-checks  [validate-task-is-review
                 validate-review-kind
                 (permit/validate-permit-type-is permit/R permit/YA)
                 (task-state-assertion (tasks/all-states-but :sent))]}
  [_])

(defcommand review-done
  {:description "Marks review done, generates PDF/A and sends data to backend"
   :parameters  [id taskId lang]
   :input-validators [(partial non-blank-parameters [:id :taskId :lang])]
   :pre-checks  [validate-task-is-review
                 validate-review-kind
                 (permit/validate-permit-type-is permit/R permit/YA)  ; KRYPS mapping currently implemented only for R & YA
                 validate-required-review-fields
                 (task-state-assertion (tasks/all-states-but :sent))]
   :user-roles  #{:authority}
   :states      valid-states}
  [{application :application user :user created :created :as command}]
  (let [task (util/find-by-id taskId (:tasks application))
        tila (get-in task [:data :katselmus :tila :value])

        task-pdf-version (generate-task-pdfa application task user lang)
        application (domain/get-application-no-access-checking id)
        all-attachments (:attachments application)
        command (assoc command :application application)

        sent-file-ids (mapping-to-krysp/save-review-as-krysp application task user lang)
        set-statement (util/deep-merge
                        (attachment/create-sent-timestamp-update-statements all-attachments sent-file-ids created)
                        (attachment/create-read-only-update-statements
                          all-attachments
                          (if task-pdf-version (conj sent-file-ids (:fileId task-pdf-version)) sent-file-ids)))]

    (set-state command taskId :sent (when (seq set-statement) {$set set-statement}))

    (case tila
      ; Create new, similar task
      "osittainen"
      (let [schema-name (get-in task [:schema-info :name])
            meta {:created created :assignee (:assignee task)}
            source {:type :task :id taskId}
            laji (get-in task [:data :katselmuksenLaji :value])
            rakennus-data (when (= "task-katselmus" schema-name)
                            (util/strip-empty-maps {:rakennus (tasks/rakennus-data-from-buildings {} (:buildings application))}))
            data (merge rakennus-data {:katselmuksenLaji laji})
            new-task (tasks/new-task schema-name (:taskname task) data meta source)]
        (update-application command {$push {:tasks new-task}}))

      ; Mark the state of the original task to final
      "lopullinen"
      (let [root-task (root-task application task)
            task-updates [[[:katselmus :tila] "lopullinen"]]
            schema  (schemas/get-schema (:schema-info task))
            updates (filter (partial doc-persistence/update-key-in-schema? (:body schema)) task-updates)]
        (if (and (not= (:id root-task) taskId)
                 (get-in root-task [:data :vaadittuLupaehtona :value]))
          (doc-persistence/persist-model-updates application "tasks" root-task updates created)
          (ok)))

      (ok))))
