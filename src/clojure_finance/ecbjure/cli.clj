(ns clojure-finance.ecbjure.cli
  (:require [clojure-finance.ecbjure.fx :as fx]
            [clojure.string :as str])
  (:import [java.time LocalDate])
  (:gen-class))

(defn- parse-date [s]
  (LocalDate/parse s))

(defn- usage []
  (println "Usage: ecbjure <amount> <from> [--to <currency>] [--date <yyyy-MM-dd>] [--source <url-or-path>]")
  (println "       ecbjure <amount> <from> -v    (list available currencies)"))

(defn -main [& args]
  (let [argv (vec args)]
    (cond
      (< (count argv) 2)
      (usage)

      (= (last argv) "-v")
      (let [amount (Double/parseDouble (nth argv 0))
            from (str/upper-case (nth argv 1))
            c (fx/make-converter)]
        (println (format "%.3f %s" amount from))
        (println (count (:currencies c)) "available currencies:")
        (println (str/join " " (sort (:currencies c)))))

      :else
      (let [amount (Double/parseDouble (nth argv 0))
            from (str/upper-case (nth argv 1))
            to (if-let [i (.indexOf argv "--to")]
                 (when (>= i 0) (str/upper-case (nth argv (inc i))))
                 nil)
            to (or to "EUR")
            date (when-let [i (let [idx (.indexOf argv "--date")]
                                (when (>= idx 0) idx))]
                   (parse-date (nth argv (inc i))))
            source (when-let [i (let [idx (.indexOf argv "--source")]
                                  (when (>= idx 0) idx))]
                     (nth argv (inc i)))
            c (fx/make-converter source)
            result (fx/convert c amount from to date)
            used-date (or date (get-in c [:bounds from :last-date]))]
        (println (format "%.3f %s = %.3f %s on %s" amount from result to used-date))))))
