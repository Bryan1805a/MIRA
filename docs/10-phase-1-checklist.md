# MIRA — Phase 1: Conversation & Chat Core Checklist

| Attribute | Value |
| :--- | :--- |
| **Document ID** | MIRA-CHK-001 |
| **Classification** | Internal Engineering Specification |
| **Status** | Active Execution Baseline |
| **Version** | 1.9.0 |
| **Last Updated** | 2026-10-29 |
| **Author / Lead** | Bryan / MIRA Core Engineering Team |
| **Phase Milestone** | Phase 1 — End-to-End Text Chat |

---

## Document Revision History

| Version | Date | Author | Description of Changes |
| :--- | :--- | :--- | :--- |
| 1.0.0 | 2026-10-21 | Bryan | Initial Phase 1 baseline: conversation API, message orchestration, provider abstraction, Android client, hardening, CI, and exit gate. |
| 1.1.0 | 2026-10-23 | Bryan | Reconciled completed backend deliverables: conversation endpoints, message orchestration, response lifecycle, idempotency checking, bounded retries, MockAIProvider, request IDs, and enriched error envelope (52 passing tests). |
| 1.2.0 | 2026-10-24 | Bryan | Reconciled completed Android client authentication layer (LoginActivity, RegisterActivity, MainActivity session gate & logout, TokenStore EncryptedSharedPreferences, auto-refresh TokenAuthenticator, ApiClient) and DeepSeekAIProvider backend implementation (58 passing tests). |
| 1.3.0 | 2026-10-25 | Bryan | Reconciled completed Android conversation management and chat UI (`ConversationsActivity`, `ChatActivity`), backend in-memory message rate limiting (`MessageRateLimiter`, 10 req/min per user), Actuator health probes, and Docker Compose local database environment (63 passing backend tests). |
| 1.4.0 | 2026-10-25 | Bryan | Configured GitHub Actions CI workflows for backend (`backend.yml`) and Android (`android.yml`), and resolved Android lint issues (property escaping, DiffUtil equals). |
| 1.5.0 | 2026-10-26 | Bryan | Closed Phase 1: exit gate verified (68 backend tests), merged to `main` via PR #5, and listed the remaining follow-ups carried into Phase 2. |
| 1.6.0 | 2026-10-27 | Bryan | Recorded the current suite at 72 tests after Phase 2 injection coverage; Phase 1 closed with 68. |
| 1.7.0 | 2026-10-27 | Bryan | Recorded the current suite at 79 tests after Phase 2 extraction coverage; Phase 1 closed with 68. |
| 1.8.0 | 2026-10-27 | Bryan | Recorded the current suite at 83 tests after Phase 2 candidate-review coverage; Phase 1 closed with 68. |
| 1.9.0 | 2026-10-29 | Bryan | Recorded the current suite at 89 tests after Phase 2 retry/recovery and race-suite coverage; Phase 1 closed with 68. |

---

## 1. Purpose & Objectives

Phase 1 delivers the first complete vertical slice of MIRA: a signed-in user can manage conversations and exchange text messages with the assistant from the Android app, with every message and response persisted safely.

```mermaid
flowchart LR
    subgraph Client ["Android Client (Phase 1)"]
        AuthUI["Login / Register"]
        ConvoUI["Conversation List"]
        ChatUI["Chat + Retry"]
    end

    subgraph Backend ["Spring Boot Backend"]
        Auth["Auth Module\n(implemented)"]
        Chat["Chat Orchestration\n(Phase 1)"]
        Provider["AIProvider\n(Phase 1)"]
    end

    DB[("PostgreSQL")]
    DeepSeek["DeepSeek API"]

    AuthUI --> Auth
    ConvoUI --> Chat
    ChatUI --> Chat
    Auth --> DB
    Chat --> DB
    Chat --> Provider
    Provider -->|Mock in tests| DeepSeek
```

Phase 1 objectives:

1. Complete the conversation HTTP API on top of the existing entity and repository.
2. Implement message persistence, response lifecycle, idempotent sends, and bounded retries.
3. Introduce the `AIProvider` abstraction with a DeepSeek client and a deterministic mock.
4. Deliver a usable Android authentication and chat experience with secure token storage.
5. Harden the API contract with request IDs, a complete error envelope, and rate limiting.
6. Establish CI for both projects.

