# MIRA — Automated Testing & Quality Assurance Strategy

| Attribute | Value |
| :--- | :--- |
| **Document ID** | MIRA-TST-001 |
| **Classification** | Internal Engineering Specification |
| **Status** | Approved Baseline |
| **Version** | 2.11.0 |
| **Last Updated** | 2026-11-14 |
| **Author / Lead** | Bryan / MIRA Quality Assurance & Test Engineering |
| **Testing Scope** | Multi-Tier Automated Testing, Testcontainers, Mock Doubles |

---

## Document Revision History

| Version | Date | Author | Description of Changes |
| :--- | :--- | :--- | :--- |
| 0.1.0 | 2026-10-10 | QA Team | Initial test pyramid, unit test scope, and mock strategy draft. |
| 1.0.0 | 2026-10-11 | Bryan | Enterprise standardization: integrated visual Mermaid test pyramid, CI pipeline, lifecycle retry test matrix, session revocation tests, and consolidated all draft updates. |
| 1.1.0 | 2026-10-21 | Bryan | Reconciled the test baseline with the implemented suites, Java 21/Testcontainers versions, Flyway V1–V8, and the Phase 1/Phase 2 delivery split. |
| 1.2.0 | 2026-10-23 | Bryan | Reconciled test inventory to 52 passing tests, updated migration baseline to V1–V9, and marked completed Phase 1 lifecycle/retry test scenarios. |
| 1.3.0 | 2026-10-24 | Bryan | Reconciled test inventory to 58 passing tests (adding DeepSeekAIProviderTests MockRestServiceServer suite) and noted Android assembleDebug / testDebugUnitTest verification. |
| 1.4.0 | 2026-10-25 | Bryan | Reconciled test inventory to 63 passing tests (adding MessageRateLimiterTests and rate-limiting integration test) and verified Android conversation and chat unit tests. |
| 1.5.0 | 2026-10-25 | Bryan | Documented GitHub Actions CI pipelines (`backend.yml` and `android.yml`) and Android lint verification (`lintDebug`). |
| 1.6.0 | 2026-10-26 | Bryan | Reconciled Phase 1 closure and Phase 2 backend delivery: 68 passing tests including style-memory provisioning, OCC conflict, allow-list rejection, toggle, and purge/generation coverage. |
| 1.7.0 | 2026-10-27 | Bryan | Reconciled advisory prompt injection: added four context-assembly tests (enabled, disabled, empty, all-unspecified) and updated the inventory to 72 passing tests. |
| 1.8.0 | 2026-10-27 | Bryan | Reconciled the extraction pipeline: seven new tests for scheduling/eligibility, explicit merge, inferred staging, stale-generation skip, and malformed output; inventory now 79 tests. |
| 1.9.0 | 2026-10-27 | Bryan | Reconciled candidate review: four new tests for list/accept, reject, purge cleanup, and cross-account rejection; inventory now 83 tests. |
| 2.0.0 | 2026-10-29 | Bryan | Reconciled extraction retry/recovery and the MEM-TEST race scenarios: six new tests (68 integration, 89 total). |
| 2.1.0 | 2026-11-13 | Bryan | Reconciled Android memory options unit test suite (`MemoryOptionsTest`, 3 tests; 4 total passing Android unit tests). |
| 2.2.0 | 2026-11-13 | Bryan | Reconciled memory integration and privacy audit test suites (15 tests across `MemoryConflictResolutionTest`, `MemoryEndpointSecurityTest`, `MemoryInjectionFailureTest`, `MemoryInjectionSafetyTest`, `MemoryPrivacyAuditTest`; inventory now 104 passing backend tests, 0 failures). |
| 2.3.0 | 2026-11-13 | Bryan | Added Phase 3 security hardening suites: `AuthRateLimiterTests`, `AuthSecurityTest`, and `LogSanitizationTest` (12 tests; inventory now 116 passing backend tests). |
| 2.4.0 | 2026-11-14 | Bryan | Added the backend container-image build to CI and documented the signed Android release workflow. |
| 2.5.0 | 2026-11-14 | Bryan | Added observability coverage: `MiraMetricsTests`, `ObservabilityTest`, and custom metrics documentation (inventory now 124 backend tests). |
| 2.6.0 | 2026-11-14 | Bryan | Added Android production-readiness tests: `TokenStoreTest`, `AuthInterceptorTest`, `ApiErrorsTest`, and the instrumented `SessionApiSmokeTest` (16 Android unit tests; instrumented run pending a device). |
| 2.7.0 | 2026-11-14 | Bryan | Split the monolithic backend suite by domain, introduced a shared singleton Testcontainers base, added `LatencySmokeTest`, and enabled Android release-build verification (inventory now 125 backend tests). |
| 2.8.0 | 2026-11-14 | Bryan | Added gitleaks and Dependabot coverage, plus the opt-in production Android smoke test. |
| 2.9.0 | 2026-11-14 | Bryan | Correction: removed the stale claim that refresh rotation/replay tests were an open Phase 1 follow-up; they are covered by `AuthSecurityTest`. |
| 2.10.0 | 2026-11-14 | Bryan | Added the chat in-flight placeholder instrumented suite (`MessageAdapterThinkingTest`, 4 tests) and refreshed the Android instrumented inventory and execution status. |
| 2.11.0 | 2026-11-15 | Bryan | Added RegenerateApiTests (6 tests) and updated the backend inventory to 131. |

