# MIRA — Communication-Style Memory Lifecycle Specification

| Attribute | Value |
| :--- | :--- |
| **Document ID** | MIRA-MEM-001 |
| **Classification** | Internal Engineering Specification |
| **Status** | Approved Baseline |
| **Version** | 1.7.0 |
| **Last Updated** | 2026-10-29 |
| **Author / Lead** | Bryan / MIRA Personalization & Memory Team |
| **Scope** | Persistent Communication-Style Preferences |

---

## Document Revision History

| Version | Date | Author | Description of Changes |
| :--- | :--- | :--- | :--- |
| 0.1.0 | 2026-10-10 | Memory Team | Initial memory scope, schema draft, and extraction lifecycle definition. |
| 1.0.0 | 2026-10-11 | Bryan | Enterprise standardization: integrated visual Mermaid extraction pipeline, eligibility gate specification, and generation tombstone mechanics. |
| 1.1.0 | 2026-10-11 | Bryan | Integrated durable `USER_MEMORY_CONTROL` generation table, atomic row-level locking deletion protocol, and robust deletion-race verification suite. |
| 1.2.0 | 2026-10-21 | Bryan | Added the delivery-phase status, confirmed the generation/profile table split against Flyway V5–V6, and clarified that this specification is the Phase 2 target. |
| 1.3.0 | 2026-10-26 | Bryan | Reconciled Phase 2 backend delivery: governance endpoints, allow-list validation, OCC conflict handling, and generation-guarded purge are implemented. Extraction, hybrid policy staging, prompt injection, Android UI, and MEM-TEST-01..07 remain pending. |
| 1.4.0 | 2026-10-27 | Bryan | Reconciled advisory prompt injection: enabled profiles are appended as a system message, unspecified fields are skipped, and disabled/empty profiles are omitted. Extraction, hybrid policy, Android UI, and MEM-TEST-01..07 remain pending. |
| 1.5.0 | 2026-10-27 | Bryan | Reconciled the asynchronous extraction pipeline (Flyway V10): `COMPLETED`-only scheduling, hybrid persistence, candidate staging, and generation/version commit guards are implemented. Candidate confirmation, Android UI, and race suites remain pending. |
| 1.6.0 | 2026-10-27 | Bryan | Reconciled backend candidate review: pending inferred candidates can be listed, accepted with generation/allow-list validation and OCC, or rejected; purge deletes pending candidates. Android UI and race suites remain pending. |
| 1.7.0 | 2026-10-29 | Bryan | Reconciled extraction retry/recovery (bounded attempts, stale-processing requeue, skip-locked claim) and marked the MEM-TEST race scenarios covered. Android UI and privacy audit remain pending. |

---

## Implementation Status (2026-10-27)

The durable schema, governance surface, advisory injection, and asynchronous extraction pipeline are in place:

- `user_style_profile` (`V5`) stores the JSONB profile, `version`, and `enabled`.
- `user_memory_control` (`V6`) stores the durable `generation` tombstone counter.
- `memory_extraction_job` and `user_memory_candidate` (`V10`) store extraction jobs and staged inferred candidates.
- `MemoryController` and `StyleProfileService` implement:
  - `GET /api/v1/memory/style` with first-read provisioning (`enabled = true`, `version = 1`, empty profile).
  - `PUT /api/v1/memory/style` with allow-list validation and OCC (`409 version_conflict`).
  - `PATCH /api/v1/memory/style/settings` to toggle `enabled`.
  - `DELETE /api/v1/memory/style` to purge the profile and increment `generation` under a row lock.
- `StyleProfileService.activeStylePrompt(userId)` implements advisory injection:
  - The enabled profile is serialized in a fixed field order and appended as a dedicated system message after the base instruction.
  - `no_strong_preference` values and missing fields are skipped; a profile with no specified values produces no message.
  - Disabled profiles, absent rows, and empty profiles inject nothing.
  - Injection is advisory and states that current explicit user instructions take precedence.
  - A profile-lookup failure is swallowed so chat remains available.
