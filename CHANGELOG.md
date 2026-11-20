# Changelog

All notable changes to MIRA are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the
project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- Tablet layout: on devices whose smallest width is 600dp or more, the history
  becomes a permanently visible sidebar with the chat inset beside it. The
  hamburger and the drawer close button disappear, the scrim is dropped, and
  back no longer tries to close the panel. Phones keep the existing overlay
  drawer.
- Dynamic color (Material You) toggle under Settings > Preferences, shown on
  Android 12+. When enabled, each activity re-inflates with the wallpaper-derived
  palette; the chat screen picks the change up on return without a restart. The
  preference defaults to off so the purple brand stays the default look.
- Skeleton placeholders replace the spinner while chat history and the drawer
  load: message-shaped bars and row-shaped bars pulse gently, then disappear on
  success or error.
- About screen (Settings > About MIRA) with the app icon, version, build type,
  backend URL and a plain-language privacy statement covering what memory holds,
  what it never extracts, and how to purge it.
- History drawer upgrades: a search field filters the loaded conversations by
  title locally (no extra requests), a "No chats match your search" state, and
  swipe-right on a row asks to delete it with a red band and trash icon. Chats
  updated within the hour read "Updated just now" or "Updated N min ago"; older
  ones keep the absolute date. Left-swipe is deliberately not offered because
  the drawer uses that gesture to close.
- The chat composer now shows a "Style memory on" chip whenever communication
  memory is enabled, tappable to open the memory screen. It reads "Memory on"
  when the profile exists but has no preferences yet, disappears when
  personalization is off, and refreshes when returning from the memory screen.
- Rich answers: headings scale up and bold, bullets hang-indent, inline code is
  monospaced on a tinted background, and fenced code renders as full-width
  monospaced bands inside the same bubble. Nested emphasis (code inside bold)
  renders too. The typewriter reveal animates the styled text.
- The chat toolbar now carries the conversation title, an inline AI-effort chip
  and a new-chat action; the empty state shows horizontal prompt cards and a
  gradient greeting.

### Changed

- The backend system prompt now asks for light Markdown (headings, bold, lists,
  fenced code) instead of plain text, so answers can use the richer renderer.
- Regenerate: any completed answer can be regenerated from the chat footer
  (`POST /api/v1/conversations/{id}/responses/{id}/regenerate`). The replacement
  is written in place so a thread keeps one answer per prompt, the new text types
  itself in, and a provider failure keeps the previous answer and tells the user.
  Regeneration is owner-scoped, rate limited, idempotent, and shares the bounded
  3-attempt budget with send/retry.
- Assistant messages returned by the history endpoint now carry the same
  `response` metadata as user messages, so regeneration is offered for answers
  loaded from history too.
- Assistant answers type themselves in (character-by-character reveal, capped at
  ~1.4 s) the first time they are shown; the animation updates only the text
  view, so list diffing is untouched, and it never replays for history.
- The purple glow behind the conversation now breathes while an answer is
  generating (and on the empty state) instead of appearing only when idle.
- Prompt feedback now precedes the network: in a new chat the prompt pill and
  thinking row appear before the conversation is created, so a slow create call
  no longer leaves the screen looking unresponsive.
- Chat-first navigation: signing in lands on a conversation, history lives in a
  left navigation drawer (recent chats with updated timestamps, long-press to
  delete, "No chats yet" empty state), and preferences moved into a Settings hub
  holding Communication memory, Appearance, and sign-out.
- New chats create their conversation lazily from the first prompt, so opening
  the app never leaves empty threads in history.
- Gemini-style chat presentation: AI answers render as editorial text on the
  bare surface with a gradient spark, user prompts sit in a soft pill, and each
  answer carries copy / share / timestamp actions. A pulsing spark replaces the
  mechanical spinner, and prompts appear optimistically while waiting.
- Empty chat state with the spark, a soft purple glow, and starter prompts as
  icon rows that send with one tap.
- Assistant replies render as readable plain text (`MarkdownText`): emphasis,
  heading, inline-code and display-math markers are stripped, bullets normalised,
  links turned into `text (url)`, and arithmetic or identifiers left untouched
  (12 unit tests).
- "AI effort" selector above the prompt bar (Low / Medium / High). Presentational
  only: the choice is remembered locally and does not change how the backend
  generates answers.
- Appearance setting (System / Light / Dark), applied app-wide and restored on
  launch through `MiraApplication`.
- Message bubbles show a local timestamp and support long-press to copy.
- MIRA brand: adaptive launcher icon (gradient speech bubble with three memory
  nodes), branded splash background, tighter headline tracking, and a purple-led
  Material 3 palette mapped for light and dark.
