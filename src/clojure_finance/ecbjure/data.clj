(ns clojure-finance.ecbjure.data
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io])
  (:import [java.io BufferedReader InputStreamReader]
           [java.time LocalDate]
           [java.time.format DateTimeFormatter]
           [java.util.zip ZipInputStream]))

(def ^:private long-date-fmt
  (DateTimeFormatter/ofPattern "dd MMMM yyyy"))

(defn- parse-date [^String s]
  (if (re-matches #"\d{4}-\d{2}-\d{2}" s)
    (LocalDate/of (Integer/parseInt (subs s 0 4))
                  (Integer/parseInt (subs s 5 7))
                  (Integer/parseInt (subs s 8 10)))
    (LocalDate/parse s long-date-fmt)))

(defn- parse-rate [^String s cast-fn na-values]
  (when-not (na-values s)
    (cast-fn (Double/parseDouble s))))

(defn parse-ecb-csv
  "Parse ECB CSV lines into a rates map {currency (sorted-map date rate)}.
   Lines should be a seq of strings (not yet parsed)."
  [lines {:keys [na-values cast-fn]
          :or {na-values #{"" "N/A"}
               cast-fn double}}]
  (let [rows (csv/read-csv (clojure.string/join "\n" lines))
        [header & data-rows] rows
        currencies (rest header)]
    (reduce
     (fn [acc row]
       (let [date-str (first row)
             values (rest row)]
         (if (empty? date-str)
           acc
           (let [date (parse-date date-str)]
             (reduce
              (fn [acc [currency rate-str]]
                (if-let [rate (parse-rate rate-str cast-fn na-values)]
                  (update acc currency
                          (fn [m] (assoc (or m (sorted-map)) date rate)))
                  acc))
              acc
              (map vector currencies values))))))
     {}
     data-rows)))

(defn- reader-lines [rdr]
  (line-seq (BufferedReader. (InputStreamReader. rdr "UTF-8"))))

(defn- parse-zip-stream
  "Read first CSV entry from a ZipInputStream, return lines."
  [^ZipInputStream zis]
  (.getNextEntry zis)
  (reader-lines zis))

(defn lines-from-zip-bytes [^bytes bs]
  (parse-zip-stream (ZipInputStream. (io/input-stream bs))))

(defn lines-from-stream [stream]
  (reader-lines stream))

(defn- fetch-bytes [url-str]
  (with-open [in (.openStream (java.net.URL. url-str))]
    (.readAllBytes in)))

(defn load-lines
  "Load CSV lines from a source:
   - nil → fetch latest data from ECB URL
   - string starting with http(s):// → fetch from URL (ZIP or CSV)
   - string (file path) → local file (ZIP or CSV)
   - java.io.File / Path → treated as local file
   Returns a seq of raw CSV lines."
  [source]
  (cond
    (nil? source)
    (let [bs (fetch-bytes "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip")]
      (lines-from-zip-bytes bs))

    (and (string? source)
         (or (clojure.string/starts-with? source "http://")
             (clojure.string/starts-with? source "https://")))
    (let [bs (fetch-bytes source)]
      (if (or (clojure.string/ends-with? source ".zip")
              (= (aget bs 0) (byte 0x50)))
        (lines-from-zip-bytes bs)
        (clojure.string/split-lines (String. bs "UTF-8"))))

    (string? source)
    (let [f (io/file source)]
      (if (clojure.string/ends-with? source ".zip")
        (with-open [zis (ZipInputStream. (io/input-stream f))]
          (doall (parse-zip-stream zis)))
        (with-open [rdr (io/reader f)]
          (doall (line-seq rdr)))))

    :else
    (throw (ex-info "Unknown source type" {:source source}))))