---

## 1. Testing Philosophy & Core Principles

MIRA adheres to four foundational testing principles to ensure rock-solid reliability without incurring cloud provider costs:

1. **Test Observable Behavior, Not Implementation Internals:** Tests verify observable API contracts, HTTP status codes, and database state transitions rather than private class methods or reflection hacks.
2. **Deterministic, Offline-First Test Suites:** Routine automated tests (unit and integration) must **never** make live network requests to the DeepSeek API or depend on production databases. Third-party provider calls are substituted with high-fidelity test doubles (`MockAIProvider`).
3. **Real PostgreSQL via Testcontainers:** Database integration tests run against real PostgreSQL instances spun up via Testcontainers. In-memory databases (like H2) are prohibited because they fail to reproduce PostgreSQL-specific JSONB operations, check constraints, and concurrency locking semantics.
4. **Explicit Concurrency & Security Verification:** Race conditions (memory deletion vs background extraction, duplicate message submissions, token reuse) and authorization boundaries (IDOR attacks) are tested explicitly with automated negative test suites.

---

## 2. Multi-Tier Testing Pyramid

```mermaid
flowchart TD
    subgraph Pyramid ["Testing Pyramid & Test Distribution"]
        E2E["End-to-End Tests (~5%)\n(Full Mobile Client + Backend Integration Workflows)"]
        API["API & Security Contract Tests (~20%)\n(MockMvc, Cross-Account Isolation, Token Lifecycles)"]
        IntTest["Database Integration Tests (~30%)\n(Testcontainers + PostgreSQL, Flyway Migrations, OCC Locks)"]
        Unit["Unit Tests (~45%)\n(Domain Entities, Schema Validation, Policy Classifiers, Fast Feedback)"]
    end

    E2E --> API
    API --> IntTest
    IntTest --> Unit
```

---

## 3. Test Suites & Verification Scope

### 3.1 Backend Unit Testing
* **Framework:** JUnit 5, Mockito, AssertJ.
* **Execution Target:** Sub-second execution; runs on every build.
* **Verification Scope:**
  * Password hashing validation and email normalization rules.
  * Memory schema allow-list validation (valid vs invalid enum keys and values).
  * Hybrid policy classification (explicit durable vs ephemeral task vs weak inference).
  * Bounded context truncation logic (sliding window of chat turns).
  * Provider error mapping (HTTP 429 to rate-limited exception, 504 to gateway timeout).

