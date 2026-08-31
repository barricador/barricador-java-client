# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/), and this project adheres to
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.3.0] - 2026-08-30
### Changed
- **BREAKING (behavioral): flag synchronization now polls by default.** `streamingEnabled` defaults
  to `false`; the ruleset is refreshed every `pollInterval` (new, default 30s) with a conditional
  `If-None-Match` request, so an unchanged ruleset returns `304 Not Modified`. Holding an SSE stream
  open bills backend instance time for the entire connection, which made an idle SDK cost as much as
  a busy one. Restore the previous behavior with `.streamingEnabled(true)`.
- Propagation latency in the default mode is now up to one `pollInterval`. Use streaming for
  kill-switch flags where that delay is unacceptable.

### Added
- `pollInterval(Duration)` builder option.

## [0.2.0] - 2026-06-25
### Changed
- Rebrand to barricador; coordinates are now io.github.barricador:barricador-java-client.

## [0.1.1] - 2026-06-22
### Changed
- Default base URL is now `https://app.barricador.com` (was `app.barricador.io`).

## [0.1.0] - 2026-06-21
### Added
- Initial public release.
