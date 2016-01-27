(ns lupapalvelu.smoketest.application-smoke-tests
  (:require [lupapalvelu.smoketest.core :refer [defmonster]]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.states :as states]
            [lupapalvelu.document.model :as model]
            [lupapalvelu.application :as app]
            [lupapalvelu.server] ; ensure all namespaces are loaded
            [lupapalvelu.attachment :as att]
            [sade.schemas :as ssc]
            [schema.core :as sc]))

(def application-keys [:infoRequest
                       :operations :secondaryOperations :primaryOperation
                       :documents :schema-version :attachments :auth
                       :state :modified  :created :opened :submitted :sent :started :closed
                       :organization :municipality :propertyId :location])

(def applications (delay (mongo/select :applications {} application-keys)))
(def submitted-applications (delay (mongo/select :submitted-applications {} application-keys)))

(defn- resolve-operations [application]
  ; Support the old and the new application schema
  (or (:operations application) (app/get-operations application)))

(defn- validate-doc [ignored-errors application {id :id schema-info :schema-info :as doc}]
  (if (and (:name schema-info) (:version schema-info))
    (let [ignored (set ignored-errors)
          results (filter
                    (fn [{result :result}]
                      (and (= :err (first result)) (not (ignored (second result)))))
                    (model/validate application doc))]
      (when (seq results)
        {:document-id id :schema-info schema-info :results results}))
    {:document-id id :schema-info schema-info :results "Schema name or version missing"}))

(defn- validate-documents [ignored-errors {id :id state :state documents :documents :as application}]
  (let [results (filter seq (map (partial validate-doc ignored-errors application) documents))]
    (when (seq results)
      {:id id
       :state state
       :results results})))

(defn- documents-are-valid [applications & ignored-errors]
  (if-let [validation-results (seq (filter seq (pmap (partial validate-documents ignored-errors) applications)))]
    {:ok false :results validation-results}
    {:ok true}))

;; Every document is valid.

(defmonster applications-documents-are-valid
  (documents-are-valid @applications))

(defmonster submitted-applications-documents-are-valid
  (documents-are-valid @submitted-applications "illegal-hetu"))

;; Latest attachment version and latestVersion match
(defn validate-latest-version [{id :id versions :versions latestVersion :latestVersion}]
  (when-not (or (empty? versions) (= latestVersion (last versions)))
    {:attachment-id id :error "latest version does not match last element of versions array"}))

(def coerce-attachment (ssc/json-coercer att/Attachment))

(defn validate-attachment-against-schema [{id :id :as attachment}]
  (when-let [err (->> attachment coerce-attachment (sc/check att/Attachment))]
    {:attachment-id id :error "Not valid attachment" :schema-error err}))

(defn validate-attachments [attachments]
  (->> attachments
       (mapcat (juxt validate-attachment-against-schema validate-latest-version))
       (remove nil?)
       seq))

(defmonster attachment-validation
  (if-let [results (time (->> @applications
                              (pmap (fn [{attachments :attachments id :id}]
                                      (some->> (validate-attachments attachments)
                                               (assoc {:application-id id} :result))))
                              (remove nil?)
                              seq))]
    {:ok false :results results}
    {:ok true}))

;; Documents have operation information

(defn- application-schemas-have-ops [{documents :documents :as application}]
  (when-not (:infoRequest application)
    (let [operations (resolve-operations application)
          docs-with-op (count (filter #(get-in % [:schema-info :op]) documents))
          ops          (count operations)]
      (when-not (= docs-with-op ops)
        (:id application)))))

(defn- schemas-have-ops [apps]
  (let [app-ids-with-invalid-docs (remove nil? (map application-schemas-have-ops apps))]
    (when (seq app-ids-with-invalid-docs)
      {:ok false :results (into [] app-ids-with-invalid-docs)})))

(defmonster applications-schemas-have-ops
  (schemas-have-ops @applications))

(defmonster submitted-applications-schemas-have-ops
  (schemas-have-ops @submitted-applications))

;; not null
(defn nil-property [property]
  (if-let [results (seq (remove nil? (map #(when (nil? (property %)) (:id %)) @applications)))]
    {:ok false :results results}
    {:ok true}))

(defmonster organization-is-set
  (nil-property :organization))

(defmonster property-id-is-set
  (nil-property :propertyId))

(defmonster location-is-set
  (nil-property :location))

(defmonster municipality-is-set
  (nil-property :municipality))

(defmonster schema-version-is-set
  (nil-property :schema-version))

(defn timestamp-is-set [ts-key states]
  (if-let [results (seq (remove nil? (map #(when (and (states (keyword (:state %))) (nil? (ts-key %))) (:id %)) @applications)))]
    {:ok false :results results}
    {:ok true}))

(defmonster opened-timestamp
  (timestamp-is-set :opened (states/all-states-but [:draft :canceled])))

;;
;; Skips applications with operation "aiemmalla-luvalla-hakeminen" (previous permit aka paperilupa)
;;
(defmonster submitted-timestamp
 (if-let [results (seq (remove nil? (map
                                      (fn [app]
                                        (when (and
                                                ((states/all-application-states-but [:canceled :draft :open]) (keyword (:state app)))
                                                (when-not (some #(= "aiemmalla-luvalla-hakeminen" %) (map :name (resolve-operations app)))
                                                  (nil? (:submitted app))))
                                          (:id app)))
                                      @applications)))]
   {:ok false :results results}
   {:ok true}))

; Fails with 255 applications
;(defmonster canceled-timestamp
;  (timestamp-is-set :canceled #{:canceled}))

(defmonster sent-timestamp
  (timestamp-is-set :sent #{:sent :complementNeeded}))

(defmonster closed-timestamp
  (timestamp-is-set :closed #{:closed}))


; Not a valid test anymore. Fails if a verdict is replaced.
(comment

  ;; task source is set
  (defn every-task-refers-verdict [{:keys [verdicts tasks id]}]
    (let [verdict-ids (set (map :id verdicts))]
       (when-not (every? (fn [{:keys [source]}] (or (not= "verdict" (:type source)) (verdict-ids (:id source)))) tasks)
         id)))

  (defmonster task-source-refers-verdict
       (if-let [results (seq (remove nil? (map every-task-refers-verdict @applications)))]
         {:ok false :results results}
         {:ok true})))
