(ns lupapalvelu.statement
  (:use [monger.operators]
        [clojure.tools.logging]
        [lupapalvelu.core]
        [sade.env])
  (:require [sade.security :as sadesecurity]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.security :as security]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.municipality :as municipality]
            [lupapalvelu.notifications :as notifications]))

;;
;; Common
;;

(defn get-statement [{:keys [statements]} id]
  (first (filter #(= id (:id %)) statements)))

(defn statement-exists [{{:keys [statementId]} :data} application]
  (when-not (get-statement application statementId)
    (fail :error.no-statement :statementId statementId)))

(defn statement-owner [{{:keys [statementId]} :data {user-email :email} :user} application]
  (let [{{statement-email :email} :person} (get-statement application statementId)]
    (when-not (= statement-email user-email)
      (fail :error.not-statement-owner))))

(defn statement-not-given [{{:keys [statementId]} :data {user-email :email} :user} application]
  (let [{:keys [given]} (get-statement application statementId)]
    (when given
      (fail :error.statement-already-given))))

;;
;; Authority Admin operations
;;

(defquery "get-statement-persons"
  {:roles [:authority :authorityAdmin]}
  [{{:keys [organizations]} :user}]
  (let [organization (mongo/select-one :organizations {:_id (first organizations)})
        permitPersons (or (:statementPersons organization) [])]
    (println organization)
    (ok :data permitPersons)))

(defcommand "create-statement-person"
  {:parameters [:email :text]
   :roles      [:authorityAdmin]}
  [{{:keys [email text]} :data {:keys [municipality] :as user} :user}]
  (with-user email
    (fn [{:keys [firstName lastName] :as user}]
      (if-not (security/authority? user)
        (fail :error.not-authority)
        (do
          (mongo/update
            :municipalities
            {:_id municipality}
            {$push {:statementPersons {:id (mongo/create-id)
                                       :text text
                                       :email email
                                       :name (str firstName " " lastName)}}})
          (notifications/send-create-statement-person! email text municipality))))))

(defcommand "delete-statement-person"
  {:parameters [:personId]
   :roles      [:authorityAdmin]}
  [{{:keys [personId]} :data {:keys [municipality] :as user} :user}]
  (mongo/update
    :municipalities
    {:_id municipality}
    {$pull {:statementPersons {:id personId}}}))

;;
;; Authority operations
;;

(defcommand "request-for-statement"
  {:parameters  [:id :personIds]
   :roles       [:authority]
   :description "Adds statement-requests to the application and ensures writer-permission to all new users."}
  [{user :user {:keys [id personIds]} :data {:keys [host]} :web :as command}]
  (with-application command
    (fn [{:keys [municipality] :as application}]
      (municipality/with-municipality municipality
        (fn [{:keys [statementPersons]}]
          (let [now            (now)
                personIdSet    (set personIds)
                persons        (filter #(-> % :id personIdSet) statementPersons)
                users          (map #(security/get-or-create-user-by-email (:email %)) persons)
                writers        (map #(role % :writer) users)
                new-writers    (filter #(not (domain/has-auth? application (:id %))) writers)
                new-userids    (set (map :id new-writers))
                unique-writers (distinct new-writers)
                ->statement    (fn [person] {:id        (mongo/create-id)
                                             :person    person
                                             :requested now
                                             :given     nil
                                             :status    nil})
                statements    (map ->statement persons)]
            (mongo/update :applications {:_id id} {$pushAll {:statements statements
                                                             :auth unique-writers}})
            (notifications/send-on-request-for-statement! persons application user host)))))))

(defcommand "delete-statement"
  {:parameters [:id :statementId]
   :roles      [:authority]}
  [{{:keys [id statementId]} :data}]
  (mongo/update :applications {:_id id} {$pull {:statements {:id statementId}}}))

(defcommand "give-statement"
  {:parameters  [:id :statementId :status :text]
   :validators  [statement-exists statement-owner statement-not-given]
   :roles       [:authority]
   :description "authrority-roled statement owners can give statements that are not given already"}
  [{{:keys [id statementId status text]} :data}]
  (mongo/update
    :applications
    {:_id id
     :statements {$elemMatch {:id statementId}}}
    {$set {:statements.$.status status
           :statements.$.given (now)
           :statements.$.text text}}))
