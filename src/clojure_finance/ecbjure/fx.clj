(ns clojure-finance.ecbjure.fx
  (:require [clojure-finance.ecbjure.data :as data])
  (:import [java.time LocalDate ZonedDateTime Instant]
           [java.time.format DateTimeFormatter]))

(def ecb-url
  "ECB historical FX rates ZIP (daily reference rates since 1999, ~42 currencies)."
  "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip")

(def ecb-daily-url
  "ECB latest-day FX rates ZIP (single business day, updated daily)."
  "https://www.ecb.europa.eu/stats/eurofxref/eurofxref.zip")

(def ^:private default-opts
  {:fallback-on-wrong-date false
   :ref-currency "EUR"
   :cast-fn double
   :na-values #{"" "N/A"}})

(defn- coerce-date [d]
  (cond
    (instance? LocalDate d) d
    (instance? ZonedDateTime d) (.toLocalDate ^ZonedDateTime d)
    (instance? Instant d) (.toLocalDate (.atZone ^Instant d java.time.ZoneOffset/UTC))
    :else (throw (ex-info "Cannot coerce to LocalDate" {:value d :type (type d)}))))

(defn- compute-bounds [rates]
  (into {}
        (map (fn [[ccy date-map]]
               [ccy {:first-date (first (keys date-map))
                     :last-date (last (keys date-map))}]))
        rates))

(defn make-converter
  "Build a converter map from a source.
   source: nil (default) fetches latest data from ECB URL, or pass a URL string or file path.
   opts: map with keys :fallback-on-wrong-date, :ref-currency, :cast-fn, :na-values."
  ([] (make-converter nil {}))
  ([source] (make-converter source {}))
  ([source opts]
   (let [opts (merge default-opts opts)
         lines (data/load-lines source)
         rates (data/parse-ecb-csv lines (select-keys opts [:na-values :cast-fn]))
         bounds (compute-bounds rates)
         ccys (conj (set (keys rates)) (:ref-currency opts))]
     {:rates rates
      :currencies ccys
      :bounds bounds
      :ref-currency (:ref-currency opts)
      :options (select-keys opts [:fallback-on-wrong-date :cast-fn])})))

(defn make-converter-from-lines
  "Build a converter from an already-loaded seq of CSV lines."
  ([lines] (make-converter-from-lines lines {}))
  ([lines opts]
   (let [opts (merge default-opts opts)
         rates (data/parse-ecb-csv lines (select-keys opts [:na-values :cast-fn]))
         bounds (compute-bounds rates)
         ccys (conj (set (keys rates)) (:ref-currency opts))]
     {:rates rates
      :currencies ccys
      :bounds bounds
      :ref-currency (:ref-currency opts)
      :options (select-keys opts [:fallback-on-wrong-date :cast-fn])})))

(defn- lookup-rate
  "Look up the rate for a currency on a date, with optional fallback-on-wrong-date."
  [{:keys [rates bounds options]} currency date]
  (let [date-map (rates currency)
        {:keys [first-date last-date]} (bounds currency)
        fallback? (:fallback-on-wrong-date options)]
    (cond
      (nil? date-map)
      (throw (ex-info "Unknown currency" {:currency currency}))

      (and (not fallback?) (or (.isBefore date first-date) (.isAfter date last-date)))
      (throw (ex-info "Date outside currency bounds"
                      {:currency currency :date date
                       :first-date first-date :last-date last-date}))

      fallback?
      (let [clamped (cond (.isBefore date first-date) first-date
                          (.isAfter date last-date) last-date
                          :else date)]
        (or (date-map clamped)
            (throw (ex-info "Rate not found for date"
                            {:currency currency :date clamped}))))

      :else
      (or (date-map date)
          (throw (ex-info "Rate not found for date (missing / weekend?)"
                          {:currency currency :date date}))))))

(defn get-rate
  "Return the EUR-referenced rate for currency on date."
  [converter currency date]
  (lookup-rate converter currency (coerce-date date)))

(defn rate-history
  "Return the full sorted-map of {date rate} for a currency."
  [{:keys [rates]} currency]
  (or (rates currency)
      (throw (ex-info "Unknown currency" {:currency currency}))))

(defn cross-rate
  "Return the cross rate from currency-a to currency-b on date."
  [converter currency-a currency-b date]
  (let [d (coerce-date date)
        ra (lookup-rate converter currency-a d)
        rb (lookup-rate converter currency-b d)]
    (/ rb ra)))

(defn convert
  "Convert amount from currency `from` to currency `to` on `date`.
   If date is omitted, uses the last available date for `from`.
   If `to` is omitted, uses ref-currency (EUR)."
  ([converter amount from]
   (convert converter amount from (:ref-currency converter) nil))
  ([converter amount from to]
   (convert converter amount from to nil))
  ([converter amount from to date]
   (let [{:keys [rates ref-currency options]} converter
         cast-fn (:cast-fn options)
         ref ref-currency
         date (if date
                (coerce-date date)
                (last (keys (rates from))))
         amount (cast-fn amount)]
     (cond
       (= from to)
       amount

       (= from ref)
       (* amount (lookup-rate converter to date))

       (= to ref)
       (/ amount (lookup-rate converter from date))

       :else
       (/ (* amount (lookup-rate converter to date))
          (lookup-rate converter from date))))))
