# MIRA — Domain Model & Database Design

**Document:** `docs/03-domain-model.md`  
**Status:** Draft  
**Database:** PostgreSQL  
**Scope:** MVP — Authentication, text chat, conversation history, communication-style memory

## 1. Purpose

This document defines MIRA's initial domain entities, relationships, persistence rules, and data integrity requirements.

The design separates account identity, conversation history, individual messages, and persistent communication-style preferences.

The schema is a logical design. Physical migrations and ORM mappings will be implemented after the API contract and transaction policies are finalized.

## 2. Design Principles

- Every user's private data must be isolated by ownership.
- PostgreSQL is the authoritative persistent store.
- Foreign keys and database constraints must enforce data integrity.
- The client cannot establish identity or authorization by supplying an arbitrary user ID.
- Communication-style memory must remain independent of individual conversations.
- User corrections and deletions must not be silently reversed by stale memory extraction tasks.
- The MVP should avoid tables that have no current, justified use case.

## 3. Entity Overview

| Entity | Responsibility |
|---|---|
| `USER_ACCOUNT` | Account identity and authentication data |
| `CONVERSATION` | A user's chat session |
| `MESSAGE` | A message within a conversation |
| `USER_STYLE_PROFILE` | The user's persistent communication-style preferences |

## 4. Entity Definitions

### 4.1 USER_ACCOUNT

Represents a registered MIRA user.

| Field | Proposed type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | Primary key | Account identifier |
| `email` | VARCHAR(254) | Not null, unique after normalization | Login email |
| `password_hash` | VARCHAR(255) | Not null | Password hash |
| `created_at` | TIMESTAMPTZ | Not null | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | Not null | Last account update |

Rules:

- Passwords must never be stored in plaintext.
- Email normalization must be consistent across registration and authentication.
- Database uniqueness must be enforced even when concurrent registration requests occur.
- Authentication failures must not disclose password data.
- Email changes, if supported, must use a separate validation process.

### 4.2 CONVERSATION

Represents a chat session owned by one user.

| Field | Proposed type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | Primary key | Conversation identifier |
| `user_id` | UUID | Not null, foreign key | Owner account |
| `title` | VARCHAR(200) | Nullable | Display title |
| `created_at` | TIMESTAMPTZ | Not null | Creation timestamp |
| `updated_at` | TIMESTAMPTZ | Not null | Last conversation update |

Rules:

- A conversation belongs to exactly one account.
- Every read, update, and deletion operation must verify ownership.
- Conversation titles may initially be null and generated or assigned later.
- An index should support retrieving conversations by owner and recent update time.

### 4.3 MESSAGE

Represents a persisted message in a conversation.

| Field | Proposed type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | Primary key | Message identifier |
| `conversation_id` | UUID | Not null, foreign key | Parent conversation |
| `role` | VARCHAR(20) | Not null, constrained values | Message role |
| `content` | TEXT | Not null | Text content |
| `created_at` | TIMESTAMPTZ | Not null | Creation timestamp |

Initial role values:

- `USER`
- `ASSISTANT`

Rules:

- The backend determines the permitted message roles.
- Clients cannot submit arbitrary system or developer instructions as trusted messages.
- Messages must only be retrieved through an authorized conversation.
- An index should support retrieving messages for a conversation in chronological order.
- Empty-message and maximum-length policies must be enforced by the application and, where appropriate, the database.

The initial text-only model intentionally excludes attachment metadata, tool calls, and multimodal content structures. These can be introduced through an explicit schema extension when their requirements are defined.

### 4.4 USER_STYLE_PROFILE

Represents one user's current communication-style profile.

| Field | Proposed type | Constraints | Description |
|---|---|---|---|
| `user_id` | UUID | Primary key, foreign key | Profile owner |
| `profile` | JSONB | Not null | Structured preferences |
| `version` | INTEGER | Not null, positive | Profile revision number |
| `enabled` | BOOLEAN | Not null | Whether memory is applied |
| `updated_at` | TIMESTAMPTZ | Not null | Last profile update |

The `user_id` field is both the primary key and a foreign key to `USER_ACCOUNT`. This enforces at most one current style profile per account.

Illustrative profile:

    {
      "explanation_depth": "detailed",
      "tone": "conversational",
      "examples": "prefer_concrete_examples",
      "formatting": "structured"
    }

