# MIRA — Deployment Plan

**Document:** `docs/08-deployment-plan.md`  
**Status:** Draft  
**Scope:** MVP deployment and operational baseline  
**Related documents:** `02-architecture.md`, `04-api-contract.md`, `06-security-and-privacy.md`, `07-testing-strategy.md`

---

## 1. Purpose

This document defines how MIRA will move from local development to a publicly accessible application.

The deployment strategy prioritizes:

- Low operational complexity.
- Predictable and controllable costs.
- Secure management of credentials.
- Reproducible builds and configuration.
- Reliable database initialization and schema migration.
- Clear separation between development and production.
- A deployment process suitable for a student portfolio project.

The MVP will use a modular monolith rather than multiple independently deployed backend services.

## 2. Deployment Architecture

The initial deployment consists of three primary components:

| Component | Technology | Responsibility |
|---|---|---|
| Android client | Java + Android Studio | User interface and API communication |
| Backend | Spring Boot + Maven | Authentication, chat, memory, and provider integration |
| Database | Managed PostgreSQL on Neon | Persistent application data |

The backend communicates with the DeepSeek API using server-side credentials.

The Android application must never connect directly to PostgreSQL or call DeepSeek using a privileged backend API key.

### 2.1 Proposed topology

```text
Android Application
        |
        | HTTPS / JSON
        v
Public Backend URL
        |
        v
Spring Boot Application
        |
        +----------> Neon PostgreSQL
        |
        +----------> DeepSeek API
```

The backend is the security boundary between the client, persistent data, and external AI provider.

## 3. Environment Strategy

MIRA should use separate configuration for development, testing, and production.

### 3.1 Development

The developer's computer runs:

- Spring Boot backend.
- Local Android development environment.
- A development PostgreSQL database or isolated development database.
- A mock AI provider for ordinary automated tests.
- DeepSeek API access only when needed for manual integration testing.

The development environment must not use production credentials or production user data.

### 3.2 Testing

The test environment should provide:

- A disposable PostgreSQL instance for integration tests.
- Test-specific signing keys and credentials.
- A controlled mock AI provider.
- Automated build and test execution.

Tests must not depend on the production database or a live AI provider unless explicitly designed as integration smoke tests.

### 3.3 Production

The production environment contains:

- A deployed Spring Boot backend.
- A managed PostgreSQL database.
- Server-side provider credentials.
- HTTPS.
- Restricted database access.
- Production logging and health checks.

Production secrets must be configured through the hosting platform's secret-management mechanism or an equivalent secure configuration mechanism.

### 3.4 Configuration separation

Configuration values should be classified into two categories.

**Non-secret configuration**

- Application port.
- Active Spring profile.
- Public backend URL used by the Android application.
- Connection-pool limits.
- Provider timeout settings.
- Logging level.

**Secret configuration**

- Database password or credential-bearing database URL.
- DeepSeek API key.
- JWT signing key, if applicable.
- Other private authentication or integration credentials.

Secret values must not be committed to Git, embedded in the APK, or printed in logs.

A local environment file may be used for development if it is excluded from version control and protected appropriately.

## 4. Backend Hosting

### 4.1 Hosting requirements

The selected hosting platform must support:

- A compatible Java runtime.
- Running a Spring Boot application.
- HTTPS for the public API.
- Environment variables or secure secret configuration.
- Configurable application startup and health checks.
- Access to the managed PostgreSQL database.
- Logs for diagnosing deployment failures.
- A documented deployment and rollback process.

The deployment should not depend on a developer's computer remaining online.

### 4.2 Packaging

MIRA should initially deploy as a standard executable Spring Boot JAR.

The build process should produce a reproducible artifact using the project's Maven configuration.

Example local build command:

```bash
./mvnw clean verify
```

On Windows, the equivalent wrapper command is:

```powershell
.\mvnw.cmd clean verify
```

The exact command must match the actual repository structure and Maven configuration.

If the build succeeds, the executable JAR can be deployed to a compatible Java hosting platform.

Docker is optional. It may be introduced if the chosen host requires a container image or if containerization materially improves reproducibility. It is not a prerequisite for the initial architecture.

### 4.3 Port configuration

The application must use the port supplied by the hosting environment when the platform requires a dynamic port.

A typical Spring Boot configuration is:

```properties
server.port=${PORT:8080}
```

This allows a locally convenient default while supporting platforms that inject `PORT`.

The actual deployment configuration must follow the selected host's requirements.

## 5. PostgreSQL Deployment

### 5.1 Managed database

Neon PostgreSQL is the proposed managed database for the MVP.

Before selecting a production configuration, verify the current plan's limits, connection requirements, compute behavior, storage limits, backup capabilities, and pricing.

A free tier, if available, must not be assumed to provide unlimited uptime, permanent storage, or a production-grade recovery guarantee.

