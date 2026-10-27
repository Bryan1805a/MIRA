# MIRA — RESTful API Contract Specification

| Attribute | Value |
| :--- | :--- |
| **Document ID** | MIRA-API-001 |
| **Classification** | Internal Engineering Specification |
| **Status** | Approved Baseline |
| **Version** | 1.7.0 |
| **Last Updated** | 2026-10-27 |
| **Author / Lead** | Bryan / MIRA API & Backend Team |
| **Protocol & Base URL** | HTTPS / `/api/v1` |
| **Data Format** | JSON (UTF-8 Encoded) |

---

## Document Revision History

| Version | Date | Author | Description of Changes |
| :--- | :--- | :--- | :--- |
| 0.1.0 | 2026-10-10 | Backend Team | Initial draft of public REST endpoints. |
| 1.0.0 | 2026-10-11 | Bryan | Enterprise standardization: integrated session refresh/logout endpoints, response retry contracts, idempotency specifications, and consolidated draft notes. |
| 1.1.0 | 2026-10-21 | Bryan | Reconciled auth payloads and the error envelope with the implementation; marked conversation, messaging, and memory endpoints with their delivery phase. |
| 1.2.0 | 2026-10-23 | Bryan | Reconciled conversation, messaging, and retry endpoints with the implemented backend controller and service layer; updated error code registry and error envelope status. |
| 1.3.0 | 2026-10-24 | Bryan | Reconciled Android client authentication implementation (screens, TokenStore, auto-refresh authenticator) and DeepSeekAIProvider backend implementation. |
| 1.4.0 | 2026-10-25 | Bryan | Reconciled Android client conversation & chat endpoint integration, and marked `rate_limited` error code as Implemented (sliding window limit on message send/retry). |
| 1.5.0 | 2026-10-26 | Bryan | Corrected stale Android status (conversation and chat screens delivered), marked style-memory governance endpoints and `version_conflict` as Implemented (Backend), and clarified that provider failures are represented by response status rather than 502/504 responses. |
| 1.6.0 | 2026-10-27 | Bryan | Noted implemented advisory style-profile injection during message send: enabled profiles add a system message, while disabled, empty, or all-unspecified profiles add none. No endpoint shape changes. |
| 1.7.0 | 2026-10-27 | Bryan | Recorded the internal asynchronous extraction pipeline. No new public endpoints; staged inferred candidates are not yet exposed for confirmation. |

---

## 1. Global API Conventions

### 1.1 Transport & Headers
* All production traffic must utilize TLS 1.3 (HTTPS).
* Protected endpoints require an `Authorization` header containing a valid Bearer access token:
  ```http
  Authorization: Bearer <access_token>
  ```
* Mutations on conversational messaging endpoints require an `Idempotency-Key` header:
  ```http
  Idempotency-Key: <unique-client-generated-uuid-or-v4-string>
  ```

### 1.2 Data Serialization & Timestamps
* All timestamps use ISO 8601 UTC representation (e.g., `2026-10-11T08:30:00Z`).
* Identifiers are represented as 128-bit UUID strings (e.g., `3fa85f64-5717-4562-b3fc-2c963f66afa6`).
* JSON field names use camelCase.

### 1.3 Implementation Status

| Area | Status | Notes |
| :--- | :--- | :--- |
| Registration, login, refresh, logout, `/me` | **Implemented (Backend & Android)** | Live on backend; Android authentication UI and session storage delivered. See sections 2.1–2.5. |
| Standard error envelope | **Implemented (Backend & Android)** | Uniform `{ "error", "message", "requestId", "details" }` schema handled via `ApiExceptionHandler`/`RequestIdFilter` and mapped via `ApiError` in Android. |
| Conversation endpoints | **Implemented (Backend & Android)** | List, create, and delete endpoints with cursor pagination and strict ownership isolation; Android `ConversationsActivity` delivered. |
| Message send/list and retry | **Implemented (Backend & Android)** | Message persistence, response lifecycle (`PENDING`, `COMPLETED`, `FAILED`, `UNKNOWN`), `MockAIProvider`, `DeepSeekAIProvider`, idempotency key/hash, bounded retries, and Android `ChatActivity` delivered. |
| Style-memory governance (read/replace/toggle/purge) | **Implemented (Backend)** | Allow-list validation, OCC `version`, generation-guarded purge, advisory injection, and the internal extraction pipeline are live and covered by tests. Candidate confirmation and the Android memory UI remain Phase 2. |

