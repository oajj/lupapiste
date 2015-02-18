(ns lupapalvelu.xml.disk-writer
  (:require [taoensso.timbre :as timbre :refer [info error]]
            [me.raynes.fs :as fs]
            [clojure.data.xml :refer [emit indent-str]]
            [clojure.java.io :as io]
            [sade.core :refer :all]
            [sade.strings :as ss]
            [lupapalvelu.mongo :as mongo]
            [lupapalvelu.permit :as permit]
            [lupapalvelu.pdf-export :as pdf-export]
            [lupapalvelu.xml.validator :as validator]))

(defn get-file-name-on-server [file-id file-name]
  (str file-id "_" (ss/encode-filename file-name)))

(defn get-submitted-filename [application-id]
  (str  application-id "_submitted_application.pdf"))

(defn get-current-filename [application-id]
  (str application-id "_current_application.pdf"))

(defn- write-application-pdf-versions [output-dir application submitted-application lang]
  (let [id (:id application)
        submitted-file (io/file (str output-dir "/" (get-submitted-filename id)))
        current-file (io/file (str output-dir "/" (get-current-filename id)))]
    (pdf-export/generate submitted-application lang submitted-file)
    (pdf-export/generate application lang current-file)))

(defn write-attachments [attachments output-dir]
  (doseq [attachment attachments]
    (let [file-id (get-in attachment [:Liite :fileId])
          filename (get-in attachment [:Liite :filename])
          attachment-file (mongo/download file-id)
          content (:content attachment-file)
          attachment-file-name (str output-dir "/" filename)
          attachment-file (io/file attachment-file-name)]
      (with-open [out (io/output-stream attachment-file)
                  in (content)]
        (io/copy in out)))))

(defn- flatten-statement-attachments [statement-attachments]
  (let [attachments (for [statement statement-attachments] (vals statement))]
    (reduce concat (reduce concat attachments))))

(defn write-statement-attachments [statement-attachments output-dir]
  (let [attachments (flatten-statement-attachments statement-attachments)]
    (write-attachments attachments output-dir)))

(defn- write-application-pdf-versions [output-dir application submitted-application lang]
  (let [id (:id application)
        submitted-file (io/file (str output-dir "/" (get-submitted-filename id)))
        current-file (io/file (str output-dir "/" (get-current-filename id)))]
    (pdf-export/generate submitted-application lang submitted-file)
    (pdf-export/generate application lang current-file)))

(defn write-to-disk
  "Writes XML string to disk and copies attachments from database. XML is validated before writing.
   Returns a sequence of attachment fileIds that were written to disk."
  [application attachments statement-attachments xml krysp-version output-dir & [submitted-application lang]]
  {:pre [(string? output-dir)]
   :post [%]}

  (let [file-name  (str output-dir "/" (:id application) "_" (now))
        tempfile   (io/file (str file-name ".tmp"))
        outfile    (io/file (str file-name ".xml"))
        xml-s      (indent-str xml)]

    (try
      (validator/validate xml-s (permit/permit-type application) krysp-version)
      (catch org.xml.sax.SAXParseException e
       (info e "Invalid KRYSP XML message")
       (fail! :error.integration.send :details (.getMessage e))))

    (fs/mkdirs output-dir)
    (try
      (with-open [out-file-stream (io/writer tempfile)]
        (emit xml out-file-stream))
      ;; this has to be called before calling "with-open" below
      (catch java.io.FileNotFoundException e
        (error e (.getMessage e))
        (fail! :error.sftp.user.does.not.exist :details (.getMessage e))))


    (write-attachments attachments output-dir)
    (write-statement-attachments statement-attachments output-dir)

    (when (and submitted-application lang)
      (write-application-pdf-versions output-dir application submitted-application lang))

    (when (fs/exists? outfile) (fs/delete outfile))
    (fs/rename tempfile outfile))

  (->>
    (concat attachments (flatten-statement-attachments statement-attachments))
    (map #(get-in % [:Liite :fileId]))
    (filter identity)))