> [!IMPORTANT]
> **Memory is intentionally out of scope for Phase 1.** Phase 1 prompt assembly uses conversation history only. Phase 2 delivered the backend governance surface, advisory prompt injection, the asynchronous extraction pipeline, and candidate review, and remains responsible for the Android memory UI, as specified in [05-memory-lifecycle.md](./05-memory-lifecycle.md).

---

## 2. Scope

### 2.1 In Scope

- Conversation list, create, and delete endpoints with strict ownership checks and cursor pagination.
- Message history retrieval with cursor pagination.
- Synchronous message send with persisted user message and logical AI response.
- Response lifecycle: `PENDING`, `COMPLETED`, `FAILED`, `UNKNOWN`.
- Idempotency-key enforcement, request hashing, and conflict detection.
- Bounded retry endpoint with per-response attempt tracking.
- Provider abstraction, DeepSeek HTTP client, and `MockAIProvider`.
- Per-account rate limiting and request-size limits.
- Request IDs and the enriched error envelope.
- Android login/register, encrypted token storage, conversation list, chat screen, and retry UI.
- GitHub Actions CI for backend and Android.
- Documentation updates for any new migrations or contract changes.

### 2.2 Out of Scope (Deferred)

| Capability | Deferred To |
| :--- | :--- |
| Style-memory governance endpoints (`GET/PUT/PATCH/DELETE /memory/style`) | Phase 2 |
| Memory extraction, hybrid policy engine, OCC/generation commits | Phase 2 |
| Memory injection into prompt assembly | Phase 2 |
| Android memory settings UI | Phase 2 |
| Multimodal PDF/image ingestion | Post-MVP |
| Conversation sharing, multi-user collaboration, push notifications | Post-MVP |
| Production deployment, TLS termination, managed secrets, release signing | Phase 3 |
| Global revocation of all sessions on refresh-token replay | Phase 1 hardening decision (see 4.1) |

---

## 3. Baseline Before Phase 1

| Capability | Current State |
| :--- | :--- |
| Registration, login, refresh, logout, `/me` | Implemented and tested (Backend & Android) |
| JWT resource-server security and JSON 401/403 responses | Implemented |
| Refresh-token rotation and replay rejection | Implemented (session-scoped revocation; auto-refresh on Android client) |
| `conversation` table, entity, ownership query, cursor query | Implemented |
| Conversation API (CRUD, cursor pagination) | Implemented (Backend & Android `ConversationsActivity`) |
| Messaging API, response lifecycle, retries, idempotency | Implemented (Backend & Android `ChatActivity`) |
| `AIProvider` abstraction, `MockAIProvider`, and `DeepSeekAIProvider` | Implemented (Backend) |
| Per-account message rate limiting | Implemented (Backend in-memory sliding window, 10 req/min per user) |
| Request-ID propagation and enriched error envelope | Implemented (Backend & Android) |
| Schema `V1`–`V9` at closure (V10 added in Phase 2) | Implemented |
| Backend test suite | 89 passing tests in local Testcontainers and unit suites (68 at Phase 1 closure) |
| Android application | Full UI delivered (Login, Register, session gate, encrypted token store, auto-refresh authenticator, Conversations list, Chat with retry; compiles via `assembleDebug`, passes `testDebugUnitTest`, lint-clean) |
| CI workflows | Configured (GitHub Actions for backend verification and Android build/test/lint) |
| Android debug APK verification | Verified (build passes cleanly) |

### 3.1 Phase 0 Carry-Over

- [x] Run `.\gradlew.bat assembleDebug` in `android/` and confirm a debug APK is produced.
- [ ] Install the APK on an emulator or physical device and confirm the scaffold launches.
- [ ] Record the Android SDK/build-tools versions used for the verified build.

---

## 4. Delivery Workstreams

### 4.1 Platform & Contract Hardening

