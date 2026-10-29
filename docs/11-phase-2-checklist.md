# MIRA — Phase 2: Communication-Style Memory Checklist

| Attribute | Value |
| :--- | :--- |
| **Document ID** | MIRA-CHK-002 |
| **Classification** | Internal Engineering Specification |
| **Status** | Active Execution Baseline |
| **Version** | 1.4.0 |
| **Last Updated** | 2026-10-29 |
| **Author / Lead** | Bryan / MIRA Core Engineering Team |
| **Phase Milestone** | Phase 2 — Communication-Style Memory Subsystem |

---

## Document Revision History

| Version | Date | Author | Description of Changes |
| :--- | :--- | :--- | :--- |
| 1.0.0 | 2026-10-26 | Bryan | Initial Phase 2 baseline: closed Phase 1, recorded implemented backend governance (OCC, allow-list, generation-guarded purge), and scheduled extraction, prompt injection, Android UI, and concurrency suites. |
| 1.1.0 | 2026-10-27 | Bryan | Marked advisory prompt injection complete (enabled profiles injected, disabled/empty/unspecified omitted) and updated the suite to 72 tests. Extraction, hybrid policy, Android UI, and race suites remain. |
| 1.2.0 | 2026-10-27 | Bryan | Marked the extraction pipeline complete: V10 job/candidate tables, `COMPLETED`-only scheduling, hybrid policy, and generation/version commit guards with seven new tests (79 total). Candidate confirmation, Android UI, and race/privacy suites remain. |
| 1.3.0 | 2026-10-27 | Bryan | Marked backend candidate review complete: list/accept/reject pending inferred candidates with ownership, generation, and allow-list checks; purge deletes pending candidates (83 total tests). Android UI and race/privacy suites remain. |
| 1.4.0 | 2026-10-29 | Bryan | Marked extraction reliability and the MEM-TEST race scenarios complete: bounded retries, skip-locked claiming, and stale-processing recovery (89 total tests). Android UI and privacy audit remain. |

---

## 1. Purpose & Objectives

Phase 2 delivers MIRA's differentiator: a persistent, user-controlled **communication-style memory** that informs future responses without storing biographical facts. It builds on the completed Phase 1 chat core and the schema introduced in Flyway `V5`/`V6`.

```mermaid
flowchart LR
    subgraph Backend ["Spring Boot Backend (Phase 2)"]
        Governance["Governance API\n(implemented)\nGET/PUT/PATCH/DELETE /memory/style"]
        Extraction["Extraction Pipeline\n(Phase 2)\nCOMPLETED gate + hybrid policy"]
        Injection["Context Injection\n(Phase 2)\nenabled profile -> system prompt"]
        Concurrency["OCC + Generation Guards\n(partially implemented)"]
    end

    subgraph Client ["Android Client (Phase 2)"]
        MemoryUI["Memory Settings UI\n(inspect / edit / toggle / purge)"]
    end

    DB[("PostgreSQL\nuser_style_profile + user_memory_control")]
    Chart["Chat Orchestration\n(Phase 1)"]

    MemoryUI --> Governance
    Governance --> DB
    Chart --> Injection
    Injection --> DB
    Chart --> Extraction
    Extraction --> Concurrency
    Concurrency --> DB
```

Phase 2 objectives:

1. Finish the governance surface (already implemented on the backend) and exercise it from the Android client.
2. Implement candidate extraction gated strictly on `COMPLETED` assistant responses.
3. Apply the hybrid persistence policy: explicit durable preferences merge automatically, inferred preferences are staged, ephemeral instructions are discarded.
4. Enforce OCC versioning and the durable generation tombstone in the same locked transaction as any profile write.
5. Inject the enabled style profile into prompt assembly while preserving current-instruction dominance.
6. Prove deletion-race and collision safety with `MEM-TEST-01`..`MEM-TEST-07`.

---

## 2. Scope

### 2.1 In Scope

- Style-profile read, replace, toggle, and purge endpoints. *(Backend implemented.)*
- First-read provisioning of `user_style_profile` and `user_memory_control` rows. *(Implemented.)*
- Allow-list schema validation and `409 version_conflict` mapping. *(Implemented.)*
- Generation-guarded purge (`generation += 1` under row lock). *(Implemented.)*
- Candidate extraction and eligibility gating on `COMPLETED` responses. *(Backend implemented.)*
- Hybrid persistence classification and staging for inferred preferences. *(Backend implemented; review surfaces implemented.)*
- Candidate review: list pending inferred candidates and accept/reject them. *(Backend implemented.)*
- Asynchronous extraction that never blocks chat latency. *(Backend implemented via in-process pollers with bounded retries and stale-job recovery.)*
- Memory injection into prompt assembly when `enabled = true`; omission when disabled, empty, or purged. *(Backend implemented.)*
- Android memory inspection, editing, toggling, and purge UI.
- Concurrency and privacy test suites.