- The extraction pipeline is implemented:
  - `MessageService` schedules a job only after a response reaches `COMPLETED`; scheduling failures never fail the chat request.
  - `MemoryExtractionScheduler` polls `PENDING` jobs on a configurable fixed delay (`mira.memory.extraction.scheduler-enabled`, `poll-interval-ms`) and drains the queue.
  - Job claiming uses `FOR UPDATE SKIP LOCKED`, so concurrent pollers cannot claim the same row.
  - `MemoryExtractionWorker` verifies the turn is `COMPLETED`, calls the provider in JSON mode, validates the allow-list and classifications, and records job outcomes (`COMPLETED`, `SKIPPED`, `FAILED`).
  - Retryable failures return the job to `PENDING` until `mira.memory.extraction.max-attempts` is reached, after which it becomes `FAILED`.
  - `MemoryExtractionRecovery` requeues jobs stuck in `PROCESSING` beyond `stale-after-seconds` at startup and on a recovery interval (`recovery-interval-ms`), recording `STALE_PROCESSING`.
  - `MemoryExtractionCommitService` locks the control row, profile row, and job; rejects stale generation, disabled memory, or version drift with `SKIPPED`; merges explicit durable/correction updates; stages inferred candidates; and discards ephemeral candidates.
  - Repeated evidence for the same pending inferred candidate increments `evidence_count`.
- Candidate review is implemented:
  - `GET /api/v1/memory/candidates` lists pending inferred candidates for the authenticated user, newest first.
  - `POST /api/v1/memory/candidates/{id}/accept` locks the control/profile/candidate rows, validates `generation` and the allow-list, applies the value (incrementing `version` only when it changes), and marks the candidate `ACCEPTED`.
  - `POST /api/v1/memory/candidates/{id}/reject` marks the candidate `REJECTED` without changing the profile.
  - Purge deletes `PENDING` candidates in the same transaction.
- Integration tests cover provisioning, OCC conflict, allow-list rejection, toggling, purge/generation behavior, injection, extraction scheduling/eligibility, explicit merge, inferred staging, stale-generation skip, retry exhaustion, stale-processing recovery, malformed output, candidate list/accept/reject/ownership, and the `MEM-TEST-01`..`MEM-TEST-07` scenarios.

Not yet implemented: the Android memory UI, the extraction privacy audit, an automatic evidence threshold for promoting staged candidates, current-instruction dominance coverage, and a unit test for the non-fatal injection lookup path. The active plan is [11-phase-2-checklist.md](./11-phase-2-checklist.md).

---

## 1. Core Memory Axioms & Design Boundaries

The MIRA personalization engine is built upon five foundational axioms:

1. **Memory Informs, Never Defines:** The memory profile stores *communication style* preferences only. It never stores biographical facts, personal identifiers, health data, finances, or political stances.
2. **Current Request Dominance:** Immediate, explicit prompt instructions in an active conversation (e.g., *"Provide this specific answer in bullet points"*) **always** take precedence over historical style preferences.
3. **Manual Overrides Automated:** A direct user edit or deletion in profile settings **always** overrules and invalidates automated background model extractions.
4. **Extraction Decoupled from Ingestion:** Chat responses are delivered to users without waiting for memory extraction. Extraction runs asynchronously and never impacts chat latency or response availability.
5. **Strict Eligibility Gate:** Memory extraction is permitted **only** on messages associated with successfully persisted assistant responses in `COMPLETED` status. In-flight (`PENDING`), failed (`FAILED`), or unresolved (`UNKNOWN`) responses are strictly ineligible.

---

## 2. Memory Extraction & Ingestion Pipeline

