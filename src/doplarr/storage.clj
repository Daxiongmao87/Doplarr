(ns doplarr.storage
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(def storage-file "requests.edn")

(defn save-request [request]
  (let [requests (if (.exists (io/file storage-file))
                   (edn/read-string (slurp storage-file))
                   [])]
    (spit storage-file (pr-str (conj requests request)))))

(defn get-requests []
  (if (.exists (io/file storage-file))
    (edn/read-string (slurp storage-file))
    []))

(defn remove-request [request]
  (let [requests (get-requests)]
    (spit storage-file (pr-str (remove #(= % request) requests)))))