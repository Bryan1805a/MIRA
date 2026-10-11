# MIRA — Security and Privacy

**Document:** `docs/06-security-and-privacy.md`  
**Status:** Draft  
**Scope:** MVP security baseline and privacy requirements  
**Related documents:** `01-requirements.md`, `02-architecture.md`, `03-domain-model.md`, `04-api-contract.md`, `05-memory-lifecycle.md`

---

## 1. Purpose

This document defines the security and privacy requirements for MIRA, a memory-informed conversational assistant built with a native Android application, a Spring Boot backend, PostgreSQL, and the DeepSeek API.

The objectives are to:

- Protect user accounts and authentication credentials.
- Prevent unauthorized access to conversations, messages, and memory profiles.
- Protect backend credentials and third-party API access.
- Give users meaningful control over their stored data and personalization settings.
- Minimize sensitive information exposed through logs, errors, and external services.
- Establish a security baseline before implementing the core features.

Security controls must be proportionate to the MVP. The project should prioritize effective, testable controls rather than introduce unnecessary infrastructure.

## 2. Security Principles

### 2.1 Backend-enforced authorization

The backend is the authoritative enforcement point for authentication, authorization, and data ownership.

The Android application may hide unavailable actions for usability, but the backend must independently validate every protected operation.

### 2.2 Least privilege

Each operation should access only the resources and permissions it requires.

Users must not be able to access another user's account data, conversations, messages, or memory profile by changing an identifier in an API request.

### 2.3 Data minimization

MIRA should collect, retain, and transmit only the data necessary for its documented features.

Conversation content must not automatically become persistent memory. Memory extraction follows the Hybrid Memory Persistence Policy defined in `05-memory-lifecycle.md`.

### 2.4 Secure defaults

- Memory personalization is enabled only according to the user's saved settings and profile state.
- Protected endpoints require authentication.
- New accounts cannot access other users' data.
- Secrets are never embedded in the Android application.
- Errors do not expose internal implementation details.
- Sensitive request and response bodies are not logged by default.

### 2.5 Explicit user control

Users must be able to inspect, modify, disable, and delete their communication-style memory.

Disabling personalization and deleting stored memory are distinct operations and must have distinct behavior.

---

## 3. Threat Model

MIRA's initial threat model focuses on realistic risks for a student-built, internet-accessible application.

| Threat | Example | Required mitigation |
|---|---|---|
| Credential theft | Password database is exposed | Strong password hashing; no plaintext passwords |
| Broken object-level authorization | User A requests User B's conversation ID | Verify ownership on every protected resource operation |
| Token theft | Stolen access token is reused | Short-lived access tokens; secure token handling and revocation strategy |
| API key exposure | DeepSeek key extracted from the APK | Keep provider credentials on the backend |
| Resource abuse | Automated requests generate excessive API charges | Rate limits, quotas, request limits, and usage monitoring |
| Prompt injection | User content attempts to manipulate memory extraction | Treat conversation content as untrusted; validate and constrain extracted memory |
| Data leakage | Conversation text appears in application logs | Redacted, structured logging |
| Stale memory update | A background task restores a deleted profile | Profile-generation checks and atomic persistence |
| Database compromise | Unauthorized party obtains database contents | Restricted credentials, encrypted transport, secure secret management, backups protection |
| Dependency vulnerability | Vulnerable library exposes an endpoint | Dependency updates and vulnerability checks |

This threat model is a baseline, not a claim that the system is immune to every attack.

## 4. Authentication and Account Security

### 4.1 Registration

The registration endpoint must:

- Validate and normalize email addresses consistently.
- Enforce a documented password policy.
- Reject invalid input before creating an account.
- Enforce email uniqueness at the database level.
- Never store plaintext passwords.
- Avoid returning database errors or stack traces to the client.

The database should enforce a unique constraint on the normalized email representation. Application-level checks alone are insufficient because concurrent registration requests can race.

The API must define a consistent response for duplicate registrations without unnecessarily exposing account information.

### 4.2 Password storage

Passwords must be hashed using a password-hashing algorithm designed to resist offline guessing.

