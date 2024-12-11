(ns doplarr.scheduler
  (:require [clojure.core.async :as a]
            [doplarr.backends.overseerr :as overseerr]
            [doplarr.storage :as storage]
            [doplarr.discord :as discord]
            [doplarr.state :as state]
            [discljord.messaging :as m]
            [taoensso.timbre :refer [info]]))

(defn check-request-status []
  (a/go
    (info "Checking request statuses...")
    (let [{:keys [messaging]} @state/discord
          requests (storage/get-requests)]
      (doseq [request requests]
        (let [is-available (a/<! (overseerr/check-availability request))]
          (when is-available
            (let [discord-id (:discord-id request)
                  channel-id (:channel-id request)
                  media-type (:media-type request)
                  plain-content (discord/request-available-plain request media-type discord-id)]
              (info "Processing request with channel ID:" channel-id)

              (when channel-id
                (m/create-message! messaging channel-id plain-content)
                (storage/remove-request request)
                (info "Notified user" discord-id "about availability of" (:item-id request))))))))))


(defn start-scheduler []
  (a/go-loop []
    (check-request-status)
    (a/<! (a/timeout 10000)) 
    (recur)))