```mermaid
flowchart TD
    subgraph MessageLifecycle ["1. Message Completion Gate"]
        MsgSaved["Assistant Message Persisted"]
        CheckStatus{"Response Status == COMPLETED?"}
        MsgSaved --> CheckStatus
        CheckStatus -- No (PENDING / FAILED / UNKNOWN) --> AbortGate["Halt: Ineligible for Extraction"]
        CheckStatus -- Yes --> TriggerExtraction["Eligible: Schedule Extraction Task"]
    end

    subgraph ExtractionPipeline ["2. Candidate Extraction & Validation"]
        TriggerExtraction --> CaptureExpectedGen["Capture Immutable expected_generation\n& Profile version"]
        CaptureExpectedGen --> ParseSignals["LLM Analyzes Message Turn"]
        ParseSignals --> ValidateSchema{"Conforms to Allowed Schema?"}
        ValidateSchema -- No --> DiscardCandidate["Discard Malformed Candidate"]
        ValidateSchema -- Yes --> ClassifyPolicy{"Classify Candidate Policy"}
    end

    subgraph PolicyEngine ["3. Hybrid Persistence Engine"]
        ClassifyPolicy -- "Ephemeral Instruction" --> DiscardCandidate
        ClassifyPolicy -- "Weak / Inferred Preference" --> HoldForEvidence["Stage Candidate / Await Confirmation"]
        ClassifyPolicy -- "Explicit Durable Preference" --> ProceedToCommit["Proceed to Atomic Transaction Gate"]
    end

    subgraph ConcurrencyGate ["4. Transactional Lock & Generation Gate"]
        ProceedToCommit --> LockControl["BEGIN TX; Lock USER_MEMORY_CONTROL\n(SELECT FOR UPDATE)"]
        LockControl --> CheckGen{"current_generation == expected_generation?"}
        CheckGen -- No (User Deleted Profile Concurrently) --> AbortStale["ROLLBACK; Stale Task Terminated"]
        CheckGen -- Yes --> CheckEnabled{"Memory Still Enabled?"}
        CheckEnabled -- No --> AbortDisabled["ROLLBACK; Disabled Profile Unmodified"]
        CheckEnabled -- Yes --> CheckOCC{"version == expectedVersion?"}
        CheckOCC -- No --> AbortOCC["ROLLBACK; Version Conflict Handled"]
        CheckOCC -- Yes --> CommitUpdate["Persist Profile JSONB, Increment Version\nCOMMIT TX"]
    end
```

> [!NOTE]
> Implemented in `MemoryExtractionWorker` / `MemoryExtractionCommitService`. The gate, schema validation, and commit guards are live. Staged inferred candidates are persisted for evidence accumulation and exposed through the candidate-review endpoints; the Android review UI is still pending.

---

## 3. Communication-Style Profile Schema

The user's persistent style preferences are stored as a strictly validated JSONB document in the `USER_STYLE_PROFILE` table.

### 3.1 Allowed Fields & Enum Values

| Field Name | Type | Allowed Values | Semantic Definition |
| :--- | :--- | :--- | :--- |
| `explanation_depth` | String | `concise`, `balanced`, `detailed` | Preferred depth and breadth of technical explanations. |
| `tone` | String | `conversational`, `formal`, `academic` | Tone and stylistic register of the generated output. |
| `examples` | String | `prefer_concrete_examples`, `theory_first`, `no_strong_preference` | Whether to anchor concepts in concrete examples or abstract theory. |
| `formatting` | String | `structured`, `prose`, `bulleted_lists`, `no_strong_preference` | Structural layout of responses (markdown headings vs prose). |
| `teaching_sequence` | String | `principles_first`, `code_first`, `analogy_first`, `no_strong_preference` | Ordering of pedagogical presentation. |

### 3.2 Schema Validation Invariants
* Any candidate update containing unlisted keys or unlisted enum values is rejected outright.
* Missing fields are treated as `no_strong_preference` (unspecified) and fall back to default assistant behavior.
* The JSON document cannot contain nested executable code, markdown prompts, or free-form arbitrary text fields.

---

## 4. Candidate Classification & The Hybrid Persistence Policy

Candidate style attributes extracted from conversational turns are categorized according to evidence strength:

