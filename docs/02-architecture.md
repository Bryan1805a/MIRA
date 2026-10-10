# MIRA — System Architecture

**Document:** `docs/02-architecture.md`  
**Status:** Draft  
**Project:** Memory-Informed Response Agent  
**Architecture style:** Modular monolith  
**Last updated:** 2026-10-10

## 1. Purpose

This document defines MIRA's high-level architecture, component responsibilities, data flows, security boundaries, and major technical decisions.

The architecture prioritizes:

- Clear separation of responsibilities.
- A single source of truth for persistent user data.
- Secure handling of credentials and AI provider secrets.
- Testable business logic.
- Incremental development and deployment.
- Future extensibility without premature complexity.

## 2. System Context

MIRA is a native Android chatbot backed by a server-side application. Its primary distinguishing feature is a persistent communication-style profile that can influence responses across conversations.

### System components

1. **Android application:** User interface, chat interaction, conversation selection, and secure token handling.
2. **MIRA backend:** Authentication, authorization, conversation management, memory management, and AI request orchestration.
3. **PostgreSQL database:** Persistent storage for user accounts, conversations, messages, and communication-style profiles.
4. **DeepSeek API:** External language-model inference provider.

### High-level data flow

Android App → HTTPS REST API → MIRA Backend → PostgreSQL / DeepSeek API

The Android application must not connect directly to PostgreSQL or call the DeepSeek API using a privileged server-side API key.

## 3. Architecture Style

MIRA uses a **modular monolith**.

The backend is deployed as one application while its internal components have explicit responsibilities and dependencies.

Microservices, message brokers, distributed caches, and orchestration platforms are not required for the initial MVP. They may be considered later if measurable operational or scaling requirements justify them.

## 4. Backend Components

### 4.1 Auth Module

Responsibilities:

- Register accounts using email and password.
- Authenticate users.
- Issue, validate, and revoke or expire authentication credentials.
- Enforce user identity and access-control rules.

Security requirements:

- Store password hashes, never plaintext passwords.
- Use HTTPS for network communication.
- Apply rate limiting to sensitive authentication endpoints.
- Avoid exposing whether an email exists when doing so could enable account enumeration.
- Never trust a user ID supplied by the client as proof of identity.

### 4.2 Chat Module

Responsibilities:

- Create, list, retrieve, and delete conversations as defined by the API contract.
- Accept user messages.
- Retrieve the relevant conversation history.
- Coordinate response generation.
- Persist messages and expose conversation history to authorized users.

The Chat module must verify conversation ownership on every relevant operation.

### 4.3 Memory Module

Responsibilities:

- Retrieve a user's communication-style profile.
- Validate candidate memory updates.
- Merge approved changes without unintentionally replacing unrelated preferences.
- Support inspecting, editing, disabling, and deleting style memory.
- Prevent stale asynchronous updates from restoring preferences that a user has corrected or deleted.

The MVP memory profile is limited to communication style, such as preferred explanation depth, tone, examples, and formatting.

It is not intended to become a general-purpose personal knowledge store.

Memory extraction and memory updates must be separate from ordinary response generation. Temporary instructions must not automatically become permanent preferences.

### 4.4 AI Integration Module

Responsibilities:

- Assemble the context required for a model request.
- Combine the current user message, relevant conversation history, and applicable style profile.
- Call the configured AI provider.
- Validate provider responses and normalize errors.
- Apply timeout and bounded retry policies where appropriate.

The application must depend on an internal provider abstraction rather than embedding DeepSeek-specific HTTP logic throughout the business layer.

Illustrative interface:

`AIProvider.generate(AIRequest) -> AIResponse`

The exact interface, synchronous or asynchronous execution model, and streaming behavior will be determined during implementation planning.

### 4.5 File Processing Module — Future Scope

Image understanding and PDF question answering are planned capabilities, but they should be added after the text-chat workflow is reliable.

Before implementation, verify the selected provider model's current support for image inputs, document processing, context limits, and relevant API endpoints.

File processing must include file-type and size validation, appropriate access control, and a documented retention and deletion policy.

## 5. Data Ownership

PostgreSQL is the authoritative store for persistent application data.

