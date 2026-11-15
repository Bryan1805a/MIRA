# MIRA

**Memory-Informed Response Agent** — a personal AI chat application designed to provide conversational continuity through user-controlled communication-style memory.

## Project Goals

- Build a native Android chat application.
- Develop a Java Spring Boot backend.
- Integrate the DeepSeek API without exposing provider credentials to the client.
- Store conversations and user-approved communication preferences in PostgreSQL.
- Give users control over viewing, editing, disabling, and deleting persistent memory.

## Development Status

**Phase 0, Phase 1, Phase 2, and Phase 3 are complete. Phase 3 (production hardening) delivered authentication rate limiting, account-wide refresh-token replay revocation, log-sanitization verification, Android release signing, backend container packaging, observability metrics, health monitoring, and Android session/error tests, with 131 passing backend tests, 28 passing Android unit tests, and an instrumented suite that runs on a physical device. The backend is live at `https://mira-backend-094w.onrender.com`, and v1.2.1 is the current release. Phase 2 delivered backend style-memory governance, OCC versioning, generation-guarded purge, advisory prompt injection, the asynchronous extraction pipeline (with bounded retries and recovery), candidate review, the MEM-TEST race scenarios, the Android memory UI (`MemoryActivity`), and dedicated memory integration test suites (privacy audit, injection safety, lookup failure tolerance, and conflict resolution).**

- Authentication, token rotation, logout, and the current-user endpoint are implemented across both backend and Android client.
- Android client features full authentication flows (Login, Registration, chat-first root with a session gate in `ChatActivity`, encrypted token storage via `EncryptedSharedPreferences`, auto-refresh `TokenAuthenticator`, and network security config).
- Android client delivers conversation management through a navigation drawer (recent chats, new chat, long-press to delete) with the chat screen as the app root.
- Android client delivers complete chat messaging (`ChatActivity`, `MessageAdapter`) with automatic client-side UUID idempotency key generation, response lifecycle visualization (`PENDING`, `COMPLETED`, `FAILED`, `UNKNOWN`), and bounded retry controls (up to 3 attempts).
- Backend conversation management (CRUD + cursor pagination) is implemented with strict ownership isolation.
- Backend message sending, response lifecycle tracking, AI provider abstraction (`MockAIProvider`, `DeepSeekAIProvider`), idempotency key/payload verification, attempt tracking, and bounded retries are implemented.
- Per-account message rate limiting (sliding window of 10 requests/minute returning HTTP `429 rate_limited`) and Actuator health/probe endpoints are active.
- Local PostgreSQL environment is pre-configured via `docker-compose.yml` and `.env.example`.
- GitHub Actions CI workflows are configured for backend verification (`.github/workflows/backend.yml`) and Android build, test, and lint (`.github/workflows/android.yml`).
- The backend test suite passes with 131 automated tests across unit, mock server, and PostgreSQL Testcontainers suites (including the domain-split `PersistenceConstraintTests`, `AuthApiTests`, `ApiContractTests`, `ConversationApiTests`, `MessagingApiTests`, `MemoryLifecycleTests`, `LatencySmokeTest`, `MiraMetricsTests`, `ObservabilityTest`, and the security suites).
- The Android test, build, and lint suites pass via `.\gradlew.bat clean assembleDebug testDebugUnitTest lintDebug` (28 unit tests); the instrumented suite (7 tests) passes on a physical device via `connectedDebugAndroidTest`, covering the API smoke flow, the chat in-flight indicator, and the adapter's thinking/reveal dispatch.
- Unreleased on `main`: chat-first navigation with a history drawer, a Gemini-style UI refresh (purple brand, empty-state glow, screenshots in `docs/demo`), regenerate for completed answers, and a client-side typewriter reveal.
- Backend memory governance is implemented (`GET/PUT/PATCH/DELETE /api/v1/memory/style`) with allow-list validation, OCC `409 version_conflict`, and generation-guarded purge.
- Enabled style profiles are injected into chat context as an advisory system message; disabled, empty, and all-unspecified profiles inject nothing, and lookup failures never block chat.
- Asynchronous memory extraction is implemented (Flyway `V10`): `COMPLETED`-only job scheduling, JSON extraction, explicit durable/correction merging, inferred candidate staging, ephemeral discard, generation/version commit guards, bounded retries, skip-locked claiming, and stale-processing recovery.
- Candidate review is implemented: pending inferred candidates can be listed, accepted with OCC, or rejected; purge deletes pending candidates.
- Android client delivers complete communication-style memory management and candidate review (`MemoryActivity`, `MemoryRepository`, `MemoryOptions`) with profile inspection, toggling, manual OCC save, permanent purge, and candidate acceptance/rejection.
- Extraction privacy audit tests prove that biographical/unknown attributes are discarded, evidence is capped at 500 characters, no prompt/transcript columns exist in audit tables, and user content/tokens are never leaked to logs. Current-instruction dominance and post-purge omission are verified.
- Phase 3 security hardening: login is throttled per email and client IP, registration and refresh per client IP, and replay of a consumed refresh token revokes every active session for the account. Log-sanitization tests assert that passwords, tokens, prompts, and completions never reach application logs.
- Phase 3 observability: the `prod` profile emits ECS JSON logs and exposes authenticated Micrometer metrics for chat outcomes, provider errors, extraction jobs, auth throttling, purges, and HTTP latency percentiles. Alert thresholds, backup/restore, and rollback drills are documented in `docs/08-deployment-plan.md`.