### 3.2 Database Integration Testing (Testcontainers)
* **Framework:** Spring Boot Test (`@DataJpaTest`), Testcontainers PostgreSQL.
* **Verification Scope:**
  * **Flyway Migrations:** Verify that all versioned migrations (`V1` through `V10`) execute successfully from a clean database. The current suite uses `postgres:18-alpine` through Testcontainers.
  * **Relational Constraints:** Verify that deleting a `user_account` cascades to `conversation` and `message` records, but leaves unrelated data intact.
  * **Optimistic Concurrency Control (OCC):** Verify that concurrent `UPDATE user_style_profile` operations with identical `expectedVersion` integers permit only one update and trigger a version conflict on the second.
  * **Idempotency Uniqueness:** Verify that inserting duplicate `(conversation_id, idempotency_key)` tuples violates database unique constraints.

### 3.3 API Contract & Security Testing
* **Framework:** Spring MockMvc / WebTestClient with Spring Security context.
* **Verification Scope:**
  * **Authentication:** Verify registration, login, token rotation, and invalid credential rejections. Registration, login, refresh, logout, and `/me` are implemented and covered, including rotation and replay rejection (`AuthSecurityTest`).
  * **Session Revocation & Reuse:** Verify that using an already-rotated refresh token revokes every active session for the account and rejects further use of the token. Covered by `AuthSecurityTest` (account-wide revocation and cross-account isolation).
  * **Object-Level Authorization (IDOR):** Verify that User A attempting to read, modify, or delete User B's conversation receives `404 Not Found`.
  * **Uniform Error Formatting:** Verify that invalid inputs return the standard JSON error schema with appropriate error codes.

### 3.4 AI Provider Testing & Simulation

```mermaid
flowchart LR
    subgraph TestExecution ["Integration Test Run"]
        Service["ChatOrchestrationService"]
        ProviderInterface["AIProvider (Interface)"]
        MockImpl["MockAIProvider\n(Configurable Scenarios)"]
    end

    Service --> ProviderInterface
    ProviderInterface -.->|Injected in Tests| MockImpl
    
    subgraph Scenarios ["Simulated Outcomes"]
        MockImpl --> S1["Scenario: Instant Success (Valid Text)"]
        MockImpl --> S2["Scenario: Provider Timeout (Simulate Socket Hang)"]
        MockImpl --> S3["Scenario: Rate Limited (HTTP 429)"]
        MockImpl --> S4["Scenario: Upstream Malformed Output"]
    end
```

The `MockAIProvider` enables deterministic simulation of complex edge cases without spending API credits or encountering live network flakiness.

### 3.5 Current Test Inventory (2026-11-13)

