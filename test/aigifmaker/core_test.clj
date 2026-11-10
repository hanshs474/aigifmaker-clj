(ns aigifmaker.core-test
  "Runs the wrapper against a local HTTP server that answers with the service's
  real response shapes (every answer HTTP 200, the JSON code decides).
  java -cp <clojure+spec jars>:<aigifmaker jar>:src:test clojure.main -m aigifmaker.core-test
  AIGIFMAKER_SMOKE=1 adds two free live checks (no generation)."
  (:require [aigifmaker.core :as gif]
            [clojure.string :as str])
  (:import (com.sun.net.httpserver HttpServer HttpHandler HttpExchange)
           (java.net InetSocketAddress)))

(def seen (atom []))
(def answers (atom {}))

(defn- serve []
  (let [srv (HttpServer/create (InetSocketAddress. "127.0.0.1" 0) 0)]
    (.createContext srv "/"
      (reify HttpHandler
        (handle [_ ex]
          (let [path (.getPath (.getRequestURI ^HttpExchange ex))
                body (slurp (.getRequestBody ^HttpExchange ex))
                _ (swap! seen conj [path body (.getFirst (.getRequestHeaders ^HttpExchange ex) "Authorization")])
                ^String out ((get @answers path))
                bytes (.getBytes out "UTF-8")]
            (.sendResponseHeaders ^HttpExchange ex 200 (alength bytes))
            (with-open [o (.getResponseBody ^HttpExchange ex)] (.write o bytes))))))
    (.start srv)
    srv))

(defn- check [ok what] (if ok (println "ok  " what) (throw (AssertionError. (str "FAIL " what)))))

(defn- reason [f] (try (f) nil (catch clojure.lang.ExceptionInfo e (:reason (ex-data e)))))

(def submitted "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"pending\",\"taskId\":\"up_9\",\"provider\":\"openlux\"}}")

(defn -main [& _]
  (let [srv (serve)
        base (str "http://127.0.0.1:" (.getPort (.getAddress srv)))
        o {:api-key "sk-test" :base-url base :poll-ms 1}
        polls (atom 0)]
    (reset! answers
            {"/api/ai/generate" (fn [] submitted)
             "/api/ai/query" (fn [] (case (swap! polls inc)
                                      1 "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"processing\",\"images\":[],\"queued\":true,\"queueEta\":40}}"
                                      "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"success\",\"images\":[\"https://cdn.aigifmaker.ai/g/task_1.gif\"],\"watermarked\":[true]}}"))
             "/api/user/get-user-credits" (fn [] "{\"code\":0,\"message\":\"ok\",\"data\":{\"remainingCredits\":42,\"made\":3}}")})
    (check (= "https://cdn.aigifmaker.ai/g/task_1.gif" (gif/generate "a corgi dancing" o)) "generate waits through the queue")
    (let [[path body auth] (first @seen)]
      (check (= path "/api/ai/generate") "submit path")
      (check (= auth "Bearer sk-test") "bearer header")
      (doseq [f ["\"provider\":\"openlux\"" "\"mediaType\":\"image\"" "\"model\":\"gif-maker-v1\""
                 "\"scene\":\"text-to-image\"" "\"prompt\":\"a corgi dancing\"" "\"output_format\":\"gif\""]]
        (check (str/includes? body f) (str "submit body has " f))))
    (check (= "{\"taskId\":\"task_1\"}" (second (second @seen))) "query body uses data.id")
    (check (= 42 (gif/credits o)) "credits")
    (let [s (gif/status "task_1" o)]
      (check (and (= "success" (:status s)) (= "https://cdn.aigifmaker.ai/g/task_1.gif" (:url s))) "status map"))
    (doseq [[body r] [["{\"code\":-1,\"message\":\"invalid API key — create one at https://aigifmaker.ai/settings/apikeys\"}" :auth]
                      ["{\"code\":-1,\"message\":\"no auth, please sign in\"}" :auth]
                      ["{\"code\":0,\"message\":\"ok\",\"data\":{\"wall\":true,\"reason\":\"anon_credits\"}}" :auth]
                      ["{\"code\":-1,\"message\":\"insufficient credits\"}" :quota]
                      ["{\"code\":-2,\"message\":\"content cooldown, try again later\"}" :rejected]
                      ["{\"code\":-1,\"message\":\"upstream busy\"}" :other]]]
      (swap! answers assoc "/api/ai/generate" (fn [] body))
      (check (= r (reason #(gif/submit "x" o))) (str body " -> " r)))
    (swap! answers assoc "/api/ai/generate" (fn [] submitted)
           "/api/ai/query" (fn [] "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"failed\",\"errorMessage\":\"upstream error\",\"refundNote\":\"credit returned\"}}"))
    (check (= :failed (reason #(gif/generate "x" o))) "failed run")
    (swap! answers assoc "/api/ai/query" (fn [] "{\"code\":0,\"message\":\"ok\",\"data\":{\"id\":\"task_1\",\"status\":\"failed\",\"errorMessage\":\"blocked\",\"failureKind\":\"moderation\"}}"))
    (check (= :rejected (reason #(gif/generate "x" o))) "moderation -> rejected")
    (.stop srv 0)
    (when (System/getenv "AIGIFMAKER_SMOKE")
      (check (= :auth (reason #(gif/credits {:api-key "sk-invalidtest000"}))) "live: wrong key credits -> :auth")
      (check (= :auth (reason #(gif/submit "a cat waving" {:api-key ""}))) "live: no key submit -> :auth (wall)"))
    (println "all tests passed")
    (shutdown-agents)))