- [x] Add a request-ID filter/servlet component (`RequestIdFilter`):
  - [x] Read `X-Request-Id` if supplied and well-formed, otherwise generate a UUID.
  - [x] Put the value in the logging MDC and the response header.
  - [x] Include it in every error body as `requestId`.
- [x] Extend `ApiErrorResponse` to `{ error, message, requestId, details? }` without breaking `error`/`message`.
- [x] Add a catch-all exception handler returning `500 internal_error` with a sanitized message.
- [x] Populate `details` for bean-validation failures (`field`, `reason`); avoid leaking class names or SQL.
- [x] Enforce request limits:
  - [x] Message content: non-blank, max 4,000 characters.
  - [x] Conversation title: max 200 characters, trimmed; blank becomes `null`.
  - [x] List limits: conversations default 20/max 50, messages default 50/max 100.
- [x] Implement per-account rate limiting for message sends (target: 10 requests/minute) returning `429 rate_limited`:
  - [x] Choose a single-instance in-memory token bucket for the MVP and document the limitation (`MessageRateLimiter`).
  - [x] Do not rate-limit health checks or authentication by the same bucket; use separate policies if auth throttling is added.
- [x] Enable Actuator liveness/readiness health groups and restrict exposed endpoints to health only.
- [ ] Sanitize logs: no passwords, tokens, prompts, assistant content, or provider payloads.
- [ ] Decide the refresh-token replay policy:
  - [ ] Implement account-wide session revocation on replay, **or**
  - [ ] Record the accepted risk and keep session-scoped revocation, updating [06-security-and-privacy.md](./06-security-and-privacy.md) accordingly.

### 4.2 Conversation API

- [x] Add DTOs:
  - `CreateConversationRequest(String title)`
  - `ConversationResponse(UUID id, String title, Instant createdAt, Instant updatedAt)`
  - `ConversationPageResponse(List<ConversationResponse> items, String nextCursor)`
- [x] Add `ConversationService`:
  - [x] `create(userId, title)`.
  - [x] `list(userId, limit, cursor)` ordered by `updatedAt DESC, id DESC`.
  - [x] `delete(userId, conversationId)` with ownership check.
- [x] Encode the cursor as an opaque token containing `(updatedAt, id)`; reject malformed cursors with `400 invalid_request`.
- [x] Add `ConversationController`:
  - [x] `GET /api/v1/conversations` → `200`.
  - [x] `POST /api/v1/conversations` → `201`.
  - [x] `DELETE /api/v1/conversations/{conversationId}` → `204`.
- [x] Return `404 resource_not_found` for unknown or unowned conversations, never `403`, to avoid IDOR enumeration.
- [x] Ensure conversation deletion cascades to messages and AI responses and does not touch memory state.
- [x] Tests:
  - [x] Create/list/delete happy paths.
  - [x] Newest-first ordering and cursor pagination across pages.
  - [x] Cross-account list isolation and delete attempts return `404`.
  - [x] Invalid limit/cursor returns `400`.
  - [x] Unauthenticated access returns `401`.

### 4.3 Messaging & Response Lifecycle

- [x] Add JPA entities and enums:
  - [x] `Message` with `MessageRole { USER, ASSISTANT }`.
  - [x] `AiResponse` with `AiResponseStatus { PENDING, COMPLETED, FAILED, UNKNOWN }`.
  - [x] `AiProcessingAttempt` with `AttemptOutcome { IN_PROGRESS, SUCCESS, TIMEOUT, ERROR }`.
- [x] Add repositories:
  - [x] Message history by conversation with deterministic cursor ordering.
  - [x] Idempotency lookup by `(userId, operation, idempotencyKey)`.
  - [x] Attempt lookup/next-number with a lock safe for concurrent retries.
- [x] Implement `sendMessage(userId, conversationId, content, idempotencyKey)`:
  - [x] Validate ownership and content before writing.
  - [x] Require the `Idempotency-Key` header; missing key returns `400 invalid_request`.
  - [x] Compute a canonical `request_hash` (for example SHA-256 over conversation ID + normalized content).
  - [x] In one transaction: persist the user message, create the logical `PENDING` response, and create attempt `IN_PROGRESS`.
  - [x] On duplicate key with the same hash: return the existing response/status without creating another message or provider call.
  - [x] On duplicate key with a different hash: return `409 idempotency_conflict`.
