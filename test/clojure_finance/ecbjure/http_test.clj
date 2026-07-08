(ns clojure-finance.ecbjure.http-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure-finance.ecbjure.http :as http])
  (:import [java.net ServerSocket SocketTimeoutException]
           [java.nio.charset StandardCharsets]))

(defn- with-stalling-server
  "Start a server that accepts connections, optionally sends `preamble`,
   then goes silent. Calls (f port), then shuts the server down."
  [^String preamble f]
  (let [server (ServerSocket. 0)
        accept-loop (future
                      (try
                        (loop []
                          (let [sock (.accept server)]
                            (when (seq preamble)
                              (doto (.getOutputStream sock)
                                (.write (.getBytes preamble StandardCharsets/US_ASCII))
                                (.flush)))
                            ;; never write anything else, never close
                            (recur)))
                        (catch Exception _)))]
    (try
      (f (.getLocalPort server))
      (finally
        (.close server)
        (future-cancel accept-loop)))))

(deftest read-timeout-aborts-stalled-response
  (testing "server that accepts but never responds"
    (with-stalling-server nil
      (fn [port]
        (binding [http/*read-timeout-ms* 200]
          (is (thrown? SocketTimeoutException
                       (http/fetch-bytes (str "http://127.0.0.1:" port "/")))))))))

(deftest read-timeout-aborts-stalled-body
  (testing "server that sends headers and part of the body, then stalls"
    (with-stalling-server
      (str "HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\npartial body")
      (fn [port]
        (binding [http/*read-timeout-ms* 200]
          (is (thrown? SocketTimeoutException
                       (http/fetch-bytes (str "http://127.0.0.1:" port "/")))))))))

(deftest fetch-bytes-reads-local-files
  (testing "non-HTTP URLs still work through the helper"
    (let [f (java.io.File/createTempFile "ecbjure-http-test" ".txt")]
      (try
        (spit f "hello")
        (is (= "hello" (String. (http/fetch-bytes (str (.toURI f))) "UTF-8")))
        (finally
          (.delete f))))))
