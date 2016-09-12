(ns lupapalvelu.info-links
  (:require [clojure.set :refer [union]]
            [monger.operators :refer :all]
            [sade.env :as env]
            [sade.util :as util]
            [sade.core :refer [ok fail fail! now]]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.domain :as domain]
            [lupapalvelu.action :refer [defquery defcommand update-application notify] :as action]
            [lupapalvelu.application :as app]
            [lupapalvelu.authorization :as auth]
            [lupapalvelu.notifications :as notifications]
            [lupapalvelu.open-inforequest :as open-inforequest]
            [lupapalvelu.states :as states]
            [lupapalvelu.user :as user])
  (:import
            [org.bson.types ObjectId]))

;; info-link: {linkId num, :text text, :url url, :modified timestamp}

(defn- can-edit-links? [app user]
  "Check if the user is an authority or a statement giver for the application"
  (or (user/authority? user)
      (contains? 
         (set (map :id (filter (fn [auth] (= (:role auth) :statementGiver)) (:auth app)))) 
         (:id user))))
 
(defn- last-seen-date [app user]
  (get (:_info-links-seen-by app) (keyword (:id user)) 0))

(defn- take-first [pred lst def]
  (loop [lst lst]
    (cond
      (empty? lst) def
      (pred (first lst)) (first lst)
      :else (recur (rest lst)))))

(defn- free-link-id [links]
  ;(+ 1 (reduce max 0 (map :linkId links)))
  (str (ObjectId.)))

(defn- order-links [links ids]
  (map (fn [id] (take-first (fn [x] (= (:linkId x) id)) links nil)) ids))

(defn- update-info-links! [app links]
  (mongo/update-by-id :applications (:id app) {$set {:info-links links}}))

;; external api also adds some flags  
(defn- info-links [app]
  (or (:info-links app) []))

(defn mark-links-seen! 
  "mark info-links seen by the current user"
  [command]
  (update-application command
    {$set (app/mark-collection-seen-update (:user command) (now) "info-links")}))
 
(defn info-links-with-flags 
  "get the info links and flags whether they are editable and seen by the given user"
  [app user]
  (let [last-read (last-seen-date app user)]
    (map
      (fn [link] 
        (-> link
          (assoc :isNew (< last-read (:modified link)))
          (assoc :canEdit (can-edit-links? app user))
          (dissoc :modified)))
      (info-links app))))

(defn add-info-link! 
  "add a new info link"
  [app text url timestamp]
  (let [links (info-links app)
        new-id (free-link-id links)
        link-node {:linkId new-id :text text :url url :modified timestamp}]
    (update-info-links! app (concat links (list link-node)))
    new-id))

(defn delete-info-link! 
  "remove an info link"
  [app link-id]
  (update-info-links! app
    (remove (fn [x] (= link-id (:linkId x))) (info-links app))))

(defn update-info-link! 
  "update and existing info link" 
  [app link-id text url timestamp]
  (let [links (info-links app)
      link-node {:linkId link-id :text text :url url :modified timestamp}
      new-links (map (fn [x] (if (= (:linkId x) link-id) link-node x)) links)]
      (update-info-links! app new-links)
      link-id))

(defn reorder-info-links! 
  "set the order of info links"
  [app link-ids]
  (let [links (info-links app)]
    ;; depending on UI needs could just sort the intersection
    (if (= (set link-ids) (set (map :linkId links)))
      (do (update-info-links! app (order-links links link-ids))
         true)
      false)))

