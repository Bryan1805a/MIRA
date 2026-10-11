# MIRA — API Contract

**Document:** `docs/04-api-contract.md`  
**Status:** Draft  
**API prefix:** `/api/v1`  
**Data format:** JSON  
**Authentication:** Bearer access token

## 1. Purpose

This document defines the public HTTP API between the MIRA Android application and the MIRA backend.

It specifies endpoints, request and response structures, status codes, authentication requirements, and expected error behavior.

The API contract is independent of the backend's internal implementation.

## 2. Conventions

### 2.1 Request and response format

- Request and response bodies use JSON encoded as UTF-8.
- Timestamps use ISO 8601 UTC format, for example `2026-10-11T06:00:00Z`.
- Resource identifiers use UUID strings.
- Protected endpoints require `Authorization: Bearer <access_token>`.
- Production endpoints must use HTTPS.

### 2.2 HTTP status codes

| Status | Meaning |
|---|---|
| `200 OK` | Request succeeded |
| `201 Created` | Resource created |
| `204 No Content` | Request succeeded without a response body |
| `400 Bad Request` | Invalid request or malformed input |
| `401 Unauthorized` | Missing, invalid, or expired access token |
| `403 Forbidden` | Authenticated caller lacks permission |
| `404 Not Found` | Resource not found or intentionally concealed |
| `409 Conflict` | Resource state or idempotency conflict |
| `413 Content Too Large` | Request exceeds a configured size limit |
| `415 Unsupported Media Type` | Unsupported request content type |
| `422 Unprocessable Content` | Valid JSON but invalid field values or business rules |
| `429 Too Many Requests` | Rate limit exceeded |
| `500 Internal Server Error` | Unexpected internal failure |
| `502 Bad Gateway` | Upstream provider returned an unusable response |
| `503 Service Unavailable` | Required service temporarily unavailable |
| `504 Gateway Timeout` | Upstream provider timed out |

The implementation must use these statuses consistently. More specific validation conventions may be established during implementation.

## 3. Authentication

### 3.1 Register

**Endpoint:** `POST /api/v1/auth/register`  
**Authentication:** Not required  
**Success:** `201 Created`

Request:

    {
      "email": "user@example.com",
      "password": "a-strong-password"
    }

Response:

    {
      "user": {
        "id": "UUID",
        "email": "user@example.com",
        "createdAt": "2026-10-11T06:00:00Z"
      },
      "accessToken": "ACCESS_TOKEN",
      "expiresIn": 900
    }

The example assumes a 15-minute access-token lifetime. The actual lifetime is a configuration decision.

The backend must validate the email and password, normalize the email consistently, hash the password, and enforce unique account identity.

The response must never contain the password or password hash.

### 3.2 Login

**Endpoint:** `POST /api/v1/auth/login`  
**Authentication:** Not required  
**Success:** `200 OK`

Request:

    {
      "email": "user@example.com",
      "password": "a-strong-password"
    }

Response:

    {
      "user": {
        "id": "UUID",
        "email": "user@example.com"
      },
      "accessToken": "ACCESS_TOKEN",
      "expiresIn": 900
    }

Invalid credentials must produce a generic authentication error rather than revealing whether the email or password was incorrect.

### 3.3 Refresh and logout

Proposed endpoints:

- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

A production-quality mobile session needs an explicit refresh-token strategy. The preferred design is a short-lived access token and a securely stored, revocable refresh token with rotation.

The exact refresh-token transport, reuse detection, revocation behavior, and persistence strategy must be specified before authentication implementation begins.

Refresh tokens must not be treated as ordinary access tokens or exposed in logs.

### 3.4 Current user

**Endpoint:** `GET /api/v1/me`  
**Authentication:** Required  
**Success:** `200 OK`

Response:

    {
      "id": "UUID",
      "email": "user@example.com",
      "createdAt": "2026-10-11T06:00:00Z"
    }

The user identity is derived from the authenticated principal, not a client-supplied user ID.

## 4. Conversations

### 4.1 List conversations

**Endpoint:** `GET /api/v1/conversations`  
**Authentication:** Required  
**Success:** `200 OK`

Optional query parameters:

- `limit`: maximum number of items returned.
- `cursor`: pagination cursor.

