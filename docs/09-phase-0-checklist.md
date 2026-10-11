# MIRA — Phase 0: Repository and Development Environment Setup

**Document:** `docs/09-phase-0-checklist.md`  
**Status:** Draft  
**Phase:** 0 — Foundation  
**Goal:** Establish a reproducible development environment and a working project skeleton before implementing MVP features.

**Related documents:**
- `00-overview.md`
- `01-requirements.md`
- `02-architecture.md`
- `03-domain-model.md`
- `04-api-contract.md`
- `05-memory-lifecycle.md`
- `06-security-and-privacy.md`
- `07-testing-strategy.md`
- `08-deployment-plan.md`

---

## 1. Phase 0 Objectives

Phase 0 prepares the repository, development environment, application skeletons, and database migration workflow.

At the end of this phase:

- The repository has a clear, minimal structure.
- The Android application builds successfully.
- The Spring Boot backend builds and starts successfully.
- The backend exposes a basic health endpoint.
- PostgreSQL connectivity can be verified in a controlled environment.
- Database schema changes are managed through versioned migrations.
- Secrets and local configuration are excluded from version control.
- Backend and Android tests can be executed locally.
- The project has a documented workflow for development and contribution.
- Remaining architecture decisions are recorded rather than silently assumed.

Phase 0 does not implement the complete authentication flow, chatbot, or persistent memory feature.

## 2. Prerequisites

Before starting, verify the development environment.

### Required tools

- [ ] Git.
- [ ] A Java JDK compatible with the selected Spring Boot version.
- [ ] Maven Wrapper for backend builds.
- [ ] Android Studio.
- [ ] Android SDK and a compatible Android build toolchain.
- [ ] PostgreSQL access for local development or integration testing.
- [ ] A code editor and terminal.
- [ ] An Android device or emulator for basic application verification.

The exact Java, Gradle, Android Gradle Plugin, SDK, and Spring Boot versions must be selected and recorded when initializing the projects.

Avoid upgrading unrelated toolchain components during feature development unless a compatibility or security requirement justifies it.

### Environment verification

- [ ] Verify Git works.
- [ ] Verify the selected JDK version.
- [ ] Verify the Maven Wrapper can execute.
- [ ] Verify Android Studio can build a new Android project.
- [ ] Verify a PostgreSQL client can connect to the development database.
- [ ] Record any environment-specific limitations.

## 3. Repository Structure

Use a simple monorepo so the Android client, backend, and shared project documentation remain together.

Proposed initial structure:

```text
mira/
├── android/
│   ├── app/
│   ├── gradle/
│   ├── build.gradle
│   ├── settings.gradle
│   └── gradlew
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   │       └── java/
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
│
├── docs/
│   ├── 00-overview.md
│   ├── 01-requirements.md
│   ├── 02-architecture.md
│   ├── 03-domain-model.md
│   ├── 04-api-contract.md
│   ├── 05-memory-lifecycle.md
│   ├── 06-security-and-privacy.md
│   ├── 07-testing-strategy.md
│   ├── 08-deployment-plan.md
│   └── 09-phase-0-checklist.md
│
├── .gitignore
├── README.md
└── LICENSE
```

This is a target structure, not a requirement to recreate directories that already exist. Android Studio may generate additional Gradle files, wrapper configuration, resources, and test directories. Preserve generated files required by the actual build.

### Structure rules

- `android/` contains the native Android client.
- `backend/` contains the Spring Boot API and backend tests.
- `docs/` contains architecture, requirements, operational policies, and implementation decisions.
- Root-level files describe the repository and contribution workflow.
- Build artifacts must not be committed.
- Secrets must not be committed.
- Do not create separate services or modules until the requirements justify them.

## 4. Initialize the Android Project

### 4.1 Project creation

Create a native Android application using:

- Language: Java.
- Build system: Gradle.
- IDE: Android Studio.
- UI approach: choose a consistent approach compatible with the selected Android SDK and the team's skill level.

Place the Android project under `android/`.

### 4.2 Initial implementation

The initial Android application should contain only enough functionality to demonstrate that the project builds and launches.

- [ ] Application launches successfully.
- [ ] Main activity displays a minimal placeholder screen.
- [ ] Application ID and package name are finalized.
- [ ] Minimum supported Android version is selected and documented.
- [ ] Gradle Wrapper is included.
- [ ] Debug build succeeds.
- [ ] A basic unit test executes successfully.
- [ ] A physical device or emulator can install and launch the app.

Do not implement chat UI, authentication screens, or memory controls during this step.

### 4.3 API configuration

Prepare a clear mechanism for configuring the backend base URL.

Development and production configurations must be distinguishable. The Android application must not contain DeepSeek credentials or database credentials.

The final production URL will be configured when the backend is deployed.

## 5. Initialize the Spring Boot Backend

### 5.1 Project creation

Create a Spring Boot application using:

- Language: Java.
- Build system: Maven.
- Packaging: executable JAR.
- API format: REST with JSON.
- API version prefix: `/api/v1`.

