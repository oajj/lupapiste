(ns lupapalvelu.file-upload-api
  (:require [noir.response :as resp]
            [clojure.set :refer [rename-keys]]
            [sade.core :refer :all]
            [sade.env :as env]
            [lupapalvelu.action :refer [defcommand defraw]]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.file-upload :as file-upload]
            [lupapalvelu.mime :as mime]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.roles :as roles]
            [lupapalvelu.states :as states]
            [lupapalvelu.vetuma :as vetuma]))

(defn- file-size-legal [{{files :files} :data {role :role} :user}]
  (let [max-size (env/value :file-upload :max-size (if (contains? roles/all-authenticated-user-roles (keyword role)) :logged-in :anonymous))]
    (when-not (every? #(<= % max-size) (map :size files))
      (fail :error.file-upload.illegal-upload-size :errorParams (/ max-size 1000 1000)))))

(defn- file-mime-type-accepted [{{files :files} :data}]
  (when-not (every? mime/allowed-file? (map :filename files))
    (fail :error.file-upload.illegal-file-type)))

(defraw upload-file
  {:user-roles #{:anonymous}
   :parameters [files]
   :input-validators [file-mime-type-accepted file-size-legal]
   :pre-checks [vetuma/session-pre-check]}
  (let [file-info (pmap
                    #(file-upload/save-file % :sessionId (vetuma/session-id) :linked false)
                    (map #(rename-keys % {:tempfile :content}) files))]
    (->> {:files file-info :ok true}
         (resp/json)
         (resp/status 200))))

(defraw upload-file-authenticated
  {:user-roles       #{:authority :applicant}
   :user-authz-roles (conj roles/default-authz-writer-roles :foreman :statementGiver)
   :parameters       [files]
   :optional-parameters [id]
   :input-validators [file-mime-type-accepted
                      file-size-legal]
   :states           states/all-states}
  [{:keys [application]}]
  (let [{:keys [ok] :as result} (file-upload/save-files application files (vetuma/session-id))]
    (->> result
         (resp/json)
         (resp/status (if ok 200 400)))))

(defn- file-upload-in-database
  [{{attachment-id :attachmentId} :data}]
  (let [file-found? (mongo/any? :fs.files {:_id attachment-id "metadata.sessionId" (vetuma/session-id)})]
    (when-not file-found?
      (fail :error.file-upload.not-found))))

(defn- attachment-not-linked
  [{{attachment-id :attachmentId} :data}]
  (when-let [{{bulletin-id :bulletinId} :metadata} (mongo/select-one :fs.files {:_id attachment-id "metadata.sessionId" (vetuma/session-id)})]
    (when-not (empty? bulletin-id)
      (fail :error.file-upload.already-linked))))

(defcommand remove-uploaded-file
  {:parameters [attachmentId]
   :input-validators [file-upload-in-database attachment-not-linked]
   :user-roles #{:anonymous}}
  (if-let [{file-id :id} (mongo/select-one :fs.files {:_id attachmentId "metadata.sessionId" (vetuma/session-id)})]
    (do
      (mongo/delete-file-by-id file-id)
      (ok :attachmentId attachmentId))
    (fail :error.file-upload.removing-file-failed)))
