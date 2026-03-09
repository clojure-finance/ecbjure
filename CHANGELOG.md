# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `clojure-finance.ecbjure.sdmx/list-dataflows` — fetches all ~100 available ECB SDMX dataflows and returns a sorted map of `id → description`; parses SDMX 2.1 XML via JDK DOM (`javax.xml.parsers`) — no new dependencies
- `list-dataflows-parse` test in `sdmx_test.clj` — fully offline, 3 assertions using an inline XML fixture; SDMX test suite now 6 tests, 26 assertions

### Changed
- `:fallback-on-wrong-date` (boolean) replaced by `:fallback` keyword — accepts `false` (default, throw on out-of-bounds), `:nearest`, `:before`, `:after`; `true` is a backward-compat alias for `:nearest`
- `fx_test.clj` — updated fallback tests to cover all three modes; error-message expectations corrected to `"Date outside currency bounds"` for out-of-bounds dates

## [0.1.2] - 2026-03-09

### Added
- `clojure-finance.ecbjure.sdmx/get-series` — fetch any ECB SDMX series via the REST API; returns a vector of observation maps with `:time-period` (LocalDate) and `:obs-value` (double), plus all CSV dimension columns
- `clojure-finance.ecbjure.sdmx/parse-sdmx-csv` — parse SDMX CSV lines (format=csvdata) into observation maps; handles daily, monthly, and annual TIME_PERIOD formats
- Predefined series-key constants: `exr-daily`, `exr-monthly`, `euribor-1w`, `euribor-1m`, `euribor-3m`, `euribor-6m`, `euribor-1y`, `euribor-overnight`, `estr-daily`, `hicp-euro-area`
- `sdmx_test.clj` — 4 tests, 19 assertions; fully offline using in-memory fixture data
- `clojure-finance.ecbjure.dataset/rates-wide` — TMD dataset in wide format (rows = dates, columns = currency codes)
- `clojure-finance.ecbjure.dataset/rates-long` — TMD dataset in tidy/long format (columns: `:date`, `:currency`, `:rate`)
- `:dataset` alias in `deps.edn` for `techascent/tech.ml.dataset 7.066` — optional, not a core dependency
- `:test-dataset` alias in `deps.edn` — kaocha + tech.ml.dataset for running dataset tests
- `dataset_test.clj` — 2 tests, 17 assertions covering shape, values, column names, EUR rows, and sort order

### Changed
- `README.md` — added SDMX Client section and dataset output examples; install coordinate bumped to `0.1.2`
- Removed date-based caching from roadmap (local file path already covers this use case)

## [0.1.1] - 2026-03-08

### Fixed
- POM license `<n>` tag was emitted as `<n>` due to tools.build keyword collision; fixed by using string key `"name"` in pom-data

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