```mermaid
stateDiagram-v2
    [*] --> Classification

    state Classification {
        ExplicitDurable: Explicit Durable Preference\n('From now on, explain from first principles')
        RepeatedInferred: Repeated Inferred Preference\n(User repeatedly asks for analogies)
        EphemeralTask: Context-Specific Instruction\n('Make this response brief')
        ExplicitCorrection: Explicit Correction\n('Stop using analogies')
    }

    ExplicitDurable --> AutoPersist: Validated & Persisted Automatically
    RepeatedInferred --> AwaitConfirmation: Staged / Requires User Confirmation
    EphemeralTask --> Discarded: Excluded from Profile
    ExplicitCorrection --> HighPriorityPersist: Overwrites Prior Memory Immediately
```

### 4.1 Candidate Taxonomy & Handling Rules

1. **Explicit Durable Preference:**
   * *User Utterance:* *"From now on, always give me concrete Java examples before explaining the theory."*
   * *Action:* Validated against the schema and merged into the active profile automatically.
2. **Context-Specific (Ephemeral) Instruction:**
   * *User Utterance:* *"Keep this specific response under two sentences."*
   * *Action:* Applied exclusively to the current prompt context; discarded from persistent memory.
3. **Inferred Preference:**
   * *User Utterance:* User asks for code snippets across two conversations without explicitly requesting a permanent rule.
   * *Action:* Staged as an unconfirmed candidate. Not persisted to the profile without explicit user review.
4. **Explicit User Correction:**
   * *User Utterance:* *"I don't want detailed formatting anymore; just use plain prose."*
   * *Action:* High-priority update. Overwrites historical preferences immediately.

> [!NOTE]
> The backend implements all four paths: explicit durable values merge automatically, explicit corrections override same-field values, inferred candidates are staged in `user_memory_candidate` with an incremented `evidence_count`, and ephemeral candidates are discarded. Pending inferred candidates are surfaced to the user for acceptance through the candidate-review endpoints.

---

## 5. Concurrency Controls & Memory Deletion Race Protection

Because memory extraction executes asynchronously in background workers, tasks may complete **after** a user has manually updated their profile, disabled memory, or purged it entirely. MIRA coordinates these operations via database-level concurrency controls.

```mermaid
sequenceDiagram
    autonumber
    actor User as User Action
    participant API as Backend Service
    participant DB as PostgreSQL
    participant Worker as Background Extraction Worker

    Note over Worker: 1. Extraction Job Dispatched
    Worker->>DB: Read state: captures expected_generation = 1
    Worker->>Worker: LLM analyzes conversational turn...

    Note over User, DB: 2. Concurrent Memory Deletion
    User->>API: DELETE /api/v1/memory/style
    API->>DB: BEGIN TRANSACTION;
    API->>DB: SELECT * FROM user_memory_control WHERE user_id = :id FOR UPDATE;
    API->>DB: UPDATE user_memory_control SET generation = generation + 1 (gen -> 2);
    API->>DB: DELETE FROM user_style_profile WHERE user_id = :id;
    API->>DB: COMMIT TRANSACTION;
    API-->>User: 204 No Content (Memory Purged)

    Note over Worker, DB: 3. Extraction Attempt With Stale Generation
    Worker->>DB: BEGIN TRANSACTION;
    Worker->>DB: SELECT generation FROM user_memory_control WHERE user_id = :id FOR UPDATE;
    Worker->>Worker: Compare: current_generation (2) != expected_generation (1)
    Worker->>DB: ROLLBACK TRANSACTION;
    Worker->>Worker: Terminate Job: Stale Generation Detected!
    Note over DB: Profile remains completely purged. Zero resurrection.
```

### 5.1 Durable Generation Control (`USER_MEMORY_CONTROL`)
To ensure that generational counters persist even when `USER_STYLE_PROFILE` rows are physically deleted, MIRA maintains a dedicated, durable control record per user:

```sql
CREATE TABLE user_memory_control (
    user_id UUID PRIMARY KEY REFERENCES user_account(id) ON DELETE CASCADE,
    generation INTEGER NOT NULL DEFAULT 1 CHECK (generation >= 1),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```
