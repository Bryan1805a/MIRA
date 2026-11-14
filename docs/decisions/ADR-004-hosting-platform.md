# ADR-004: Backend Hosting Platform

| Attribute | Value |
| :--- | :--- |
| **Status** | Accepted |
| **Deciders** | Bryan, MIRA Architecture Team |
| **Date** | 2026-11-14 |
| **Technical Story** | [08-deployment-plan.md](../08-deployment-plan.md) |

---

## Context and Problem Statement

MIRA needs to host a single Java/Spring Boot container that connects to Neon PostgreSQL and the DeepSeek API. The MVP does not need horizontal autoscaling, multi-region failover, or a service mesh, but it does need managed TLS, secret injection, health checks, predictable cost, and a simple rollback path.

## Decision Drivers

* **Managed HTTPS and certificates** without operating a reverse proxy.
* **Secret injection** for `MIRA_JWT_SECRET_BASE64`, database credentials, and `DEEPSEEK_API_KEY`.
* **Container support** so the locally verified Docker image is the deployment artifact.
* **Health checks** using `/actuator/health/readiness`.
* **Low operational overhead** for a single-maintainer MVP.
* **Affordable** for a low-traffic personal product.
* **Portable** so the image can move to another platform later.

## Considered Options

1. **Render Web Service (Docker) + Neon**
2. **Fly.io Machines + Fly Postgres or Neon**
3. **AWS ECS Fargate + ALB + RDS/Neon**
4. **Self-hosted VPS + Docker Compose + Caddy**

## Decision Outcome

**Chosen Option: Option 1 — Render Web Service (Docker) + Neon PostgreSQL.**

### Rationale

- Render builds the existing `backend/Dockerfile`, injects secrets, terminates TLS, and performs health checks with almost no platform-specific code.
- The `render.yaml` blueprint makes the service configuration reviewable in Git.
- The container remains portable; moving to Fly.io or ECS later does not require application changes.
- Neon remains the managed database, preserving the Flyway and JSONB behavior verified by Testcontainers.
- Cost and operational overhead are lower than ECS/ALB for an MVP.

### Configuration

- Service type: Docker web service.
- Docker context: `backend/`.
- Health check: `/actuator/health/readiness`.
- Runtime profile: `prod`.
- Secrets: `MIRA_JWT_SECRET_BASE64`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `DEEPSEEK_API_KEY`.
- `MIRA_TRUST_FORWARDED_HEADERS=true` because Render terminates TLS and forwards client IPs.
- Neon connection string must include `sslmode=require`.
- The application binds to `PORT` (Render) with `SERVER_PORT` as the fallback.

## Pros and Cons of the Options

### Option 1: Render (Chosen)
- **Good:** Managed TLS, secrets, health checks, and deploys.
- **Good:** Blueprint is version-controlled.
- **Good:** Simple rollback to a previous deploy.
- **Bad:** Less control over networking and instance tuning than ECS.
- **Bad:** Cold starts or sleep behavior depend on the selected plan.

### Option 2: Fly.io
- **Good:** Excellent Docker-native deployment and regional placement.
- **Good:** Fine-grained machine sizing.
- **Bad:** More operational concepts (machines, volumes, fly.toml) for a single-service MVP.
- **Bad:** Database hosting adds another decision.

### Option 3: AWS ECS Fargate
- **Good:** Maximum control and enterprise integration.
- **Bad:** Significant setup (VPC, ALB, IAM, ECR, CloudWatch) for a single container.
- **Bad:** Highest operational and cost overhead for the MVP.

### Option 4: Self-hosted VPS
- **Good:** Cheapest at very low scale and full control.
- **Bad:** The team becomes responsible for OS patching, TLS renewal, backups, and uptime.

## Consequences & Follow-up Actions

- Provision the Neon database and record the pooled JDBC URL.
- Create the Render service from `render.yaml` and set the secret environment variables.
- Verify `GET /actuator/health` and `/actuator/health/readiness` over HTTPS.
- Confirm Flyway applies `V1`–`V10` on first boot.
- Configure the Android release `MIRA_API_BASE_URL` to the Render HTTPS domain.
- Revisit this decision if traffic, latency, or cost outgrows the Render service.
