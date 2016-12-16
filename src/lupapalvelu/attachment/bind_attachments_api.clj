(ns lupapalvelu.attachment.bind-attachments-api
  (:require [lupapalvelu.action :as action :refer [defcommand defquery]]
            [lupapalvelu.application :as app]
            [lupapalvelu.attachment.bind :as bind]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.job :as job]
            [lupapalvelu.states :as states]
            [sade.core :refer :all]
            [sade.util :as util]))

(def bind-states (states/all-application-states-but states/terminal-states))

(defcommand bind-attachments
  {:description         "API to bind files to attachments, returns job that can be polled for status per file."
   :parameters          [id filedatas]
   :optional-parameters [password]
   :input-validators    [(partial action/vector-parameters-with-map-items-with-required-keys [:filedatas] [:fileId])]
   :user-roles          #{:applicant :authority :oirAuthority}
   :user-authz-roles    (conj auth/all-authz-writer-roles :foreman)
   :pre-checks          [(partial app/validate-authority-in-drafts)]
   :states              bind-states}
  [command]
  (ok :job (bind/make-bind-job command filedatas)))

(defquery bind-attachments-job
  {:parameters [jobId version]
   :user-roles          #{:applicant :authority :oirAuthority}
   :input-validators    [(partial action/numeric-parameters [:jobId :version])]
   :states              bind-states}
  [{{job-id :jobId version :version timeout :timeout :or {version "0" timeout "10000"}} :data}]
  (ok (job/status job-id (util/->long version) (util/->long timeout))))