* **Immutable Capture:** When an extraction job is created, it captures an immutable `expected_generation` value. The worker must **never** replace this value with the current generation when processing begins.

### 5.2 Atomic Deletion Protocol
Deleting a style profile must execute atomically within a single database transaction:
1. Lock the user's `user_memory_control` row using `SELECT ... FOR UPDATE`.
2. Increment its `generation` counter (`generation = generation + 1`).
3. Delete the associated `user_style_profile` record (or reset its JSONB payload).
4. Commit the transaction before reporting successful deletion (`204 No Content`) to the client.

### 5.3 Transactional Extraction Commit Protocol
Before persisting an extracted profile, the worker must execute within an atomic transaction:
1. Lock the user's `user_memory_control` record using `SELECT ... FOR UPDATE`.
2. Compare the database `generation` with the job's immutable `expected_generation`.
3. **Reject and abort** the write if the generations differ.
4. Verify that memory is currently enabled; if disabled, abort without modifying the profile.
5. Verify that the profile update satisfies the current `version` precondition (Optimistic Concurrency Control).
6. Persist the profile and commit the transaction.

> [!IMPORTANT]
> The generation check, enabled check, and profile write must occur within the **same database transaction under row lock**. An un-locked read outside the write transaction is insufficient to prevent race windows.

> [!NOTE]
> Implemented in `MemoryExtractionCommitService`: the control row, profile row, and job row are locked, and a generation/version/enabled mismatch marks the job `SKIPPED` without touching the profile. Concurrent purge/extraction and OCC collisions are covered by `MEM-TEST-03`, `MEM-TEST-06`, and the related concurrency tests.

### 5.4 Disabled Memory Behavior
* When a user toggles memory to `enabled: false`, the profile is immediately excluded from prompt context assembly for subsequent AI requests.
* Background workers must verify the `enabled` flag inside the locked commit transaction. If memory is disabled, the worker must not commit modifications to the stored profile.

### 5.5 Establishing Preferences After Deletion
* Deleting a profile permanently invalidates all background extraction work associated with earlier generations.
* Subsequent conversational activity occurring **after** deletion will capture the *new* generation number, allowing fresh preferences to be established cleanly under the current generation.
* Stale jobs created prior to deletion cannot commit simply because their execution was delayed.

---

## 6. Memory Governance Operations

Users maintain complete sovereignty over their personalization data via standard API operations:

```mermaid
flowchart LR
    subgraph GovernanceActions ["User Governance Operations"]
        View["GET /memory/style\n(Inspect active preferences)"]
        Edit["PUT /memory/style\n(Manual override with OCC)"]
        Toggle["PATCH /memory/style/settings\n(Enable/Disable toggle)"]
        Purge["DELETE /memory/style\n(Atomic generation bump & purge)"]
        Review["GET /memory/candidates\naccept / reject pending candidates"]
    end

    subgraph ProfileState ["Persistent Profile State"]
        EnabledState["Personalization Active\n(Injected into System Prompt)"]
        DisabledState["Personalization Inactive\n(Excluded from System Prompt)"]
        PurgedState["Empty Profile\n(Generation Incremented)"]
    end

    View --> EnabledState
    Edit --> EnabledState
    Review -->|accept| EnabledState
    Review -->|reject| EnabledState
    Toggle -->|enabled: false| DisabledState
    Toggle -->|enabled: true| EnabledState
    Purge --> PurgedState
```

### 6.1 State Transitions
* **Inspecting Memory:** Returns the currently active preferences, version integer, and enabled state. *(Implemented — backend)*
* **Editing Memory:** Manually replaces preferences with full schema validation; increments the version counter. *(Implemented — backend)*
* **Toggling Memory:** Setting `enabled: false` immediately excludes the profile from all future AI prompt context assemblies without deleting the underlying preference configuration. *(Implemented — backend)*
* **Purging Memory:** Atomically increments the generation counter in `USER_MEMORY_CONTROL` and purges the profile, permanently neutralizing in-flight extraction workers. *(Implemented — backend)*

