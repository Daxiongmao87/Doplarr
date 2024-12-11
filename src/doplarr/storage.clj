(ns doplarr.storage
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [taoensso.timbre :refer [info]]))

(def storage-dir "db") ;; Directory for the database
(def storage-file (str storage-dir "/requests.edn")) ;; File path

(defn ensure-db-dir []
  ;; Ensure the storage directory exists
  (let [dir (io/file storage-dir)]
    (when-not (.exists dir)
      (.mkdirs dir)
      (info "Created database directory:" storage-dir))))

(defn ensure-db []
  ;; Ensure the database file exists
  (ensure-db-dir)
  (let [db-file (io/file storage-file)]
    (when-not (.exists db-file)
      (spit db-file "[]") ;; Initialize with an empty list
      (info "Initialized database file:" storage-file))))


(defn save-request [request]
  (ensure-db-dir) ;; Ensure the directory exists before saving
  (let [requests (if (.exists (io/file storage-file))
                   (edn/read-string (slurp storage-file))
                   [])]
    (spit storage-file (pr-str (conj requests request)))))

(defn get-requests []
  (ensure-db-dir) ;; Ensure the directory exists before reading
  (if (.exists (io/file storage-file))
    (edn/read-string (slurp storage-file))
    []))

(defn remove-request [request]
  (ensure-db-dir) ;; Ensure the directory exists before modifying
  (let [requests (get-requests)]
    (spit storage-file (pr-str (remove #(= % request) requests)))))

