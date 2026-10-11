# MIRA — RESTful API Contract Specification

| Attribute | Value |
| :--- | :--- |
| **Document ID** | MIRA-API-001 |
| **Classification** | Internal Engineering Specification |
| **Status** | Approved Baseline |
| **Version** | 1.0.0 |
| **Last Updated** | 2026-10-11 |
| **Author / Lead** | Bryan / MIRA API & Backend Team |
| **Protocol & Base URL** | HTTPS / `/api/v1` |
| **Data Format** | JSON (UTF-8 Encoded) |

---

## Document Revision History

| Version | Date | Author | Description of Changes |
| :--- | :--- | :--- | :--- |
| 0.1.0 | 2026-10-10 | Backend Team | Initial draft of public REST endpoints. |
| 1.0.0 | 2026-10-11 | Bryan | Enterprise standardization: integrated session refresh/logout endpoints, response retry contracts, idempotency specifications, and consolidated draft notes. |

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
  "user": {
    "id": "c1f7b8d4-5390-48e2-9d33-4df45e317c01",
    "email": "user@example.com",
    "createdAt": "2026-10-11T08:30:00Z"
  },
  "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
  "refreshToken": "7c4a16b9f2e848d7a123456789abcdef...",
  "expiresIn": 900
}
```

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
  "user": {
    "id": "c1f7b8d4-5390-48e2-9d33-4df45e317c01",
    "email": "user@example.com"
  },
  "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
  "refreshToken": "7c4a16b9f2e848d7a123456789abcdef...",
  "expiresIn": 900
}
```

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
  "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
  "refreshToken": "8d5b27c0a3f949e8b23456789abcdef0...",
  "expiresIn": 900
}
```
> [!NOTE]
> Refresh token rotation is atomic. The submitted refresh token is permanently revoked, and a newly generated refresh token is returned. Attempting to reuse an already-revoked refresh token triggers immediate session termination across all devices for that user.

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
* **Behavior:** Revokes the specific `user_session` record associated with the provided refresh token.

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

### 3.1 List Conversations
* **Endpoint:** `GET /api/v1/conversations`
* **Authentication:** Required
* **Query Parameters:**
  * `limit` (Optional, integer, default `20`, max `50`): Number of conversations to return.
  * `cursor` (Optional, UUID): Pagination cursor representing the last retrieved conversation ID.
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

### 4.1 List Conversation Messages
* **Endpoint:** `GET /api/v1/conversations/{conversationId}/messages`
* **Authentication:** Required
* **Query Parameters:**
  * `limit` (Optional, integer, default `50`, max `100`)
  * `cursor` (Optional, timestamp or UUID)
* **Status Code:** `200 OK`

#### Response Body (`200 OK`)
```json
{
  "items": [
    {
      "id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
      "role": "USER",
      "content": "Explain Shor's algorithm from first principles.",
      "createdAt": "2026-10-11T08:10:00Z"
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

#### Error & Timeout Response (`504 Gateway Timeout` / `200 OK` with `UNKNOWN`)
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

---

## 5. Communication-Style Memory Endpoints

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
* **Conflict Behavior (`409 Conflict`):** If the database `version` is not equal to `expectedVersion`, the mutation is rejected with `409 Conflict` and error code `VERSION_CONFLICT`.

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

All error responses strictly adhere to the standardized schema:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "The request contains invalid fields.",
    "requestId": "4a7f92b4-7b12-421b-8041-382901309fef",
    "details": [
      {
        "field": "content",
        "reason": "must not be blank"
      }
    ]
  }
}
```

### 6.1 Standard Error Code Registry

| HTTP Status | Error Code | Description / Occurrence |
| :--- | :--- | :--- |
| `400 Bad Request` | `VALIDATION_ERROR` | Malformed JSON or failed schema field validations. |
| `401 Unauthorized` | `AUTHENTICATION_REQUIRED` | Missing, malformed, or expired access token. |
| `401 Unauthorized` | `INVALID_CREDENTIALS` | Invalid email or password during login. |
| `403 Forbidden` | `ACCESS_DENIED` | Authenticated caller does not have permission. |
| `404 Not Found` | `RESOURCE_NOT_FOUND` | Resource does not exist or caller does not own it. |
| `409 Conflict` | `IDEMPOTENCY_CONFLICT` | Idempotency key reused with different payload. |
| `409 Conflict` | `VERSION_CONFLICT` | OCC expectedVersion mismatch on style memory update. |
| `429 Too Many Requests`| `RATE_LIMITED` | Exceeded per-minute account request quotas. |
| `502 Bad Gateway` | `AI_PROVIDER_UNAVAILABLE` | External AI provider returned unusable output. |
| `504 Gateway Timeout` | `AI_PROVIDER_TIMEOUT` | Upstream provider failed to respond within timeout window. |
| `500 Internal Error` | `INTERNAL_ERROR` | Unexpected backend server fault (sanitized, logged). |