- Instrumented test covering the thinking placeholder.

### Changed

- Cold-start resilience: the Android read timeout is now 90 s and the production
  health monitor allows 120 s per probe, because a sleeping instance took ~59 s
  to answer its first request and previously failed both budgets.
- The chat system prompt asks for plain mobile-friendly text with no LaTeX or
  dollar-delimited math, since the client renders plain text.
- Memory preferences use single-select chips instead of five dropdowns.

### Fixed

- Regeneration matched the user prompt's response metadata first, which
  replaced the prompt instead of the answer. The client now targets the
  assistant row only.
- Copy and share use the rendered text rather than raw Markdown.
- Chat follows to the newest message after the list diff commits, so a new reply
  is never left below the fold.
- Drawer insets are applied to a wrapper because `DrawerLayout` consumes window
  insets; back handling uses `OnBackPressedDispatcher` for predictive back.
- Account row in Settings no longer wraps the email onto a second line.
- Documentation corrections: Phase 3 status, refresh replay follow-up, Phase 3
  decision rows, purge semantics, `LICENSE` reference, NFR-005.1 layout note.
## [1.2.1] - 2026-11-14

### Fixed

- Regeneration matched the user prompt's response metadata first, which
  replaced the prompt instead of the answer. The client now targets the
  assistant row only.
- Release-only startup crash caused by R8 obfuscating Kotlin classes used by
  OkHttp. Added keep rules for `okhttp3`, `okio`, `kotlin`, and `kotlinx`.
- v1.2.0 release artifacts are affected by this crash; use v1.2.1 instead.

## [1.2.0] - 2026-11-14

### Added

- Tablet layout: on devices whose smallest width is 600dp or more, the history
  becomes a permanently visible sidebar with the chat inset beside it. The
  hamburger and the drawer close button disappear, the scrim is dropped, and
  back no longer tries to close the panel. Phones keep the existing overlay
  drawer.
- Dynamic color (Material You) toggle under Settings > Preferences, shown on
  Android 12+. When enabled, each activity re-inflates with the wallpaper-derived
  palette; the chat screen picks the change up on return without a restart. The
  preference defaults to off so the purple brand stays the default look.
- Skeleton placeholders replace the spinner while chat history and the drawer
  load: message-shaped bars and row-shaped bars pulse gently, then disappear on
  success or error.
- About screen (Settings > About MIRA) with the app icon, version, build type,
  backend URL and a plain-language privacy statement covering what memory holds,
  what it never extracts, and how to purge it.
- History drawer upgrades: a search field filters the loaded conversations by
  title locally (no extra requests), a "No chats match your search" state, and
  swipe-right on a row asks to delete it with a red band and trash icon. Chats
  updated within the hour read "Updated just now" or "Updated N min ago"; older
  ones keep the absolute date. Left-swipe is deliberately not offered because
  the drawer uses that gesture to close.
- The chat composer now shows a "Style memory on" chip whenever communication
  memory is enabled, tappable to open the memory screen. It reads "Memory on"
  when the profile exists but has no preferences yet, disappears when
  personalization is off, and refreshes when returning from the memory screen.
- Rich answers: headings scale up and bold, bullets hang-indent, inline code is
  monospaced on a tinted background, and fenced code renders as full-width
  monospaced bands inside the same bubble. Nested emphasis (code inside bold)
  renders too. The typewriter reveal animates the styled text.
- The chat toolbar now carries the conversation title, an inline AI-effort chip
  and a new-chat action; the empty state shows horizontal prompt cards and a
  gradient greeting.

### Changed

- The backend system prompt now asks for light Markdown (headings, bold, lists,
  fenced code) instead of plain text, so answers can use the richer renderer.
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

- Regeneration matched the user prompt's response metadata first, which
  replaced the prompt instead of the answer. The client now targets the
  assistant row only.
- Android CI SDK installation (`setup-android` v4 and `platforms;android-37.0`).
- Machine-specific Gradle JDK path removed from `android/gradle.properties`.
- Production port binding honors Render's `PORT` instead of a hardcoded
  `SERVER_PORT`.
- Unexpected-exception logs no longer include raw exception messages or causes.

## [1.1.1] - 2026-11-14

### Added

- Tablet layout: on devices whose smallest width is 600dp or more, the history
  becomes a permanently visible sidebar with the chat inset beside it. The
  hamburger and the drawer close button disappear, the scrim is dropped, and
  back no longer tries to close the panel. Phones keep the existing overlay
  drawer.