The Android application may cache data for presentation or temporary offline behavior, but its local state must not override server-side authorization or become the authoritative memory profile.

Initial logical entities:

- `USER_ACCOUNT`
- `CONVERSATION`
- `MESSAGE`
- `USER_STYLE_PROFILE`

Additional entities may be introduced for refresh tokens, memory revisions, uploaded files, or audit events if the detailed requirements justify them.

The relational schema and constraints will be defined in `docs/03-domain-model.md`.

## 6. Request Lifecycle

For a standard text message:

1. The Android application submits a request using its authenticated session.
2. The backend authenticates the caller and verifies conversation ownership.
3. The Chat module retrieves the relevant conversation context.
4. The Memory module retrieves the applicable communication-style profile.
5. The AI Integration module assembles the model request.
6. The backend calls the configured provider using server-side credentials.
7. The backend validates and persists the response according to the agreed failure-handling policy.
8. The API returns a structured response to Android.

Memory updates are handled through a separate controlled workflow. A model response must not directly overwrite the user's persistent style profile.

## 7. Security Boundaries

- All production client-server communication must use HTTPS.
- DeepSeek API credentials must remain on the server and outside source control.
- Secrets must be provided through deployment configuration or a secret-management mechanism.
- Authentication credentials must be stored securely on Android.
- Every operation involving user-owned data must enforce authorization on the server.
- Database access must use parameterized queries or a suitable ORM.
- Request bodies, uploaded files, and provider responses must be validated.
- Logs must not expose passwords, authentication tokens, API keys, or unnecessary sensitive conversation content.
- Account deletion and memory deletion must have explicit, documented semantics.

## 8. Error Handling and Reliability

The backend must distinguish between:

- Invalid client requests.
- Authentication and authorization failures.
- Missing resources.
- Rate limits.
- Provider timeouts or unavailability.
- Invalid or unexpected provider responses.
- Internal persistence failures.

Retries must be bounded and restricted to operations where retrying is safe. In particular, repeated client submissions must not unintentionally create duplicate messages or trigger uncontrolled provider charges.

The implementation must define how failed AI requests affect persisted messages and how clients can recover from uncertain request outcomes.

## 9. Testing Strategy

### Unit tests

- Memory-profile validation and merge behavior.
- Temporary versus persistent preference handling.
- Authorization and business rules.
- Provider-response normalization.
- Error mapping.

### Integration tests

- Database persistence and transaction behavior.
- Authentication and access-control enforcement.
- Conversation and message lifecycle.
- Memory lifecycle and deletion behavior.
- Backend integration using a mocked AI provider.

### End-to-end tests

- Register, log in, and send a message.
- Continue a conversation across application restarts.
- Reuse a style profile in a separate conversation.
- Correct or delete a preference and verify it does not return.
- Verify that one user cannot access another user's data.

Automated tests must not depend on paid provider calls unless explicitly configured for a separate integration test.

## 10. Deployment Model

The initial deployment consists of:

- One Spring Boot backend service.
- One managed PostgreSQL database.
- One Android application distributed as an installable APK or through an appropriate testing channel.
- External access to the configured AI provider.

The deployment environment must provide database credentials and provider secrets without committing them to Git.

Development, testing, and production configuration should be separated.

## 11. Architectural Decision Records

| ID | Decision | Status |
|---|---|---|
| ADR-001 | Use a modular monolith for the backend | Proposed |
| ADR-002 | Keep provider credentials on the backend | Required |
| ADR-003 | Use PostgreSQL as the authoritative persistent store | Proposed |
| ADR-004 | Keep communication-style memory separate from chat history | Proposed |
| ADR-005 | Defer image and PDF support until text chat is stable | Proposed |

## 12. Open Decisions

- Token format, lifetime, refresh, and revocation strategy.
- Exact model and API capabilities for the selected DeepSeek endpoint.
- Message persistence and idempotency strategy.
- Memory extraction timing, validation, and concurrency control.
- File storage and deletion policy.
- Deployment provider, backup strategy, and operational logging.
- Whether streaming responses are required for the initial release.

These decisions must be resolved in the relevant design documents before their implementation begins.