Choose dependencies based on actual MVP needs. The initial set will generally include:

- Spring Web.
- Spring Validation.
- Spring Data JPA, if JPA is selected for persistence.
- PostgreSQL JDBC driver.
- Spring Boot Actuator or an equivalent health-check mechanism.
- Spring Boot Test.
- A migration tool such as Flyway.

Authentication and security dependencies should be introduced when implementing the authentication baseline. They do not need to be configured prematurely in an otherwise empty skeleton.

### 5.2 Minimal application

- [ ] Backend starts locally.
- [ ] The application uses externalized configuration.
- [ ] Port configuration supports the local default and the hosting environment.
- [ ] A basic health endpoint responds successfully.
- [ ] Errors do not expose stack traces through public API responses.
- [ ] The Maven Wrapper is included.
- [ ] Unit tests execute successfully.
- [ ] Integration-test infrastructure can be started independently.

A successful health response confirms that the process is running. It does not prove that PostgreSQL or DeepSeek is operational.

### 5.3 Initial package organization

Start with a small, domain-oriented package structure.

```text
com.mira
├── MiraApplication.java
├── config/
├── common/
├── auth/
├── chat/
├── memory/
└── ai/
```

These are package boundaries, not a mandate to create empty classes for every planned component.

Add controllers, services, repositories, DTOs, and domain types when the corresponding feature is implemented.

The intended responsibilities are:

- `auth`: account registration, authentication, and session management.
- `chat`: conversations, messages, and chat orchestration.
- `memory`: profile validation, persistence, and lifecycle rules.
- `ai`: provider abstraction and DeepSeek integration.
- `config`: application configuration and infrastructure wiring.
- `common`: genuinely shared concerns, such as error handling.

Keep the backend a modular monolith. Do not introduce microservices.

## 6. Configuration and Secret Management

### 6.1 Local configuration

Use environment variables or a local configuration file excluded from Git.

Potential configuration names include:

```text
SPRING_PROFILES_ACTIVE
SERVER_PORT
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
DEEPSEEK_API_KEY
JWT_SIGNING_KEY
```

These are proposed names. The actual implementation may use Spring Boot's conventional datasource variables or another consistent configuration scheme.

Do not create placeholder secret values that could accidentally be used in production.

### 6.2 Configuration requirements

- [ ] Commit a safe example configuration containing names and non-sensitive examples only.
- [ ] Exclude local environment files from Git.
- [ ] Exclude production credentials from Git.
- [ ] Keep the DeepSeek key exclusively on the backend.
- [ ] Ensure configuration errors do not print secret values.
- [ ] Document how each developer configures their local environment.

A `.env.example` file may document the required names. It must not contain real credentials.

### 6.3 Configuration validation

The backend should fail with a clear, sanitized startup error when a required production secret is missing.

Optional integrations should be handled deliberately. For example, the backend skeleton should still be testable with a mock AI provider even when no DeepSeek key is configured.

## 7. Git and Repository Hygiene

### 7.1 Initial files

- [ ] Add a root `README.md`.
- [ ] Add a root `.gitignore`.
- [ ] Add the project documentation under `docs/`.
- [ ] Confirm the Android and backend wrappers are included.
- [ ] Confirm generated artifacts are excluded.
- [ ] Confirm local credentials are excluded.

The `.gitignore` should cover relevant generated files, including:

- Java and Gradle build outputs.
- Android build artifacts.
- IDE-local state where appropriate.
- Local environment files.
- Logs.
- Local databases and temporary test artifacts.
- Signing credentials and private key files.

Do not blanket-ignore all configuration files. Safe project configuration and migration files need to remain version-controlled.

### 7.2 Initial commit

The initial foundation commit should contain the project skeleton, safe configuration examples, and agreed documentation.

Suggested commit:

```text
chore: initialize MIRA project foundation
```

Use small, descriptive commits after this point.

### 7.3 README minimum contents

The root README should explain:

- What MIRA is.
- The architecture and technology stack.
- Repository structure.
- Prerequisites.
- How to run the backend locally.
- How to build and run the Android app.
- How to configure the development database.
- How to run tests.
- Where the design documents are located.

Do not document commands that have not been tested against the actual repository.

## 8. Database Foundation

### 8.1 Development database

Choose one development approach:

- A local PostgreSQL installation.
- A dedicated development database on a managed PostgreSQL provider.
- A disposable PostgreSQL instance for integration testing.

Do not use the production database as the routine development database.

The selected development environment should be documented so another developer can reproduce it.

### 8.2 Migration tooling

Configure the selected migration tool and verify that it can initialize an empty test database.

The first migration should create only the schema necessary for the initial implemented feature. It does not need to create every planned table before the corresponding domain behavior exists.

As the domain model is implemented, migrations will introduce tables such as:

- `USER_ACCOUNT`
- `CONVERSATION`
- `MESSAGE`
- `USER_STYLE_PROFILE`