- Dynamic color (Material You) toggle under Settings > Preferences, shown on
  Android 12+. When enabled, each activity re-inflates with the wallpaper-derived
  palette; the chat screen picks the change up on return without a restart. The
  preference defaults to off so the purple brand stays the default look.
- Skeleton placeholders replace the spinner while chat history and the drawer
  load: message-shaped bars and row-shaped bars pulse gently, then disappear on
  success or error.
- About screen (Settings > About MIRA) with the app icon, version, build type,
  backend URL and a plain-language privacy statement covering what memory holds,
  what it never extracts, and how to purge it.
- History drawer upgrades: a search field filters the loaded conversations by
  title locally (no extra requests), a "No chats match your search" state, and
  swipe-right on a row asks to delete it with a red band and trash icon. Chats
  updated within the hour read "Updated just now" or "Updated N min ago"; older
  ones keep the absolute date. Left-swipe is deliberately not offered because
  the drawer uses that gesture to close.
- The chat composer now shows a "Style memory on" chip whenever communication
  memory is enabled, tappable to open the memory screen. It reads "Memory on"
  when the profile exists but has no preferences yet, disappears when
  personalization is off, and refreshes when returning from the memory screen.
- Rich answers: headings scale up and bold, bullets hang-indent, inline code is
  monospaced on a tinted background, and fenced code renders as full-width
  monospaced bands inside the same bubble. Nested emphasis (code inside bold)
  renders too. The typewriter reveal animates the styled text.
- The chat toolbar now carries the conversation title, an inline AI-effort chip
  and a new-chat action; the empty state shows horizontal prompt cards and a
  gradient greeting.

### Changed

- The backend system prompt now asks for light Markdown (headings, bold, lists,
  fenced code) instead of plain text, so answers can use the richer renderer.
- Production container image and Render blueprint.
- Signed Android release workflow and release keystore configuration.
- Local container verification against PostgreSQL.

## [1.1.0] - 2026-11-14

### Added

- Tablet layout: on devices whose smallest width is 600dp or more, the history
  becomes a permanently visible sidebar with the chat inset beside it. The
  hamburger and the drawer close button disappear, the scrim is dropped, and
  back no longer tries to close the panel. Phones keep the existing overlay
  drawer.
- Dynamic color (Material You) toggle under Settings > Preferences, shown on
  Android 12+. When enabled, each activity re-inflates with the wallpaper-derived
  palette; the chat screen picks the change up on return without a restart. The
  preference defaults to off so the purple brand stays the default look.
- Skeleton placeholders replace the spinner while chat history and the drawer
  load: message-shaped bars and row-shaped bars pulse gently, then disappear on
  success or error.
- About screen (Settings > About MIRA) with the app icon, version, build type,
  backend URL and a plain-language privacy statement covering what memory holds,
  what it never extracts, and how to purge it.
- History drawer upgrades: a search field filters the loaded conversations by
  title locally (no extra requests), a "No chats match your search" state, and
  swipe-right on a row asks to delete it with a red band and trash icon. Chats
  updated within the hour read "Updated just now" or "Updated N min ago"; older
  ones keep the absolute date. Left-swipe is deliberately not offered because
  the drawer uses that gesture to close.
- The chat composer now shows a "Style memory on" chip whenever communication
  memory is enabled, tappable to open the memory screen. It reads "Memory on"
  when the profile exists but has no preferences yet, disappears when
  personalization is off, and refreshes when returning from the memory screen.
- Rich answers: headings scale up and bold, bullets hang-indent, inline code is
  monospaced on a tinted background, and fenced code renders as full-width
  monospaced bands inside the same bubble. Nested emphasis (code inside bold)
  renders too. The typewriter reveal animates the styled text.
- The chat toolbar now carries the conversation title, an inline AI-effort chip
  and a new-chat action; the empty state shows horizontal prompt cards and a
  gradient greeting.

### Changed

- The backend system prompt now asks for light Markdown (headings, bold, lists,
  fenced code) instead of plain text, so answers can use the richer renderer.
- Android communication-style memory management and candidate review
  (`MemoryActivity`).
- Release signing configuration and tag-based signed artifacts.

[Unreleased]: https://github.com/Bryan1805a/MIRA/compare/v1.2.1...HEAD
[1.2.1]: https://github.com/Bryan1805a/MIRA/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/Bryan1805a/MIRA/compare/v1.1.1...v1.2.0
[1.1.1]: https://github.com/Bryan1805a/MIRA/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/Bryan1805a/MIRA/releases/tag/v1.1.0