### 2.2 Out of Scope (Deferred)

| Capability | Deferred To |
| :--- | :--- |
| Multimodal PDF/image ingestion | Post-MVP (ADR-003) |
| Biographical or multi-domain long-term knowledge | Not in product scope |
| Production deployment, TLS termination, release signing | Phase 3 |
| Vector stores, embeddings, semantic retrieval | Not in product scope (ADR-002) |
| Conversation sharing, collaboration, push notifications | Post-MVP |

---

## 3. Baseline Before Phase 2

| Capability | Current State |
| :--- | :--- |
| Phase 1 chat core | Complete; merged to `main` via PR #5 |
| Schema `V1`–`V10` | Implemented |
| `user_style_profile` / `user_memory_control` tables | Implemented (`V5` / `V6`) |
| Governance endpoints | Implemented (backend) |
| Allow-list validation and OCC | Implemented (backend) |
| Generation-guarded purge | Implemented (backend) |
| Advisory prompt injection | Implemented (backend; enabled, non-empty profiles only) |
| Extraction pipeline (jobs, hybrid policy, commit guards, retries, recovery) | Implemented (backend; Flyway V10) |
| Candidate review (list/accept/reject) | Implemented (backend) |
| Backend test suite | 89 passing tests (26 memory-governance, injection, extraction, and candidate tests included) |
| Android memory UI | Not started |
| Privacy audit and remaining unit tests | Not started |
| Phase 1 open follow-ups | See section 3.1 |

### 3.1 Phase 1 Follow-Ups Carried Forward

These are tracked here because Phase 1 closed with them still open:

- [ ] Install the debug APK on a device/emulator and record the SDK/build-tools versions.
- [ ] Complete the log-sanitization audit.
- [ ] Decide the refresh-token replay policy (account-wide vs session-scoped).
- [ ] Add Android unit tests for token storage, the auth interceptor, and error mapping.
- [ ] Add an instrumented login → conversation → send smoke test.
- [ ] Split `MiraBackendApplicationTests` by domain.

---

## 4. Delivery Workstreams

### 4.1 Memory Governance API

- [x] Add `MemoryController` with:
  - [x] `GET /api/v1/memory/style`.
  - [x] `PUT /api/v1/memory/style` (OCC via `expectedVersion`).
  - [x] `PATCH /api/v1/memory/style/settings` (`enabled` toggle).
  - [x] `DELETE /api/v1/memory/style` (purge).
- [x] Add `StyleProfileService`:
  - [x] First-read provisioning of control and profile rows.
  - [x] Allow-list validation of fields and enum values.
  - [x] OCC version check with `VersionConflictException` → `409 version_conflict`.
  - [x] Enable/disable toggle without deleting the profile.
  - [x] Purge under `SELECT ... FOR UPDATE` that increments `generation` and clears the profile.
- [x] Add DTOs: `StyleProfileResponse`, `MemorySettingsResponse`, `UpdateStyleProfileRequest`, `UpdateMemorySettingsRequest`.
- [x] Add entities/repositories: `UserStyleProfile`, `UserMemoryControl`, and their locking repositories.
- [x] Map `VersionConflictException` in `ApiExceptionHandler` with `details[0].field = expectedVersion`.
- [x] Tests:
  - [x] First read provisions `enabled=true`, `version=1`, empty profile.
  - [x] Replace validates and increments the version.
  - [x] Stale `expectedVersion` returns `409 version_conflict`.
  - [x] Unknown field or enum value returns `400 invalid_request`.
  - [x] Toggle flips `enabled` and increments the version.
  - [x] Purge clears the profile and increments `generation`.
- [ ] Add cross-account isolation tests for every memory endpoint.
- [ ] Add unauthenticated-access tests (`401`) for every memory endpoint.

### 4.2 Candidate Extraction & Eligibility Gate

