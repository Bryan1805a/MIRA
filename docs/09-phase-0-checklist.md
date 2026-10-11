# MIRA — Phase 0: Foundation & Environment Setup Checklist

| Attribute | Value |
| :--- | :--- |
| **Document ID** | MIRA-CHK-000 |
| **Classification** | Internal Engineering Specification |
| **Status** | Active Execution Baseline |
| **Version** | 1.0.0 |
| **Last Updated** | 2026-10-11 |
| **Author / Lead** | Bryan / MIRA Core Engineering Team |
| **Phase Milestone** | Phase 0 — Reproducible Project Foundation |

---

## Document Revision History

| Version | Date | Author | Description of Changes |
| :--- | :--- | :--- | :--- |
| 0.1.0 | 2026-10-10 | Engineering Team | Initial draft of local development prerequisites and repository setup. |
| 1.0.0 | 2026-10-11 | Bryan | Enterprise standardization: integrated visual Mermaid execution roadmap, concrete skeleton milestones, Flyway setup, and exit gating criteria. |

---

## 1. Phase 0 Purpose & Objectives

Phase 0 establishes a reproducible development environment, structured monorepo, clean build toolchains, and initial application skeletons **prior** to developing functional MVP features (authentication, chat orchestration, and memory extraction).

### 1.1 Core Objectives
* Verify development toolchains (JDK 17+, Maven Wrapper, Android Studio, Gradle, PostgreSQL).
* Establish the official repository structure with strict `.gitignore` rules.
* Scaffold a minimal, compiling Spring Boot 3.x backend application with Actuator health checks.
* Scaffold a minimal, compiling native Android Java application with Gradle wrapper.
* Establish version-controlled database migrations via Flyway.
* Provide comprehensive local run instructions so any engineer can clone, compile, and test the project in minutes without guesswork.

```mermaid
flowchart TD
    subgraph Phase0Plan ["Phase 0 Execution Flowchart"]
        RepoSetup["1. Monorepo Setup\n(.gitignore, README, docs layout)"]
        BackendSkel["2. Backend Skeleton\n(Spring Boot 3, Maven Wrapper, Actuator)"]
        AndroidSkel["3. Android Skeleton\n(Java, Gradle Wrapper, Basic Activity)"]
        DBSkel["4. Database & Flyway\n(Neon Connection, Migration Baseline)"]
        Verification["5. Multi-Project Verification\n(mvn test, gradlew assembleDebug)"]
    end

    RepoSetup --> BackendSkel
    RepoSetup --> AndroidSkel
    BackendSkel --> DBSkel
    DBSkel --> Verification
    AndroidSkel --> Verification
```

---

## 2. Target Repository Structure

```text
mira/
├── .gitignore                      # Monorepo root ignore rules (builds, secrets, IDE state)
├── README.md                       # Comprehensive onboarding & developer setup guide
├── LICENSE                         # Project license
│
├── android/                        # Native Android Application (Java / Gradle)
│   ├── app/
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/mira/android/
│   │   │   │   └── res/
│   │   │   └── test/
│   │   └── build.gradle
│   ├── gradle/
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradlew
│   └── gradlew.bat
│
├── backend/                        # Spring Boot Modular Monolith (Java 17 / Maven)
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/mira/
│   │   │   │   ├── MiraApplication.java
│   │   │   │   ├── common/
│   │   │   │   ├── auth/
│   │   │   │   ├── chat/
│   │   │   │   ├── memory/
│   │   │   │   └── ai/
│   │   │   └── resources/
│   │   │       ├── application.properties
│   │   │       └── db/migration/   # Flyway SQL migrations
│   │   └── test/
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
│
└── docs/                           # Central Engineering Documentation
    ├── 00-overview.md
    ├── 01-requirements.md
    ├── 02-architecture.md
    ├── 03-domain-model.md
    ├── 04-api-contract.md
    ├── 05-memory-lifecycle.md
    ├── 06-security-and-privacy.md
    ├── 07-testing-strategy.md
    ├── 08-deployment-plan.md
    ├── 09-phase-0-checklist.md
    └── decisions/                  # Architecture Decision Records (ADRs)
        ├── ADR-001-backend-stack.md
        ├── ADR-002-memory-model.md
        └── ADR-003-file-processing.md
```