| Suite | Type | Tests | Scope |
| :--- | :--- | :--- | :--- |
| `PersistenceConstraintTests` | Spring Boot + Testcontainers | 9 | Account/session persistence, normalized-email and refresh-token uniqueness, registration service rules, and AI-response idempotency constraints. |
| `AuthApiTests` | Spring Boot + Testcontainers + MockMvc | 11 | Registration endpoints, protected access, logout lifecycle, cross-account revocation, and `/me`. |
| `ApiContractTests` | Spring Boot + Testcontainers + MockMvc | 3 | Request-ID propagation, error envelope shape, and validation details. |
| `ConversationApiTests` | Spring Boot + Testcontainers + MockMvc | 6 | Conversation ownership, ordering, cursor pagination, create/list/delete, and invalid pagination input. |
| `MessagingApiTests` | Spring Boot + Testcontainers + MockMvc | 13 | Message lifecycle, persistence ordering, idempotent replay/conflict, timeout mapping, bounded retry, cross-account isolation, and rate limiting. |
| `RegenerateApiTests` | Spring Boot + Testcontainers + MockMvc | 6 | Regeneration replaces a completed answer in place, assistant messages expose response metadata, idempotent replay, provider failure keeps the previous answer, the 3-attempt limit, and cross-account isolation. |
| `MemoryLifecycleTests` | Spring Boot + Testcontainers + MockMvc | 26 | Governance, injection, extraction, candidate review, purge cleanup, and the `MEM-TEST-01`..`MEM-TEST-07` scenarios. |
| `LatencySmokeTest` | Spring Boot + Testcontainers + MockMvc | 1 | NFR-004 smoke: read-endpoint p95 below 200 ms after warm-up. |
| `MemoryConflictResolutionTest` | Spring Boot + Testcontainers + MockMvc | 5 | Full hybrid persistence matrix: explicit durable auto-merge, inferred candidate staging with evidence counter accumulation, ephemeral instruction discard, and immediate user correction overwrite. |
| `MemoryEndpointSecurityTest` | Spring Boot + Testcontainers + MockMvc | 2 | Multi-tenant authorization boundaries: profile mutations and candidate accept/reject are strictly scoped to the owner, rejecting cross-account IDOR attempts with 404/403. |
| `MemoryInjectionFailureTest` | Spring Boot + Testcontainers + MockMvc | 1 | Fault tolerance: simulated style profile lookup failures during chat context assembly are caught non-fatally, allowing chat to proceed smoothly without memory. |
| `MemoryInjectionSafetyTest` | Spring Boot + Testcontainers + MockMvc | 2 | Prompt injection safety: immediate explicit user prompt instruction is strictly ordered last, advisory style injection is subordinate, and purged memory is omitted from subsequent chat requests. |
| `MemoryPrivacyAuditTest` | Spring Boot + Testcontainers + MockMvc | 5 | Privacy & data minimization audit: biographical/unknown candidate keys are never persisted, candidate evidence is capped at 500 characters, no transcript/prompt columns exist in extraction tables, and user content/tokens are never emitted to logs. |
| `AuthRateLimiterTests` | Unit | 6 | Auth sliding-window limits: login per email/IP, register per IP, refresh per IP, email normalization, window refill, and IP isolation. |
| `AuthSecurityTest` | Spring Boot + Testcontainers + MockMvc | 3 | Account-wide refresh-token replay revocation, cross-account replay isolation, and `429 rate_limited` on repeated failed logins. |
| `LogSanitizationTest` | Spring Boot + Testcontainers + MockMvc | 3 | Log audit: login credentials, tokens, chat prompts, and completions never appear in application logs. |
| `MiraMetricsTests` | Unit | 4 | Custom Micrometer counters for chat outcomes, provider errors, retries, extraction jobs, auth throttling, purges, and candidate review. |
| `ObservabilityTest` | Spring Boot + Testcontainers + MockMvc | 4 | End-to-end metric increments for successful chat/extraction, provider failure/retry, rate-limited login, and memory purge. |
| `MessageRateLimiterTests` | Unit | 4 | In-memory sliding-window rate limiter accounting, quota enforcement (10 req/min), multi-user isolation, and window expiration. |
| `DeepSeekAIProviderTests` | Unit + MockRestServiceServer | 6 | OpenAI-compatible request serialization, Bearer authorization, response parsing, and HTTP status/error mapping (timeout, 429 rate limit, 5xx unavailable, 4xx provider error, invalid/blank output). |
| `MockAIProviderTests` | Unit | 5 | Deterministic simulation of instant success, timeout, HTTP 429 rate limiting, HTTP 5xx error, and malformed response scenarios. |
| `TokenServiceTests` | Unit | 5 | JWT claims and expiry, refresh-token uniqueness/URL safety, deterministic SHA-256 hashing. |
| `JwtConfigurationTests` | Unit | 1 | Secret validation and decoder configuration. |
| `MemoryOptionsTest` (Android) | Unit | 3 | Style options indexing, unspecified value mapping, allow-list enum selection, and invalid value rejection. |
| `TokenStoreTest` (Android) | Unit | 4 | Session persistence, clearing, refresh-token presence, and absent-session behavior. |
| `AuthInterceptorTest` (Android) | Unit | 4 | Bearer header injection, no-token behavior, existing-header preservation, and blank-token handling. |
| `ApiErrorsTest` (Android) | Unit | 4 | Error-envelope message extraction and fallbacks for malformed, blank, and absent bodies. |
| `SessionApiSmokeTest` (Android) | Instrumented (MockWebServer) | 1 | Login → list conversations → create conversation → send message, including bearer and idempotency headers. |
| `MessageAdapterThinkingTest` (Android) | Instrumented | 4 | Chat in-flight placeholder: sentinel detection, themed layout inflation on the main thread, adapter dispatch to the dedicated thinking holder, and regular user messages still binding the user holder. |
| `ProductionSmokeTest` (Android) | Instrumented (manual) | 1 | Opt-in on-device register/login against the production backend (`miraProductionSmoke=true`). |
| `ExampleUnitTest` (Android) | Unit | 1 | Android unit testing baseline. |