### 5.2 Connection configuration

The backend should obtain database connection details from server-side configuration.

The JDBC URL, username, and password must not be embedded in source code.

If the database provider requires TLS, configure the JDBC connection to validate the server certificate according to the provider's documented instructions. Do not disable certificate validation to work around connection problems.

Use a connection pool with reasonable limits for the selected host and database plan.

The initial pool size should reflect actual deployment resources rather than an arbitrary large number of connections.

### 5.3 Database migrations

Use a versioned migration tool, such as Flyway, to manage schema changes.

Migrations should:

- Be committed to Git.
- Have stable, unique version identifiers.
- Be tested against a clean database.
- Preserve existing data unless a change explicitly requires otherwise.
- Be reviewed for destructive operations.
- Be applied in a controlled deployment step.

Do not rely on manually editing the production schema or automatically recreating tables whenever the application starts.

Production schema changes should be deliberate and traceable.

### 5.4 Database backups

Before production deployment, verify the selected database plan's backup and recovery capabilities.

The deployment documentation should specify:

- What data is backed up.
- How frequently backups or recovery points are available.
- How long recovery points are retained.
- Who can access them.
- How restoration is tested.

A backup that has never been restored is not a verified recovery procedure.

For the MVP, a documented provider-supported recovery process may be sufficient. More advanced backup automation can be added if required by the chosen service.

## 6. DeepSeek Integration

### 6.1 Credential configuration

The DeepSeek API key must be provided only to the backend.

The production Android application contains the public backend URL, not the provider key.

The backend should fail safely if required provider configuration is missing. It must not expose the missing credential's value or internal configuration details in its error response.

### 6.2 Timeouts and failure handling

Configure bounded connection and response timeouts.

The application must handle:

- Provider rate limits.
- Provider service errors.
- Network failures.
- Timeouts.
- Malformed responses.
- Exhausted usage budgets, where applicable.

Retries must be limited and must respect the idempotency and message-processing policy documented in `04-api-contract.md`.

A timeout does not prove that the provider did not process the request. The backend must not assume otherwise.

### 6.3 Cost control

Before making the application publicly accessible:

- Set request and message-size limits.
- Implement per-account rate limits.
- Monitor provider usage and costs.
- Configure provider-side spending limits or alerts where available.
- Bound concurrent requests.
- Avoid unbounded automatic retries.
- Keep routine tests on a mock provider.

Provider-side budget controls depend on the capabilities of the selected account and service plan. If a hard spending limit is unavailable, document the available monitoring and mitigation procedures.

## 7. API and Health Checks

### 7.1 Public API

The deployed backend should expose the documented API under:

```text
/api/v1
```

The actual public base URL depends on the hosting platform.

For example, the Android application might use a configuration value representing:

```text
https://<deployed-backend-host>/api/v1
```

The placeholder must be replaced with the actual deployed URL before release.

The production application must not point to `localhost` or a development machine's private address.

### 7.2 Health endpoints

Use Spring Boot Actuator or an equivalent lightweight mechanism for health checks.

Distinguish between:

- **Liveness:** the application process is functioning sufficiently to continue running.
- **Readiness:** the application is ready to receive traffic.

Database connectivity may affect readiness, depending on the deployment architecture. A temporary DeepSeek outage should not necessarily make the entire backend unready if authentication, history retrieval, and other non-AI operations remain functional.

Health endpoints must not disclose credentials, environment variables, detailed database connection strings, or internal diagnostic information to the public.

Detailed health information should be restricted to trusted operational access where appropriate.

### 7.3 API error responses

Production error responses should follow the documented error contract.

They must not expose stack traces, SQL statements, internal file paths, provider keys, or other private implementation details.

Use request correlation identifiers to connect client-visible failures with protected server logs without logging the full conversation content.

## 8. Database Migration and Deployment Order

The release procedure must account for application code and database schema compatibility.

Recommended deployment sequence:

1. Run the automated test suite.
2. Build the release artifact.
3. Review the database migrations included in the release.
4. Confirm the required production configuration and secrets.
5. Verify that an appropriate recovery point or backup exists when the change warrants it.
6. Apply the required database migration.
7. Deploy the backend artifact.
8. Verify startup and readiness.
9. Run a small set of production smoke tests.
10. Inspect logs and usage metrics for unexpected failures.

### 8.1 Schema compatibility

Avoid migrations that immediately break the currently deployed application during a rolling or partial deployment.

When necessary, use an expand-and-contract approach:

1. Add a compatible schema change.
2. Deploy code that can work with the compatible schema.
3. Migrate existing data if needed.
4. Remove obsolete schema elements in a later release.

This is more important when a platform can run multiple application instances during deployment.

## 9. Android Release Configuration

### 9.1 Backend URL

The Android application should use a deliberate configuration mechanism for its backend base URL.

