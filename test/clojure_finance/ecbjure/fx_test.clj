(ns clojure-finance.ecbjure.fx-test
  (:require [clojure.test :refer [deftest is are testing]]
            [clojure-finance.ecbjure.fx :as fx])
  (:import [java.time LocalDate]))

(def ^:private sample-lines
  ["Date,USD,JPY,GBP,CHF"
   "2024-01-02,1.0963,157.91,0.8629,0.9302"
   "2024-01-03,1.0900,158.10,0.8600,0.9280"
   "2024-01-04,1.1000,159.00,0.8700,0.9350"])

(def ^:private c (fx/make-converter-from-lines sample-lines))

(def ^:private d02 (LocalDate/of 2024 1 2))
(def ^:private d03 (LocalDate/of 2024 1 3))
(def ^:private d04 (LocalDate/of 2024 1 4))

(defn- approx=
  ([a b] (approx= a b 1e-9))
  ([a b tol] (< (Math/abs (- (double a) (double b))) tol)))

(deftest convert-test
  (testing "EUR → foreign"
    (is (approx= (* 100 1.0963) (fx/convert c 100 "EUR" "USD" d02)))
    (is (approx= (* 100 0.8629) (fx/convert c 100 "EUR" "GBP" d02))))

  (testing "foreign → EUR"
    (is (approx= (/ 100.0 1.0963) (fx/convert c 100 "USD" "EUR" d02)))
    (is (approx= (/ 100.0 0.8629) (fx/convert c 100 "GBP" "EUR" d02))))

  (testing "cross rate (triangulate through EUR)"
    (is (approx= (* 100 (/ 0.8629 1.0963)) (fx/convert c 100 "USD" "GBP" d02))))

  (testing "same currency is identity"
    (is (= 100.0 (fx/convert c 100 "USD" "USD" d02))))

  (testing "default target is ref-currency (EUR)"
    (is (approx= (/ 100.0 1.1) (fx/convert c 100 "USD"))))

  (testing "default date is last available"
    (is (approx= (* 100 1.1) (fx/convert c 100 "EUR" "USD"))))

  (testing "bigdec cast-fn"
    (let [bc (fx/make-converter-from-lines sample-lines {:cast-fn bigdec})]
      (is (decimal? (fx/convert bc 100 "EUR" "USD" d02))))))

(deftest error-test
  (testing "unknown from-currency throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unknown currency"
                          (fx/convert c 100 "XXX" "USD"))))

  (testing "unknown to-currency throws"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unknown currency"
                          (fx/convert c 100 "USD" "XXX"))))

  (testing "date outside bounds throws Date outside currency bounds"
    (let [out-of-bounds (LocalDate/of 2024 1 5)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Date outside currency bounds"
                            (fx/convert c 100 "USD" "EUR" out-of-bounds)))))

  (testing "date before first-date throws without fallback"
    (let [early (LocalDate/of 2000 1 1)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Date outside currency bounds"
                            (fx/convert c 100 "USD" "EUR" early)))))

  (testing ":nearest clamps date after last-date to last available"
    (let [fc (fx/make-converter-from-lines sample-lines {:fallback :nearest})
          late (LocalDate/of 2030 1 1)]
      (is (approx= (/ 100.0 1.1) (fx/convert fc 100 "USD" "EUR" late)))))

  (testing ":before clamps date before first-date to first available"
    (let [fc (fx/make-converter-from-lines sample-lines {:fallback :before})
          early (LocalDate/of 2000 1 1)]
      (is (approx= (/ 100.0 1.0963) (fx/convert fc 100 "USD" "EUR" early)))))

  (testing ":before with date after last-date clamps to last available"
    (let [fc (fx/make-converter-from-lines sample-lines {:fallback :before})
          late (LocalDate/of 2030 1 1)]
      (is (approx= (/ 100.0 1.1) (fx/convert fc 100 "USD" "EUR" late)))))

  (testing ":after clamps date after last-date to last available"
    (let [fc (fx/make-converter-from-lines sample-lines {:fallback :after})
          late (LocalDate/of 2030 1 1)]
      (is (approx= (/ 100.0 1.1) (fx/convert fc 100 "USD" "EUR" late)))))

  (testing ":after with date before first-date clamps to first available"
    (let [fc (fx/make-converter-from-lines sample-lines {:fallback :after})
          early (LocalDate/of 2000 1 1)]
      (is (approx= (/ 100.0 1.0963) (fx/convert fc 100 "USD" "EUR" early)))))

  (testing "true (backward-compat) behaves like :nearest"
    (let [fc (fx/make-converter-from-lines sample-lines {:fallback true})
          late (LocalDate/of 2030 1 1)]
      (is (approx= (/ 100.0 1.1) (fx/convert fc 100 "USD" "EUR" late))))))

(deftest metadata-test
  (testing ":currencies includes EUR and all CSV currencies"
    (is (= #{"EUR" "USD" "JPY" "GBP" "CHF"} (:currencies c))))

  (testing ":bounds has correct first and last dates"
    (is (= d02 (get-in c [:bounds "USD" :first-date])))
    (is (= d04 (get-in c [:bounds "USD" :last-date]))))

  (testing ":ref-currency defaults to EUR"
    (is (= "EUR" (:ref-currency c)))))

(deftest get-rate-test
  (is (approx= 1.0963 (fx/get-rate c "USD" d02)))
  (is (approx= 157.91 (fx/get-rate c "JPY" d02)))
  (is (= 1.0 (fx/get-rate c "EUR" d02)))
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Date outside currency bounds"
                        (fx/get-rate c "USD" (LocalDate/of 2024 1 7)))))

(deftest rate-history-test
  (let [h (fx/rate-history c "USD")]
    (is (= 3 (count h)))
    (is (approx= 1.0963 (get h d02)))
    (is (approx= 1.09 (get h d03)))
    (is (approx= 1.1 (get h d04)))))

(deftest rate-history-sorted-test
  (is (instance? clojure.lang.PersistentTreeMap (fx/rate-history c "USD"))))

(deftest cross-rate-test
  (is (approx= (/ 0.8629 1.0963) (fx/cross-rate c "USD" "GBP" d02)))
  (is (approx= (/ 1.0 1.0963) (fx/cross-rate c "USD" "EUR" d02)))
  (is (approx= 1.0963 (fx/cross-rate c "EUR" "USD" d02))))
