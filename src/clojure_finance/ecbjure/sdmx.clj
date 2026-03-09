(ns clojure-finance.ecbjure.sdmx
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io BufferedReader InputStreamReader]
           [java.net URI]
           [java.time LocalDate YearMonth]
           [java.time.format DateTimeFormatter]))

(def ^:private base-url "https://data-api.ecb.europa.eu/service/data")

(def exr-daily
  "Series key for all daily ECB FX reference rates (vs EUR)."
  "EXR/D..EUR.SP00.A")

(def exr-monthly
  "Series key for all monthly ECB FX reference rates (vs EUR)."
  "EXR/M..EUR.SP00.A")

(def euribor-overnight
  "Series key for EONIA/€STR overnight rate (monthly)."
  "FM/M.U2.EUR.RT.MM.EONIA_TO.HSTA")

(def euribor-1w
  "Series key for EURIBOR 1-week rate (monthly)."
  "FM/M.U2.EUR.RT.MM.EURIBOR1WD_.HSTA")

(def euribor-1m
  "Series key for EURIBOR 1-month rate (monthly)."
  "FM/M.U2.EUR.RT.MM.EURIBOR1MD_.HSTA")

(def euribor-3m
  "Series key for EURIBOR 3-month rate (monthly)."
  "FM/M.U2.EUR.RT.MM.EURIBOR3MD_.HSTA")

(def euribor-6m
  "Series key for EURIBOR 6-month rate (monthly)."
  "FM/M.U2.EUR.RT.MM.EURIBOR6MD_.HSTA")

(def euribor-1y
  "Series key for EURIBOR 1-year rate (monthly)."
  "FM/M.U2.EUR.RT.MM.EURIBOR1YD_.HSTA")

(def estr-daily
  "Series key for €STR (Euro Short-Term Rate), daily."
  "FM/D.U2.EUR.RT.MM.ESTR.HSTA")

(def hicp-euro-area
  "Series key for Euro Area HICP overall index (monthly)."
  "ICP/M.U2.Y.000000.3.INX")

(defn- build-url [series-key params]
  (let [base (str base-url "/" series-key)
        default-params {"format" "csvdata"}
        all-params (merge default-params params)
        query-str (str/join "&" (map (fn [[k v]] (str k "=" v)) all-params))]
    (str base "?" query-str)))

(defn- fetch-csv-lines [url-str]
  (with-open [stream (.openStream (.toURL (URI/create url-str)))
              rdr (BufferedReader. (InputStreamReader. stream "UTF-8"))]
    (doall (line-seq rdr))))

(defn- parse-time-period [^String s]
  (condp = (count s)
    10 (LocalDate/parse s)
    7 (.atDay (YearMonth/parse s) 1)
    4 (LocalDate/of (Integer/parseInt s) 1 1)
    (LocalDate/parse s)))

(defn parse-sdmx-csv
  "Parse SDMX CSV lines (format=csvdata) into a vector of observation maps.
  Each map has :time-period (LocalDate) and :obs-value (Double or nil),
  plus any other columns from the CSV as keyword keys."
  [lines {:keys [cast-fn na-values]
          :or {cast-fn double na-values #{"" "NaN" "N/A"}}}]
  (let [rows (csv/read-csv (str/join "\n" lines))
        [header & data-rows] rows
        col-keys (mapv (comp keyword str/lower-case) header)
        tp-idx (.indexOf col-keys :time_period)
        ov-idx (.indexOf col-keys :obs_value)]
    (when (or (neg? tp-idx) (neg? ov-idx))
      (throw (ex-info "Missing TIME_PERIOD or OBS_VALUE column"
                      {:type :parse-error :columns col-keys})))
    (into []
          (comp
           (remove #(empty? (nth % ov-idx "")))
           (remove #(na-values (nth % ov-idx "")))
           (map (fn [row]
                  (let [base (zipmap col-keys row)]
                    (-> base
                        (assoc :time-period (parse-time-period (nth row tp-idx)))
                        (assoc :obs-value (cast-fn (Double/parseDouble (nth row ov-idx))))
                        (dissoc :time_period :obs_value))))))
          data-rows)))

(defn get-series
  "Fetch an ECB SDMX series and return a vector of observation maps.

  series-key - a string like \"EXR/D.USD.EUR.SP00.A\" or one of the
               predefined constants (exr-daily, euribor-3m, hicp-euro-area, etc.)

  Options (all optional):
    :start-period  - ISO date string, e.g. \"2020-01-01\" or \"2020-01\"
    :end-period    - ISO date string
    :last-n        - integer, return only the last N observations per series
    :first-n       - integer, return only the first N observations per series
    :cast-fn       - coercion fn for OBS_VALUE (default: double)
    :na-values     - set of strings to treat as missing (default: #{\"\" \"NaN\" \"N/A\"})

  Returns a vector of maps, each with:
    :time-period   - java.time.LocalDate (first day of period for monthly/annual)
    :obs-value     - numeric value (cast by :cast-fn)
    plus dimension columns from the CSV (e.g. :currency, :freq, :key, etc.)"
  ([series-key] (get-series series-key {}))
  ([series-key {:keys [start-period end-period last-n first-n cast-fn na-values]
                :as opts}]
   (let [params (cond-> {}
                  start-period (assoc "startPeriod" start-period)
                  end-period (assoc "endPeriod" end-period)
                  last-n (assoc "lastNObservations" last-n)
                  first-n (assoc "firstNObservations" first-n))
         url (build-url series-key params)
         lines (fetch-csv-lines url)]
     (parse-sdmx-csv lines (select-keys opts [:cast-fn :na-values])))))
