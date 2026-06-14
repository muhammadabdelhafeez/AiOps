# KFH AIOps Command Center — Architecture

## Architecture principles
- Multi-tenant by design: every request scoped by tenant.
- Permission-based RBAC; scope policies control what data is visible.
- Evidence-first AI: no hallucinated RCA; must cite retrieval pack evidence.
- Async processing via Outbox pattern (reliable background jobs).
- Degraded-mode resilience: system keeps operating if Neo4j/OpenAI are down.

## High-level components
### Frontend (React)
- Pages: Dashboard, Alerts, Incidents, Applications, Inventory, Reports, Admin (Connectors/Schedules/Users/Settings)
- Reads from backend APIs only.
- Uses drawers/drilldowns for NOC speed.

### Backend (Spring Boot)
- API Gateway layer (controllers)
- API context validation filter for `/api/**` (`X-Tenant-Id`, `X-User-Id`, safe `X-Correlation-Id`)
- Services (business logic + RBAC + audit)
- Repositories (tenant-scoped DB access)
- Outbox publisher/worker consumers
- Integration clients (SharePoint, Teams, OpenAI, Neo4j)

### Local development profile
- The root Spring Boot application `org.kfh.aiops.AiOpsApplication` serves the static SPA from `classpath:/static/` and exposes Phase 1 tenant-aware `/api/v1/**` scaffold endpoints.
- The `local` Spring profile disables datasource, JPA, Flyway, Spring Batch JDBC, Neo4j, and Redis auto-connections so UI/API scaffold work can proceed without PostgreSQL, Neo4j, Azure OpenAI, Redis, or SharePoint credentials.
- This profile is for local development only and must not be used as a production substitute for tenant-scoped persistence, RBAC, audit, and integration-backed backend services.

### Datastores
- PostgreSQL: system of record for config, identity, incidents, reports, runs, audit, outbox.
- Neo4j: hot graph for topology/correlation, impact paths, neighbors.
- SharePoint: artifacts storage
  - /raw/ (raw canonical exports)
  - /evidence/ (evidence CSVs)
  - /output/ (report bundles)

## Data ownership rules
- PostgreSQL is authoritative for: tenants/users/RBAC, connectors/schedules, incidents lifecycle, report pack index, audit/outbox.
- Neo4j is authoritative for: topology traversal + correlation edges (analytics only).
- SharePoint is authoritative for: large evidence artifacts, exports.
- API-facing ID-based reads/writes must use tenant-scoped lookups and never `id` alone.

## Main workflows (deep)

### 1) Admin configuration
1. Admin configures connectors/schedules/inventory scope.
2. Secrets stored encrypted; audit logged.
3. Outbox event emitted for workers to refresh/validate connectors.

### 2) Ingestion
1. Connector run starts (manual or scheduled).
2. Raw alerts collected from sources.
3. Raw stored/archived (/raw/) and/or persisted in DB staging.
4. Connector run result recorded.

### 3) Normalization & fingerprinting
1. Normalize alert into canonical form.
2. Compute exact and family fingerprints.
3. Upsert canonical alert occurrence/group in DB.
4. Emit outbox events for graph upsert + AI embedding jobs.

### 4) Graph upsert (Neo4j)
1. Upsert resources/apps and dependency edges.
2. Upsert alert group nodes and relationships.
3. Compute neighbor correlations (time-window co-occurrence).

### 5) AI processing
1. Embeddings generated for alert groups/incidents.
2. Retrieval pack assembled:
   - top correlated alerts
   - impacted apps/resources + topology neighborhood
   - recurrence matches (exact/family/semantic)
   - evidence references (raw/evidence pointers)
3. GPT-5.2-Pro generates:
   - executive summary
   - likely RCA hypotheses with confidence + evidence references
   - impact and recommended checks
4. Results stored as incident/report narratives with traceability.

### 6) Evidence & reporting
1. Generate evidence CSVs (artifact bundle).
2. Write report pack index entry in DB.
3. Store artifacts in SharePoint (/evidence/ and /output/).
4. Send Teams summary alert with deep links.

## Degraded-mode behavior
- Neo4j down:
  - Continue incident creation using DB fingerprints
  - Mark correlation "degraded"
- OpenAI down:
  - Queue AI jobs in outbox for retry
  - Mark "AI pending" in UI/report

## Observability
- Metrics:
  - connector run success rate, latency
  - schedule run success rate
  - AI queue depth, AI latency
  - evidence generation time
  - incidents per hour and recurring ratio
- Logs:
  - correlationId per request and per run
  - no secrets in logs
- Tracing (recommended):
  - distributed tracing across ingestion → processing → notifications