Endpoints marked Implemented are live and covered by automated test suites. Phase 1 is complete; the remaining Phase 2 work is candidate confirmation (accept/reject), the Android memory UI, and the concurrency/privacy suites.

---

## 2. Authentication & Session Endpoints

### 2.1 Register Account
* **Endpoint:** `POST /api/v1/auth/register`
* **Authentication:** None (Public)
* **Status Code:** `201 Created`

#### Request Body
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123!"
}
```

#### Response Body (`201 Created`)
```json
{
  "id": "c1f7b8d4-5390-48e2-9d33-4df45e317c01",
  "email": "user@example.com",
  "createdAt": "2026-10-11T08:30:00Z"
}
```
* Registration does **not** issue tokens. The client authenticates via `POST /api/v1/auth/login` after a successful registration.
* Passwords must be 12–128 characters. Emails are trimmed and lowercased before storage.

#### Error Responses
* `400 Bad Request` / `invalid_registration` — email or password fails service-level registration rules.
* `400 Bad Request` / `invalid_request` — bean-validation failure.
* `409 Conflict` / `email_already_registered` — normalized email already exists.

---

### 2.2 User Login
* **Endpoint:** `POST /api/v1/auth/login`
* **Authentication:** None (Public)
* **Status Code:** `200 OK`

#### Request Body
```json
{
  "email": "user@example.com",
  "password": "SecurePassword123!"
}
```

#### Response Body (`200 OK`)
```json
{
  "userId": "c1f7b8d4-5390-48e2-9d33-4df45e317c01",
  "email": "user@example.com",
  "sessionId": "5d3f2a1e-7c4b-4f2a-9d11-8b6c5a4e3f21",
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
  "accessTokenExpiresAt": "2026-10-11T08:45:00Z",
  "refreshToken": "7c4a16b9f2e848d7a123456789abcdef...",
  "refreshTokenExpiresAt": "2026-11-10T08:30:00Z"
}
```
* Invalid credentials return `401 Unauthorized` with `invalid_credentials`; the response does not distinguish between an unknown email and a wrong password.

---

### 2.3 Token Refresh (Session Rotation)
* **Endpoint:** `POST /api/v1/auth/refresh`
* **Authentication:** None (Requires valid refresh token in body)
* **Status Code:** `200 OK`

#### Request Body
```json
{
  "refreshToken": "7c4a16b9f2e848d7a123456789abcdef..."
}
```

#### Response Body (`200 OK`)
```json
{
  "userId": "c1f7b8d4-5390-48e2-9d33-4df45e317c01",
  "email": "user@example.com",
  "sessionId": "5d3f2a1e-7c4b-4f2a-9d11-8b6c5a4e3f21",
  "tokenType": "Bearer",
  "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
  "accessTokenExpiresAt": "2026-10-11T09:00:00Z",
  "refreshToken": "8d5b27c0a3f949e8b23456789abcdef0...",
  "refreshTokenExpiresAt": "2026-11-10T08:30:00Z"
}
```
> [!NOTE]
> Refresh token rotation is atomic. The submitted token is marked consumed in `refresh_token_history`, the session's current token hash is updated in place, and a newly generated refresh token is returned. The absolute session expiry is not extended. Presenting an already-consumed token is treated as replay: the implicated session is revoked and the request is rejected with `401 Unauthorized` (`invalid_credentials`). Global revocation across every device remains an open Phase 1 follow-up carried into Phase 2; it is not current behavior.

---

### 2.4 User Logout
* **Endpoint:** `POST /api/v1/auth/logout`
* **Authentication:** Required (`Bearer <access_token>`)
* **Status Code:** `204 No Content`

#### Request Body
```json
{
  "refreshToken": "8d5b27c0a3f949e8b23456789abcdef0..."
}
```
* **Behavior:** Revokes the specific `user_session` record associated with the provided refresh token. Logout is idempotent: repeated calls and unknown tokens still return `204 No Content`. A token belonging to another user is ignored, and that user's session is not revoked. The endpoint requires a valid access token and a non-blank refresh token.

---

### 2.5 Current User Profile
* **Endpoint:** `GET /api/v1/me`
* **Authentication:** Required (`Bearer <access_token>`)
* **Status Code:** `200 OK`

#### Response Body (`200 OK`)
```json
{
  "id": "c1f7b8d4-5390-48e2-9d33-4df45e317c01",
  "email": "user@example.com",
  "createdAt": "2026-10-11T08:30:00Z"
}
```

---

## 3. Conversation Management Endpoints

> **Delivery status:** Implemented on backend and in the Android client. `ConversationController`, `ConversationService`, DTOs, and cursor pagination are live; `ConversationsActivity` consumes the endpoints.

### 3.1 List Conversations
* **Endpoint:** `GET /api/v1/conversations`
* **Authentication:** Required
* **Query Parameters:**
  * `limit` (Optional, integer, default `20`, max `50`): Number of conversations to return.
  * `cursor` (Optional, string): Opaque pagination cursor from the previous response's `nextCursor`. The server encodes `(updatedAt, id)` in the cursor; clients must not parse or construct it.
* **Status Code:** `200 OK`

#### Response Body (`200 OK`)
```json
{
  "items": [
    {
      "id": "8fa85f64-5717-4562-b3fc-2c963f66afa6",
      "title": "Discussion on Quantum Computing",
      "createdAt": "2026-10-11T08:00:00Z",
      "updatedAt": "2026-10-11T08:25:00Z"
    }
  ],
  "nextCursor": null
}
```

---

### 3.2 Create Conversation
* **Endpoint:** `POST /api/v1/conversations`
* **Authentication:** Required
* **Status Code:** `201 Created`

#### Request Body
```json
{
  "title": "Discussion on Quantum Computing"
}
```

#### Response Body (`201 Created`)
```json
{
  "id": "8fa85f64-5717-4562-b3fc-2c963f66afa6",
  "title": "Discussion on Quantum Computing",
  "createdAt": "2026-10-11T08:00:00Z",
  "updatedAt": "2026-10-11T08:00:00Z"
}
```

---

### 3.3 Delete Conversation
* **Endpoint:** `DELETE /api/v1/conversations/{conversationId}`
* **Authentication:** Required
* **Status Code:** `204 No Content`
* **Behavior:** Permanently deletes the conversation and cascades to all child messages and AI response records. Does **not** modify or delete the user's persistent style profile.

---

## 4. Messaging & AI Response Endpoints

> **Delivery status:** Implemented on backend and in the Android client. Synchronous message orchestration, `MockAIProvider`, `DeepSeekAIProvider`, message cursor listing, response lifecycle transitions, attempt tracking, and bounded retries are live; `ChatActivity` consumes the endpoints.

### 4.1 List Conversation Messages
* **Endpoint:** `GET /api/v1/conversations/{conversationId}/messages`
* **Authentication:** Required
* **Query Parameters:**
  * `limit` (Optional, integer, default `50`, max `100`)
  * `cursor` (Optional, string): Opaque cursor from the previous response's `nextCursor`, encoding the last message's `(createdAt, id)`.
* **Status Code:** `200 OK`

#### Response Body (`200 OK`)
```json
{
  "items": [
    {
      "id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "role": "USER",
      "content": "Explain Shor's algorithm from first principles.",
      "createdAt": "2026-10-11T08:10:00Z",
      "response": {
        "id": "f1e2d3c4-5a6b-4c7d-8e9f-0a1b2c3d4e5f",
        "status": "COMPLETED",
        "attemptCount": 1
      }
    },
    {
      "id": "a2c3eb4d-4b7d-4bad-8bee-3b0d7b3dcb7e",
      "role": "ASSISTANT",
      "content": "Shor's algorithm is a quantum algorithm that finds prime factors...",
      "createdAt": "2026-10-11T08:10:05Z"
    }
  ],
  "nextCursor": null
}
```
* `response` is present on `USER` messages that have a logical AI response. It lets the client render and retry `FAILED` or `UNKNOWN` responses, which have no `ASSISTANT` message row.
* `attemptCount` is the number of `ai_processing_attempt` rows for that response; retry is available only while the status is retryable and the count is below the configured maximum.

---

### 4.2 Send Message (Synchronous Orchestration)
* **Endpoint:** `POST /api/v1/conversations/{conversationId}/messages`
* **Authentication:** Required
* **Required Headers:**
  * `Idempotency-Key: <unique-request-key>`
* **Status Code:** `201 Created`

#### Request Body
```json
{
  "content": "Explain Shor's algorithm from first principles."
}
```

#### Response Body (`201 Created`)
```json
{
  "userMessage": {
    "id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "role": "USER",
    "content": "Explain Shor's algorithm from first principles.",
    "createdAt": "2026-10-11T08:10:00Z"
  },
  "assistantResponse": {
    "id": "a2c3eb4d-4b7d-4bad-8bee-3b0d7b3dcb7e",
    "status": "COMPLETED",
    "content": "Shor's algorithm is a quantum algorithm that finds prime factors...",
    "createdAt": "2026-10-11T08:10:05Z"
  }
}
```
* `assistantResponse.id` is the logical `ai_response` UUID used by the retry endpoint. Its `content` is read from the linked assistant `message` row; `ai_response` itself stores no content.
* When memory is enabled and the stored profile contains at least one specified preference, the provider context includes an advisory system message derived from the profile. The request and response payloads are unchanged, and current user instructions take precedence.

#### Error & Timeout Response (`200 OK` with `UNKNOWN`)
If an upstream AI provider times out after the user message has been safely persisted:
```json
{
  "userMessage": {
    "id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "role": "USER",
    "content": "Explain Shor's algorithm from first principles.",
    "createdAt": "2026-10-11T08:10:00Z"
  },
  "assistantResponse": {
    "id": "a2c3eb4d-4b7d-4bad-8bee-3b0d7b3dcb7e",
    "status": "UNKNOWN",
    "content": null,
    "createdAt": "2026-10-11T08:10:05Z"
  }
}
```

> [!NOTE]
> Provider failures are represented in the response lifecycle, not as HTTP gateway errors. A timeout returns `200 OK` with status `UNKNOWN`; a terminal provider error returns `201 Created` with status `FAILED`. The user message is always preserved, and the client may retry the logical response.

---

### 4.3 Retry AI Response
* **Endpoint:** `POST /api/v1/conversations/{conversationId}/responses/{responseId}/retry`
* **Authentication:** Required
* **Required Headers:**
  * `Idempotency-Key: <retry-attempt-key>`
* **Status Code:** `200 OK`

#### Response Body (`200 OK`)
```json
{
  "assistantResponse": {
    "id": "a2c3eb4d-4b7d-4bad-8bee-3b0d7b3dcb7e",
    "status": "COMPLETED",
    "content": "Shor's algorithm is a quantum algorithm that finds prime factors...",
    "createdAt": "2026-10-11T08:10:05Z"
  }
}
```
* **Rules:**
  * Bounded retry execution: Maximum 3 attempts per logical response.
  * Preserves original user prompt and response UUID; logs an additional row in `ai_processing_attempt`.
  * Retry requests use the idempotency scope `operation = RETRY`: replaying the same key for the same response returns the existing attempt/result, while reusing the key for a different response returns `409 idempotency_conflict`.

---

## 5. Communication-Style Memory Endpoints

> **Delivery status:** Backend governance, advisory injection, and the internal extraction pipeline are implemented. `GET`, `PUT`, `PATCH /settings`, and `DELETE /api/v1/memory/style` are live, including first-read provisioning, allow-list validation, OCC version checks, and generation-guarded purge. During message send, an enabled profile is serialized into an advisory system message; disabled, empty, or all-unspecified profiles add none. After a `COMPLETED` response, an asynchronous job extracts candidates and applies the hybrid policy. Staged inferred candidates are not yet exposed through an HTTP endpoint; the candidate-confirmation API and Android memory UI remain Phase 2 work.

### 5.1 Read Style Profile
* **Endpoint:** `GET /api/v1/memory/style`
* **Authentication:** Required
* **Status Code:** `200 OK`

#### Response Body (`200 OK`)
```json
{
  "enabled": true,
  "version": 4,
  "profile": {
    "explanation_depth": "detailed",
    "tone": "conversational",
    "examples": "prefer_concrete_examples",
    "formatting": "structured",
    "teaching_sequence": "principles_first"
  },
  "updatedAt": "2026-10-11T07:45:00Z"
}
```

---

### 5.2 Replace Style Profile (Optimistic Concurrency Control)
* **Endpoint:** `PUT /api/v1/memory/style`
* **Authentication:** Required
* **Status Code:** `200 OK`

#### Request Body
```json
{
  "expectedVersion": 4,
  "profile": {
    "explanation_depth": "concise",
    "tone": "formal",
    "examples": "prefer_concrete_examples",
    "formatting": "prose",
    "teaching_sequence": "principles_first"
  }
}
```

#### Response Body (`200 OK`)
```json
{
  "enabled": true,
  "version": 5,
  "profile": {
    "explanation_depth": "concise",
    "tone": "formal",
    "examples": "prefer_concrete_examples",
    "formatting": "prose",
    "teaching_sequence": "principles_first"
  },
  "updatedAt": "2026-10-11T08:35:00Z"
}
```
* **Conflict Behavior (`409 Conflict`):** If the database `version` is not equal to `expectedVersion`, the mutation is rejected with `409 Conflict` and error code `version_conflict`.

---

### 5.3 Toggle Memory Settings
* **Endpoint:** `PATCH /api/v1/memory/style/settings`
* **Authentication:** Required
* **Status Code:** `200 OK`

#### Request Body
```json
{
  "enabled": false
}
```

#### Response Body (`200 OK`)
```json
{
  "enabled": false,
  "version": 5,
  "updatedAt": "2026-10-11T08:36:00Z"
}
```

---

### 5.4 Delete Style Memory (Purge)
* **Endpoint:** `DELETE /api/v1/memory/style`
* **Authentication:** Required
* **Status Code:** `204 No Content`
* **Behavior:** Clears the stored JSONB profile and increments the `generation` counter. Any running background extraction jobs carrying older generation numbers are automatically aborted to prevent resurrecting deleted preferences.

---

## 6. Standardized Error Handling

### 6.1 Standard Error Envelope (Implemented)

All handled error responses use a uniform, enriched envelope:

```json
{
  "error": "invalid_request",
  "message": "Request fields are missing or invalid.",
  "requestId": "4a7f92b4-7b12-421b-8041-382901309fef",
  "details": [
    {
      "field": "content",
      "reason": "must not be blank"
    }
  ]
}
```

| Field | Type | Description |
| :--- | :--- | :--- |
| `error` | String | Stable, lower-snake-case error code. Clients compare this value, not the message. |
| `message` | String | Safe, human-readable description. Never contains stack traces, SQL, tokens, or provider payloads. |
| `requestId` | String | Unique request correlation UUID propagated from `X-Request-Id` or generated on ingress via `RequestIdFilter`. |
| `details` | Array | Optional list of specific validation failures (`field`, `reason`). Empty for non-validation errors. |

A catch-all `500` handler in `ApiExceptionHandler` guarantees unexpected backend faults emit `internal_error` in this uniform envelope with the associated `requestId` logged for correlation.

### 6.2 Standard Error Code Registry

| HTTP Status | Error Code | Occurrence | Status |
| :--- | :--- | :--- | :--- |
| `400 Bad Request` | `invalid_registration` | Email or password fails service-level registration rules. | Implemented |
| `400 Bad Request` | `invalid_request` | Bean-validation failure on a request body or parameter. | Implemented |
| `400 Bad Request` | `malformed_request` | Missing or unparseable JSON request body. | Implemented |
| `401 Unauthorized` | `authentication_required` | Missing, malformed, or expired access token. | Implemented |
| `401 Unauthorized` | `invalid_credentials` | Invalid login credentials or unusable refresh token. | Implemented |
| `403 Forbidden` | `access_denied` | Authenticated caller lacks permission. | Implemented |
| `404 Not Found` | `resource_not_found` | Resource does not exist or is not owned by the caller. | Implemented |
| `409 Conflict` | `email_already_registered` | Normalized email already exists. | Implemented |
| `409 Conflict` | `idempotency_conflict` | Idempotency key reused with a different payload hash. | Implemented |
| `409 Conflict` | `version_conflict` | OCC `expectedVersion` mismatch on style-memory update. | Implemented |
| `429 Too Many Requests`| `rate_limited` | Exceeded per-account request quota (10 requests/minute on message send/retry). | Implemented |
| `502 Bad Gateway` | `ai_provider_unavailable` | Reserved. Provider terminal errors are persisted as response status `FAILED`; the API returns `201 Created` with that status rather than a gateway error. | Not emitted |
| `504 Gateway Timeout` | `ai_provider_timeout` | Reserved. Provider timeouts are persisted as response status `UNKNOWN`; the API returns `200 OK` with that status rather than a gateway error. | Not emitted |
| `500 Internal Error` | `internal_error` | Unexpected backend fault (sanitized and logged with request ID). | Implemented |

> [!IMPORTANT]
> Error codes are lower snake_case. Older drafts used uppercase constants; clients must treat the current strings above as authoritative.
