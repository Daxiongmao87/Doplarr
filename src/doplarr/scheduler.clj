(ns doplarr.scheduler
  (:require [clojure.core.async :as a]
            [doplarr.backends.overseerr.impl :as overseerr]
            [doplarr.storage :as storage]
            [doplarr.discord :as discord]
            [doplarr.utils :as utils]
            [taoensso.timbre :refer [info]]))

(defn check-request-status []
  (a/go
    (let [requests (storage/get-requests)]
      (doseq [request requests]
        (let [is-available (a/<! (overseerr/check-availability (:item-id request)))]
          (when is-available
            (discord/notify-user (:user-id request) (:item-details request))
            (storage/remove-request request)
            (info "Notified user" (:user-id request) "about availability of" (:item-id request))))))))

(defn start-scheduler []
  (a/go-loop []
    (check-request-status)
    (a/<! (a/timeout 3600000)) ; Check every hour
    (recur)))