See [`docs/12-phase-3-checklist.md`](docs/12-phase-3-checklist.md) for the active Phase 3 plan.
See [`docs/11-phase-2-checklist.md`](docs/11-phase-2-checklist.md) for the Phase 2 plan and exit gate.
See [`docs/10-phase-1-checklist.md`](docs/10-phase-1-checklist.md) for the closed Phase 1 checklist.
See [`docs/09-phase-0-checklist.md`](docs/09-phase-0-checklist.md) for the completed foundation checklist.

## Architecture

- **Android:** Java 11 source level, Gradle (Kotlin DSL), Android Views/XML, compileSdk 37, minSdk 26
- **Backend:** Java 21, Spring Boot 4.1.x, Maven
- **Database:** PostgreSQL 15+ (Neon in deployment; PostgreSQL 18 in Testcontainers)
- **AI Provider:** DeepSeek API
- **API:** Versioned REST endpoints under `/api/v1`

MIRA uses a modular monolith architecture. The backend owns authentication, conversation persistence, memory lifecycle management, and communication with the AI provider.

The backend currently uses a layer-first package layout under `com.mira.mira_backend` (`config`, `controller`, `dto`, `entity`, `exception`, `repository`, `security`, `service`). Domain boundaries are enforced by service APIs and ownership queries; a feature-package refactor is optional and not required for the MVP.

## Repository Structure

```text
mira/
├── android/                        # Native Android application (Java / Gradle)
│   ├── app/
│   └── gradle/
├── backend/                        # Spring Boot modular monolith (Java 21 / Maven)
│   ├── src/main/java/com/mira/mira_backend/
│   │   ├── config/                 # Security, JWT, password configuration
│   │   ├── controller/             # REST controllers
│   │   ├── dto/                    # Request/response records
│   │   ├── entity/                 # JPA entities
│   │   ├── exception/              # Global API exception handling
│   │   ├── repository/             # Spring Data JPA repositories
│   │   ├── security/               # REST authentication/authorization handlers
│   │   └── service/                # Domain services
│   ├── src/main/resources/
│   │   └── db/migration/           # Flyway migrations (V1–V10)
│   └── src/test/                   # Unit and Testcontainers integration tests
├── docs/                           # Requirements, architecture, API, security, testing, deployment
└── README.md
```

## Prerequisites

- **Git**
- **JDK 21** for the backend
- **Docker Desktop** (or another Docker runtime) for Testcontainers-backed integration tests
- **PostgreSQL 15+** or a Neon database for local runtime
- **Android Studio** with Android SDK 37 and build tools installed for the Android app

## Backend

### Configuration