The backend suite (131 tests total) runs against a real PostgreSQL container (`postgres:18-alpine`) with Flyway applying `V1`–`V10`. The Android unit suite (16 tests) executes via `.\gradlew.bat testDebugUnitTest`. The instrumented suite (7 tests) runs via `.\gradlew.bat connectedDebugAndroidTest` on a physical device (TECNO LI7, Android 15) and covers the API smoke flow, the chat in-flight placeholder, and the opt-in production smoke test (skipped unless `miraProductionSmoke=true`). No test contacts DeepSeek. The integration suite shares one singleton container and one Spring context across domain classes. Extraction runs are deterministic: the scheduler and recovery pollers are disabled in the test profile, and workers are invoked directly with scripted mock responses. All 131 backend tests pass offline without failures or errors.

---

## 4. Lifecycle, Retry & Concurrency Test Matrix

| Test Suite Category | Scenario Description | Input / Setup | Expected Observable Assertion | Phase |
| :--- | :--- | :--- | :--- | :--- |
| **Response Lifecycle** | Successful Response | User submits valid prompt. | Logical response transitions to `COMPLETED`; assistant message persisted; eligible for memory extraction. | Implemented (Backend) |
| **Response Lifecycle** | Provider Timeout | `MockAIProvider` throws `TimeoutException`. | Logical response transitions to `UNKNOWN`; user message remains intact; attempt logged as `TIMEOUT`. | Implemented (Backend) |
| **Response Lifecycle** | Provider Terminal Failure | `MockAIProvider` returns HTTP 500 error. | Logical response transitions to `FAILED`; attempt logged as `ERROR`. | Implemented (Backend) |
| **Idempotent Retry** | Client Submits Identical Key | Re-send request with same `Idempotency-Key` and payload. | Returns existing response without dispatching duplicate AI request or duplicating messages. | Implemented (Backend) |
| **Idempotent Retry** | Client Reuses Key with New Body | Re-send same `Idempotency-Key` with different message. | Server rejects request with `409 Conflict` and error code `idempotency_conflict`. | Implemented (Backend) |
| **Memory Governance** | First-read provisioning | Authenticated `GET /memory/style` with no existing profile. | `200 OK` with `enabled=true`, `version=1`, empty profile object; control row exists. | Implemented (Backend) |
| **Memory Governance** | Allow-list rejection | `PUT /memory/style` with an unknown field or enum value. | `400 invalid_request`; stored profile unchanged. | Implemented (Backend) |
| **Memory Governance** | OCC conflict | Second `PUT` reusing an already-consumed `expectedVersion`. | `409 version_conflict` with `details` naming `expectedVersion`. | Implemented (Backend) |
| **Memory Governance** | Toggle settings | `PATCH /memory/style/settings` with `enabled=false` then `true`. | Flag flips and `version` increments on each mutation. | Implemented (Backend) |
| **Memory Governance** | Purge & generation | `DELETE /memory/style` after establishing a profile. | Profile becomes empty, `generation` increments by one, `version` increments. | Implemented (Backend) |
| **Memory Injection** | Enabled profile injected | Established, enabled profile with specified fields; send a message. | Provider request includes an advisory system message containing the specified fields; the user message remains the last message. | Implemented (Backend) |
| **Memory Injection** | Disabled profile omitted | Toggle `enabled=false`; send a message. | Provider request contains no communication-style system message. | Implemented (Backend) |
| **Memory Injection** | Empty / unspecified profile omitted | No stored profile, or only `no_strong_preference` values. | Provider request contains no communication-style system message. | Implemented (Backend) |
| **Memory Injection** | Lookup failure is non-fatal | Simulate a profile lookup failure during assembly. | Chat proceeds without memory (`MemoryInjectionFailureTest`). | Implemented (Backend) |
| **Memory Injection** | Instruction Dominance & Subordination | Stored memory injected alongside conflicting immediate user prompt. | Stored memory is framed as advisory/subordinate; immediate user turn is ordered last (`MemoryInjectionSafetyTest`). | Implemented (Backend) |
| **Memory Privacy Audit** | Biographical rejection & minimization | Attempt extraction on biographical keys, long evidence, or inspect logs. | Biographical candidates rejected; evidence capped at 500 chars; zero transcript columns in DB; zero user content/tokens in logs (`MemoryPrivacyAuditTest`). | Implemented (Backend) |
| **Deterministic Ordering** | Microsecond tie-breaking & clock skew | Rapid sequential message persistence and sub-millisecond refresh token rotation. | Assistant `createdAt` guaranteed after user `createdAt`; token consumption time clamped to issue time to satisfy DB constraints. | Implemented (Backend) |
| **Memory Extraction** | Schedule on completed response | Send a message that completes successfully. | One `PENDING` job with the current `expected_generation`/`expected_version`; attempts `0`. | Implemented (Backend) |
| **Memory Extraction** | No job for timeout or disabled memory | Provider timeout, or `enabled=false` before send. | No extraction job row is created. | Implemented (Backend) |
| **Memory Extraction** | Explicit durable merge | Worker returns `EXPLICIT_DURABLE` for `tone=formal`. | Job `COMPLETED`; profile `tone=formal`; version increments. | Implemented (Backend) |
| **Memory Extraction** | Inferred staging | Worker returns `INFERRED`. | Job `COMPLETED`; profile unchanged; `user_memory_candidate` is `PENDING` with `evidence_count=1`. | Implemented (Backend) |
| **Memory Extraction** | Stale generation skip | Purge memory after scheduling, then run the worker. | Job `SKIPPED`; purged profile remains empty. | Implemented (Backend) |
| **Memory Extraction** | Malformed / retryable provider output | Provider returns non-JSON or a retryable error. | Job returns to `PENDING` with `INVALID_OUTPUT`/error category until `max-attempts` is reached, then `FAILED`. | Implemented (Backend) |
| **Memory Extraction** | Stale `PROCESSING` recovery | Job remains `PROCESSING` beyond `stale-after-seconds`. | Recovery requeues it (or fails it at the attempt limit) with `STALE_PROCESSING`. | Implemented (Backend) |
| **Memory Extraction** | Concurrent claim / OCC race | Multiple workers or generations race to commit. | `SKIP LOCKED` prevents duplicate claims; exactly one commit wins and stale jobs skip. | Implemented (MEM-TEST suite) |
| **Candidate Review** | List and accept pending candidate | Stage an inferred candidate, list it, accept it. | List shows the candidate; accept applies the value, increments `version`, and marks `ACCEPTED`. | Implemented (Backend) |
| **Candidate Review** | Reject candidate | Stage an inferred candidate and reject it. | `204 No Content`; profile unchanged; candidate `REJECTED`. | Implemented (Backend) |
| **Candidate Review** | Purge deletes pending candidates | Stage a candidate, then purge memory. | Candidate row is deleted; profile remains empty. | Implemented (Backend) |
| **Candidate Review** | Cross-account access | User B lists/accepts User A's candidate. | `404 resource_not_found`; User A's candidate is unchanged. | Implemented (Backend) |
| **Memory Client UI** | Android Style Options Mapping | Unspecified mapping, valid enum indexing, invalid rejection. | Validated via `MemoryOptionsTest`. | Implemented (Android) |
| **Memory Gating** | Extraction from Incomplete Response | Response is `PENDING`, `FAILED`, or `UNKNOWN`. | Extraction service strictly skips task; zero memory updates committed. | Implemented (Backend) |
| **Memory Concurrency** | OCC Version Collision (concurrent) | Two jobs race to commit with the same `expectedVersion`. | One commit succeeds; the other is skipped without overwriting the profile. | Implemented (MEM-TEST-06) |
| **Memory Deletion Race**| Background Extraction vs User Purge | Extraction task starts; User executes `DELETE /memory/style`; Extraction finishes. | Extraction task detects incremented `generation` counter and aborts; profile remains completely purged. | Implemented (MEM-TEST-01/03) |
| **Session Security** | Refresh Token Rotation | Client calls `POST /auth/refresh` with valid RT1. | RT1 is marked consumed; RT2 is issued and verified active; the session ID is unchanged. | Implemented (Backend) |
| **Session Security** | Token Reuse Interception | Client attempts to reuse consumed RT1. | Server detects reuse, revokes every active session for the account, and returns `401 Unauthorized` (`AuthSecurityTest`). | Implemented (Backend) |
| **Authentication Security** | Auth throttling | Repeated failed logins or registration/refresh attempts. | `429 rate_limited` per configured email/IP window (`AuthRateLimiterTests`, `AuthSecurityTest`). | Implemented (Backend) |
| **Log Security** | Sensitive-data log audit | Login, failed login, successful chat, and provider failure. | Passwords, tokens, prompts, and completions never appear in logs (`LogSanitizationTest`). | Implemented (Backend) |