- [x] Trigger extraction only after a logical response reaches `COMPLETED` (`MessageService` schedules after completion).
- [x] Never extract from `PENDING`, `FAILED`, or `UNKNOWN` responses (no job is created; the worker also re-checks status).
- [x] Build an extraction prompt restricted to the allow-listed style fields.
- [x] Explicitly instruct the model to discard biographical, financial, health, and political content.
- [x] Validate candidate output against the allow-list; discard malformed candidates.
- [x] Run extraction asynchronously so chat latency is unaffected (in-process poller; scheduling failures are non-fatal).
- [x] Capture an immutable `expected_generation` and `expected_version` when the job is created.
- [x] Persist extraction attempts/outcomes for audit without storing raw prompts (job status, attempts, error category; evidence is capped at 500 characters).
- [x] Use JSON response mode for the provider call (`response_format: json_object`).
- [x] Tests: completed scheduling, timeout suppression, disabled-memory suppression, malformed output, retry exhaustion, and stale-processing recovery.
- [ ] Add the dedicated `MemoryPrivacyAuditTest` proving no biographical extraction.

### 4.3 Hybrid Persistence Policy

- [x] Classify candidates as explicit durable, inferred, or ephemeral.
- [x] Auto-merge explicit durable preferences after validation.
- [x] Stage inferred preferences and require repeated evidence or user confirmation (`user_memory_candidate`, `evidence_count`).
- [x] Discard ephemeral task instructions.
- [x] Let explicit user corrections overwrite prior memory immediately.
- [x] Decide and implement where staged candidates live (`user_memory_candidate`, Flyway `V10`).
- [x] Expose candidate review (accept/reject) over HTTP with ownership and generation checks.
- [ ] Add the Android candidate-review UI.
- [ ] Decide whether an evidence threshold should auto-promote candidates (currently manual confirmation only).
- [ ] Tests: `MemoryConflictResolutionTest` for the full hybrid matrix.

### 4.4 Concurrency & Tombstone Safety

