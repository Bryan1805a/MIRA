# ADR-001: Backend Technology Stack & Architecture Style

| Attribute | Value |
| :--- | :--- |
| **Status** | Accepted |
| **Deciders** | Bryan, MIRA Architecture Team |
| **Date** | 2026-10-10 |
| **Technical Story** | [02-architecture.md](../02-architecture.md) |

---

## Context and Problem Statement

MIRA requires a backend service to handle user registration, credential authentication, conversational message persistence, external AI provider orchestration (DeepSeek API), and style memory persistence. 

We need to choose a backend runtime, framework, and architectural deployment model that maximizes engineering velocity, guarantees robust data consistency, minimizes operational complexity for a small engineering team, and provides high maintainability.

## Decision Drivers

* **Type Safety & Maintainability:** Minimizing runtime typing defects and maintaining a clean domain model.
* **Transaction Integrity (ACID):** Need atomic guarantees across authentication tokens, message logging, and memory version updates.
* **Low Operational Overhead:** The deployment model must be simple to deploy, maintain, and monitor without dedicated infrastructure operators.
* **Secret Isolation:** Provider API keys and database credentials must remain strictly isolated on the backend.
* **Testing Ecosystem:** Rich automated testing tooling (mocking, containerized database testing).

## Considered Options

1. **Option 1: Modular Monolith with Java 21+ and Spring Boot 4.1.x** (originally evaluated as Java 17+ / Spring Boot 3.x)
2. **Option 2: Microservices Architecture with Node.js / Express**
3. **Option 3: Python FastApi / AsyncIO Monolith**

---

## Decision Outcome

**Chosen Option: Option 1 — Modular Monolith with Java 21 and Spring Boot 4.1.x.** The original decision selected Java 17+ / Spring Boot 3.x; the delivered scaffold uses the newer LTS/runtime baseline, as documented in the 2026-10-21 amendment below.

### Rationale

Spring Boot 4.1.x provides an enterprise-tested, battle-proven ecosystem with first-class support for:
1. **Modular Domain Isolation:** Domain packages (`auth`, `chat`, `memory`, `ai`) maintain clean service boundaries in a single repository without premature network segmentation.
2. **Declarative Transactions (`@Transactional`):** Guarantees that multi-table state changes (e.g., token rotation, assistant response creation) are executed atomically.
3. **Rich Ecosystem Integration:** Seamless integration with PostgreSQL, Flyway schema migrations, Spring Security, and Testcontainers.
4. **Single-Artifact Deployment:** Compiles to a single executable fat JAR, minimizing hosting cost and deployment complexity on cloud PaaS providers.

---

## Pros and Cons of the Options

### Option 1: Java 21+ / Spring Boot 4.1.x Modular Monolith (Chosen)
* **Good:** Strict compile-time type safety and enterprise maintainability.
* **Good:** Single deployment artifact reduces operational friction to near zero.
* **Good:** Native integration with Testcontainers for running real PostgreSQL test suites.
* **Good:** First-class security tooling via Spring Security.
* **Bad:** Slightly higher memory footprint on initial JVM startup compared to lightweight runtimes.

### Option 2: Node.js / Express Microservices
* **Good:** Fast cold-start times.
* **Bad:** High operational complexity: distributed tracing, inter-service network failure modes, and two-phase commits.
* **Bad:** Dynamic typing introduces runtime type hazards unless TypeScript is heavily configured.
* **Bad:** Premature distribution introduces unnecessary overhead for an MVP.

### Option 3: Python FastAPI
* **Good:** Rapid prototyping and intuitive syntax.
* **Bad:** Asynchronous database drivers and concurrent task management in Python can introduce subtle race conditions.
* **Bad:** Less mature transaction and enterprise security orchestration compared to Spring Security.

---

## Consequences & Follow-up Actions

* All backend code will reside in `backend/` using Maven as the build system.
* Application packages will enforce modular domain boundaries (`com.mira.auth`, `com.mira.chat`, `com.mira.memory`, `com.mira.ai`).
* Database migrations will be managed using Flyway.

---

## Amendment — 2026-10-21: Runtime Baseline & Packaging

The executable scaffold was generated with a newer toolchain than the original decision record assumed. The architectural choice (Option 1, modular monolith) is unchanged; the runtime and packaging details below supersede the version-specific wording above.

| Topic | Original ADR | Delivered Baseline |
| :--- | :--- | :--- |
| Java | 17+ | **Java 21** |
| Spring Boot | 3.x | **4.1.x** |
| JSON | Jackson 2 | **Jackson 3** (`tools.jackson.databind`) |
| Web starter | `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| Testcontainers | 1.x | **2.x** (`testcontainers-postgresql`, `org.testcontainers.postgresql`) |
| Package root | `com.mira` feature packages | **`com.mira.mira_backend`** layer-first packages |

The backend currently uses `config`, `controller`, `dto`, `entity`, `exception`, `repository`, `security`, and `service` packages. Domain isolation is preserved through service boundaries, ownership queries, and DTO contracts. A feature-package refactor remains possible and does not require an API or schema change; it is not a Phase 1 deliverable.

**Consequences**

* CI and local instructions must use JDK 21 and the Maven Wrapper.
* New dependencies must be compatible with Spring Boot 4.1.x and Jackson 3.
* The original `com.mira.auth` / `com.mira.chat` package names in this ADR are historical; the current layout is authoritative until a refactor is explicitly scheduled.
