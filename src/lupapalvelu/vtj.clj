(ns lupapalvelu.vtj
  (:use [ring.util.codec :only [url-decode]])
  (:require [clojure.string :as string]
            [net.cgrand.enlive-html :as enlive]))

(defn- strip-ns [s] (string/replace s #"ns\d*:" ""))
(defn- strip-plus [s] (string/replace s #"\+" " "))
(defn- content [t] (-> t first :content first))
(defn- select [d & path] (-> d (enlive/select path) content))
(defn- parse [s] (-> s (url-decode "ISO-8859-1") strip-ns strip-plus enlive/xml-resource))

(defn extract [s m]
  (let [parsed (parse s)]
    (into {} (for [[k v] m] [k (apply select parsed v)]))))