The example is not the final profile schema. Permitted keys, value types, defaults, and validation rules must be defined in application code and covered by tests.

Rules:

- Only communication-style preferences are in scope for the MVP.
- Model-generated candidate updates must be validated before persistence.
- Profile updates must preserve unrelated preferences unless they are explicitly changed.
- The `version` value must increase when the profile changes.
- Updates must use optimistic concurrency control or an equivalent mechanism to prevent stale writes.
- Disabling memory must stop the profile from being applied without necessarily deleting it.
- Deleting memory must remove the stored profile and prevent stale tasks from recreating deleted preferences.

## 5. Relationships

- `USER_ACCOUNT` 1 — 0..N `CONVERSATION`
- `CONVERSATION` 1 — 0..N `MESSAGE`
- `USER_ACCOUNT` 1 — 0..1 `USER_STYLE_PROFILE`

Each conversation has one owner. Each message belongs to one conversation. Each account can have one current style profile or no profile.

## 6. Referential Integrity and Deletion

The physical schema must define explicit foreign-key deletion behavior.

Recommended initial policy:

- Deleting a conversation deletes its messages.
- Deleting a user initiates a documented account-deletion workflow that removes or appropriately handles owned conversations, messages, and style memory.
- Deleting a conversation must not delete the user's style profile.
- Account deletion must account for active sessions, outstanding requests, backups, and any data retained under a documented legal or operational requirement.

Cascading deletion may be used for dependent records where appropriate, but account deletion should be orchestrated carefully so that related data and external resources are handled consistently.

## 7. Indexes and Constraints

Initial database requirements:

- Unique constraint on normalized account email.
- Foreign keys for all entity relationships.
- Constraint limiting message roles to permitted values.
- Positive-value constraint for style-profile version.
- Index for conversations by owner and update time.
- Index for messages by conversation and creation order.

Exact index definitions, normalization strategy, and tie-breaking rules for messages with identical timestamps will be specified in the database migration.

## 8. Memory Update Concurrency

Memory extraction may finish after a user has manually edited or deleted their profile. A stale task must not overwrite the newer state.

The implementation should:

1. Read the current profile and version.
2. Generate or validate a candidate update.
3. Attempt to save only if the expected version and current memory state still match.
4. Increment the version atomically on a successful update.
5. Discard or recompute the candidate if the profile changed concurrently.

Memory deletion also requires a strategy for invalidating pending extraction work. A deleted profile must not be silently recreated by an older task.

The precise locking or compare-and-swap implementation will be documented with the memory lifecycle design.

## 9. Data Lifecycle

### Conversation deletion

Remove the selected conversation and its associated messages according to the deletion policy. Preserve the account's independent style profile.

### Memory disablement

Retain the profile but exclude it from context assembly for subsequent requests. Define how already-running requests are handled.

### Memory deletion

Remove the profile and invalidate outstanding updates that could restore it. Verify the result through an authenticated read or a dedicated memory-status endpoint.

### Account deletion

Remove account-owned data according to the account-deletion workflow. Document any backup retention period and eventual purge behavior.

## 10. Testing Requirements

- Duplicate normalized emails cannot be registered.
- A user cannot retrieve another user's conversations or messages.
- Deleting a conversation does not delete the user's style profile.
- Disabling memory prevents it from influencing new requests.
- Deleting memory removes the current profile.
- Stale updates cannot overwrite a newer profile version.
- A deleted profile cannot be recreated by an outdated extraction task.
- Invalid profile keys and values are rejected.
- Referential integrity is preserved during deletion and failed transactions.

## 11. Deferred Schema Extensions

The following are intentionally excluded from the initial four-entity schema:

- Refresh-token or session storage.
- Memory revision history.
- Uploaded-file metadata and storage references.
- AI usage and cost accounting.
- Audit-event storage.
- Message processing and idempotency records.

These entities should be added when their corresponding API, security, or operational requirements are specified, rather than preemptively creating unused tables.

## 12. Open Decisions

- Whether account deletion is immediate or handled asynchronously.
- Whether message edits are supported.
- Whether conversation titles are user-defined, model-generated, or both.
- Whether memory revision history is required for the portfolio MVP.
- Whether an explicit request-id or idempotency mechanism is required for message submission.
- Exact style-profile keys, permitted values, and validation rules.
- Authentication session and refresh-token persistence strategy.