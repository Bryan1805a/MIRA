# MIRA — Memory Lifecycle

**Document:** `docs/05-memory-lifecycle.md`  
**Status:** Draft  
**Scope:** Persistent communication-style memory  
**Related documents:** `00-overview.md`, `01-requirements.md`, `02-architecture.md`, `03-domain-model.md`, `04-api-contract.md`

## 1. Purpose

This document defines how MIRA discovers, validates, stores, applies, updates, disables, and deletes communication-style preferences.

The objective is to provide useful personalization without treating every user message as a permanent preference.

Memory must be:

- Limited to the MVP's communication-style scope.
- Structured and validated.
- Consistent across conversations.
- Editable and removable by the user.
- Protected against stale or concurrent updates.
- Testable independently of language-model output.
- Predictable when memory is disabled or deleted.

## 2. Scope and Terminology

### 2.1 Conversation history

The persisted messages belonging to a conversation.

Conversation history provides context for answering the current request. It is not automatically a permanent preference.

### 2.2 Candidate preference

A proposed communication-style preference extracted from conversation content.

A candidate is not authoritative. It must pass validation and conflict checks before it can change the persistent profile.

### 2.3 Persistent style profile

The structured set of communication preferences currently accepted by MIRA.

The backend is the authoritative owner of this profile.

### 2.4 Memory extraction

A process that analyzes relevant conversation content and proposes changes to the style profile.

Extraction may use a language model, but its output must be treated as untrusted structured data.

### 2.5 Profile version

A monotonically increasing revision number used to detect conflicting updates.

A profile version changes whenever the persisted profile or its effective settings change, according to the implementation's versioning policy.

## 3. Memory Scope

The MVP stores communication-style preferences only.

Examples of permitted preference categories:

- Explanation depth.
- Preferred tone.
- Use of concrete examples.
- Formatting preferences.
- Preferred teaching or explanation sequence.

Examples of information outside the intended memory scope:

- Personal biography and life events.
- Health information.
- Financial information.
- Political or religious beliefs.
- Passwords, API keys, and authentication secrets.
- Temporary task instructions that do not indicate a durable preference.

The absence of a preference must not be interpreted as evidence of the opposite preference.

For example, if the user has never expressed a preference for diagrams, MIRA must not assume that diagrams are unwanted.

Conversation history may still contain sensitive information. Memory scope alone does not establish a complete privacy policy. Conversation retention, access, logging, account deletion, and backup deletion require separate controls.

## 4. Profile Schema

The application defines a controlled set of fields and permitted values.

Illustrative schema:

| Field | Example values |
|---|---|
| `explanation_depth` | `concise`, `balanced`, `detailed` |
| `tone` | `formal`, `conversational` |
| `examples` | `prefer_concrete_examples`, `no_strong_preference` |
| `formatting` | `structured`, `prose`, `no_strong_preference` |
| `teaching_sequence` | `principles_first`, `examples_first`, `no_strong_preference` |

This is a proposed initial schema, not a final product requirement.

Implementation rules:

1. Every field has a defined type and allowed values.
2. Missing fields are treated as unspecified.
3. Unknown fields are rejected or explicitly discarded according to a documented validation policy.
4. Model-generated values must never bypass validation.
5. The profile must not contain arbitrary executable instructions.
6. The backend serializes validated profile data into controlled prompt context.

The database may store this structure as PostgreSQL JSONB, but JSONB flexibility must not become unrestricted application-level schema flexibility.

## 5. Candidate Classification

Candidates are classified by their supporting evidence.

### 5.1 Explicit durable preference

The user directly indicates an ongoing preference.

Example:

"From now on, explain difficult concepts from first principles before showing code."

This is a strong candidate for a persistent profile update.

### 5.2 Repeated preference

The same preference appears consistently across multiple interactions.

Example:

The user repeatedly asks for a concrete example before a mathematical derivation.

This may justify a candidate, but repetition alone does not prove the preference is permanent.

### 5.3 Context-specific instruction

The user requests a temporary response behavior.

Example:

"Keep this answer short."

This applies to the current request unless additional evidence indicates a durable preference.

### 5.4 Ambiguous candidate

The evidence does not reliably establish a preference.

Example:

The user asks for a table once but does not indicate whether this is a general preference.

The candidate should normally be discarded rather than persisted.

### 5.5 Explicit correction

The user corrects an existing preference.

Example:

"I no longer want every explanation to start with an analogy."

This correction takes precedence over an older inferred preference.

### 5.6 Hybrid Memory Persistence Policy

MIRA uses a hybrid policy to balance personalization convenience with memory accuracy.

#### Explicit durable preferences

A preference may be persisted automatically when the user clearly expresses an ongoing communication-style preference.