---

## 5. Continuous Integration (CI) Workflow

```mermaid
flowchart TD
    subgraph Trigger ["GitHub Pull Request / Push to Main"]
        PR["PR Submitted"]
    end

    subgraph BackendPipeline ["Backend CI Pipeline (Maven)"]
        Compile["1. Compile Java 21 Sources"]
        UnitTests["2. Run Unit Tests (JUnit 5)"]
        Testcontainers["3. Run Testcontainers Integration Tests (PostgreSQL)"]
        Lint["4. Static Code & Security Analysis"]
        PR --> Compile
        Compile --> UnitTests
        UnitTests --> Testcontainers
        Testcontainers --> Lint
    end

    subgraph AndroidPipeline ["Android CI Pipeline (Gradle)"]
        Assemble["1. Assemble Debug APK"]
        AndroidUnit["2. Run Android Unit Tests (JUnit)"]
        AndroidLint["3. Android Lint Verification"]
        PR --> Assemble
        Assemble --> AndroidUnit
        AndroidUnit --> AndroidLint
    end

    subgraph Gate ["Quality Release Gate"]
        CheckSuccess{"All Jobs Passed?"}
        Lint --> CheckSuccess
        AndroidLint --> CheckSuccess
        CheckSuccess -- Yes --> MergeApproved["Approve Merge / Artifact Ready"]
        CheckSuccess -- No --> BlockMerge["Block Merge & Alert Engineering"]
    end
```

