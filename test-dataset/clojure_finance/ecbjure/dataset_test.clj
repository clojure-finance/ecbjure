(ns clojure-finance.ecbjure.dataset-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure-finance.ecbjure.fx :as fx]
            [clojure-finance.ecbjure.dataset :as ds]
            [tech.v3.dataset :as tmd])
  (:import [java.time LocalDate]))

(def ^:private sample-lines
  ["Date,USD,JPY,GBP"
   "2024-01-02,1.0963,157.91,0.8629"
   "2024-01-03,1.0900,158.10,0.8600"
   "2024-01-04,1.1000,159.00,0.8700"])

(def ^:private c (fx/make-converter-from-lines sample-lines))

(def ^:private d02 (LocalDate/of 2024 1 2))
(def ^:private d03 (LocalDate/of 2024 1 3))
(def ^:private d04 (LocalDate/of 2024 1 4))

(deftest rates-wide-test
  (let [ds (ds/rates-wide c)]
    (testing "shape: 3 rows, 5 columns (date + EUR + USD + JPY + GBP)"
      (is (= 3 (tmd/row-count ds)))
      (is (= 5 (tmd/column-count ds))))

    (testing "columns present"
      (is (= #{:date "EUR" "USD" "JPY" "GBP"} (set (tmd/column-names ds)))))

    (testing "EUR column is always 1.0"
      (is (every? #(= 1.0 %) (tmd/column ds "EUR"))))

    (testing "rows sorted chronologically"
      (is (= [d02 d03 d04] (vec (tmd/column ds :date)))))

    (testing "spot values"
      (let [row0 (first (tmd/rows ds {:as :maps}))]
        (is (= d02 (:date row0)))
        (is (= 1.0963 (get row0 "USD")))
        (is (= 157.91 (get row0 "JPY")))
        (is (= 0.8629 (get row0 "GBP")))))))

(deftest rates-long-test
  (let [ds (ds/rates-long c)]
    (testing "shape: 12 rows (3 dates × 4 currencies), 3 columns"
      (is (= 12 (tmd/row-count ds)))
      (is (= 3 (tmd/column-count ds))))

    (testing "columns are :date :currency :rate"
      (is (= #{:date :currency :rate} (set (tmd/column-names ds)))))

    (testing "EUR rows present with rate 1.0"
      (let [eur-rows (->> (tmd/rows ds {:as :maps})
                          (filter #(= "EUR" (:currency %))))]
        (is (= 3 (count eur-rows)))
        (is (every? #(= 1.0 (:rate %)) eur-rows))))

    (testing "spot value for USD on 2024-01-02"
      (let [row (->> (tmd/rows ds {:as :maps})
                     (filter #(and (= d02 (:date %)) (= "USD" (:currency %))))
                     first)]
        (is (= 1.0963 (:rate row)))))

    (testing "sorted by date then currency"
      (let [rows (vec (tmd/rows ds {:as :maps}))
            first-row (first rows)]
        (is (= d02 (:date first-row)))
        (is (= "EUR" (:currency first-row)))))))