Preferred options include Argon2id or appropriately configured bcrypt, using a maintained library and parameters suitable for the deployment environment.

Password hashing is not the same as ordinary SHA-256 hashing. Fast general-purpose hashes are unsuitable for directly storing passwords.

Passwords must never appear in:

- Application logs.
- Exception messages.
- Analytics events.
- Source control.
- Plaintext database columns.

### 4.3 Access and refresh tokens

MIRA will use bearer-token authentication for protected REST endpoints.

The proposed strategy is:

- **Access token:** short-lived; approximately 15 minutes is an initial configuration to evaluate, not a fixed requirement.
- **Refresh token:** longer-lived, revocable, and rotated when successfully used.
- **Logout:** invalidates the applicable refresh session or token.
- **Refresh reuse:** reuse of an invalidated rotated refresh token should trigger an appropriate session-revocation response.

The implementation must define token expiration, refresh rotation, logout behavior, and what happens when a token is stolen.

If refresh tokens are opaque random values, the backend should store a securely hashed representation rather than the raw token. If signed access tokens are used, signature, issuer, audience where applicable, expiration, and accepted algorithms must be validated.

On Android:

- Keep refresh credentials in storage designed for sensitive application data, using Android Keystore-backed protection where appropriate.
- Do not place credentials in source code, ordinary debug logs, or unprotected shared preferences.
- Clear local credentials on logout.
- Do not assume that a client-side logout invalidates an already issued access token unless the backend implements that guarantee.

The precise token implementation must be selected before the authentication endpoints are considered complete.

### 4.4 Password recovery

Password recovery is outside the initial MVP unless explicitly added to the requirements.

If introduced later, it must use time-limited, single-use recovery tokens and must not disclose whether an arbitrary email address has an account.

---

## 5. Authorization and Data Isolation

### 5.1 Ownership enforcement

Every operation involving a user-owned resource must verify ownership using the authenticated identity.

Examples include:

- Retrieving a conversation.
- Listing or retrieving messages.
- Sending a message to an existing conversation.
- Deleting a conversation.
- Reading or modifying a memory profile.

The backend must derive the acting user's identity from the validated authentication context. It must not trust a `userId` supplied by the Android client as proof of ownership.

For example, the following request is not authorized merely because the caller knows the conversation ID:

`GET /api/v1/conversations/{conversationId}/messages`

The backend must verify that the requested conversation belongs to the authenticated user before returning messages.

### 5.2 Preventing insecure direct object references

UUIDs make identifiers harder to guess but do not replace authorization.

A UUID is an identifier, not an access-control mechanism.

Tests must attempt to access resources owned by another account by substituting their identifiers into otherwise valid requests.

Where appropriate, the API may return `404 Not Found` for both nonexistent and inaccessible resources to avoid revealing resource existence. The policy must be consistent across endpoints.

### 5.3 Administrative access

The MVP does not require a human administrator to read users' conversations.

Administrative permissions, if added later, must be explicit and separate from ordinary account permissions. They must not grant unrestricted conversation access by default.

---

## 6. Protecting the DeepSeek Integration

### 6.1 API key management

The DeepSeek API key is a backend secret.

It must not be:

- Embedded in Android source code or APK resources.
- Returned in API responses.
- Committed to Git.
- Included in screenshots, recordings, or public documentation.
- Printed in exception messages or request logs.

For local development, use environment variables or an ignored local configuration file. Production deployments should use the deployment platform's secret-management mechanism where available.

Example configuration names:

- `DEEPSEEK_API_KEY`
- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `JWT_SIGNING_KEY`, if applicable

Example values must not be committed alongside the configuration names.

If a secret is exposed, remove the exposure and rotate the secret. Deleting the visible line from the latest Git commit does not guarantee that it has disappeared from repository history.

### 6.2 Provider abstraction

The `AIProvider` interface must isolate provider-specific implementation details from chat and memory business logic.

The DeepSeek implementation is responsible for:

- Authentication with the provider.
- Request and response mapping.
- Timeouts and bounded retries.
- Handling rate limits and provider errors.
- Avoiding unnecessary transmission of personal data.
- Reporting sanitized internal errors.

