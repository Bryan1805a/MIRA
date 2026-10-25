# MIRA

**Memory-Informed Response Agent** — a personal AI chat application designed to provide conversational continuity through user-controlled communication-style memory.

## Project Goals

- Build a native Android chat application.
- Develop a Java Spring Boot backend.
- Integrate the DeepSeek API without exposing provider credentials to the client.
- Store conversations and user-approved communication preferences in PostgreSQL.
- Give users control over viewing, editing, disabling, and deleting persistent memory.

## Development Status

**Phase 0 is complete. Phase 1 (Conversation & Chat Core) core capabilities are delivered: backend conversation, messaging orchestration, rate limiting, and DeepSeek provider integration are implemented, alongside the full native Android authentication, conversation management, and chat screens.**

- Authentication, token rotation, logout, and the current-user endpoint are implemented across both backend and Android client.
- Android client features full authentication flows (Login, Registration, session gate in `MainActivity`, encrypted token storage via `EncryptedSharedPreferences`, auto-refresh `TokenAuthenticator`, and network security config).
- Android client delivers full conversation management (`ConversationsActivity`) with cursor-based pagination, creation dialog, and deletion confirmation.
- Android client delivers complete chat messaging (`ChatActivity`, `MessageAdapter`) with automatic client-side UUID idempotency key generation, response lifecycle visualization (`PENDING`, `COMPLETED`, `FAILED`, `UNKNOWN`), and bounded retry controls (up to 3 attempts).
- Backend conversation management (CRUD + cursor pagination) is implemented with strict ownership isolation.
- Backend message sending, response lifecycle tracking, AI provider abstraction (`MockAIProvider`, `DeepSeekAIProvider`), idempotency key/payload verification, attempt tracking, and bounded retries are implemented.
- Per-account message rate limiting (sliding window of 10 requests/minute returning HTTP `429 rate_limited`) and Actuator health/probe endpoints are active.
- Local PostgreSQL environment is pre-configured via `docker-compose.yml` and `.env.example`.
- GitHub Actions CI workflows are configured for backend verification (`.github/workflows/backend.yml`) and Android build, test, and lint (`.github/workflows/android.yml`).
- The backend test suite passes with 63 automated tests (including unit, mock server, and PostgreSQL Testcontainers suites).
- The Android test, build, and lint suites pass via `.\gradlew.bat clean assembleDebug testDebugUnitTest lintDebug`.
- Memory extraction and memory governance are deferred to Phase 2.

See [`docs/10-phase-1-checklist.md`](docs/10-phase-1-checklist.md) for the active plan and exit gate.
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
│   │   └── db/migration/           # Flyway migrations (V1–V9)
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

The integration suite starts a real PostgreSQL container, applies every Flyway migration (V1–V9), and exercises the HTTP contract through MockMvc. No live DeepSeek calls are made by the test suite (63 automated tests pass).

### Run Locally

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```
cd backend
.\mvnw.cmd spring-boot:run
```

Verify the service is up at `http://localhost:8080/actuator/health`.

## Android

```powershell
cd android
.\gradlew.bat assembleDebug testDebugUnitTest
```

Open the `android/` directory in Android Studio to run on an emulator or physical device. Authentication (registration, login, encrypted token storage, session verification, logout) is implemented; conversation list and chat screens are the active Phase 1 deliverables.

## Implemented API (as of 2026-10-24)

| Method | Endpoint | Status |
| :--- | :--- | :--- |
| `POST` | `/api/v1/auth/register` | Implemented (Backend & Android) |
| `POST` | `/api/v1/auth/login` | Implemented (Backend & Android) |
| `POST` | `/api/v1/auth/refresh` | Implemented (Backend & Android rotating authenticator) |
| `POST` | `/api/v1/auth/logout` | Implemented (Backend & Android) |
| `GET` | `/api/v1/me` | Implemented (Backend & Android) |
| `GET` | `/api/v1/conversations` | Implemented (Backend) |
| `POST` | `/api/v1/conversations` | Implemented (Backend) |
| `DELETE` | `/api/v1/conversations/{conversationId}` | Implemented (Backend) |
| `GET` | `/api/v1/conversations/{conversationId}/messages` | Implemented (Backend) |
| `POST` | `/api/v1/conversations/{conversationId}/messages` | Implemented (Backend) |
| `POST` | `/api/v1/conversations/{conversationId}/responses/{responseId}/retry` | Implemented (Backend) |
| `GET/PUT/PATCH/DELETE` | `/api/v1/memory/style` | Phase 2 |

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
- [`docs/10-phase-1-checklist.md`](docs/10-phase-1-checklist.md) — Active conversation and chat milestone

## Security Notes

- Never commit provider keys, database passwords, JWT secrets, or signing material.
- `secret.txt` is ignored by Git; use environment variables or an untracked local configuration instead.
- The Android APK must contain only the public backend base URL — never a DeepSeek key or database credential.
