# Changelog

All notable changes to MIRA are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Chat renders an in-flight "MIRA is thinking…" assistant bubble while the
  backend waits on the AI provider, so a synchronous generation that can take
  up to 30 seconds no longer looks like a frozen screen. The placeholder is
  local to the UI: it is never persisted or sent to the backend, and the input
  is disabled until the request completes or fails.
- Instrumented test covering the thinking placeholder (detection, layout
  inflation, and adapter view-type dispatch).

### Added

- Message bubbles show a local timestamp and support long-press to copy the
  original message text (with haptic feedback and a confirmation toast).
- Home screen rebuilt as a branded hero card, a session card ("Signed in as"
  plus status), a primary conversation action, a Memory/Sign-out row, and a
  short "How memory works" explainer instead of three stacked buttons.
- Assistant replies are rendered as readable plain text (`MarkdownText`): bold,
  italic, heading, inline-code, and display-math markers are stripped, list
  bullets normalized, links turned into `text (url)`, and arithmetic such as
  `2 * 3` or identifiers such as `snake_case` left untouched (12 unit tests).
- Chat now follows to the newest message after the list diff commits, so a
  freshly sent message or a long reply is no longer left below the fold.
- Home screen shows a real tagline instead of the stale "Conversations and chat
  arrive in the next checkpoint." placeholder.

### Changed

- Chat adopts a Gemini-style "container-light" presentation: AI answers render
  as editorial text on the bare surface with a gradient spark instead of a
  bubble, user prompts sit in a soft pill, and each answer carries a
  copy / share / timestamp footer.
- Empty threads show a "Hello, [name]" greeting with starter-prompt cards that
  send with one tap; the prompt bar is now a floating rounded pill with a
  circular send action.
- The prompt appears immediately as an optimistic pill, so a slow provider no
  longer looks like the message was swallowed; it is replaced by the server
  message on success and removed on failure.
- "Thinking" state is a pulsing gradient spark plus label rather than a
  mechanical spinner.
- Palette moved to neutral surfaces (white / soft blue-grey in light, deep
  charcoal in dark) with the blue-violet-coral gradient reserved for brand
  moments; the launcher icon uses the same gradient.
- Cold-start resilience: the Android HTTP client read timeout is now 90 s and
  the production health monitor allows 120 s per probe. A sleeping managed
  instance took ~59 s to answer its first request, which previously exceeded
  both budgets and turned a cold sign-in into a failure.
- Brand identity: replaced the stock Android robot launcher icon with a MIRA
  adaptive icon (violet-to-indigo gradient with a speech bubble holding three
  memory nodes, monochrome-safe for themed icons), and branded the Android 12+
  splash background.
- Replaced the default Material purple with a MIRA palette (violet primary, teal
  secondary, amber tertiary) mapped across every Material 3 colour role, with
  light and dark values plus matching system-bar icon polarity.
- Tightened headline/title tracking for a deliberate wordmark; set the window
  background so the launch transition no longer flashes a default surface.
- The chat system prompt asks the model for plain mobile-friendly text and no
  LaTeX or dollar-delimited math, since the client renders plain text.

### Fixed

- Removed a duplicate `Intent` import in `MainActivity`.
- Documentation corrections: Phase 3 security hardening is no longer listed as
  "in progress" in the architecture status table; refresh-token rotation and
  replay tests are no longer described as an open Phase 1 follow-up; the Phase 3
  decision rows are closed against ADR-004/ADR-005; purge is documented as
  clearing the profile JSONB and incrementing `version` rather than deleting
  the `user_style_profile` row; `LICENSE` was removed from the Phase 0 target
  tree; NFR-005.1 records the delivered layer-first package layout.

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
