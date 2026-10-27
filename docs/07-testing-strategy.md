# MIRA — Automated Testing & Quality Assurance Strategy

| Attribute | Value |
| :--- | :--- |
| **Document ID** | MIRA-TST-001 |
| **Classification** | Internal Engineering Specification |
| **Status** | Approved Baseline |
| **Version** | 1.9.0 |
| **Last Updated** | 2026-10-27 |
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
  * **Authentication:** Verify registration, login, token rotation, and invalid credential rejections. Registration, login, refresh, logout, and `/me` are implemented and covered; dedicated rotation/replay tests remain an open Phase 1 follow-up.
  * **Session Revocation & Reuse:** Verify that using an already-rotated refresh token revokes the implicated session and rejects further use of that token. Current tests cover logout revocation and post-logout refresh rejection; replay-specific tests remain an open Phase 1 follow-up.
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

### 3.5 Current Test Inventory (2026-10-27)

| Suite | Type | Tests | Scope |
| :--- | :--- | :--- | :--- |
| `MiraBackendApplicationTests` | Spring Boot + Testcontainers + MockMvc | 62 | Registration, duplicate email, login/logout/refresh, `/me`, conversation CRUD, cursor pagination, message send, lifecycle transitions, idempotency replay, payload conflict, bounded retry, per-account rate limiting (`429 rate_limited`), cross-account isolation, style-memory governance (first-read provisioning, OCC conflict, allow-list rejection, settings toggle, purge/generation increment), prompt injection (enabled, disabled, empty, all-unspecified), extraction (scheduling/eligibility, explicit merge, inferred staging, stale-generation skip, malformed output), and candidate review (list/accept, reject, purge cleanup, cross-account rejection). |
| `MessageRateLimiterTests` | Unit | 4 | In-memory sliding-window rate limiter accounting, quota enforcement (10 req/min), multi-user isolation, and window expiration. |
| `DeepSeekAIProviderTests` | Unit + MockRestServiceServer | 6 | OpenAI-compatible request serialization, Bearer authorization, response parsing, and HTTP status/error mapping (timeout, 429 rate limit, 5xx unavailable, 4xx provider error, invalid/blank output). |
| `MockAIProviderTests` | Unit | 5 | Deterministic simulation of instant success, timeout, HTTP 429 rate limiting, HTTP 5xx error, and malformed response scenarios. |
| `TokenServiceTests` | Unit | 5 | JWT claims and expiry, refresh-token uniqueness/URL safety, deterministic SHA-256 hashing. |
| `JwtConfigurationTests` | Unit | 1 | Secret validation and decoder configuration. |

The suite (83 tests total) runs against a real PostgreSQL container (`postgres:18-alpine`) with Flyway applying `V1`–`V10`. No test contacts DeepSeek. Extraction runs are deterministic: the scheduler is disabled in the test profile and the worker is invoked directly with a scripted mock response. The classes should be split by domain as the Phase 2 surface grows; the current consolidated class is a baseline, not the target structure.

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
| **Memory Injection** | Lookup failure is non-fatal | Simulate a profile lookup failure during assembly. | Chat proceeds without memory. | Unit coverage pending |
| **Memory Extraction** | Schedule on completed response | Send a message that completes successfully. | One `PENDING` job with the current `expected_generation`/`expected_version`; attempts `0`. | Implemented (Backend) |
| **Memory Extraction** | No job for timeout or disabled memory | Provider timeout, or `enabled=false` before send. | No extraction job row is created. | Implemented (Backend) |
| **Memory Extraction** | Explicit durable merge | Worker returns `EXPLICIT_DURABLE` for `tone=formal`. | Job `COMPLETED`; profile `tone=formal`; version increments. | Implemented (Backend) |
| **Memory Extraction** | Inferred staging | Worker returns `INFERRED`. | Job `COMPLETED`; profile unchanged; `user_memory_candidate` is `PENDING` with `evidence_count=1`. | Implemented (Backend) |
| **Memory Extraction** | Stale generation skip | Purge memory after scheduling, then run the worker. | Job `SKIPPED`; purged profile remains empty. | Implemented (Backend) |
| **Memory Extraction** | Malformed provider output | Provider returns non-JSON. | Job `FAILED` with `INVALID_OUTPUT`; profile unchanged. | Implemented (Backend) |
| **Memory Extraction** | Concurrent claim / OCC race | Multiple workers or generations race to commit. | Exactly one commit wins; stale jobs skip. | Phase 2 (MEM-TEST suite) |
| **Candidate Review** | List and accept pending candidate | Stage an inferred candidate, list it, accept it. | List shows the candidate; accept applies the value, increments `version`, and marks `ACCEPTED`. | Implemented (Backend) |
| **Candidate Review** | Reject candidate | Stage an inferred candidate and reject it. | `204 No Content`; profile unchanged; candidate `REJECTED`. | Implemented (Backend) |
| **Candidate Review** | Purge deletes pending candidates | Stage a candidate, then purge memory. | Candidate row is deleted; profile remains empty. | Implemented (Backend) |
| **Candidate Review** | Cross-account access | User B lists/accepts User A's candidate. | `404 resource_not_found`; User A's candidate is unchanged. | Implemented (Backend) |
| **Memory Gating** | Extraction from Incomplete Response | Response is `PENDING`, `FAILED`, or `UNKNOWN`. | Extraction service strictly skips task; zero memory updates committed. | Phase 2 |
| **Memory Concurrency** | OCC Version Collision (concurrent) | Two threads attempt update with `expectedVersion: 4`. | Thread 1 succeeds (version becomes 5); Thread 2 receives `409 Conflict` (`version_conflict`). | Phase 2 (API-level conflict implemented; two-thread race suite pending) |
| **Memory Deletion Race**| Background Extraction vs User Purge | Extraction task starts; User executes `DELETE /memory/style`; Extraction finishes. | Extraction task detects incremented `generation` counter and aborts; profile remains completely purged. | Phase 2 |
| **Session Security** | Refresh Token Rotation | Client calls `POST /auth/refresh` with valid RT1. | RT1 is marked consumed; RT2 is issued and verified active; the session ID is unchanged. | Implemented (Backend) |
| **Session Security** | Token Reuse Interception | Client attempts to reuse consumed RT1. | Server detects reuse, revokes the implicated session, and returns `401 Unauthorized`. Global all-session revocation is a hardening item. | Implemented (Backend) |

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
> GitHub Actions workflows are configured in `.github/workflows/backend.yml` (Maven verify with Testcontainers) and `.github/workflows/android.yml` (Gradle build, testDebugUnitTest, lintDebug, and APK artifact upload). Both pipelines run on push, pull request, and manual dispatch.

---

## 6. Definition of Done & Release Gating Checklist

Before any feature or sprint release is accepted into the production branch, all of the following conditions must be satisfied:

- [ ] 100% of backend unit and Testcontainers integration tests pass locally and in CI.
- [ ] Cross-account authorization (IDOR) tests verify that resources cannot be accessed across user boundaries.
- [ ] Memory concurrency tests prove that stale background tasks cannot overwrite manual user edits or resurrect deleted memory.
- [ ] Refresh token rotation and token reuse detection are verified with automated test cases.
- [ ] All database migrations (`V1`..`Vn`) execute cleanly against an empty PostgreSQL instance.
- [ ] No API keys, passwords, or raw user message transcripts are emitted into application log files.
- [ ] Android application builds cleanly in debug and release modes with zero lint errors.
- [ ] Core conversational flow is manually verified on at least one physical Android device.