- [x] Assemble provider context:
  - [x] System instruction establishing the assistant role and current-instruction dominance.
  - [x] Last 10 messages in chronological order (bounded by a character/token budget).
  - [x] No style-memory injection in Phase 1.
- [x] Call the provider outside the persistence transaction; write the outcome in a new transaction.
- [x] Transition the response:
  - [x] Success → persist assistant message, link `assistant_message_id`, set `COMPLETED`, attempt `SUCCESS`.
  - [x] Terminal provider error → `FAILED`, attempt `ERROR` with `error_category`.
  - [x] Timeout/ambiguous network failure → `UNKNOWN`, attempt `TIMEOUT`; keep the user message.
- [x] Implement `GET /api/v1/conversations/{conversationId}/messages` with cursor pagination.
  - [x] Include a `response` object (`id`, `status`, `attemptCount`) on user messages so the client can render and retry `FAILED`/`UNKNOWN` responses that have no assistant message row.
- [x] Implement retry:
  - [x] `POST /api/v1/conversations/{conversationId}/responses/{responseId}/retry`.
  - [x] Only `FAILED` or `UNKNOWN` responses are retryable; otherwise `409` or `400` with a stable code.
  - [x] Maximum 3 total attempts per logical response (configurable, never unbounded).
  - [x] Add a new `ai_processing_attempt`; never duplicate the user message or response row.
  - [x] Reuse the original user prompt for regeneration.
  - [x] Apply idempotency to retry under `operation = RETRY`; the same key for the same response returns the existing result, and reuse for a different response returns `409 idempotency_conflict`.
- [x] Tests:
  - [x] Successful send returns user message + completed assistant response.
  - [x] Timeout maps to `UNKNOWN` and preserves the user message.
  - [x] Provider terminal error maps to `FAILED`.
  - [x] Same key + same payload returns the existing result without a second dispatch.
  - [x] Same key + different payload returns `409 idempotency_conflict`.
  - [x] Retry succeeds and increments attempt count; retry limit is enforced.
  - [x] Cross-account send/list/retry returns `404`.
  - [x] Empty and oversized content return `400`.

### 4.4 AI Provider Abstraction

- [x] Define `com.mira.mira_backend.ai.AIProvider` with request/response records and a normalized `AIProviderException` hierarchy (timeout, rate limited, unavailable, invalid output).
- [x] Implement `DeepSeekAIProvider`:
  - [x] Use Spring's HTTP client (`RestClient`) with connection timeout 5s and read timeout 30s.
  - [x] Read `DEEPSEEK_API_KEY`, base URL, and model from configuration; fail fast when the key is missing in non-test profiles.
  - [x] Map provider HTTP status and JSON errors to domain exceptions.
  - [x] Never log the API key, prompt, or raw completion body.
- [x] Implement `MockAIProvider` with configurable scenarios:
  - [x] Instant success.
  - [x] Timeout.
  - [x] HTTP 429 / rate limited.
  - [x] HTTP 5xx / unavailable.
  - [x] Malformed/empty output.
- [x] Add provider selection via Spring profiles/configuration so tests always use the mock.
- [x] Add HTTP-client tests (for example `MockRestServiceServer`) for status/error mapping; do not require network access.
- [x] Add a startup guard that prevents the real provider from being used in the `test` profile.

### 4.5 Android Client

- [x] Add dependencies:
  - [x] Retrofit + OkHttp + Gson (typed JSON client).
  - [x] `androidx.security:security-crypto` for `EncryptedSharedPreferences`.
  - [x] RecyclerView, Material Components, lifecycle components.
- [x] Build the network layer:
  - [x] Base URL from `BuildConfig`/resource configuration; no secrets in the APK.
  - [x] Bearer-token interceptor (`AuthInterceptor`).
  - [x] Single-flight `Authenticator` that refreshes on `401`, rotates the stored refresh token, and retries the original request once (`TokenAuthenticator`).
  - [x] Centralized error parsing for `{ error, message, requestId, details }` (`ApiError`).
