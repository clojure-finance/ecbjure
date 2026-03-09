(ns clojure-finance.ecbjure.dataset
  "tech.ml.dataset integration for ecbjure converters.
   Requires the :dataset alias (techascent/tech.ml.dataset)."
  (:require [clojure-finance.ecbjure.fx :as fx]
            [tech.v3.dataset :as ds]))

(defn rates-wide
  "Return a TMD dataset in wide format: one row per date, one column per currency.
   Columns: :date + one column per currency code (EUR-referenced rates).
   EUR column is all 1.0 (the reference currency).
   Rows are sorted chronologically."
  [converter]
  (let [ccys (sort (disj (:currencies converter) (:ref-currency converter)))
        all-dates (sort (distinct (mapcat keys (map #(get-in converter [:rates %]) ccys))))
        rows (map (fn [date]
                    (into {:date date}
                          (map (fn [ccy]
                                 [ccy (get (get-in converter [:rates ccy]) date)]))
                          ccys))
                  all-dates)
        ref (:ref-currency converter)
        rows (map #(assoc % ref 1.0) rows)]
    (ds/->dataset rows {:dataset-name "ecb-rates-wide"})))

(defn rates-long
  "Return a TMD dataset in long/tidy format: one row per (date, currency) observation.
   Columns: :date, :currency, :rate (EUR-referenced).
   Rows with missing rates (weekends/holidays for that currency) are excluded.
   Sorted by date then currency."
  [converter]
  (let [ref (:ref-currency converter)
        ccys (sort (disj (:currencies converter) ref))
        rows (for [ccy ccys
                   [date rate] (fx/rate-history converter ccy)]
               {:date date :currency ccy :rate rate})
        ref-dates (sort (distinct (mapcat keys (map #(get-in converter [:rates %]) ccys))))
        ref-rows (map (fn [date] {:date date :currency ref :rate 1.0}) ref-dates)
        all-rows (sort-by (juxt :date :currency) (concat rows ref-rows))]
    (ds/->dataset all-rows {:dataset-name "ecb-rates-long"})))