The backend reads its runtime configuration from environment variables. No datasource credentials or JWT keys are committed to the repository.

| Variable | Required | Purpose |
| :--- | :--- | :--- |
| `MIRA_JWT_SECRET_BASE64` | Yes | Base64-encoded HMAC-SHA256 signing key. Must decode to at least 32 bytes. |
| `SPRING_DATASOURCE_URL` | Yes | JDBC URL, for example `jdbc:postgresql://localhost:5432/mira`. |
| `SPRING_DATASOURCE_USERNAME` | Yes | Database user. |
| `SPRING_DATASOURCE_PASSWORD` | Yes | Database password. |
| `SERVER_PORT` | No | HTTP port. Spring Boot defaults to `8080`. |
| `SPRING_PROFILES_ACTIVE` | No | Active Spring profile. |

Example PowerShell session:

```powershell
# Quick local database start with Docker Compose
docker compose up -d

# Set environment variables for the session
$bytes = New-Object byte[] 32; [Security.Cryptography.RNGCryptoServiceProvider]::Create().GetBytes($bytes); [Convert]::ToBase64String($bytes)
$env:MIRA_JWT_SECRET_BASE64 = "+L53z9jm4gHIb1airMoZ8p2DVODAhmQB4Rd1F3hnVxs="
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/mira"
$env:SPRING_DATASOURCE_USERNAME = "mira"
$env:SPRING_DATASOURCE_PASSWORD = "admin123"

# Optional: Run with mock AI provider if no DeepSeek API key is configured
# $env:SPRING_PROFILES_ACTIVE = "test"
# Or provide your key:
# $env:DEEPSEEK_API_KEY = "your-deepseek-api-key"
```

Deterministic test configuration lives in `backend/src/test/resources/application.properties`; tests obtain PostgreSQL from Testcontainers.

### Build and Test

```powershell
cd backend
.\mvnw.cmd clean verify
```

The integration suite starts a real PostgreSQL container, applies every Flyway migration (V1-V10), and exercises the HTTP contract through MockMvc. No live DeepSeek calls are made by the test suite (131 automated tests pass).

### Run Locally

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Verify the service is up at `http://localhost:8080/actuator/health`.

### Container & Deployment

The backend ships as a multi-stage Docker image (`backend/Dockerfile`) and runs with the `prod` profile:

```powershell
docker build -t mira-backend:ci backend

docker run --rm -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=prod `
  -e MIRA_JWT_SECRET_BASE64="<base64-secret>" `
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://host.docker.internal:5432/mira" `
  -e SPRING_DATASOURCE_USERNAME="mira" `
  -e SPRING_DATASOURCE_PASSWORD="admin123" `
  -e DEEPSEEK_API_KEY="<key>" `
  mira-backend:ci
```

The `prod` profile enables ECS JSON logging, graceful shutdown, forward-header handling, Hikari pool settings, and Flyway on boot. Deployment uses Render + Neon via `render.yaml`; see [`docs/08-deployment-plan.md`](docs/08-deployment-plan.md) and [`docs/decisions/ADR-004-hosting-platform.md`](docs/decisions/ADR-004-hosting-platform.md).

## Android

```powershell
cd android
.\gradlew.bat assembleDebug testDebugUnitTest
```

Open the `android/` directory in Android Studio to run on an emulator or physical device. Authentication, a chat-first root with a navigation drawer for history, chat with bounded retry (`ChatActivity`), and Settings → Communication memory with candidate review (`MemoryActivity`) are fully implemented.

### Release Build

Release signing is configured but the keystore is never committed. Provide the values through environment variables or an untracked `android/keystore.properties`:

```properties
storeFile=mira-release.jks
storePassword=your-store-password
keyAlias=mira
keyPassword=your-key-password
```

Then build a signed APK and AAB:

```powershell
cd android
$env:MIRA_API_BASE_URL = "https://api.yourdomain.com/"
$env:MIRA_VERSION_CODE = "2"
$env:MIRA_VERSION_NAME = "1.1.0"
.\gradlew.bat clean assembleRelease bundleRelease
```

