(ns clojure-finance.ecbjure.sdmx-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure-finance.ecbjure.sdmx :as sdmx])
  (:import [java.time LocalDate]))

(def sample-csv-lines
  ["KEY,FREQ,CURRENCY,CURRENCY_DENOM,EXR_TYPE,EXR_SUFFIX,TIME_PERIOD,OBS_VALUE,OBS_STATUS,OBS_CONF"
   "EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,2024-01-02,1.0942,A,"
   "EXR.D.USD.EUR.SP00.A,D,USD,EUR,SP00,A,2024-01-03,1.0931,A,"
   "EXR.D.GBP.EUR.SP00.A,D,GBP,EUR,SP00,A,2024-01-02,0.8661,A,"
   "EXR.D.GBP.EUR.SP00.A,D,GBP,EUR,SP00,A,2024-01-03,0.8637,A,"
   "EXR.D.JPY.EUR.SP00.A,D,JPY,EUR,SP00,A,2024-01-02,,A,"
   "EXR.M.USD.EUR.SP00.A,M,USD,EUR,SP00,A,2024-01,1.0912,A,"])

(deftest parse-basic
  (testing "returns correct count (skips missing OBS_VALUE)"
    (let [obs (sdmx/parse-sdmx-csv sample-csv-lines {})]
      (is (= 5 (count obs)))))
  (testing "time-period is LocalDate for daily (YYYY-MM-DD)"
    (let [obs (sdmx/parse-sdmx-csv sample-csv-lines {})
          first-obs (first obs)]
      (is (instance? LocalDate (:time-period first-obs)))
      (is (= (LocalDate/of 2024 1 2) (:time-period first-obs)))))
  (testing "time-period is first-of-month for monthly (YYYY-MM)"
    (let [obs (sdmx/parse-sdmx-csv sample-csv-lines {})
          monthly (last obs)]
      (is (= (LocalDate/of 2024 1 1) (:time-period monthly)))))
  (testing "obs-value is double by default"
    (let [obs (first (sdmx/parse-sdmx-csv sample-csv-lines {}))]
      (is (= 1.0942 (:obs-value obs)))))
  (testing "obs-value cast-fn is applied"
    (let [obs (first (sdmx/parse-sdmx-csv sample-csv-lines {:cast-fn bigdec}))]
      (is (decimal? (:obs-value obs)))))
  (testing "dimension columns are included as keywords"
    (let [obs (first (sdmx/parse-sdmx-csv sample-csv-lines {}))]
      (is (= "USD" (:currency obs)))
      (is (= "D" (:freq obs))))))

(deftest parse-missing-column
  (testing "throws on missing TIME_PERIOD"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Missing TIME_PERIOD"
                          (sdmx/parse-sdmx-csv ["OBS_VALUE\n1.0"] {})))))

(deftest series-key-constants
  (testing "exr-daily starts with EXR"
    (is (clojure.string/starts-with? sdmx/exr-daily "EXR")))
  (testing "hicp-euro-area starts with ICP"
    (is (clojure.string/starts-with? sdmx/hicp-euro-area "ICP")))
  (testing "euribor-3m starts with FM"
    (is (clojure.string/starts-with? sdmx/euribor-3m "FM"))))