Provider credentials must never be passed to the Android client.

### 6.3 Data sent to the provider

MIRA must document what information is sent to DeepSeek when generating a response.

Depending on the chosen context strategy, this may include:

- The current user message.
- Relevant prior conversation messages.
- The enabled communication-style profile.
- Necessary system instructions.

The backend should not transmit unrelated conversations, account credentials, database records, or other unnecessary personal information.

A communication-style profile should contain only the fields needed for personalization. It should not become a general-purpose repository for sensitive personal facts.

Before production deployment, review the provider's applicable privacy policy, retention terms, data-processing terms, and available data controls. Do not promise users that submitted content is never retained or used by the provider unless that claim has been verified for the actual service and account configuration.

### 6.4 Untrusted model output

AI-generated content is untrusted input, even when returned by the configured provider.

The backend must not blindly treat model output as executable instructions, authorization decisions, SQL, or trusted memory updates.

Memory extraction must follow the schema validation, evidence checks, conflict-resolution rules, and version controls in `05-memory-lifecycle.md`.

---

## 7. API and Transport Security

### 7.1 HTTPS

All deployed API traffic must use HTTPS.

Production credentials and conversation content must not be transmitted over plaintext HTTP. Development-only exceptions must remain local and must not be copied into production configuration.

### 7.2 Input validation

The backend must validate all client-controlled input, including:

- Email and password fields.
- Conversation identifiers.
- Message content and length.
- Pagination parameters.
- Memory profile fields and enum values.
- Expected memory versions.
- Idempotency keys.

Request size and message-length limits must be configured to reduce accidental overload and abuse.

Validation must occur on the backend even if Android performs equivalent checks for usability.

### 7.3 Database queries

Use parameterized queries or the ORM's safe query mechanisms. Do not construct SQL by concatenating untrusted input.

Database access should use a dedicated application account with only the privileges required by the application.

Database administrative credentials must not be used as the application's normal runtime credentials.

### 7.4 Error handling

The API should return a consistent error format, as defined in `04-api-contract.md`.

Client responses must not contain:

- Stack traces.
- SQL statements.
- Password hashes.
- Provider API keys.
- Database connection strings.
- Internal infrastructure details.

Detailed diagnostics should remain in appropriately protected server-side logs, with sensitive fields redacted.

### 7.5 CORS and API exposure

The native Android application is not governed by browser CORS enforcement in the same way as a web application. CORS configuration must not be treated as an authentication or authorization mechanism.

If MIRA later gains a browser frontend, configure allowed origins deliberately rather than allowing arbitrary origins with credentialed requests.

---

## 8. Rate Limits and Cost Controls

Because each AI request can incur provider charges, resource-abuse controls are part of the MVP security baseline.

The backend should enforce:

- Per-account limits on authentication attempts and message submissions.
- Maximum message and request sizes.
- Bounded concurrent provider requests.
- Provider timeouts and limited retries.
- Usage monitoring and a configurable budget or alert threshold where supported.
- Reasonable pagination limits for conversation history.

Unauthenticated endpoints such as registration and login also require abuse controls.

Limits should be configurable rather than hardcoded throughout business logic.

Retries must not accidentally create duplicate messages or duplicate provider charges. The idempotency contract in `04-api-contract.md` must be implemented consistently with the chosen request-processing lifecycle.

The application should handle provider rate limits and outages gracefully, without entering an unbounded retry loop.

---

## 9. Memory Privacy and Lifecycle

### 9.1 Memory is separate from conversation history

MIRA maintains distinct categories of data:

1. Conversation history, used to display past interactions.
2. Candidate preferences, awaiting validation or confirmation where required.
3. The persistent communication-style profile, used for personalization when enabled.

A user's message must not automatically become a permanent memory merely because it appears in a conversation.

### 9.2 Hybrid persistence policy

MIRA follows the Hybrid Memory Persistence Policy:

- Explicit, durable communication-style preferences may be saved automatically after backend validation.
- Inferred preferences require user confirmation or sufficiently strong supporting evidence before persistence.
- Temporary or context-specific instructions are not durable by default.
- Explicit user corrections take precedence over older inferred preferences.
- Deletion invalidates pending updates associated with the deleted profile generation.

