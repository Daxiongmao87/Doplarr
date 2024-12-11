(ns doplarr.sqlite
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.io :as io]))

(def db-spec {:classname "org.sqlite.JDBC"
              :subprotocol "sqlite"
              :subname "requests.db"})

(defn create-schema []
  (jdbc/execute! db-spec
                 ["CREATE TABLE IF NOT EXISTS requests (
                   id INTEGER PRIMARY KEY AUTOINCREMENT,
                   user_id TEXT,
                   channel_id TEXT,
                   media_type TEXT,
                   title TEXT,
                   year INTEGER,
                   status TEXT
                 )"]))

(defn save-request [request]
  (jdbc/insert! db-spec :requests request))

(defn get-requests []
  (jdbc/query db-spec ["SELECT * FROM requests"]))

(defn update-request-status [id status]
  (jdbc/update! db-spec :requests {:status status} ["id=?" id]))
