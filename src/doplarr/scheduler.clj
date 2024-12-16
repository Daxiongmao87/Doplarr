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
        (let [kw-request (update request :media-type #(if (string? %) (keyword %) %))
              is-available (a/<! (overseerr/check-availability kw-request))]
          (when is-available
            (let [discord-id (:discord-id kw-request)
                  channel-id (:channel-id kw-request)
                  media-type (:media-type request)
                  plain-content (discord/request-available-plain kw-request media-type discord-id)]
              (info "Processing request with channel ID:" channel-id)
              (when channel-id
                (m/create-message! messaging channel-id plain-content))

              (storage/remove-request request)
              (info "Notified user" discord-id "about availability of" (:item-id request)))))))))
(defn start-scheduler []
  (a/go-loop []
    (check-request-status)
    (a/<! (a/timeout 300000)) 
    (recur)))