Response:

    {
      "items": [
        {
          "id": "CONVERSATION_UUID",
          "title": "Learning gradient descent",
          "createdAt": "2026-10-11T06:00:00Z",
          "updatedAt": "2026-10-11T06:15:00Z"
        }
      ],
      "nextCursor": null
    }

The backend returns only conversations owned by the authenticated user.

The default and maximum page sizes must be configured and documented during implementation.

### 4.2 Create conversation

**Endpoint:** `POST /api/v1/conversations`  
**Authentication:** Required  
**Success:** `201 Created`

Request:

    {
      "title": "Learning gradient descent"
    }

The `title` field is optional.

Response:

    {
      "id": "CONVERSATION_UUID",
      "title": "Learning gradient descent",
      "createdAt": "2026-10-11T06:00:00Z",
      "updatedAt": "2026-10-11T06:00:00Z"
    }

The backend assigns the owner from the authenticated principal.

### 4.3 Delete conversation

**Endpoint:** `DELETE /api/v1/conversations/{conversationId}`  
**Authentication:** Required  
**Success:** `204 No Content`

The backend deletes the conversation and its dependent messages according to the documented deletion policy.

Deleting a conversation does not delete the user's communication-style profile.

Unauthorized access to another user's conversation must not disclose the resource's contents or existence.

## 5. Messages

### 5.1 List messages

**Endpoint:** `GET /api/v1/conversations/{conversationId}/messages`  
**Authentication:** Required  
**Success:** `200 OK`

Optional query parameters:

- `limit`
- `cursor`

Response:

    {
      "items": [
        {
          "id": "MESSAGE_UUID_1",
          "role": "USER",
          "content": "Explain gradient descent.",
          "createdAt": "2026-10-11T06:00:00Z"
        },
        {
          "id": "MESSAGE_UUID_2",
          "role": "ASSISTANT",
          "content": "Gradient descent is an optimization method...",
          "createdAt": "2026-10-11T06:00:02Z"
        }
      ],
      "nextCursor": null
    }

Messages must be returned in a deterministic order. The implementation should use a stable tie-breaker when multiple messages share the same timestamp.

### 5.2 Send a message

**Endpoint:** `POST /api/v1/conversations/{conversationId}/messages`  
**Authentication:** Required  
**Success:** `201 Created`

Required header:

    Idempotency-Key: <unique-request-key>

Request:

    {
      "content": "Explain gradient descent from first principles."
    }

Response:

    {
      "userMessage": {
        "id": "USER_MESSAGE_UUID",
        "role": "USER",
        "content": "Explain gradient descent from first principles.",
        "createdAt": "2026-10-11T06:00:00Z"
      },
      "assistantMessage": {
        "id": "ASSISTANT_MESSAGE_UUID",
        "role": "ASSISTANT",
        "content": "Gradient descent is an optimization method...",
        "createdAt": "2026-10-11T06:00:02Z"
      }
    }

The backend must:

1. Authenticate the caller and verify conversation ownership.
2. Validate the message content and request size.
3. Apply idempotency handling.
4. Retrieve relevant conversation context and the applicable style profile.
5. Generate a response through the configured AI provider.
6. Persist messages according to the agreed transaction and failure policy.
7. Return the response in the documented structure.

The client cannot supply trusted system instructions, a privileged provider key, or an arbitrary user identity.

#### Idempotency rules

- The key is scoped to the authenticated user and operation.
- Reusing a key with the same request must not create duplicate messages or trigger another model generation.
- Reusing a key with a different request payload must return `409 Conflict`.
- Concurrent requests with the same key must be coordinated safely.
- The backend must document how long idempotency records remain valid.

If a provider request fails, the API must distinguish the failure from a successful response. The persistence policy must make clear whether the user message remains stored and how the client can retry safely.

A retry after an uncertain network outcome must reuse the original idempotency key.

## 6. Communication-Style Memory

### 6.1 Read style profile

**Endpoint:** `GET /api/v1/memory/style`  
**Authentication:** Required  
**Success:** `200 OK`

Response when a profile exists:

    {
      "enabled": true,
      "version": 3,
      "profile": {
        "explanation_depth": "detailed",
        "tone": "conversational",
        "examples": "prefer_concrete_examples",
        "formatting": "structured"
      },
      "updatedAt": "2026-10-11T06:00:00Z"
    }

