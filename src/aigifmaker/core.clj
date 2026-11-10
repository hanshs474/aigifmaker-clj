(ns aigifmaker.core
  "Official Clojure SDK for AI GIF Maker (https://aigifmaker.ai): turn text
  into animated GIFs.

    (require '[aigifmaker.core :as gif])
    (gif/generate \"a corgi in sunglasses dancing on a beach, looping\")
    ;; => URL of the finished GIF
    (gif/credits)

  Pass :api-key or set AIGIFMAKER_API_KEY; create a key at
  https://aigifmaker.ai/settings/apikeys. Each GIF costs 1 credit on your
  AI GIF Maker account, the same as on the website.

  Options: :api-key, :base-url, :client (a configured
  io.github.hanshs474.aigifmaker.Aigifmaker), :timeout-ms (default 15
  minutes), :poll-ms (default 3000).

  Failures throw ex-info whose data carries :reason, one of :auth, :quota,
  :rejected, :failed, :timeout or :other."
  (:require [clojure.string :as str])
  (:import (io.github.hanshs474.aigifmaker Aigifmaker Aigifmaker$Status AigifmakerException)
           (java.time Duration)))

(defn- client ^Aigifmaker [{:keys [api-key base-url client timeout-ms poll-ms]}]
  (let [key (or api-key (System/getenv "AIGIFMAKER_API_KEY") "")
        ^Aigifmaker c (or client (if base-url (Aigifmaker. ^String key ^String base-url) (Aigifmaker. ^String key)))]
    (when timeout-ms (.timeout c (Duration/ofMillis timeout-ms)))
    (when poll-ms (.pollEvery c (Duration/ofMillis poll-ms)))
    c))

(defn- rethrow [^AigifmakerException e]
  (throw (ex-info (.getMessage e)
                  {:reason (keyword (str/lower-case (str (.kind e))))
                   :code (.code e)}
                  e)))

(defmacro ^:private guard [& body]
  `(try ~@body (catch AigifmakerException e# (rethrow e#))))

(defn generate
  "Turns a prompt into an animated GIF and returns its URL: submits, waits,
  and returns when the GIF is ready."
  ([prompt] (generate prompt {}))
  ([prompt opts] (guard (.generate (client opts) prompt))))

(defn submit
  "Starts a GIF without waiting and returns its id; follow it with `status`."
  ([prompt] (submit prompt {}))
  ([prompt opts] (guard (.submit (client opts) prompt))))

(defn status
  "Where a GIF stands: {:id :status (\"pending\" \"processing\" \"success\"
  \"failed\") :url :queued :error-message :raw}."
  ([id] (status id {}))
  ([id opts]
   (guard (let [^Aigifmaker$Status s (.status (client opts) id)]
            {:id (.id s) :status (.status s) :url (.url s) :queued (.queued s)
             :error-message (.errorMessage s) :raw (into {} (.raw s))}))))

(defn credits
  "The credits left on the account. Free to call."
  ([] (credits {}))
  ([opts] (guard (.credits (client opts)))))
