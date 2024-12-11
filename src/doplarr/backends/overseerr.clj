(ns doplarr.backends.overseerr
  (:require
   [clojure.core.async :as a]
   [doplarr.backends.overseerr.impl :as impl]
   [doplarr.state :as state]
   [doplarr.utils :as utils]
   [clojure.walk :as walk]
   [taoensso.timbre :refer [info]]))

(defn search [term media-type]
  (let [type (impl/media-type media-type)]
    (utils/request-and-process-body
     impl/GET
     (partial impl/process-search-result type)
     (str "/search?query=" (utils/url-encode-illegal-characters term)))))

; In Overseerr, the only additional option we'll need is which season,
; if the request type is a series
(defn additional-options [result media-type]
  (a/go
    (let [details (a/<! (impl/details (:id result) media-type))
          {:keys [partial-seasons]} @state/config]
      (when (= media-type :series)
        (let [seasons (impl/seasons-list details)
              backend-partial-seasons? (a/<! (impl/partial-seasons?))]
          {:season (cond
                     (= 1 (count seasons)) (:id (first seasons))
                     (false? partial-seasons) -1
                     (false? backend-partial-seasons?) -1
                     :else (impl/seasons-list details))
           :season-count (dec (count seasons))})))))

(defn request-embed [{:keys [title id season]} media-type]
  (a/go
    (let [fourk (a/<! (impl/backend-4k? media-type))
          details (a/<! (impl/details id media-type))]
      {:title title
       :overview (:overview details)
       :poster (str impl/poster-path (:poster-path details))
       :media-type media-type
       :request-formats (cond-> [""] fourk (conj "4K"))
       :season season})))

(defn request [payload media-type]
  (a/go
    (let [{:keys [format id season season-count discord-id channel-id]} payload ;; Extract channel-id
          {:overseerr/keys [default-id]} @state/config
          ;; Retrieve details for the given media ID
          details (a/<! (impl/details id media-type))
          ;; Retrieve Discord user ID mapping
          ovsr-id ((a/<! (impl/discord-users)) discord-id)
          ;; Determine media status
          status (impl/media-status details media-type
                                    :is-4k? (= format :4K)
                                    :season season)
          ;; Build request body
          body (cond-> {:mediaType (impl/media-type media-type)
                        :mediaId id
                        :is4k (= format :4K)}
                 (= :series media-type)
                 (assoc :seasons
                        (if (= -1 season)
                          (into [] (range 1 (inc season-count)))
                          [season])))]
      ;; Handle request submission or return the appropriate status
      (cond
        (contains? #{:unauthorized :pending :processing :available} status) status
        (and (nil? ovsr-id) (nil? default-id)) :unauthorized
        :else (a/<! (impl/POST "/request" {:form-params body
                                           :content-type :json
                                           :headers {"X-API-User" (str (or ovsr-id default-id))}}))))))

(defn check-availability [request]
  (a/go
    (try
      ;; Use the details function to get media details
      (let [{:keys [item-id media-type]} request
            details (a/<! (impl/details item-id media-type))] ;; Fetch details

        ;; Check if the details were successfully retrieved
        (if (nil? details)
          (do
            (println "Failed to retrieve media details.")
            false)

          ;; Retrieve mediaInfo and status from details
          (let [media-info (:media-info details)
                status (:status media-info)]

            ;; Return true if status is 5, otherwise false
            (if (= status 5)
              (do
                true)
              (do
                false)))))

      (catch Exception e
        (utils/log-on-error "Error checking availability for item" {:item-id (:item-id request) :error e})
        false))))