Development and production builds must not accidentally share an incorrect endpoint.

The production build must use HTTPS and must not contain backend secrets.

### 9.2 Release build

Before distributing a release APK:

- Build using the intended release configuration.
- Verify that production endpoint configuration is correct.
- Check that debug logging does not expose private data.
- Ensure no test credentials are included.
- Confirm that authentication and session handling work against the deployed backend.
- Verify that network and API errors are presented appropriately.

The release artifact should be reproducible from the repository and documented build process.

### 9.3 Signing

Release builds must use an appropriate signing configuration.

Private signing credentials must not be committed to Git. Back up signing credentials securely because losing them may complicate updates to an already distributed application.

Debug signing must not be treated as the production release process.

## 10. Continuous Integration and Deployment

CI should first automate build and test verification.

A practical pipeline is:

```text
Pull Request
     |
     v
Backend Build and Tests
     |
     v
Android Build and Tests
     |
     v
Review / Merge
     |
     v
Production Deployment
     |
     v
Health Check and Smoke Tests
```

Automatic production deployment may be enabled after the process is stable. For the initial MVP, a manual approval before production deployment is acceptable.

CI must not require production credentials merely to compile the project or run ordinary tests.

Deployment credentials, if required by the chosen platform, must be narrowly scoped and stored as protected CI secrets.

## 11. Rollback and Incident Response

### 11.1 Application rollback

If a deployment introduces a serious application defect:

1. Stop further deployments.
2. Inspect health checks and sanitized logs.
3. Redeploy the last known-good application artifact if compatible with the current schema.
4. Verify readiness and core workflows.
5. Document the cause and corrective action.

Keeping release artifacts or a reproducible method for rebuilding them makes rollback easier.

### 11.2 Database rollback

Application rollback does not automatically reverse a database migration.

A migration may be irreversible or may have modified existing data. Therefore:

- Review destructive migrations before deployment.
- Prefer backward-compatible changes.
- Use a tested recovery procedure when data restoration is necessary.
- Do not execute a destructive rollback automatically without understanding its effect on production data.

### 11.3 Secret exposure

If a provider key, database credential, signing secret, or authentication signing key is exposed:

1. Revoke or rotate the affected credential.
2. Update the backend configuration.
3. Verify that the old credential can no longer be used where applicable.
4. Review relevant access logs.
5. Remove accidental exposure from the source of distribution and repository history where appropriate.
6. Document the incident and corrective action.

Removing a secret from the latest source file does not invalidate the secret itself.

## 12. Monitoring and Operational Maintenance

The MVP should monitor a small set of useful operational signals:

- Backend availability.
- Startup and readiness failures.
- API error rates.
- Response latency.
- Database connectivity failures.
- Provider error and timeout rates.
- Provider usage and estimated costs.
- Rate-limit activity.

Logs must follow `06-security-and-privacy.md`. Do not add full user messages or credentials merely to make debugging easier.

The project should also document how to:

- Inspect deployment logs.
- Update backend configuration.
- Rotate secrets.
- Apply database migrations.
- Restore data when supported.
- Redeploy a previous application version.

## 13. Deployment Checklist

### Before deployment

- [ ] Backend build and tests pass.
- [ ] Android build and tests pass.
- [ ] Production secrets are configured outside source control.
- [ ] Database connectivity is verified securely.
- [ ] Database migrations have been tested.
- [ ] Provider timeouts, request limits, and usage controls are configured.
- [ ] Public API uses HTTPS.
- [ ] Health endpoints do not expose sensitive diagnostics.
- [ ] Logging follows the privacy policy.
- [ ] A recovery procedure appropriate to the database plan is documented.

### After deployment

- [ ] Backend starts successfully.
- [ ] Readiness check passes.
- [ ] Authentication works.
- [ ] A test conversation can be created and retrieved.
- [ ] A controlled AI request succeeds.
- [ ] Memory settings behave correctly.
- [ ] Cross-account access remains blocked.
- [ ] Logs do not expose secrets or full conversation bodies.
- [ ] Provider usage and costs are monitored.
- [ ] Android can communicate with the deployed backend over HTTPS.

## 14. Deferred Infrastructure

The following are not required for the initial MVP unless the selected platform or actual workload demands them:

- Kubernetes.
- Multiple backend instances.
- Microservices.
- A dedicated message queue.
- Complex service discovery.
- A separate observability stack.
- Multi-region deployment.
- Automated zero-downtime database migration orchestration.

The deployment design should remain simple enough for one student to understand and maintain, while preserving the security and data-integrity requirements already defined.

## 15. Final Principle

A deployment is complete only when the application is accessible, its data is protected, its external dependencies are configured correctly, and its failure modes are understood.

**MIRA should be easy to deploy, but it must never depend on hardcoded secrets, undocumented manual database changes, or the assumption that external services will always be available.**