# Changelog

All notable changes to MIRA are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [1.2.1] - 2026-11-14

### Fixed

- Release-only startup crash caused by R8 obfuscating Kotlin classes used by
  OkHttp. Added keep rules for `okhttp3`, `okio`, `kotlin`, and `kotlinx`.
- v1.2.0 release artifacts are affected by this crash; use v1.2.1 instead.

## [1.2.0] - 2026-11-14

### Added

- Phase 3 security hardening: authentication rate limiting for login,
  registration, and refresh, with per-email and per-IP windows.
- Account-wide refresh-token replay revocation.
- Log-sanitization audit and automated tests.
- Micrometer counters for chat outcomes, provider errors, extraction jobs,
  auth throttling, memory purges, and candidate review.
- Authenticated `/actuator/metrics` exposure with HTTP latency percentiles.
- Production `Dockerfile`, `.dockerignore`, and `prod` Spring profile with
  ECS JSON logging, graceful shutdown, and Hikari settings.
- Render `render.yaml` blueprint and ADR-004 hosting decision.
- Android release signing, R8 rules, configurable API URL/versioning, and a
  tag-triggered signed APK/AAB workflow (ADR-005).
- Android unit tests for token storage, auth interceptor, and error mapping.
- Instrumented login → conversation → send API smoke test.
- Shared `ApiIntegrationTestSupport` with one Testcontainers PostgreSQL
  container for the whole backend suite.
- Domain-split backend tests: persistence, auth, API contract, conversations,
  messaging, memory lifecycle, and latency smoke.
- NFR-004 latency smoke test for local read endpoints (p95 < 200 ms).

### Changed

- Backend test suite reorganized by domain; the monolithic
  `MiraBackendApplicationTests` class was removed.
- Android CI now builds the release variant and compiles instrumented tests.
- `.env.example` uses placeholder values instead of local defaults.

### Fixed

- Android CI SDK installation (`setup-android` v4 and `platforms;android-37.0`).
- Machine-specific Gradle JDK path removed from `android/gradle.properties`.
- Production port binding honors Render's `PORT` instead of a hardcoded
  `SERVER_PORT`.
- Unexpected-exception logs no longer include raw exception messages or causes.

## [1.1.1] - 2026-11-14

### Added

- Production container image and Render blueprint.
- Signed Android release workflow and release keystore configuration.
- Local container verification against PostgreSQL.

## [1.1.0] - 2026-11-14

### Added

- Android communication-style memory management and candidate review
  (`MemoryActivity`).
- Release signing configuration and tag-based signed artifacts.

[Unreleased]: https://github.com/Bryan1805a/MIRA/compare/v1.2.1...HEAD
[1.2.1]: https://github.com/Bryan1805a/MIRA/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/Bryan1805a/MIRA/compare/v1.1.1...v1.2.0
[1.1.1]: https://github.com/Bryan1805a/MIRA/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/Bryan1805a/MIRA/releases/tag/v1.1.0