If no profile exists, the endpoint returns a consistent empty-profile representation. The exact representation must be selected before implementation.

The response must not include other users' data or raw internal model instructions.

### 6.2 Replace style profile

**Endpoint:** `PUT /api/v1/memory/style`  
**Authentication:** Required  
**Success:** `200 OK`

Request:

    {
      "expectedVersion": 3,
      "profile": {
        "explanation_depth": "detailed",
        "tone": "conversational",
        "examples": "prefer_concrete_examples",
        "formatting": "structured"
      }
    }

`expectedVersion` is an optimistic-concurrency precondition. If the stored profile has changed since the client read it, the backend returns `409 Conflict` rather than silently overwriting a newer version.

The backend validates the complete profile, increments its version on a successful update, and returns the saved representation.

If the profile does not yet exist, the contract must define how creation is represented. A version-zero convention or a dedicated creation endpoint may be selected during implementation.

### 6.3 Change memory settings

**Endpoint:** `PATCH /api/v1/memory/style/settings`  
**Authentication:** Required  
**Success:** `200 OK`

Request:

    {
      "enabled": false
    }

Response:

    {
      "enabled": false,
      "version": 3,
      "updatedAt": "2026-10-11T06:20:00Z"
    }

This endpoint changes whether the profile is applied. Disabling memory does not itself delete the profile.

The backend must define the effect on requests that were already running when the setting changed.

### 6.4 Delete style memory

**Endpoint:** `DELETE /api/v1/memory/style`  
**Authentication:** Required  
**Success:** `204 No Content`

The backend removes the persisted style profile and prevents outdated extraction tasks from restoring it.

Deleting style memory does not automatically delete conversation history. These are separate data-lifecycle operations.

The deletion policy must also define how pending memory updates are invalidated and how deletion is reflected in backups.

## 7. Standard Error Format

All API errors should use a consistent structure.

Example:

    {
      "error": {
        "code": "VALIDATION_ERROR",
        "message": "The request contains invalid fields.",
        "requestId": "REQUEST_UUID",
        "details": [
          {
            "field": "content",
            "reason": "must not be blank"
          }
        ]
      }
    }

Fields:

- `code`: stable machine-readable error identifier.
- `message`: safe, user-facing description.
- `requestId`: identifier for support and server-side diagnostics.
- `details`: optional validation details.

The backend must not return stack traces, database internals, provider secrets, or sensitive prompt content.

Suggested error codes include:

- `VALIDATION_ERROR`
- `INVALID_CREDENTIALS`
- `AUTHENTICATION_REQUIRED`
- `RESOURCE_NOT_FOUND`
- `ACCESS_DENIED`
- `IDEMPOTENCY_CONFLICT`
- `VERSION_CONFLICT`
- `RATE_LIMITED`
- `AI_PROVIDER_UNAVAILABLE`
- `AI_PROVIDER_TIMEOUT`
- `INTERNAL_ERROR`

## 8. Security Requirements

- All protected endpoints require a valid access token.
- The backend derives user identity from the authenticated principal.
- Every user-owned resource must be authorized server-side.
- Authentication endpoints require appropriate rate limits.
- Secrets and tokens must not appear in logs.
- Password hashes must never be returned through the API.
- Request bodies and profile fields must be validated.
- Provider credentials remain exclusively server-side.
- Production traffic must use HTTPS.
- Cross-account access must be tested explicitly.

## 9. Contract Testing

The API contract should be verified through:

- Request and response schema tests.
- Authentication and authorization tests.
- Validation and error-status tests.
- Idempotency and concurrent-request tests.
- Conversation and message lifecycle tests.
- Memory version-conflict tests.
- Memory disablement and deletion tests.
- Mocked AI-provider integration tests.

The Android application and backend should use the same documented request and response shapes.

## 10. Open Decisions

- Exact access-token and refresh-token implementation.
- Empty-profile representation and first-time profile creation.
- Exact style-profile schema and allowed values.
- Pagination cursor format and maximum page sizes.
- Message persistence semantics during provider failure.
- Idempotency-record storage and retention.
- Request size limits and rate limits.
- Whether message streaming is needed for the initial release.
- Whether image and PDF endpoints belong in a later API version or the current version.

These decisions must be resolved before the corresponding endpoint is implemented.