- [x] Implement secure session storage:
  - [x] Store access token, refresh token, and expiry in `EncryptedSharedPreferences` (`TokenStore`).
  - [x] Clear tokens on logout and on unrecoverable refresh failure.
- [x] Implement UI:
  - [x] Login and registration screens with validation and inline errors (`LoginActivity`, `RegisterActivity`, and `MainActivity` session gate & logout).
  - [x] Conversation list with pagination, create dialog, and delete (`ConversationsActivity`, `ConversationAdapter`).
  - [x] Chat screen with message history, send box, in-flight indicator, and bounded retry for `FAILED`/`UNKNOWN` (`ChatActivity`, `MessageAdapter`).
  - [x] Generate a new client-side `Idempotency-Key` per logical send and reuse it for retries of the same send.
- [x] Security configuration:
  - [x] `android:usesCleartextTraffic="false"` and a network security config that permits only the configured HTTPS host (with debug overlay).
  - [x] Keep `MainActivity` exported as the launcher only; all other components unexported.
  - [x] Strip tokens, prompts, and completions from logs; guard debug logging with `BuildConfig.DEBUG`.
- [ ] Tests:
  - [ ] Unit tests for token storage, authorization interceptor, and error mapping.
  - [ ] Instrumented smoke test for login → conversation → send with a stubbed backend or mock server.
- [x] Verify `.\gradlew.bat assembleDebug` and install on a device/emulator.

### 4.6 CI & Documentation

- [x] Add `.github/workflows/backend.yml`:
  - [x] JDK 21, Maven cache, `.\mvnw`/`./mvnw verify`.
  - [x] Docker available for Testcontainers (GitHub-hosted runners provide it).
- [x] Add `.github/workflows/android.yml`:
  - [x] JDK 21, Android SDK setup, `assembleDebug`, `testDebugUnitTest`, lint.
- [x] Upload test reports and the debug APK as workflow artifacts.
- [x] Require both workflows for merges to the main branch.
- [x] Update [04-api-contract.md](./04-api-contract.md) examples to match the delivered implementation (including the enriched error envelope).
- [x] Add new Flyway migrations as `V9+` for any schema change; never edit an applied migration.
- [x] Keep [03-domain-model.md](./03-domain-model.md) synchronized with the final schema.
- [x] Update [README.md](../README.md) with Phase 1 status and run instructions.

---

## 5. Phase 1 Test Coverage Target

| Area | Required Verification |
| :--- | :--- |
| Conversation API | CRUD, ordering, cursor pagination, validation, cross-account isolation |
| Message send | Persistence before provider call, success, timeout, terminal failure |
| Idempotency | Same key/same payload replay, same key/different payload conflict, concurrent duplicate |
| Retry | Attempt counting, bound enforcement, no duplicate user message, allowed states only |
| Provider | Success, timeout, 429, 5xx, malformed output, timeout configuration |
| Authorization | Every endpoint scoped to the authenticated principal; unowned IDs return 404 |
| Error contract | Status codes, stable codes, `requestId`, validation details |
| Android | Token storage/refresh, interceptor behavior, API error parsing, smoke flow |
| Build | `mvnw clean verify`, `gradlew assembleDebug`, CI green |

Tests must run without internet access or paid DeepSeek calls. Testcontainers remains the only database dependency.

---

## 6. Requirements Traceability

| Requirement | Phase 1 Deliverable |
| :--- | :--- |
| **FR-003** Conversation Management | Conversation controller, service, DTOs, cursor pagination, cascade delete |
| **FR-004** Text Messaging & AI Orchestration | Message send, lifecycle states, context assembly, persistence |
| **FR-005** Idempotent Retries | Idempotency key/hash, replay behavior, attempt tracking, bounded retry endpoint |
| **FR-010** Data Isolation | Ownership checks on every conversation/message/response operation |
| **FR-011** Error Formatting | `requestId`, `details`, catch-all handler, stable error registry |
| **NFR-003** Fault Isolation | Provider timeout/error mapping; `UNKNOWN` semantics; transactional writes |
| **NFR-004** Performance & Latency | Bounded query limits, 5s/30s provider timeouts |
| **NFR-006** Testability | Mock provider, Testcontainers, deterministic suites, CI |

