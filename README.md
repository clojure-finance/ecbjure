# ecbjure

[![Clojars Project](https://img.shields.io/clojars/v/com.github.clojure-finance/ecbjure.svg)](https://clojars.org/com.github.clojure-finance/ecbjure)
[![CI](https://github.com/clojure-finance/ecbjure/actions/workflows/ci.yml/badge.svg)](https://github.com/clojure-finance/ecbjure/actions/workflows/ci.yml)
[![cljdoc](https://cljdoc.org/badge/com.github.clojure-finance/ecbjure)](https://cljdoc.org/d/com.github.clojure-finance/ecbjure/CURRENT)
[![License](https://img.shields.io/badge/license-EPL--2.0-blue.svg)](LICENSE)

A pure Clojure library for accessing European Central Bank (ECB) data. It gives you
authoritative, institutional-quality financial data — FX rates, interest rates, inflation,
and more — through a clean, data-oriented API with no magic and no surprises.

## Why ecbjure?

**Authoritative data, not scraped prices.** The ECB publishes official daily reference
rates for ~42 currencies going back to 1999. These are the rates used in legal,
regulatory, and accounting contexts — not indicative quotes pulled from a commercial
feed.

**Honest about missing data.** ECB rates are published on business days only. Many
libraries silently interpolate or forward-fill missing dates, which introduces look-ahead
bias in backtesting and quietly corrupts time-series analysis. ecbjure throws on a missing
date unless you explicitly opt in to a fallback strategy. When gap-filling happens in your
pipeline, it happens visibly, in code you wrote.

**Functional and inspectable.** The converter is a plain Clojure map. There is no mutable
state, no opaque object, and no hidden cache. You can inspect it in the REPL, pass it
through pipelines, and compose it freely.

**Minimal dependencies.** The core library depends only on `org.clojure/data.csv`.
Everything else — HTTP, ZIP, XML, date handling — comes from the JDK. Optional features
(dataset output, CLI) are gated behind aliases and never pulled into your project unless
you need them.

**Broad ECB coverage via SDMX.** Beyond FX rates, the included SDMX client gives you
access to the full ECB statistical catalogue: EURIBOR, €STR, HICP inflation, and anything
else the ECB publishes. One consistent API, the same data-oriented design.

---

## Installation

```clojure
;; deps.edn
com.github.clojure-finance/ecbjure {:mvn/version "0.1.4"}
```

---

## FX Rates

### Loading the converter

`make-converter` fetches the latest ECB data and returns a plain map. Pass a path or URL
to load from a local file or a custom source instead.

```clojure
(require '[clojure-finance.ecbjure.fx :as fx])
(import '[java.time LocalDate])

;; Fetch latest rates from the ECB (default)
(def c (fx/make-converter))

;; From a local ZIP or CSV file
(def c (fx/make-converter "/path/to/eurofxref-hist.zip"))

;; With options
(def c (fx/make-converter fx/ecb-url {:fallback :nearest
                                       :cast-fn  bigdec}))
```

**Options:**

| Key | Default | Description |
|-----|---------|-------------|
| `:cast-fn` | `double` | Rate coercion function. Pass `bigdec` for exact arithmetic. |
| `:fallback` | `false` | Out-of-bounds date behaviour: `false` (throw), `:nearest`, `:before`, `:after`, or `true` (alias for `:nearest`). |
| `:ref-currency` | `"EUR"` | Triangulation pivot. |

### Converting amounts

```clojure
;; Latest available rate
(fx/convert c 100 "USD" "JPY")
;; => 15791.89

;; On a specific date
(fx/convert c 100 "USD" "EUR" (LocalDate/of 2013 3 21))
;; => 77.46

;; Omit target currency — defaults to EUR
(fx/convert c 100 "USD")
;; => 90.91
```

Conversion is triangulated through EUR: `amount × rate-to / rate-from`. When either
currency is EUR the formula simplifies accordingly.

### Querying rates

```clojure
;; EUR-referenced rate for a currency on a date
(fx/get-rate c "USD" (LocalDate/of 2014 3 28))
;; => 1.3759

;; get-rate on the reference currency always returns 1.0
(fx/get-rate c "EUR" (LocalDate/of 2014 3 28))
;; => 1.0

;; Direct cross rate between any two currencies
(fx/cross-rate c "USD" "GBP" (LocalDate/of 2014 3 28))
;; => 0.5999...

;; Full sorted date→rate history for a currency
(fx/rate-history c "USD")
;; => {#object[LocalDate "1999-01-04"] 1.1789, ...}
```

### Inspecting the converter

The converter is a plain map — query it directly:

```clojure
(:currencies c)   ;; => #{"EUR" "USD" "JPY" "GBP" ...}
(:bounds c)       ;; => {"USD" {:first-date #object[LocalDate "1999-01-04"]
                  ;;            :last-date  #object[LocalDate "2026-03-06"]} ...}
(:ref-currency c) ;; => "EUR"
```

### Constants

```clojure
fx/ecb-url        ;; "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip"
fx/ecb-daily-url  ;; "https://www.ecb.europa.eu/stats/eurofxref/eurofxref.zip"
```

---

## Error Handling

All errors are `ex-info`. Inspect `(ex-data e)` for context:

| Condition | `:type` | Extra keys |
|-----------|---------|------------|
| Currency not in dataset | `:unknown-currency` | `:currency` |
| Date outside available range | `:date-out-of-bounds` | `:currency` `:date` `:first-date` `:last-date` |
| Date in range but no rate (weekend/holiday) | `:rate-not-found` | `:currency` `:date` |

```clojure
(try
  (fx/convert c 100 "USD" "EUR" (LocalDate/of 2024 1 6)) ; Saturday
  (catch clojure.lang.ExceptionInfo e
    (ex-data e)))
;; => {:type :rate-not-found, :currency "USD", :date #object[LocalDate "2024-01-06"]}
```

To clamp out-of-bounds dates to the nearest available observation instead of throwing:

```clojure
(def c (fx/make-converter fx/ecb-url {:fallback :nearest}))
```

`:before` and `:after` let you control the clamping direction explicitly.

---

## SDMX Client

The SDMX client provides access to the full ECB statistical catalogue via the ECB SDMX
2.1 REST API. No additional dependencies — it uses `data.csv` and JDK HTTP.

```clojure
(require '[clojure-finance.ecbjure.sdmx :as sdmx])
```

### Fetching series

```clojure
;; EURIBOR 3-month rate, last 3 observations
(sdmx/get-series sdmx/euribor-3m {:last-n 3})
;; => [{:time-period #object[LocalDate "2025-12-01"] :obs-value 2.0457 :currency "EUR" ...} ...]

;; Euro area HICP inflation since 2020
(sdmx/get-series sdmx/hicp-euro-area {:start-period "2020-01"})

;; Any ECB SDMX series by key
(sdmx/get-series "EXR/D.USD.EUR.SP00.A"
                 {:start-period "2024-01-01" :end-period "2024-01-31"})
```

Each observation is a map with `:time-period` (LocalDate) and `:obs-value` (double),
plus all dimension columns from the ECB CSV response as keyword keys.

**Options:** `:start-period`, `:end-period`, `:last-n`, `:first-n`, `:updated-after`,
`:cast-fn`, `:na-values`.

### Predefined series constants

| Constant | Description | Frequency |
|----------|-------------|-----------|
| `exr-daily` | All FX rates vs EUR | Daily |
| `exr-monthly` | All FX rates vs EUR | Monthly |
| `euribor-1w` `euribor-1m` `euribor-3m` `euribor-6m` `euribor-1y` | EURIBOR rates | Monthly |
| `euribor-overnight` | EONIA / €STR | Monthly |
| `estr-daily` | €STR | Daily |
| `hicp-euro-area` | Euro area HICP inflation | Monthly |

### Building series keys

For multi-currency queries or custom series, use the series-key builders:

```clojure
;; Generic builder — strings, nil (wildcard), or sets (multi-value)
(sdmx/build-series-key "EXR" ["D" #{"USD" "JPY"} nil "SP00" "A"])
;; => "EXR/D.JPY+USD..SP00.A"

;; EXR convenience builder with named keys and sensible defaults
(sdmx/exr-series-key {:currency #{"USD" "JPY"}})
;; => "EXR/D.JPY+USD.EUR.SP00.A"

(sdmx/exr-series-key {:freq "M" :currency "GBP"})
;; => "EXR/M.GBP.EUR.SP00.A"

;; Compose directly with get-series
(sdmx/get-series (sdmx/exr-series-key {:currency #{"USD" "JPY"}})
                 {:last-n 5})
```

### Discovering available dataflows

```clojure
;; List all ~100 available ECB dataflows
(sdmx/list-dataflows)
;; => {"AME" "AMECO", "BKN" "Banknotes statistics", "EXR" "Exchange Rates", "ICP" "HICP", ...}
```

---

## Dataset Output (optional)

Requires the `:dataset` alias, which adds `techascent/tech.ml.dataset` to the classpath.
Start the REPL with `clj -M:nrepl:dataset`.

```clojure
(require '[clojure-finance.ecbjure.dataset :as ds])

;; Wide format — one row per date, one column per currency
(ds/rates-wide c)
;; => ecb-rates-wide [6846 43]:
;; |      :date |    USD |    JPY | ... |
;; |------------|-------:|-------:|
;; | 1999-01-04 | 1.1789 | 133.73 | ... |

;; Long / tidy format — one row per (date, currency) observation
(ds/rates-long c)
;; => ecb-rates-long [~280000 3]:
;; |      :date | :currency |   :rate |
;; |------------|-----------|--------:|
;; | 1999-01-04 |       USD |  1.1789 |
;; | 1999-01-04 |       GBP |  0.7111 |
```

---

## CLI

With the `:cli` alias:

```bash
clj -M:cli 100 USD --to EUR
# 100.000 USD = 90.909 EUR on 2026-03-06

clj -M:cli 100 USD --to JPY --date 2013-03-21
# 100.000 USD = 12051.282 JPY on 2013-03-21

clj -M:cli 100 USD -v
# 100.000 USD
# 41 available currencies:
# AUD BGN BRL CAD CHF CNY CZK DKK EUR GBP ...
```

Options: `--to <currency>`, `--date <yyyy-MM-dd>`, `--source <url-or-path>`.

---

## Development

```bash
clj -M:nrepl          # start nREPL on port 7888
clj -M:test           # run tests with kaocha
clj -T:build clean    # always clean before jar/deploy
clj -T:build jar      # build JAR
clj -T:build deploy   # deploy to Clojars
```

---

## License

Copyright © 2026 clojure-finance

Released under the [Eclipse Public License 2.0](LICENSE).
