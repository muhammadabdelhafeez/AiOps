# Backend Modules & Packages

## Entry points
- `org.kfh.aiops.AiOpsApplication` — Spring Boot application entry point at the root package, using default component scanning for `org.kfh.aiops.*`
- `org.kfh.aiops.ServletInitializer` — WAR deployment initializer for managed servlet containers
- `org.aiopsanalysis.AiOpsAnalysisApplication` — deprecated launcher-only compatibility shim for older IDE run configurations; contains no Spring annotations or backend components
- `org.kfh.aiops.platform.security.SecurityConfig` — security configuration

## Package map (high level)
- `org.kfh.aiops.platform` — security, tenant/country context, audit, configuration, exceptions, observability
- `org.kfh.aiops.commandcenter` — dashboard, alerts, applications, inventory, reports, schedules read/write API scaffolding
- `org.kfh.aiops.incident` — incident API, lifecycle/service contracts, model types
- `org.kfh.aiops.plugin` — connector API/service scaffolding
- Future bounded contexts follow the enterprise package map: ingestion, normalization, index, topology, health, RCA, AI, notification

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
