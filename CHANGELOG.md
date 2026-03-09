# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `clojure-finance.ecbjure.dataset/rates-wide` — TMD dataset in wide format (rows = dates, columns = currency codes)
- `clojure-finance.ecbjure.dataset/rates-long` — TMD dataset in tidy/long format (columns: `:date`, `:currency`, `:rate`)
- `:dataset` alias in `deps.edn` for `techascent/tech.ml.dataset 7.066` — optional, not a core dependency

## [0.1.1] - 2026-03-08

### Fixed
- POM license `<name>` tag was emitted as `<n>` due to tools.build keyword collision; fixed by using string key `"name"` in pom-data

## [0.1.0] - 2026-03-08

### Added
- `clojure-finance.ecbjure.fx/make-converter` — load ECB historical FX data from URL or local file
- `clojure-finance.ecbjure.fx/make-converter-from-lines` — build converter from a seq of CSV lines
- `clojure-finance.ecbjure.fx/convert` — triangulated currency conversion through EUR
- `clojure-finance.ecbjure.fx/get-rate` — EUR-referenced rate lookup for a currency and date
- `clojure-finance.ecbjure.fx/rate-history` — full sorted date→rate history for a currency
- `clojure-finance.ecbjure.fx/cross-rate` — implied cross rate between two non-EUR currencies
- `:fallback-on-wrong-date` option to clamp out-of-bounds dates to first/last available
- `:cast-fn` option for exact arithmetic via `bigdec`
- CLI entry point via `:cli` alias (`--to`, `--date`, `--source`, `-v`)
- Always fetches live data from ECB — no stale bundled rates