The extraction process must not be allowed to override user permissions or security rules.

### 9.3 Viewing, editing, and disabling memory

Users must be able to:

- Inspect the currently stored communication-style profile.
- Edit profile fields.
- Disable personalization without deleting the profile.
- Delete the stored profile.

When memory is disabled, the backend must exclude it from newly assembled AI requests.

When memory is edited manually, stale background extraction must not overwrite the user's correction.

### 9.4 Deletion semantics

The API and user interface must distinguish between:

- **Deleting a conversation:** removes that conversation and its associated messages according to the documented deletion policy; it does not automatically delete the user's style profile.
- **Deleting style memory:** removes the persistent style profile and prevents stale asynchronous work from recreating it.
- **Deleting an account:** initiates deletion of the account and its associated data under the documented retention policy.

Account deletion is not implemented merely by deleting the row in `USER_ACCOUNT` if related records, tokens, logs, backups, or provider-side data remain.

The system must define what deletion means for each data category, including any backup retention or legal obligations that actually apply.

### 9.5 In-flight requests

If a memory profile is deleted while an AI request is already being processed, the request may have already received a copy of the previous profile.

MIRA must document this limitation. Deletion must prevent future requests and stale background tasks from using or restoring the deleted profile, but it cannot retroactively retract data already transmitted to an external provider.

---

## 10. Logging, Monitoring, and Data Retention

### 10.1 Logging policy

Logs should support troubleshooting and security monitoring without duplicating users' private conversations.

Appropriate log fields may include:

- Request correlation ID.
- Endpoint and HTTP status.
- Processing duration.
- Sanitized error category.
- Provider latency and usage metadata, where available.
- Authentication or rate-limit failure counts.

Do not log by default:

- Passwords or password hashes.
- Access or refresh tokens.
- API keys.
- Full conversation messages.
- Full memory profiles.
- Unredacted authentication headers.
- Sensitive request or response bodies.

Production logs must have restricted access and a documented retention period.

### 10.2 Data retention

MIRA must define retention policies for:

- User accounts.
- Conversations and messages.
- Persistent style profiles.
- Refresh-token sessions.
- Security logs.
- Idempotency records.
- Backups.

The initial implementation should not introduce a permanent archive of deleted user data without a documented requirement.

Operational records that must be retained for security or reliability should contain the minimum necessary information and must not silently become a second copy of conversation history.

### 10.3 Backup considerations

Backups should be access-controlled and protected in transit and at rest where supported.

The account-deletion policy must explain how backup copies are handled, including when deleted data becomes unavailable in normal operation and when it expires from retained backups.

Do not claim immediate physical erasure from every backup unless the implementation can guarantee it.

---

## 11. Android Application Security

The Android client must:

- Use HTTPS for deployed backend communication.
- Keep secrets out of source code and APK resources.
- Store session credentials using an appropriate secure-storage design.
- Avoid printing tokens or conversation content in production logs.
- Avoid exposing private chat data through unnecessary exported components or insecure inter-process interfaces.
- Validate server responses before using them.
- Handle expired sessions and authentication failures predictably.
- Avoid trusting locally cached role, ownership, or memory-enabled flags as authorization decisions.

Debugging tools and development logging may be more verbose during local development, but sensitive values must remain redacted.

---

## 12. Configuration and Dependency Management

### 12.1 Environment separation

MIRA should maintain separate development and production configuration.

Development credentials must not be reused in production. Production database access should not be granted to arbitrary development tools or local application builds.

Configuration changes should be documented without committing secret values.

### 12.2 Dependency management

The project must use maintained versions of Spring Boot, authentication libraries, PostgreSQL drivers, Android dependencies, and other external libraries.

Before deployment:

- Review dependency vulnerability reports.
- Update known vulnerable dependencies.
- Avoid unnecessary libraries.
- Keep dependency versions reproducible through the project's build configuration.
- Ensure secret files and generated build artifacts are excluded from version control where appropriate.

### 12.3 Database migrations

Database schema changes should be tracked through versioned migrations.

