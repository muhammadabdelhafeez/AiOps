# Backend Modules & Packages

## Entry points
- `org.aiopsanalysis.AiOpsAnalysisApplication` — Spring Boot entry
- `org.aiopsanalysis.SecurityConfig` — security configuration

## Package map (high level)
- `org.aiopsanalysis.controller` — REST controllers (HTTP)
- `org.aiopsanalysis.service` — business logic (RBAC + audit + outbox)
- `org.aiopsanalysis.repository` — persistence (tenant-scoped)
- `org.aiopsanalysis.domain` — entities / domain models
- `org.aiopsanalysis.dto` — request/response DTOs + validation
- `org.aiopsanalysis.config` — integration configs (Neo4j, OpenAI, etc.)

## Cross-cutting requirements
- Every request must include `X-Tenant-Id` and `X-User-Id`.
- Every write must:
  1) authorize (permission-based RBAC)
  2) write audit log
  3) be tenant-scoped in DB
  4) emit outbox event for async work

## Where to look
- DB schema: `src/main/resources/db/migration/*.sql`
- Neo4j schema: `src/main/resources/db/neo4j/*.cypher`
- Static UI assets served from: `src/main/resources/static/`
