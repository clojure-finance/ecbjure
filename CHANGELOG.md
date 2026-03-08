# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-03-08

### Added
- `clojure-finance.ecbjure.fx/make-converter` — load ECB historical FX data from bundled resource, local file, or URL
- `clojure-finance.ecbjure.fx/make-converter-from-lines` — build converter from a seq of CSV lines
- `clojure-finance.ecbjure.fx/convert` — triangulated currency conversion through EUR
- `clojure-finance.ecbjure.fx/get-rate` — EUR-referenced rate lookup for a currency and date
- `clojure-finance.ecbjure.fx/rate-history` — full sorted date→rate history for a currency
- `clojure-finance.ecbjure.fx/cross-rate` — implied cross rate between two non-EUR currencies
- `:fallback-on-wrong-date` option to clamp out-of-bounds dates to first/last available
- `:cast-fn` option for exact arithmetic via `bigdec`
- Bundled `eurofxref-hist.zip` for offline use
- CLI entry point via `:cli` alias
