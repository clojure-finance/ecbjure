# ecbjure

A pure Clojure library for accessing European Central Bank (ECB) data, starting with historical currency conversion. Designed to grow into a broader ECB data client.

[![Clojars Project](https://img.shields.io/clojars/v/com.github.clojure-finance/ecbjure.svg)](https://clojars.org/com.github.clojure-finance/ecbjure)
[![CI](https://github.com/clojure-finance/ecbjure/actions/workflows/ci.yml/badge.svg)](https://github.com/clojure-finance/ecbjure/actions/workflows/ci.yml)
[![cljdoc](https://cljdoc.org/badge/com.github.clojure-finance/ecbjure)](https://cljdoc.org/d/com.github.clojure-finance/ecbjure)
[![License](https://img.shields.io/badge/license-EPL--2.0-blue.svg)](LICENSE)

## Features

- Historical FX rates from the ECB (daily reference rates, ~42 currencies since 1999)
- Triangulated conversion through EUR between any two supported currencies
- Always fetches fresh data from the ECB — no stale bundled rates
- Load from ECB URL (default), local file, or any custom URL
- Functional, data-oriented design — the converter is a plain Clojure map
- No interpolation of missing rates — missing data throws, never fabricates

## Installation

```clojure
;; deps.edn
com.github.clojure-finance/ecbjure {:mvn/version "0.1.0"}
```

## Quick Start

```clojure
(require '[clojure-finance.ecbjure.fx :as fx])
(import '[java.time LocalDate])

;; Fetch latest data from ECB (default)
(def c (fx/make-converter))

;; Convert 100 USD to JPY using the latest available rate
(fx/convert c 100 "USD" "JPY")
;; => 15791.89

;; Convert on a specific date
(fx/convert c 100 "USD" "EUR" (LocalDate/of 2013 3 21))
;; => 77.46

;; Omit target currency — defaults to EUR
(fx/convert c 100 "USD")
;; => 90.91
```

## API

### Construction

```clojure
;; Fetch from ECB URL (default)
(fx/make-converter)

;; From ECB URL (fetches latest)
(fx/make-converter fx/ecb-url)

;; From local file (ZIP or CSV)
(fx/make-converter "/path/to/eurofxref-hist.zip")

;; With options
(fx/make-converter fx/ecb-url {:fallback-on-wrong-date true
                                :cast-fn                bigdec})

;; From a seq of CSV lines (useful for testing or custom data)
(fx/make-converter-from-lines lines opts)
```

**Options:**

| Key | Default | Description |
|-----|---------|-------------|
| `:cast-fn` | `double` | Rate coercion function. Use `bigdec` for exact arithmetic. |
| `:fallback-on-wrong-date` | `false` | Clamp to first/last available date when out of bounds. |
| `:ref-currency` | `"EUR"` | Reference currency (triangulation pivot). |

### Conversion

```clojure
;; amount from → EUR (default target)
(fx/convert c 100 "USD")

;; amount from → to, latest available date
(fx/convert c 100 "USD" "JPY")

;; amount from → to, specific date
(fx/convert c 100 "USD" "JPY" (LocalDate/of 2014 3 28))
```

### Rate Queries

```clojure
;; EUR-referenced rate for a currency on a date
(fx/get-rate c "USD" (LocalDate/of 2014 3 28))
;; => 1.3759

;; Implied cross rate (not through EUR amounts, just the ratio)
(fx/cross-rate c "USD" "GBP" (LocalDate/of 2014 3 28))
;; => 0.5999...

;; Full sorted date→rate history for a currency
(fx/rate-history c "USD")
;; => {#object[LocalDate "1999-01-04"] 1.1789, ...}
```

### Metadata

The converter is a plain map — inspect it directly:

```clojure
(:currencies c)   ;; => #{"EUR" "USD" "JPY" "GBP" ...}
(:bounds c)       ;; => {"USD" {:first-date #object[LocalDate "1999-01-04"]
                  ;;            :last-date  #object[LocalDate "2026-03-06"]} ...}
(:ref-currency c) ;; => "EUR"
```

### Constants

```clojure
fx/ecb-url
;; => "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip"
```

## Error Handling

All errors are `ex-info` with a `:type` key:

| `:type` | Cause |
|---------|-------|
| `:unknown-currency` | Currency code not in the dataset |
| `:rate-not-found` | No rate available for the requested date |

```clojure
(try
  (fx/convert c 100 "USD" "EUR" (LocalDate/of 2024 1 6)) ; Saturday — no ECB data
  (catch clojure.lang.ExceptionInfo e
    (ex-data e)))
;; => {:type :rate-not-found, :currency "USD", :date #object[LocalDate "2024-01-06"]}
```

Use `:fallback-on-wrong-date true` to clamp out-of-bounds dates to the nearest available boundary instead of throwing.

## Design Notes

**No interpolation.** The ECB publishes rates on business days only. When a date has no rate (weekends, holidays), ecbjure throws rather than silently inventing a number. Interpolating financial data introduces look-ahead bias in backtesting. If you need gap-filling, do it explicitly in your own pipeline where the assumption is visible.

**The converter is a map.** No defrecord, no mutable state. `make-converter` returns a plain Clojure map; `convert` is a pure function. The converter is inspectable, serializable, and composable.

**Minimal dependencies.** Core library depends only on `org.clojure/data.csv`. No HTTP client needed — the ECB ZIP is fetched via `java.net.URI/.openStream`.

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

## Roadmap

- **Tier 2:** Thin SDMX REST client for broader ECB data — interest rates (ECB key rates, EURIBOR, €STR), inflation (HICP), money supply (M1/M2/M3), yield curves, banking statistics.
- **Enhancements:** `tech.ml.dataset` output, `clj-yfinance` integration for live spot rates, date-based caching.

## License

Copyright © 2026 clojure-finance

Released under the [Eclipse Public License 2.0](LICENSE).
