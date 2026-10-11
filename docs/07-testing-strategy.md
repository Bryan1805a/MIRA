# MIRA — Testing Strategy

**Document:** `docs/07-testing-strategy.md`  
**Status:** Draft  
**Scope:** MVP testing strategy  
**Related documents:** `01-requirements.md`, `02-architecture.md`, `03-domain-model.md`, `04-api-contract.md`, `05-memory-lifecycle.md`, `06-security-and-privacy.md`

---

## 1. Purpose

This document defines how MIRA will be tested to verify functional correctness, data integrity, security, memory behavior, and reliability.

MIRA consists of several components:

- Native Android application using Java.
- Spring Boot backend using Java and Maven.
- PostgreSQL database.
- DeepSeek API integration.

The testing strategy must verify both individual components and their interactions.

The primary objectives are to:

1. Detect defects before they reach the deployed application.
2. Verify requirements through repeatable tests.
3. Prevent regressions when the architecture or implementation changes.
4. Test memory behavior deterministically without relying on an external AI provider.
5. Verify authorization and data isolation across accounts.
6. Keep testing practical for a student portfolio project.

## 2. Testing Principles

### 2.1 Test behavior, not implementation details

Tests should primarily verify observable behavior and documented contracts.

For example, a test for conversation ownership should verify that one user cannot retrieve another user's messages. It should not depend on the names of private helper methods used to implement the check.

### 2.2 Prefer deterministic tests

Given the same initial state and inputs, a test should produce the same result.

External network access, model-generated responses, current time, and shared database state can introduce nondeterminism. Tests should control or isolate these dependencies wherever practical.

### 2.3 Test security boundaries explicitly

Authentication and authorization are not merely implementation details. They are part of the product's expected behavior.

Tests must include unauthorized access attempts, invalid tokens, malformed inputs, and cross-account resource access.

### 2.4 Use the real database where database behavior matters

Mocks are useful for isolated business logic, but they cannot prove that SQL constraints, transactions, migrations, or database-specific behavior work correctly.

Tests involving PostgreSQL constraints, transaction boundaries, and concurrency should use a real PostgreSQL test instance.

### 2.5 Keep paid API calls out of routine tests

Ordinary automated tests must not require a valid DeepSeek API key.

Provider behavior should normally be simulated. A small, explicitly triggered integration test may call the real provider when needed.

---

## 3. Testing Levels

MIRA will use a layered testing strategy.

| Level | Primary purpose | Typical examples |
|---|---|---|
| Unit tests | Verify isolated logic | Validation, memory conflict resolution |
| Integration tests | Verify component interactions | Service + repository + PostgreSQL |
| API tests | Verify HTTP contracts and authorization | Login, sending messages, ownership checks |
| Android tests | Verify client behavior | Session handling, chat history rendering |
| End-to-end tests | Verify a complete user workflow | Register → chat → reopen conversation |
| Manual exploratory tests | Discover usability and environment issues | Device behavior, network interruptions |

These levels complement one another. Passing unit tests does not guarantee that the deployed application works correctly.

## 4. Backend Unit Tests

### 4.1 Scope

Unit tests should cover business logic that can be executed without starting the entire application or contacting external services.

Potential targets include:

- Email normalization and validation.
- Password policy validation.
- Message input validation.
- Conversation title generation, if implemented.
- Pagination parameter validation.
- Memory profile schema validation.
- Hybrid memory persistence decisions.
- Memory conflict resolution.
- Expected-version checks.
- Provider error classification.
- Rate-limit and quota decisions.

### 4.2 Memory policy tests

Memory persistence is a central MIRA feature and deserves dedicated unit tests.

| Input or condition | Expected result |
|---|---|
| User explicitly requests a durable style preference | Candidate may be persisted after validation |
| Model infers a weak preference from one message | No automatic persistence without the required confirmation or stronger evidence |
| User requests a temporary response style | Preference is not made durable by default |
| User corrects an existing preference | Explicit correction takes precedence |
| Extracted profile contains an unsupported field | Candidate is rejected or safely normalized according to policy |
| Extracted profile has invalid enum values | Candidate is rejected |
| Memory is disabled | Profile is not included in newly assembled requests |
| Extraction fails | Existing valid memory remains intact |

The tests should verify the decision rules independently of the language model.

### 4.3 Avoid unnecessary mocking

Mock external dependencies, such as the AI provider, when testing business logic.

Do not mock every class merely to make a test pass. Excessive mocking can make tests reflect the expected implementation rather than the actual system behavior.

---

## 5. Backend Integration Tests

### 5.1 Scope

Integration tests verify that application components work together correctly.

Examples include:

- Service-to-repository interactions.
- Transaction rollback behavior.
- Database constraint enforcement.
- Entity relationship behavior.
- Memory version updates.
- Message persistence.
- Database migration compatibility.
- Provider request construction.

Recommended tools:

- JUnit 5 for test execution.
- Spring Boot Test for application integration.
- Mockito for isolated dependency substitution.
- Testcontainers for disposable PostgreSQL instances, where the development environment supports it.

The project should use dependency versions compatible with the selected Spring Boot version.

### 5.2 PostgreSQL integration

Tests that depend on PostgreSQL behavior should run against PostgreSQL rather than assuming that an in-memory substitute behaves identically.

At minimum, verify:

- Duplicate normalized emails are rejected.
- Foreign-key constraints prevent invalid relationships.
- Deleting a conversation handles associated messages according to the documented policy.
- Transactions do not leave partially persisted messages after a failed operation.
- Memory updates respect their expected version.
- Database migrations can initialize a clean database.

Tests must not use production data or production database credentials.

### 5.3 Transaction boundaries

Sending a message may involve multiple operations, such as saving the user message, calling DeepSeek, and saving the assistant response.

An external API call cannot be rolled back by a PostgreSQL transaction.

Therefore, tests must verify the chosen failure semantics rather than assuming the entire operation is atomic.

The API contract should define what happens when:

1. The user message is stored successfully.
2. The provider call fails or times out.
3. The assistant response is not stored.
4. The client retries the request.

The implementation must prevent retries from silently creating duplicate messages or charging the provider repeatedly where the idempotency contract promises protection.

---

## 6. API and Security Tests

API tests should verify the externally observable HTTP contract, including status codes, response bodies, authentication, authorization, and validation.

### 6.1 Authentication

Required scenarios:

- Successful registration.
- Duplicate email registration.
- Invalid registration input.
- Successful login.
- Invalid credentials.
- Missing access token.
- Malformed or expired access token.
- Expired or revoked refresh token.
- Refresh-token rotation and reuse behavior.
- Logout and subsequent refresh attempts.

Exact expected status codes and response formats must match `04-api-contract.md`.

### 6.2 Authorization and data isolation

Use at least two independent test users, User A and User B.

Required scenarios:

- User A can access their own conversation.
- User A cannot read User B's conversation.
- User A cannot list User B's messages.
- User A cannot delete User B's conversation.
- User A cannot view or edit User B's memory.
- Supplying another user's UUID does not bypass authorization.
- Unauthenticated requests cannot access protected resources.

These tests must exercise the actual request-handling and authorization path, not merely call a service method with a predetermined user ID.

### 6.3 Input validation and error handling

Test:

- Empty or whitespace-only messages.
- Messages exceeding the configured length limit.
- Invalid UUIDs.
- Invalid pagination parameters.
- Unsupported memory fields.
- Invalid expected memory versions.
- Malformed JSON.
- Oversized requests.
- Provider failures.
- Database failures.

Error responses must not reveal stack traces, API keys, password hashes, SQL statements, or internal infrastructure details.

### 6.4 Idempotency

For endpoints using `Idempotency-Key`, test the documented behavior for:

- A successful first request.
- A retry with the same key and identical request body.
- Reuse of the same key with a different body.
- Concurrent requests using the same key.
- A provider timeout followed by a retry.
- Expired idempotency records.

A repeated request should not create duplicate messages when the contract guarantees idempotency.

If a provider timeout leaves the outcome uncertain, the system must follow its documented recovery policy rather than blindly assuming the provider did nothing.

---

## 7. Memory Lifecycle and Concurrency Tests

Memory has asynchronous work, explicit user edits, and deletion semantics. These introduce race conditions that ordinary happy-path tests may miss.

### 7.1 Required lifecycle scenarios

| Scenario | Expected result |
|---|---|
| Save a valid style profile | Profile is persisted and can be retrieved |
| Update a profile using the current version | Update succeeds and version advances |
| Update using a stale version | Conflict is reported; newer data is preserved |
| Disable memory | Existing profile remains stored but is not applied to newly assembled requests |
| Delete memory | Profile is removed according to policy |
| Extraction finishes after deletion | Deleted profile is not recreated |
| Background extraction conflicts with a manual edit | Manual edit wins |
| Two updates race | A stale update cannot silently overwrite a newer update |
| Extraction output is invalid | Existing valid profile is not corrupted |
| Extraction service fails | Ordinary chat remains available where the architecture permits |

### 7.2 Deterministic concurrency testing

Do not rely on arbitrary sleep durations to reproduce race conditions.

Instead, use controlled synchronization in tests, such as latches, barriers, or test-controlled futures, to force a specific ordering.

For example:

1. Start a background memory extraction.
2. Pause it before persistence.
3. Delete the profile and advance or invalidate its generation.
4. Resume extraction.
5. Verify that the stale operation cannot restore the deleted profile.