---

## 7. Risks & Open Decisions

1. **Synchronous provider latency:** Message send may hold an HTTP request for up to 30 seconds. This is acceptable for the MVP; an asynchronous job/polling model can replace it later without changing the persisted lifecycle.
2. **In-memory rate limiting:** A single-instance bucket resets on restart and does not coordinate across replicas. Revisit when the backend scales horizontally.
3. **Replay revocation scope:** Current behavior revokes only the implicated session; the original security draft recommends revoking every session. Phase 1 must either implement account-wide revocation or explicitly accept and document the narrower behavior.
4. **Cursor stability:** Cursors embed `(updatedAt, id)`. Updates to a conversation's `updatedAt` can move it between pages; document this as accepted behavior for the MVP.
5. **Provider model selection:** The DeepSeek model name, pricing, and output limits must be configurable, not hardcoded.
6. **No memory in Phase 1:** Users should not expect personalization yet. The UI should not claim memory is active until Phase 2 ships.
7. **Android minSdk/toolchain:** Java 11 source compatibility, minSdk 26, compileSdk 37. New libraries must support these levels.

---

## 8. Phase 1 Exit Gate

**Status: complete (closed 2026-10-26).** The exit gate was verified with 68 backend tests passing and the milestone was merged to `main` via PR #5.

- [x] `backend`: `.\mvnw.cmd clean verify` passes with zero failures.
- [x] All new backend tests pass using Testcontainers and `MockAIProvider`; no live DeepSeek calls.
- [x] Conversation API supports create, list, and delete with ownership and cursor pagination.
- [x] Sending a message persists the user message before provider dispatch and records the logical response and attempts.
- [x] Idempotent replay and payload-conflict behavior are covered by tests, including a concurrent duplicate case.
- [x] Retry is bounded, creates attempts rather than duplicate messages, and only applies to `FAILED`/`UNKNOWN`.
- [x] The error envelope includes `requestId`, validation `details`, and a sanitized `500` fallback.
- [x] Rate limiting returns `429 rate_limited` under the configured threshold.
- [x] Android builds a debug APK, stores tokens encrypted, refreshes on `401`, and completes conversation management and chat messaging with bounded retry UI (`ConversationsActivity`, `ChatActivity`).
- [x] Android network security config prohibits cleartext traffic.
- [x] Backend and Android CI workflows pass on the main branch.
- [x] `README.md` and `04-api-contract.md` match the delivered behavior; `03-domain-model.md` matches all migrations.
- [x] No secrets, prompts, completions, or tokens appear in logs or committed files.

### 8.1 Open Follow-Ups Carried Into Phase 2

These items do not block the Phase 1 chat core, but remain unchecked and are tracked in [11-phase-2-checklist.md](./11-phase-2-checklist.md):

- [ ] Install the debug APK on an emulator or physical device and confirm launch.
- [ ] Record the Android SDK/build-tools versions used for the verified build.
- [ ] Complete the log-sanitization audit (no prompts, tokens, or completions).
- [ ] Decide the refresh-token replay policy: account-wide revocation, or formally accept session-scoped revocation.
- [ ] Add Android unit tests for token storage, the authorization interceptor, and API error mapping.
- [ ] Add an instrumented smoke test for login → conversation → send.
- [ ] Split the consolidated `MiraBackendApplicationTests` class by domain as the surface grows.

---

## 9. Phase 2 Preview

Phase 2 is now active and tracked in [11-phase-2-checklist.md](./11-phase-2-checklist.md). It delivers the differentiator specified in [05-memory-lifecycle.md](./05-memory-lifecycle.md):

- Style-profile read, replace, toggle, and purge endpoints. *(Backend implemented.)*
- Candidate extraction gated on `COMPLETED` responses.
- Allow-list schema validation and the hybrid persistence policy.
- OCC version checks plus durable generation locking for deletion-race safety.
- Memory injection into prompt assembly when enabled.
- Android memory inspection, edit, toggle, and purge UI.
- Concurrency test suites `MEM-TEST-01` through `MEM-TEST-07`.