The `Android Release` GitHub Actions workflow does the same on a `v*` tag, using `MIRA_KEYSTORE_BASE64`, `MIRA_KEYSTORE_PASSWORD`, `MIRA_KEY_ALIAS`, and `MIRA_KEY_PASSWORD` secrets. See [`docs/decisions/ADR-005-release-signing.md`](docs/decisions/ADR-005-release-signing.md).

## Implemented API (as of 2026-11-14)

| Method | Endpoint | Status |
| :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Implemented (Backend & Android) |
| `POST` | `/api/v1/auth/login` | Implemented (Backend & Android) |
| `POST` | `/api/v1/auth/refresh` | Implemented (Backend & Android rotating authenticator) |
| `POST` | `/api/v1/auth/logout` | Implemented (Backend & Android) |
| `GET` | `/api/v1/me` | Implemented (Backend & Android) |
| `GET` | `/api/v1/conversations` | Implemented (Backend & Android) |
| `POST` | `/api/v1/conversations` | Implemented (Backend & Android) |
| `DELETE` | `/api/v1/conversations/{conversationId}` | Implemented (Backend & Android) |
| `GET` | `/api/v1/conversations/{conversationId}/messages` | Implemented (Backend & Android) |
| `POST` | `/api/v1/conversations/{conversationId}/messages` | Implemented (Backend & Android) |
| `POST` | `/api/v1/conversations/{conversationId}/responses/{responseId}/retry` | Implemented (Backend & Android) |
| `GET/PUT/PATCH/DELETE` | `/api/v1/memory/style` | Implemented (Backend & Android `MemoryActivity`) |
| `GET` | `/api/v1/memory/candidates` | Implemented (Backend & Android `MemoryActivity`) |
| `POST` | `/api/v1/memory/candidates/{candidateId}/accept` | Implemented (Backend & Android `MemoryActivity`) |
| `POST` | `/api/v1/memory/candidates/{candidateId}/reject` | Implemented (Backend & Android `MemoryActivity`) |

## Documentation

- [`docs/00-overview.md`](docs/00-overview.md) — System overview and specification roadmap
- [`docs/01-requirements.md`](docs/01-requirements.md) — Functional and non-functional requirements
- [`docs/02-architecture.md`](docs/02-architecture.md) — Architecture, modules, and failure semantics
- [`docs/03-domain-model.md`](docs/03-domain-model.md) — Relational schema as applied by Flyway
- [`docs/04-api-contract.md`](docs/04-api-contract.md) — REST contract and error registry
- [`docs/05-memory-lifecycle.md`](docs/05-memory-lifecycle.md) — Memory extraction, validation, and concurrency
- [`docs/06-security-and-privacy.md`](docs/06-security-and-privacy.md) — Security architecture and privacy rules
- [`docs/07-testing-strategy.md`](docs/07-testing-strategy.md) — Test tiers and verification matrix
- [`docs/08-deployment-plan.md`](docs/08-deployment-plan.md) — Deployment topology and runbook
- [`docs/09-phase-0-checklist.md`](docs/09-phase-0-checklist.md) — Completed foundation checklist
- [`docs/10-phase-1-checklist.md`](docs/10-phase-1-checklist.md) — Closed conversation and chat milestone
- [`docs/11-phase-2-checklist.md`](docs/11-phase-2-checklist.md) — Completed communication-style memory milestone
- [`docs/12-phase-3-checklist.md`](docs/12-phase-3-checklist.md) — Active production hardening, deployment, and operational readiness
- [`docs/13-release-process.md`](docs/13-release-process.md) — Versioning, release checklist, hotfix, and rollback process
- [`CHANGELOG.md`](CHANGELOG.md) — Release notes and change history
- [`docs/decisions/ADR-005-release-signing.md`](docs/decisions/ADR-005-release-signing.md) — Android release signing and artifact strategy

## Security Notes

- Never commit provider keys, database passwords, JWT secrets, or signing material.
- `secret.txt` is ignored by Git; use environment variables or an untracked local configuration instead.
- The Android APK must contain only the public backend base URL — never a DeepSeek key or database credential.