The test should validate the real persistence guard, not merely simulate a deletion response.

### 7.3 Memory deletion and generation checks

The implementation must select and document a concurrency mechanism, such as a generation identifier, tombstone, or equivalent atomic state transition.

The test suite should verify the selected mechanism under concurrent operations.

The mechanism must also account for delayed tasks and retries; an in-memory flag alone is insufficient when background work can outlive the application request or server process.

---

## 8. AI Provider Tests

### 8.1 Mock provider

The backend should expose an `AIProvider` abstraction so business logic can be tested without making live DeepSeek requests.

A mock or fake provider should support controlled outcomes, including:

- Successful response.
- Provider authentication failure.
- Rate limit response.
- Server error.
- Timeout.
- Malformed response.
- Empty response.
- Unexpected response structure.

This allows the test suite to verify how MIRA handles provider behavior without incurring API charges.

### 8.2 Request construction

Tests must verify that the backend assembles the intended request context:

- Current user message is included.
- Only relevant conversation history is included.
- Enabled memory is included according to policy.
- Disabled memory is excluded.
- Unrelated users' data is never included.
- Provider credentials are not exposed to the Android client.
- Unnecessary sensitive information is not transmitted.

### 8.3 Real-provider smoke tests

Live DeepSeek tests are optional and must be explicitly triggered.

They should:

- Use a dedicated test credential and non-sensitive test content.
- Have bounded execution and cost.
- Avoid printing credentials or full sensitive payloads.
- Be excluded from the default unit-test suite.
- Fail clearly when the provider is unavailable or credentials are missing.

A live-provider test is useful for checking integration compatibility, but it must not be the only way to test core chat or memory behavior.

---

## 9. Android Application Tests

### 9.1 Unit tests

Test Android-side logic independently of the backend where practical:

- Request and response parsing.
- Input validation.
- Authentication-state transitions.
- Session-expiration handling.
- Conversation and message presentation logic.
- Error-state mapping.
- Loading and retry-state transitions.

The client must not treat local state as authoritative proof of server-side authorization.

### 9.2 UI tests

Use Android UI testing tools where their value justifies the setup cost.

Important workflows include:

- Registering and logging in.
- Creating a conversation.
- Sending a message.
- Displaying an assistant response.
- Reopening a conversation and loading history.
- Viewing and editing style memory.
- Disabling and deleting memory.
- Handling a failed request.
- Handling an expired session.

Tests should verify both success and failure states.

### 9.3 Physical-device verification

At least one physical Android device should be used for manual verification before a release candidate.

Check:

- Network interruption and recovery.
- Slow responses and repeated taps.
- Session expiration.
- Activity recreation and application restart.
- Scrolling through longer conversations.
- Keyboard behavior and input preservation.
- Loading indicators and error messages.

An emulator is useful for automated tests, but it does not replace all physical-device testing.

---

## 10. End-to-End Tests

End-to-end tests verify important workflows across the Android application, backend, and database.

The MVP should prioritize a small number of high-value workflows.

### E2E-01: Account and conversation

1. Register a test account.
2. Log in.
3. Create a conversation.
4. Send a message.
5. Receive an assistant response from the test provider.
6. Reload the conversation.
7. Verify that the messages persist.

**Expected result:** the user can complete the primary chat workflow and recover persisted history.

### E2E-02: Cross-conversation memory

1. Log in as User A.
2. Explicitly request a durable communication-style preference.
3. Verify that the backend validates and persists the preference.
4. Start another conversation.
5. Verify that the enabled profile is applied to the newly assembled provider request.
6. Disable memory.
7. Start another request.
8. Verify that the profile is excluded.

**Expected result:** memory persists across conversations but follows the user's settings.

### E2E-03: Account isolation

1. Create User A and User B.
2. Create a conversation under each account.
3. Authenticate as User A.
4. Attempt to access User B's conversation using its identifier.
5. Verify that access is denied without exposing the private messages.

**Expected result:** knowing a resource identifier does not grant access.

### E2E-04: Memory deletion race

1. Begin a controlled background memory extraction.
2. Delete the user's profile before the extraction persists.
3. Allow the extraction task to finish.
4. Retrieve the profile.
5. Verify that it remains deleted.

**Expected result:** stale background work cannot restore deleted memory.

### E2E-05: Provider failure

1. Send a valid message.
2. Configure the test provider to time out or return an error.
3. Verify the documented message state and API error behavior.
4. Retry using the same idempotency key.
5. Verify that duplicate messages are not created contrary to the contract.

**Expected result:** provider failure is handled predictably and does not silently corrupt conversation history.

E2E tests should use isolated test data and a controlled provider. They must never depend on another test's database state.

---