---

## 3. Toolchain & Environment Verification

Before executing build commands, ensure the following local prerequisites are confirmed:

- [ ] **Git:** Installed and configured (`git --version`).
- [ ] **Java Development Kit (JDK):** JDK 17 or higher configured (`java -version`).
- [ ] **Android Studio & SDK:** Android SDK platform tools and build tools installed.
- [ ] **PostgreSQL:** Local PostgreSQL or access to Neon cloud development database.
- [ ] **Network Connectivity:** Unrestricted access to Maven Central and Google Maven repositories.

---

## 4. Phase 0 Step-by-Step Execution Plan

### Step 1: Repository Hygiene & Global Ignore Rules
* Create comprehensive `.gitignore` in the repository root ignoring:
  * Maven targets (`backend/target/`).
  * Gradle builds and caches (`android/.gradle/`, `android/app/build/`).
  * IDE directories (`.idea/`, `*.iml`, `.vscode/`).
  * Local secrets and environment files (`.env`, `*.key`, `keystore.jks`).
* Ensure documentation links and paths are standardized.

### Step 2: Initialize Spring Boot Backend Skeleton
* Configure `backend/pom.xml` with dependencies:
  * `spring-boot-starter-web`
  * `spring-boot-starter-validation`
  * `spring-boot-starter-actuator`
  * `spring-boot-starter-data-jpa`
  * `postgresql` JDBC driver
  * `flyway-core`
  * `spring-boot-starter-test`
* Scaffold entry point: `com.mira.MiraApplication.java`.
* Configure `application.properties` with environment-variable placeholders.
* Expose `/actuator/health` endpoint.
* Verify clean build via `.\mvnw.cmd clean test`.

### Step 3: Initialize Native Android Skeleton
* Initialize Android project under `android/` using Java.
* Configure minimal `MainActivity.java` with a placeholder launch screen.
* Verify Android compilation via `.\gradlew.bat assembleDebug`.
* Confirm debug APK installs on an emulator or physical device.

### Step 4: Configure Database & Migration Baseline
* Connect backend to the development PostgreSQL instance.
* Create Flyway migration directory `backend/src/main/resources/db/migration/`.
* Add initial baseline migration `V1__init_user_account.sql`.
* Run backend startup and verify that Flyway executes the migration without errors.

### Step 5: Root Documentation & Developer Onboarding (README)
* Author the root [README.md](file:///C:/Users/Bryan/Documents/MIRA/README.md) documenting:
  * Architecture overview.
  * System prerequisites.
  * Backend local execution instructions (`.\mvnw.cmd spring-boot:run`).
  * Android local build instructions (`.\gradlew.bat assembleDebug`).
  * Database migration and configuration instructions.
  * Automated testing commands.

---

## 5. Phase 0 Exit & Acceptance Gate

Phase 0 is formally complete only when every item below is verified:

- [ ] Monorepo structure conforms to the specification.
- [ ] `.gitignore` prevents build outputs, IDE state, and secret files from being committed.
- [ ] Backend compiles and runs tests via `.\mvnw.cmd clean verify`.
- [ ] Backend exposes functional health telemetry on `/actuator/health`.
- [ ] Android application compiles and builds an APK via `.\gradlew.bat assembleDebug`.
- [ ] PostgreSQL connection is verified and Flyway migration initializes the schema.
- [ ] All architectural decision records (`ADR-001`, `ADR-002`, `ADR-003`) are formally authored and approved.
- [ ] Root `README.md` allows a new developer to set up and run the project locally without guessing.