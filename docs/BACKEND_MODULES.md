# Backend Modules & Packages

> **Architecture authority:** [`docs/CAUSAL_PIPELINE.md`](./CAUSAL_PIPELINE.md). The package map below mirrors the funnel stages.

## Entry points
- `org.kfh.aiops.AiOpsApplication` — Spring Boot application entry point at the root package, using default component scanning for `org.kfh.aiops.*`
- `org.kfh.aiops.ServletInitializer` — WAR deployment initializer for managed servlet containers
- `org.aiopsanalysis.AiOpsAnalysisApplication` — deprecated launcher-only compatibility shim for older IDE run configurations; contains no Spring annotations or backend components
- `org.kfh.aiops.platform.security.SecurityConfig` — security configuration

## Package map (high level)
- `org.kfh.aiops.platform` — security, tenant/country context, audit, configuration, exceptions, observability
- `org.kfh.aiops.commandcenter` — dashboard, alerts, applications, inventory, reports, schedules read/write API scaffolding
- `org.kfh.aiops.incident` — incident API, **deterministic lifecycle engine** (`incident.lifecycle`), service contracts, model types
- `org.kfh.aiops.plugin` — connector API/service scaffolding (Stage 0 collectors per [CAUSAL_PIPELINE](./CAUSAL_PIPELINE.md))
- `org.kfh.aiops.ingestion` — country gateway, batch validation, outbox push (Stage 0)
- `org.kfh.aiops.normalization` — `CanonicalTelemetryEvent` + enrichment + `fingerprint` Redis SETNX dedup (Stages 1–2)
- `org.kfh.aiops.index` — Custom Index Engine writer/reader/shard (Stage 3) — **not Elasticsearch**
- `org.kfh.aiops.topology` — Neo4j blast radius + upstream dependencies (Stage 4)
- `org.kfh.aiops.health` — Redis hot health state + scoring + baseline
- `org.kfh.aiops.rca` — `evidence` (EvidencePackBuilder, ≤ 3 KB), `causal` (11-factor scoring), `service` (orchestration)
- `org.kfh.aiops.ai` — `router` (AiRouter), `deepseek` (local), `azureopenai` (Azure OpenAI 5.5), `cost` (CostGuard), `prompt`, `model`
- `org.kfh.aiops.notification` — Teams / email / SMS / webhook (outbox-driven)

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