- [x] In the commit transaction, lock `user_memory_control` with `SELECT ... FOR UPDATE`.
- [x] Compare the locked `generation` against the immutable `expected_generation`; abort on mismatch.
- [x] Verify `enabled = true` under the same lock; abort otherwise.
- [x] Apply the `version` precondition (OCC) before writing the profile.
- [x] Commit profile JSONB and version increment atomically.
- [x] Mark stale/disabled/version-drift jobs `SKIPPED`.
- [x] Claim pending jobs with `FOR UPDATE SKIP LOCKED`.
- [x] Retry retryable extraction failures up to `max-attempts`, then mark `FAILED`.
- [x] Requeue stale `PROCESSING` jobs at startup and on a recovery interval.
- [x] Implement `MEM-TEST-01` through `MEM-TEST-07` from [05-memory-lifecycle.md](./05-memory-lifecycle.md#7-verification--concurrency-test-suite-requirements).

### 4.5 Prompt Injection

- [x] Fetch the style profile during context assembly (read-only; no provisioning).
- [x] Include the profile only when `enabled = true`.
- [x] Serialize allow-listed fields in a fixed order into an advisory system prompt that states current user instructions take precedence.
- [x] Skip `no_strong_preference` and missing fields; inject nothing when no field is specified.
- [x] Keep the bounded conversation-history window and payload limits unchanged.
- [x] Omit memory while disabled or when the profile is empty/purged.
- [x] Treat profile lookup failures as non-fatal so chat proceeds.
- [x] Tests: enabled injection, disabled omission, empty profile, all-unspecified profile.
- [ ] Test current-instruction dominance explicitly and assert no injection after a purge.
- [ ] Unit-test the non-fatal lookup-failure path.

### 4.6 Android Memory UI

- [ ] Add a memory screen reachable from the main app.
- [ ] Display enabled state, version, and active preferences.
- [ ] Allow manual editing with OCC `expectedVersion`; surface `409 version_conflict` and refresh.
- [ ] Allow toggling memory on/off.
- [ ] Allow purging with a confirmation dialog.
- [ ] List pending inferred candidates with evidence and evidence count.
- [ ] Accept/reject candidates and handle `404`/`400` outcomes.
- [ ] Never claim personalization is active while memory is disabled.
- [ ] Unit tests for the memory repository/error mapping; smoke test for the flow.

### 4.7 Documentation & CI

- [x] Update the API contract, domain model, architecture, security, and testing docs for the implemented governance surface.
- [x] Add this Phase 2 checklist and update the overview roadmap.
- [x] Keep the test inventory and migration baseline current through extraction and Flyway V10.
- [ ] Add CI coverage for the memory suites; no live provider calls.

---

## 5. Phase 2 Test Coverage Target

| Area | Required Verification | Status |
| :--- | :--- | :--- |
| Governance API | Read/replace/toggle/purge/candidate-review happy paths, validation, OCC conflict, auth isolation | Implemented (backend; candidate ownership test included) |
| Eligibility gate | `COMPLETED` only; no jobs for `PENDING`/`FAILED`/`UNKNOWN` or disabled memory | Implemented (backend tests) |
| Extraction & hybrid policy | JSON extraction, allow-list re-validation, explicit merge, inferred staging, ephemeral discard | Implemented (explicit/inferred/malformed tests) |
| Concurrency | Generation mismatch skips, disabled commit skips, OCC collision, deletion race | Implemented (MEM-TEST-01..07 and related concurrency tests) |
| Injection | Enabled profile injected, disabled/empty/unspecified profile omitted, current instruction wins | Implemented (injection/omission tests; dominance test pending) |
| Privacy | No biographical extraction, no prompt/token leakage in logs or audit rows | Partial (guardrails implemented; audit test pending) |
| Android | Memory screen CRUD, conflict handling, toggle/purge, candidate confirmation, error parsing | Not started |
| Build | `mvnw clean verify`, `gradlew assembleDebug`, CI green | Local verification green; CI pending |

All tests must run offline against `MockAIProvider` and Testcontainers PostgreSQL.

---

## 6. Requirements Traceability

| Requirement | Phase 2 Deliverable |
| :--- | :--- |
| **FR-006** Persistent Style Profile Retrieval | Governance API, profile read, and advisory injection *(implemented)* |
| **FR-007** Candidate Extraction & Eligibility Gate | Extraction pipeline gated on `COMPLETED` *(implemented)* |
| **FR-008** Hybrid Persistence & Conflict Resolution | Policy classifier, staging, OCC merge, candidate confirmation *(implemented)* |
| **FR-009** User Memory Governance | Read, replace, toggle, purge *(implemented)*; Android UI pending |
| **NFR-002** Privacy & Data Minimization | Extraction prompt guardrails *(implemented)* and privacy audit tests |
| **NFR-005** Maintainability | Memory module isolation behind service boundaries |
| **NFR-006** Testability | Deterministic extraction doubles and concurrency suites |

---

## 7. Risks & Open Decisions

1. **Extraction provider cost and latency:** Extraction adds a second model call per completed response. Batch or debounce if cost becomes material.
2. **Candidate promotion policy:** Pending inferred candidates accumulate evidence and can be manually accepted or rejected. Decide whether repeated evidence should auto-promote a candidate or whether confirmation remains strictly manual.
3. **Background execution model:** Extraction uses in-process fixed-delay pollers with `FOR UPDATE SKIP LOCKED` claiming, bounded retries, and stale-processing recovery. Claims are safe across instances; keep `stale-after-seconds` above the provider timeout. Durable retry audit/backoff tuning remains a future hardening item.
4. **Immediate vs eventual injection:** A newly extracted preference may not affect the very next response because extraction is asynchronous. Document this so the UI does not overpromise.
5. **Richer profile schema:** Do not expand the allow-list without a migration and validation update.
6. **Prompt-injection safety:** Profile values are fixed enums, which limits injection risk. Keep free-form text out of the profile.
7. **Android offline/conflict UX:** `409 version_conflict` needs a clear refresh-and-retry flow.

---

## 8. Phase 2 Exit Gate

Phase 2 is complete only when every item is verified:

- [x] Backend governance endpoints (read, replace, toggle, purge) pass their tests.
- [x] OCC `version` conflicts return `409 version_conflict`.
- [x] Allow-list violations return `400 invalid_request`.
- [x] Purge increments `generation` and clears the profile atomically.
- [x] Extraction runs only for `COMPLETED` responses and never blocks chat latency.
- [x] Explicit durable preferences merge; inferred preferences are staged; ephemeral instructions are discarded.
- [x] Candidate confirmation (accept/reject) is exposed over HTTP with ownership and generation checks.
- [x] Prompt assembly injects the profile only when enabled and omits it when disabled, empty, or all-unspecified.
- [x] `MEM-TEST-01`..`MEM-TEST-07` pass against Testcontainers PostgreSQL.
- [ ] Privacy audit proves no biographical extraction and no sensitive logging.
- [ ] Android memory UI supports inspect, edit, toggle, purge, and candidate review with conflict handling.
- [ ] `mvnw clean verify` and `gradlew assembleDebug` pass in CI.
- [x] API contract, domain model, memory spec, and test inventory match delivered behavior (reconciled through candidate review and Flyway V10; will be re-checked as the Android UI lands).