## 11. Test Data and Environment Management

### 11.1 Test data

Use synthetic test accounts, conversations, messages, and memory profiles.

Do not copy real user conversations or production credentials into the test suite.

Test data should be easy to create, inspect, and reset.

### 11.2 Environment separation

Maintain separate configurations for:

- Local development.
- Automated testing.
- Production deployment.

Tests must not run against the production database.

Database credentials, signing keys, and provider keys must be supplied through test configuration or environment variables rather than committed to source control.

### 11.3 Isolation and cleanup

Each test should either own its data or use an isolated test database/schema.

Cleanup must not depend on a test having completed successfully.

Tests involving asynchronous work must wait for explicit completion conditions rather than assuming all background operations have finished when the HTTP response returns.

---

## 12. Tooling

The initial toolset should remain small and compatible with the selected Java stack.

| Area | Proposed tools | Purpose |
|---|---|---|
| Backend unit tests | JUnit 5 | Verify isolated business logic |
| Mocking | Mockito | Replace external dependencies in tests |
| Spring integration | Spring Boot Test | Verify application wiring and HTTP behavior |
| Database integration | Testcontainers + PostgreSQL | Verify real database behavior |
| Android unit tests | JUnit and Android-supported test libraries | Verify client logic |
| Android UI tests | AndroidX instrumentation testing tools | Verify user-facing workflows |
| Build verification | Maven and Gradle | Compile and execute test suites |
| Static checks | Compatible lint and dependency scanning tools | Detect common code and dependency issues |

Tool versions should be selected to match the actual project configuration. Do not introduce a new testing framework without a clear requirement.

---

## 13. Continuous Integration

Once the repository structure and basic build are stable, CI should run a repeatable verification pipeline on pull requests and important branches.

Proposed sequence:

1. Check out the repository.
2. Set up the required JDK and Android build environment.
3. Compile the backend.
4. Run backend unit tests.
5. Run backend integration tests against a disposable PostgreSQL instance.
6. Run backend static checks where configured.
7. Compile the Android application.
8. Run Android unit tests.
9. Report failures and preserve useful diagnostic output.

Live DeepSeek smoke tests should not run on every pull request by default.

CI must not require production credentials. Any required test secrets must be narrowly scoped and protected.

The team should first make the builds and tests reproducible locally, then add CI. Automation cannot compensate for an undocumented or unreliable local build process.

---

## 14. Definition of Done

A feature is considered complete only when the following conditions apply:

- Its behavior matches the relevant requirement and API contract.
- Important success and failure paths have been tested.
- Authorization is tested wherever user-owned data is involved.
- Database changes include appropriate migrations and integrity checks.
- Memory changes include validation and relevant concurrency tests.
- External provider behavior is isolated from ordinary automated tests.
- Sensitive data is not exposed in logs or error responses.
- The relevant automated tests pass.
- Any known limitation or deferred security decision is documented.

Not every feature needs a large test suite. The amount of testing should reflect the complexity and consequences of failure.

---

## 15. MVP Release Gate

Before a release candidate is deployed, verify:

- [ ] Backend builds successfully.
- [ ] Android application builds successfully.
- [ ] Backend unit tests pass.
- [ ] Database integration tests pass.
- [ ] Authentication and cross-account authorization tests pass.
- [ ] Chat persistence and history retrieval work.
- [ ] Provider failures follow the documented behavior.
- [ ] Memory persistence, disablement, editing, and deletion tests pass.
- [ ] Memory concurrency and stale-task protection tests pass.
- [ ] Sensitive information is not leaked through logs or error responses.
- [ ] Database migrations work on a clean database.
- [ ] The primary chat workflow works on a physical Android device.
- [ ] Deployment uses the intended configuration and does not expose secrets.

A failed security or data-integrity test must not be waived merely because the UI appears to work.

---

## 16. Deferred Testing

The following areas may be expanded when the corresponding features are introduced:

- Image upload and multimodal provider requests.
- PDF parsing and document retrieval.
- Larger conversation-history performance tests.
- Load and stress testing.
- Backup restoration drills.
- Accessibility testing.
- Broader device and Android-version compatibility.
- More extensive dependency and security scanning.

These are deferred by scope, not declared unnecessary.

---

## 17. Final Principle

MIRA should be testable without requiring a paid AI model, a production database, or manual intervention for every run.

The most important tests protect the system's invariants:

1. Users can access only their own data.
2. Messages and memory are persisted according to documented rules.
3. Explicit user corrections and deletion take precedence over stale background work.
4. External provider failures do not silently corrupt application state.
5. Security guarantees are enforced by backend logic and database constraints, not by assumptions about the Android client.

**A successful build proves that the code can be assembled. A reliable test suite provides evidence that the system behaves as intended.**