The exact SQL types, foreign keys, constraints, indexes, and deletion policies must agree with `03-domain-model.md` and the implementation decisions recorded during development.

### 8.3 Database verification

- [ ] Backend can establish a connection using development credentials.
- [ ] Database credentials are externalized.
- [ ] A clean database can be initialized through migrations.
- [ ] Migrations execute consistently.
- [ ] Tests do not depend on production data.
- [ ] Database connection errors are handled without exposing credentials.

## 9. Testing Foundation

### 9.1 Backend

- [ ] Configure the unit-test framework.
- [ ] Add one meaningful passing unit test.
- [ ] Add a basic application context or API integration test.
- [ ] Configure PostgreSQL integration testing.
- [ ] Verify tests can run without a real DeepSeek API key.

### 9.2 Android

- [ ] Configure unit tests.
- [ ] Add one meaningful passing unit test.
- [ ] Verify the application builds in a clean checkout.
- [ ] Verify the application launches on a target device or emulator.

### 9.3 Test execution

The project should provide documented commands for running tests.

Typical Maven Wrapper commands include:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Typical Gradle Wrapper commands include:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

Execute these commands from the appropriate project directory and confirm that they work with the actual build configuration.

If integration tests require PostgreSQL or another service, document and automate that prerequisite where practical.

## 10. API Foundation

Before implementing individual features, establish the conventions that all endpoints will follow.

- [ ] API version prefix is documented.
- [ ] JSON request and response conventions are documented.
- [ ] Validation errors use a consistent response structure.
- [ ] Internal errors use a sanitized response structure.
- [ ] Timestamps use the documented UTC representation.
- [ ] UUIDs are used consistently where specified.
- [ ] Authentication requirements are documented for protected endpoints.
- [ ] Request correlation IDs are available for operational troubleshooting.

Do not create placeholder implementations of every endpoint simply to make the API appear complete.

Implement and test each endpoint as its corresponding feature is developed.

## 11. Architecture Decisions to Resolve

Record the following decisions in the relevant documents or in a short architecture decision record before implementing the affected feature.

### Authentication

- [ ] Select the access-token format and validation approach.
- [ ] Define refresh-token storage, rotation, expiration, and revocation.
- [ ] Decide whether email verification is required for the MVP.
- [ ] Define logout and session-reuse behavior.

### Chat processing

- [ ] Define message persistence when the provider fails.
- [ ] Define how uncertain provider outcomes are handled.
- [ ] Finalize idempotency behavior for concurrent requests and retries.
- [ ] Define the initial conversation-history context limit.

### Memory

- [ ] Finalize the communication-style profile schema.
- [ ] Choose the memory extraction trigger.
- [ ] Define evidence requirements for inferred preferences.
- [ ] Select the atomic generation/version mechanism for deletion protection.
- [ ] Define behavior when a user edits or deletes memory during extraction.

### Deployment and operations

- [ ] Select the backend hosting platform.
- [ ] Verify the selected PostgreSQL plan and its limits.
- [ ] Define environment-specific configuration.
- [ ] Document available backup and restoration capabilities.
- [ ] Define usage monitoring and provider cost controls.

These decisions do not all need to be completed before the skeleton can be built. They must be resolved before implementing the features that depend on them.

## 12. Phase 0 Exit Criteria

Phase 0 is complete when all the following conditions are satisfied:

- [ ] Repository structure is established.
- [ ] Documentation is present and internally consistent enough to begin implementation.
- [ ] Android application builds and launches.
- [ ] Spring Boot backend builds and starts.
- [ ] Health endpoint is available.
- [ ] PostgreSQL development connectivity is verified.
- [ ] Database migration tooling is operational.
- [ ] Backend and Android tests execute successfully.
- [ ] No real secrets are committed to the repository.
- [ ] README contains verified setup instructions.
- [ ] Local development and test workflows are reproducible.
- [ ] Unresolved architectural decisions are documented and assigned to the relevant implementation phase.

## 13. Out of Scope

The following are not Phase 0 requirements:

- Complete registration and login flows.
- Complete conversation management.
- Live DeepSeek integration.
- Persistent memory extraction.
- Image understanding.
- PDF processing.
- Production CI/CD automation.
- Kubernetes or microservices.
- Performance optimization before baseline measurements exist.

These belong to later implementation phases.

## 14. Recommended Execution Order

1. Review and place the design documents in the repository.
2. Establish the monorepo structure and `.gitignore`.
3. Initialize the Android application.
4. Initialize the Spring Boot backend.
5. Add a basic health endpoint and initial tests.
6. Configure PostgreSQL connectivity and migrations.
7. Verify clean builds and test execution.
8. Write and verify the README.
9. Review unresolved architecture decisions.
10. Commit the completed foundation.

## 15. Final Principle

Phase 0 should leave the project easier to extend, test, and maintain than it was before.

It is not a competition to create the most folders or install the most tools.

**The exit condition is a reproducible foundation: another developer should be able to clone MIRA, follow the documented setup instructions, build both applications, and run the initial tests without guessing how the project works.**