Migrations must be reviewed for data loss, unintended privilege changes, and compatibility with existing records.

Production schema changes should not depend on manually editing the database without a corresponding documented migration.

---

## 13. Security Testing Strategy

Security tests must be repeatable without requiring a paid AI API call for every test.

### Authentication

- Registration rejects invalid input.
- Duplicate normalized emails are rejected consistently.
- Passwords are stored as hashes, not plaintext.
- Invalid, expired, or malformed access tokens are rejected.
- Expired or revoked refresh tokens cannot establish a new session.
- Logout invalidates the applicable refresh session.

### Authorization

- User A cannot retrieve User B's conversation.
- User A cannot retrieve or delete User B's messages or conversation.
- User A cannot access or modify User B's memory profile.
- Changing a UUID does not bypass ownership checks.
- Missing authentication is rejected on protected endpoints.

### Provider integration

- Missing provider credentials cause a controlled server-side failure.
- Provider errors do not reveal API keys or internal stack traces.
- Timeouts do not cause unbounded retries.
- Client requests cannot choose arbitrary provider credentials.
- Provider request context contains only the information needed for the request.

### Memory privacy

- Disabled memory is not added to newly assembled requests.
- Explicit user corrections take precedence over stale inferred preferences.
- Temporary instructions are not automatically persisted as durable preferences.
- Invalid extraction output is rejected.
- Deleting memory prevents stale asynchronous tasks from recreating the profile.
- Concurrent updates do not silently overwrite newer user changes.

### Abuse and operational controls

- Oversized requests are rejected.
- Rate limits are enforced.
- Pagination and concurrency limits are respected.
- Logs do not contain credentials or full message bodies.
- Database queries remain safe under malicious input.

Tests should use mock providers or controlled test doubles wherever external API behavior is not the subject of the test.

---

## 14. MVP Acceptance Criteria

The MVP is not considered ready for deployment until all of the following are satisfied:

- [ ] Passwords are securely hashed.
- [ ] Authentication and token lifecycle behavior are documented and tested.
- [ ] All user-owned resource operations enforce server-side ownership checks.
- [ ] DeepSeek credentials are stored only on the backend.
- [ ] Production traffic uses HTTPS.
- [ ] Input validation, request-size limits, and rate limits are implemented.
- [ ] Provider errors and application errors do not leak secrets or internal details.
- [ ] Sensitive conversation data is excluded from routine logs.
- [ ] Users can view, edit, disable, and delete their style memory.
- [ ] Memory deletion cannot be undone by stale extraction tasks.
- [ ] Conversation, memory, and account deletion have distinct documented semantics.
- [ ] The application documents which conversation and profile data may be sent to DeepSeek.
- [ ] Security tests cover cross-user access, token failures, memory lifecycle races, and secret leakage.
- [ ] Deployment configuration and secrets are kept out of source control.

---

## 15. Decisions Required Before Production

The following items require an explicit decision before a production release:

1. Exact access-token format, lifetime, refresh-token rotation, and revocation mechanism.
2. Password-hashing algorithm and deployment-appropriate parameters.
3. Whether email verification and password recovery are required.
4. The specific provider model and its applicable data-retention and privacy terms.
5. Retention periods for logs, refresh sessions, and idempotency records.
6. Account-deletion behavior across database records, backups, and any provider-side data.
7. Usage quotas, alert thresholds, and the response to exhausted budgets.
8. Whether administrative support access is required and, if so, its authorization and audit policy.

These decisions should be recorded in the relevant architecture, API, or deployment documents rather than left implicit in code.

---

## 16. Out of Scope for the MVP

The initial project does not require:

- A microservices architecture.
- A dedicated security operations platform.
- A custom cryptographic protocol.
- A full enterprise identity provider.
- Complex role hierarchies without a real requirement.
- A separate memory database.
- Automatic deletion guarantees that the underlying provider or backup system cannot support.

The objective is a small, understandable, defensible system with security controls that can be explained, implemented, and tested.

**Design rule:** MIRA may use AI to personalize responses, but authentication, authorization, data ownership, and privacy guarantees must remain enforced by ordinary application code and database constraints.