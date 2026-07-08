(ns clojure-finance.ecbjure.http
  "Minimal HTTP fetch helpers with connect/read timeouts.

   `java.net.URL/openStream` has no timeouts, so a stalled transfer
   (e.g. the ECB rates endpoint freezing mid-download) blocks the calling
   thread forever. Every network fetch in ecbjure goes through here instead."
  (:import [java.io InputStream]
           [java.net URI URLConnection]))

(def ^:dynamic *connect-timeout-ms*
  "Max milliseconds to wait for the connection to be established.
   Rebind to override."
  10000)

(def ^:dynamic *read-timeout-ms*
  "Max milliseconds to wait on a single read before treating the transfer
   as stalled. Rebind to override."
  30000)

(defn open-stream
  "Open an InputStream on url-str with connect/read timeouts applied.
   Throws java.net.SocketTimeoutException if connecting or a read stalls."
  ^InputStream [^String url-str]
  (let [^URLConnection conn (.openConnection (.toURL (URI/create url-str)))]
    (.setConnectTimeout conn (int *connect-timeout-ms*))
    (.setReadTimeout conn (int *read-timeout-ms*))
    (.getInputStream conn)))

(defn fetch-bytes
  "Fetch url-str fully into a byte array, with timeouts."
  ^bytes [^String url-str]
  (with-open [in (open-stream url-str)]
    (.readAllBytes in)))
