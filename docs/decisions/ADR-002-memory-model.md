# ADR-002: Communication-Style Memory Model & Concurrency Control

| Attribute | Value |
| :--- | :--- |
| **Status** | Accepted |
| **Deciders** | Bryan, MIRA Architecture Team |
| **Date** | 2026-10-10 |
| **Technical Story** | [05-memory-lifecycle.md](../05-memory-lifecycle.md) |

---

## Context and Problem Statement

AI conversational assistants frequently lack persistence across sessions, forcing users to repeatedly specify communication preferences (such as depth of explanation, formatting, and tone). Conversely, systems that indiscriminately record all user statements risk storing sensitive personal facts or treating transient task instructions as permanent rules. Furthermore, asynchronous extraction workflows introduce critical race conditions where stale background tasks can overwrite manual user edits or restore deleted memory profiles.

We need to decide:
1. What data constitutes MIRA's persistent memory.
2. The policy for promoting conversational signals into persistent memory.
3. How to guarantee data integrity across concurrent user edits, background extractions, and profile deletions.

## Decision Drivers

* **Privacy & Data Minimization:** Prevent extraction of personal biography, health, financial, or political information.
* **User Sovereignty:** Ensure that direct user edits and deletions always override automated model inferences.
* **Deterministic Concurrency:** Prevent race conditions between background extraction tasks and interactive user mutations.
* **Predictable AI Ingestion:** Exclude transient prompt instructions from long-term memory.

## Considered Options

1. **Option 1: Unbounded Vector Store / Semantic Long-Term Knowledge Graph**
2. **Option 2: Strict Communication-Style Profile with Hybrid Persistence and Optimistic Concurrency Control (OCC)**
3. **Option 3: Manual-Only Preference Settings (No Automated Extraction)**

---

## Decision Outcome

**Chosen Option: Option 2 — Strict Communication-Style Profile with Hybrid Persistence and Optimistic Concurrency Control (OCC)**.

### Key Tenets of the Chosen Model

1. **Bounded Style Scope:** Memory is strictly restricted to defined communication-style attributes (`explanation_depth`, `tone`, `examples`, `formatting`, `teaching_sequence`). Biographical and personal facts are strictly out of scope.
2. **Hybrid Persistence Policy:**
   * Explicit durable statements (e.g., *"Always explain concepts from first principles"*) are validated and merged automatically.
   * Ambiguous or inferred preferences require repeated evidence or explicit user confirmation.
   * Ephemeral task instructions are discarded.
3. **Response Completion Gate:** Memory extraction is permitted **only** when an assistant response reaches `COMPLETED` status. In-flight (`PENDING`), failed (`FAILED`), or unresolved (`UNKNOWN`) responses are strictly ineligible.
4. **Optimistic Concurrency Control (OCC):** Every update transaction requires an `expectedVersion` check, preventing stale extraction workers from overwriting newer user modifications.
5. **Generation Counter (Tombstone Defense):** Deleting memory increments an atomic `generation` counter. Any running extraction task carrying an older generation number is immediately aborted, preventing resurrecting deleted memory.

---

## Pros and Cons of the Options

### Option 2: Hybrid Style Profile with OCC (Chosen)
* **Good:** Guarantees user privacy by constraining memory to stylistic parameters.
* **Good:** Complete protection against race conditions and stale overwrite bugs.
* **Good:** Zero chance of delayed tasks restoring purged memory.
* **Good:** Testable deterministically without third-party AI provider calls.
* **Bad:** Requires careful schema validation and version tracking in application logic.

### Option 1: Unbounded Vector Store / Semantic Graph
* **Good:** Capable of storing arbitrary facts and conversational memories.
* **Bad:** Severe privacy risks: easily ingests passwords, personal life events, and private user data.
* **Bad:** High operational cost: requires vector databases, embedding model API calls, and complex similarity tuning.
* **Bad:** Nondeterministic retrieval and vulnerability to prompt injection attacks.

### Option 3: Manual-Only Settings
* **Good:** Trivially simple to implement.
* **Bad:** Defeats the primary core value proposition of MIRA (adaptive, memory-informed learning).

---

## Consequences & Follow-up Actions

* Implement `USER_STYLE_PROFILE` in PostgreSQL using JSONB with an OCC `version` column.
* Store the durable `generation` counter in a separate `USER_MEMORY_CONTROL` row so it survives physical deletion of the profile (implemented in Flyway `V5`/`V6`).
* Build automated concurrency test suites validating that stale tasks abort upon version or generation divergence.

### Delivery Status (2026-10-21)

The schema portion of this decision is implemented. `user_style_profile.profile`, `version`, and `enabled` are defined in `V5`; `user_memory_control.generation` is defined in `V6`. The extraction pipeline, governance endpoints, prompt injection, and concurrency suites are scheduled for Phase 2 as described in [10-phase-1-checklist.md](../10-phase-1-checklist.md#9-phase-2-preview).