> [!NOTE]
> GitHub Actions workflows are configured in `.github/workflows/backend.yml` (Maven verify with Testcontainers plus a production container-image build), `.github/workflows/android.yml` (Gradle build, unit tests, instrumented-test compilation, release build, lint, and APK artifact upload), and `.github/workflows/security.yml` (gitleaks secret scan). The `Android Release` workflow builds and signs an APK/AAB on `v*` tags, and the `Production Health Monitor` polls production every 10 minutes. Dependabot checks Maven, Gradle, and GitHub Actions dependencies weekly.

---

## 6. Definition of Done & Release Gating Checklist

Before any feature or sprint release is accepted into the production branch, all of the following conditions must be satisfied:

- [ ] 100% of backend unit and Testcontainers integration tests pass locally and in CI.
- [ ] Cross-account authorization (IDOR) tests verify that resources cannot be accessed across user boundaries.
- [ ] Memory concurrency tests prove that stale background tasks cannot overwrite manual user edits or resurrect deleted memory.
- [x] Refresh token rotation and token reuse detection are verified with automated test cases.
- [ ] All database migrations (`V1`..`Vn`) execute cleanly against an empty PostgreSQL instance.
- [ ] No API keys, passwords, or raw user message transcripts are emitted into application log files.
- [ ] Android application builds cleanly in debug and release modes with zero lint errors.
- [ ] Core conversational flow is manually verified on at least one physical Android device.