Examples:

- "From now on, explain technical concepts from first principles."
- "I generally prefer concrete examples before abstract explanations."

Before persistence, the backend must validate the candidate's scope, field, value, and consistency with the latest profile state.

#### Inferred preferences

Preferences inferred from behavior must not be persisted automatically based on a single ambiguous interaction.

They require either:

- Explicit user confirmation, or
- Sufficient supporting evidence under a documented validation policy.

If neither condition is satisfied, the candidate remains unpersisted.

#### Temporary instructions

Instructions scoped to a particular response, task, or conversation are not durable preferences by default.

#### Corrections and deletion

Explicit user corrections take precedence over older inferred preferences. Memory deletion invalidates pending updates associated with the deleted profile generation.

#### Acceptance criteria

- Explicit durable preferences can be saved without a confirmation prompt after validation.
- Inferred preferences require confirmation or sufficient supporting evidence.
- Temporary instructions are not automatically promoted to persistent preferences.
- No candidate can bypass validation or overwrite a newer user correction.

## 6. Candidate Extraction and Validation

Extraction and persistence are separate operations.

### Stage 1: Select evidence

Use relevant conversation messages to identify possible communication-style preferences.

Avoid extracting preferences from unrelated personal content.

### Stage 2: Produce structured candidates

The extraction process proposes a field, value, evidence classification, and the applicable scope.

The output must conform to a strict schema.

### Stage 3: Validate

The backend verifies:

- The field is supported.
- The value is permitted.
- The candidate concerns communication style.
- The evidence supports a persistent preference.
- The candidate does not conflict with a newer explicit correction.
- The candidate is not merely a temporary task instruction.

Invalid candidates are rejected.

### Stage 4: Resolve conflicts

If a candidate conflicts with an existing preference, the system must not silently overwrite the existing value.

An explicit user correction has priority over older inferred evidence.

Ambiguous conflicts should be discarded or presented to the user for clarification rather than resolved through arbitrary model confidence scores.

### Stage 5: Persist

Apply the accepted changes atomically using the current profile version as a concurrency precondition.

### Stage 6: Verify

Confirm that the saved profile reflects the accepted update and that its version has advanced as expected.

## 7. Memory Update Timing

The MVP does not require extraction after every message.

Possible strategies include:

- Extraction after a conversation reaches a suitable checkpoint.
- Extraction when a conversation ends.
- Explicit user-triggered memory updates.

The initial implementation should favor a bounded, controlled extraction workflow rather than continuously processing every message.

The chosen trigger must account for API cost, latency, missed preferences, and the risk of extracting temporary instructions.

Memory extraction failure must not prevent ordinary chat responses from succeeding.

A failed extraction may be retried according to a bounded retry policy, provided the task remains valid and its expected profile state has not changed.

## 8. Applying Memory to Responses

For each new AI request:

1. Authenticate the user.
2. Load the relevant conversation context.
3. Read the user's current style-profile state.
4. If memory is enabled, serialize the validated profile into controlled context.
5. Generate the response through the configured AI provider.
6. Persist the result according to the chat transaction policy.

When memory is disabled, the profile must not influence newly assembled model requests.

The system must distinguish persistent style memory from:

- System-level instructions.
- Developer-controlled instructions.
- The user's current message.
- Conversation-specific context.

Memory is advisory personalization, not an authorization mechanism. It must never override security rules or grant the model additional permissions.

## 9. Manual Editing

Users must be able to inspect and modify their saved style preferences.

A manual edit is an authoritative user action.

When a manual edit succeeds:

- Validate the updated profile.
- Verify the expected profile version.
- Persist the change atomically.
- Increment the version.
- Ensure older inferred candidates cannot overwrite the correction.

If the profile changes between the client's read and update, return a version conflict and require the client to refresh or reconcile the profile.

The MVP may use a simple settings interface rather than an elaborate memory-management interface.

## 10. Disabling Memory

Disabling memory means retaining the saved profile while stopping its use for future requests.

Required behavior:

- Persist the disabled state.
- Exclude the profile from newly assembled model requests.
- Keep conversation history behavior independent of the memory setting.
- Make the setting visible to the user.
- Define how requests already in progress are handled.

For the MVP, a request that has already assembled its model context may complete using that context. The application must not claim that disabling memory retroactively removes information from requests already sent to an external provider.

After disabling memory, new requests must not receive the profile as personalization context.

## 11. Deleting Memory

Deleting memory means removing the persisted communication-style profile and ensuring that stale work cannot restore it.

The deletion workflow must:

1. Authenticate and authorize the request.
2. Invalidate pending memory updates associated with the previous profile state.
3. Remove the profile or record an equivalent deletion state.
4. Ensure older tasks cannot recreate the deleted profile.
5. Return a clear success or failure result.
6. Verify deletion through subsequent reads and tests.

Deleting style memory does not automatically delete conversation history.

Deleting conversation history does not automatically delete style memory.

### 11.1 Protecting against stale recreation

A simple delete followed by unrestricted profile insertion is insufficient.

The implementation must use a concurrency-safe mechanism, such as a persistent profile-state record with a generation/version counter, or an equivalent tombstone mechanism.

A deletion operation must advance or invalidate the relevant generation so that tasks created against an older generation cannot write a new profile.

If the implementation physically deletes the profile row, it must still preserve enough authoritative state elsewhere to reject stale writes.

The exact mechanism will be specified during database and transaction implementation.

## 12. Concurrency and Versioning

Every update must be conditional on the expected current state.

Conceptually:

1. Read profile version \(v\).
2. Compute and validate a candidate.
3. Attempt an atomic update only if the current version remains \(v\).
4. If successful, advance to \(v+1\).
5. If the condition fails, discard or re-evaluate the candidate against the latest state.

Manual edits and deletion operations must take precedence over stale background extraction work.

Version checks must occur at the persistence boundary, not solely in application memory.

## 13. Failure Handling

| Failure | Required behavior |
|---|---|
| Extraction output is malformed | Reject candidate; keep current profile |
| Candidate contains an unsupported field | Reject or discard according to validation policy |
| AI provider is unavailable | Continue to report the chat failure correctly; do not corrupt memory |
| Database update fails | Do not report memory update success |
| Profile version has changed | Reject stale update and reconcile |
| Memory is disabled | Do not apply the profile to new requests |
| Memory has been deleted | Prevent stale tasks from recreating it |
| Extraction fails after a successful chat response | Keep the chat response successful; record a safe diagnostic |

Errors and diagnostics must not expose credentials, raw secrets, or unnecessary sensitive conversation content.

## 14. Privacy and Retention

- Collect only communication-style preferences required for personalization.
- Provide explicit controls for viewing, editing, disabling, and deleting memory.
- Keep style memory separate from full conversation history.
- Do not place API keys, passwords, or authentication tokens in memory.
- Document whether conversation content is sent to the external AI provider.
- Define conversation retention and account-deletion behavior separately.
- Document backup retention and the expected time until deleted data is purged from backups.
- Avoid storing raw extraction prompts and outputs unless required for a justified debugging purpose.

## 15. Evaluation Strategy

Memory quality must be evaluated with a small, version-controlled test set.

### Test A: Cross-conversation persistence

Given an accepted preference, verify that a new conversation uses the same preference when memory is enabled.

### Test B: Disabled memory

Given a saved profile, disable memory and verify that new requests do not include its personalization context.

### Test C: Temporary instruction

Given a request such as "Keep this answer short," verify that it does not automatically become a persistent preference.

### Test D: Explicit preference

Given an explicit durable preference, verify that extraction proposes the expected candidate and that validation accepts it.

### Test E: Manual correction

Given an existing preference, manually change it and verify that older inferred candidates cannot restore the previous value.

### Test F: Deletion

Delete the profile, then complete a previously scheduled extraction task. Verify that the profile remains deleted.

### Test G: Concurrent updates

Run two updates against the same expected version. Verify that stale updates cannot silently overwrite newer state.

### Test H: Invalid extraction output

Supply malformed or unsupported candidate data and verify that the persistent profile remains unchanged.

Evaluation should measure correctness and safety before optimizing extraction frequency or personalization strength.

## 16. Acceptance Criteria

The MVP memory subsystem is acceptable when:

- Only supported communication-style fields can be persisted.
- Explicit corrections override older inferred preferences.
- Temporary instructions are not automatically stored as durable preferences.
- The profile persists across conversations.
- Users can inspect, edit, disable, and delete the profile.
- Disabling memory prevents it from influencing newly assembled requests.
- Deletion cannot be undone by a stale extraction task.
- Concurrent updates cannot silently overwrite newer changes.
- Extraction failures do not corrupt existing profile data.
- Memory behavior can be tested without paid AI-provider calls.

## 17. Open Decisions

- Exact profile fields and allowed values.
- Extraction trigger and scheduling policy.
- Whether users must approve inferred changes before persistence.
- Whether explicit user statements can be applied immediately.
- Whether profile revisions must be retained for audit or recovery.
- Exact generation/tombstone mechanism for deletion.
- Behavior for already-running requests after disablement or deletion.
- Conversation and backup retention policies.
- Whether extraction output is persisted temporarily for retries.

These decisions must be resolved before memory extraction and persistence are implemented.