### 6.2 Prompt Injection Semantics (Implemented)

When a chat request assembles provider context, `StyleProfileService.activeStylePrompt(userId)` reads the stored profile without provisioning a row and returns an advisory system message only when all of the following hold:

1. A `user_style_profile` row exists.
2. `enabled = true`.
3. At least one allow-listed field has a value other than `no_strong_preference`.

The message lists the specified fields in a fixed order (`explanation_depth`, `tone`, `examples`, `formatting`, `teaching_sequence`) and explicitly states that current user instructions take precedence. It is appended as a second `SYSTEM` message after the base assistant instruction and before conversation history. The API request/response shape does not change.

A profile lookup or formatting failure is treated as non-fatal: the chat request proceeds without memory. This preserves the "memory informs, never blocks" invariant.

> [!NOTE]
> Injection covers retrieval and prompt assembly only. Extraction of new preferences is implemented as described in sections 2–5 and runs asynchronously.

### 6.3 Candidate Review (Implemented)

Pending inferred candidates are exposed only to their owner:

| Operation | Endpoint | Behavior |
| :--- | :--- | :--- |
| List | `GET /api/v1/memory/candidates` | Returns `PENDING` candidates newest-first with field, value, evidence, evidence count, and creation time. |
| Accept | `POST /api/v1/memory/candidates/{id}/accept` | Locks control/profile/candidate, validates generation and allow-list, applies the value under OCC (version increments only on change), marks `ACCEPTED`, returns the updated profile. |
| Reject | `POST /api/v1/memory/candidates/{id}/reject` | Marks the candidate `REJECTED` and returns `204`; the profile is untouched. |

Unknown, foreign, or already-decided candidates return `404 resource_not_found`. A generation mismatch returns `400 invalid_request`. Purging memory deletes pending candidates so no stale suggestions survive.

---

## 7. Verification & Concurrency Test Suite Requirements

The memory subsystem must pass deterministic automated tests covering race conditions and lifecycle edge cases:

| Test ID | Test Scenario | Setup & Injected Condition | Expected System Behavior |
| :--- | :--- | :--- | :--- |
| **MEM-TEST-01** | Stale Task After Deletion | Extraction job starts (`gen=1`). User deletes memory (`gen=2`). Worker attempts commit. | Worker aborts commit on generation mismatch. Profile remains deleted. |
| **MEM-TEST-02** | Queued Job Execution | Multiple jobs queued under `gen=1`. User deletes memory (`gen=2`). Workers start execution. | All queued workers with `expected_generation=1` abort commit. |
| **MEM-TEST-03** | Concurrent Delete & Extraction | Thread A executes deletion while Thread B executes commit concurrently. | Serialized cleanly by `SELECT FOR UPDATE`. Winner commits; stale operation is safely aborted. |
| **MEM-TEST-04** | Commit on Disabled Memory | Job captures valid generation. User toggles `enabled=false`. Worker attempts commit. | Worker detects disabled state under transaction lock; aborts without altering profile. |
| **MEM-TEST-05** | New Profile Post-Deletion | Memory is purged (`gen=2`). User subsequently makes explicit durable statement. | New task captures `expected_generation=2` and successfully establishes a fresh profile. |
| **MEM-TEST-06** | OCC Version Collision | Two workers race to commit to the same generation with identical `expectedVersion`. | Exactly one worker succeeds; the second receives OCC conflict and aborts safely. |
| **MEM-TEST-07** | Worker & Service Restart | Background extraction job persists across server restart. | Stale job still carries its immutable `expected_generation` and is rejected if memory was purged during downtime. |

> [!NOTE]
> Implemented and covered: governance, injection, extraction (including retry exhaustion and stale-processing recovery), candidate review, and the `MEM-TEST-01`..`MEM-TEST-07` scenarios. The Android memory UI, extraction privacy audit, explicit current-instruction dominance test, and injection lookup-failure unit test remain open in [11-phase-2-checklist.md](./11-phase-2-checklist.md).
