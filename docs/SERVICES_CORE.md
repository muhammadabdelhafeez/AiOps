# Services — Core Domain

> Catalog of **core business services and endpoints** for the KFH Causal AIOps Platform.
> Update this file whenever a new core class or REST endpoint is added/changed.
> Support / cross-cutting services live in `docs/SERVICES_SUPPORT.md`.
>
> **Architecture authority:** [`docs/CAUSAL_PIPELINE.md`](./CAUSAL_PIPELINE.md). The funnel mapping below (Stage 0 → Stage 8) is the single source of truth for what each service does and what its inputs/outputs are. Core principle: **code finds the root cause, AI explains it** — AI only ever receives an `EvidencePack` (≤ 3 KB), never raw telemetry.

Core scope = the modules that directly serve the product goal (ingestion → normalization → fingerprint → index → topology → health → causal RCA → AI router → incident lifecycle → command center).

---

## Update Rules

When you add or change a core class/endpoint:

1. Add/edit the row in the relevant table below.
2. If it exposes a new API surface, also update `docs/API_CONTRACTS.md`.
3. If it touches schema, also update `docs/DATABASE_SCHEMA.md`.
4. Append a Task Log entry in the active `docs/PROGRESS-*.md`.

---

## Module → Service Map

### 1. ingestion (`org.kfh.aiops.ingestion`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `IngestionService` (port) | Validates incoming batch and forwards to durable stream. | 🟢 Port defined |
| Ingestion REST controller | `POST /api/v1/ingest/events`. | ⚪ Not implemented |
| Durable stream/queue publisher | Adapter to queue (Kafka/RabbitMQ/SQS). | ⚪ Not implemented |

**Endpoints:** `POST /api/v1/ingest/events` (planned)

### 2. plugin (`org.kfh.aiops.plugin`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `AiOpsConnectorPlugin` (interface) | Contract for every external connector. | 🟢 Implemented |
| `PluginMetadata`, `PluginContext`, `PluginTestResult`, `PluginHealth` | Plugin SPI value objects. | 🟢 Implemented |
| `PluginRegistry` | Discovers & registers plugin beans by `pluginType()`. | 🟢 Implemented |
| `PluginScheduler` | Runs plugin `collect()` per schedule with retry/CB/timeout. | ⚪ Not implemented |
| `SCOMConnectorPlugin` … `OpenTelemetryConnectorPlugin` | 13 source connectors. | ⚪ Not implemented |
| `ConnectorController`, `ConnectorService` | Frontend-aligned connector CRUD/toggle/test/log endpoints; strips plaintext secrets and persists secret-safe live-test failure messages, including heartbeat Java exception details. | 🟡 Phase 1 scaffold |
| `ConnectorCatalogService`, `ConnectorTypeMetadataDto`, `ConnectorFieldSchemaDto` | Serves Add Connector type metadata for BMC, AppDynamics, vROps, and SCOM, including secure-default `verifySsl` connector TLS certificate verification fields. | 🟢 Implemented |
| `ConnectorEndpointGuard` | Shared connector SSRF guard that supports public/private KFH hybrid BMC/AppDynamics/vROps endpoints while blocking metadata, localhost, loopback, link-local, and multicast targets. | 🟢 Implemented |
| `ConnectorTlsSupport`, `ConnectorTlsWebClientFactory` | Shared connector TLS helpers and WebClient factory that keep JVM certificate verification enabled by default and provide an explicit per-connector relaxed certificate-validation client for governed dev/hybrid tests. | 🟢 Implemented |
| `BmcConnectorConfigValidator` | Validates BMC Helix country/environment scope, HTTPS base URL, safe relative endpoints, bounded collection settings, and required access-key secrets while returning only secret-safe metadata. | 🟢 Implemented for BMC configuration |
| `HttpAppDynamicsConnectorLiveTester` | Performs AppDynamics controller application-discovery readiness checks, honors `verifySsl`, and returns compact redacted HTTP/Java/PKIX failure details for failed tests. | 🟢 Implemented for AppDynamics live testing |
| `ScomConnectorConfigValidator`, `PowerShellScomConnectorLiveTester` | Validate SCOM WinRM/PowerShell connector profiles and run a bounded secret-safe OperationsManager readiness probe; domain-qualified credentials are precomputed in Java and passed as `KFH_AIOPS_SCOM_QUALIFIED_USERNAME` so PowerShell never concatenates `$domain` and `$username`. | 🟢 Implemented for SCOM live testing |

**Endpoints:** `GET/POST/PUT/DELETE /api/v1/connectors`, `GET /api/v1/connectors/types`, `PATCH /api/v1/connectors/{id}/toggle`, `POST /api/v1/connectors/{id}/test`, `GET /api/v1/connectors/{id}/logs`.

### 3. normalization (`org.kfh.aiops.normalization`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `CanonicalTelemetryEvent` | Canonical model (see copilot-instructions §8). | 🟢 Implemented as immutable record |
| `Severity` | Enum: CRITICAL/HIGH/MEDIUM/LOW/INFO. | 🟢 Implemented |
| `NormalizationService` | Source-specific DTO → canonical event. | ⚪ Not implemented |
| `EnrichmentService` | Adds app/service/country/journey/owner/criticality context. | ⚪ Not implemented |
| `FingerprintService` | Deterministic fingerprint for dedup & grouping. | ⚪ Not implemented |

### 4. index (`org.kfh.aiops.index`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `TelemetryDocument` / `TelemetryKind` / `IndexQuery` / `IndexSearchResult` | Typed document/query/result model (`index.model`). | 🟢 Implemented (Phase 2 inc. 1) |
| `ShardKey` | Shard path `{country}/{env}/{kind}/{date}/shard-NN` + hash routing. | 🟢 Implemented |
| `SegmentStore` | Append-only JSONL segment I/O per shard. | 🟢 Implemented |
| `IndexWriterService` | Batched, shard-routed writes (funnel Stage 3). | 🟢 Implemented |
| `IndexSearchService` | Time-partition prune → country/env → parallel filtered scan. | 🟢 Implemented |
| `IndexProperties` | `kfh.index.*` tunables (shards-per-day, batch, parallelism, retention). | 🟢 Implemented |
| In-shard inverted index / postings | O(1) high-cardinality term lookup. | ⚪ Increment 2 |
| `RetentionService` / `ArchiveService` | Per-country/env retention + cold archive to object storage. | ⚪ Increment 2 |

**Endpoints:** `POST /api/v1/logs/search` — **implemented** (tenant/country scoped, RBAC `ALERT_READ`).

### 5. topology (`org.kfh.aiops.topology`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `TopologyService` | Upsert nodes/edges in Neo4j. | ⚪ Not implemented |
| `BlastRadiusService` | Compute impacted journeys/services from a resource. | ⚪ Not implemented |
| `CausalPathService` | Traverse upstream/downstream paths with timing. | ⚪ Not implemented |

**Endpoints:** `GET /api/v1/topology`, `GET /api/v1/topology/blast-radius` (planned).

### 6. health (`org.kfh.aiops.health`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `HealthStateService` | Read/write hot state in Redis. | ⚪ Not implemented |
| `BaselineService` | Maintain baselines per metric. | ⚪ Not implemented |
| `HealthScorer` | Compute composite health score. | ⚪ Not implemented |

**Endpoints:** `GET /api/v1/health` (planned).

### 7. rca (`org.kfh.aiops.rca`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `RcaResult`, `EvidenceItem` | Output model persisted to `incident.rca_results`. | 🟢 Implemented as immutable records |
| `EvidenceBuilder` | Build compact evidence pack from index + topology + health. | ⚪ Not implemented |
| `CausalRcaService` | Score root cause candidates (11 factors, §14). | ⚪ Not implemented |
| `RcaPromptFactory` | Build LLM-safe prompts (no secrets/PII). | ⚪ Not implemented |
| `RcaService` | Orchestrate evidence → scoring → AI → result. | ⚪ Not implemented |

**Endpoints:** `POST /api/v1/rca/run`, `GET /api/v1/rca/{incidentId}` (planned).

### 8. ai (`org.kfh.aiops.ai`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `AiRouter` | Choose DeepSeek vs Azure OpenAI per §15 rules. | ⚪ Not implemented |
| `DeepSeekClient` | Local R1 reasoning. | ⚪ Not implemented |
| `AzureOpenAiClient` | Critical incident RCA narrative. | ⚪ Not implemented |
| `AiCostTracker` | Token & $ accounting. | ⚪ Not implemented |

### 9. incident (`org.kfh.aiops.incident`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `IncidentStatus` | Enum: NEW/OPEN/ACKNOWLEDGED/MITIGATED/MONITORING/CLOSED/REOPENED/SUPPRESSED. | 🟢 Implemented |
| `IncidentController`, `IncidentService` | Frontend-aligned incident CRUD/status/evidence/related/timeline endpoints; tenant + country guarded. | 🟡 Phase 1 scaffold (in-memory) |
| `IncidentLifecycleService` | Deterministic open/keep-open/monitor/close/reopen (§13). | ⚪ Not implemented |
| `IncidentGroupService` | Causal-path grouping; recurrence detection. | ⚪ Not implemented |
| `IncidentRepository` (JPA) | Persistence to `incident.incidents`. | ⚪ Not implemented |

**Endpoints:** `GET/POST/PUT /api/v1/incidents`, `PATCH /api/v1/incidents/{id}/status`, `GET /api/v1/incidents/{id}/{evidence|related|timeline}`.

### 10. commandcenter (`org.kfh.aiops.commandcenter`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `DashboardController`, `DashboardService` | Country/env overview KPIs, trends, top apps, source summary for the static Command Center UI. | 🟡 Phase 1 scaffold (in-memory) |
| `IncidentDetailService` | Detail view assembly (incident + RCA + evidence + topology). | ⚪ Not implemented |
| `AlertController`, `AlertService` | Alert list/detail/ack/activity endpoints for the Alert Explorer. | 🟡 Phase 1 scaffold (in-memory) |
| `ApplicationController`, `ApplicationService` | Application catalog CRUD plus inventory/incidents/health drilldowns. | 🟡 Phase 1 scaffold (in-memory) |
| `InventoryController`, `InventoryService` | Resource inventory CRUD plus dependencies and alert drilldowns. | 🟡 Phase 1 scaffold (in-memory) |
| `ScheduleController`, `ScheduleService` | Schedule CRUD/toggle/run/runs endpoints for admin UI. | 🟡 Phase 1 scaffold (in-memory) |
| `ReportController`, `ReportService` | Operator/executive report list/detail/run/artifact/generate endpoints. | 🟡 Phase 1 scaffold (in-memory) |
| `CommandCenterReadModel`, `UiQuerySupport`, `PageResponse`, `UiWriteRequest` | Temporary tenant-aware read model and shared DTO helpers for frontend-backed Phase 1 APIs. | 🟡 Phase 1 scaffold |

**Endpoints:** `GET /api/v1/dashboard/{kpis|trends|top-apps|summary|sources}`, `/api/v1/alerts`, `/api/v1/applications`, `/api/v1/inventory`, `/api/v1/schedules`, `/api/v1/reports/*`.

---

## Cross-Module Contracts

| Producer → Consumer | Contract | Channel |
|---|---|---|
| `ingestion` → `normalization` | `CanonicalTelemetryEvent` | queue |
| `normalization` → `index.writer` | `CanonicalTelemetryEvent` | direct call |
| `normalization` → `health.state` | `HealthSignal` | direct call |
| `topology` → `rca.causal` | `TopologyContext` | direct call |
| `rca` → `ai.router` | `EvidencePack` (§15) | direct call |
| `rca` → `incident.lifecycle` | `RcaResult` | direct call |
| `incident.lifecycle` → outbox | `INCIDENT_OPENED` / `INCIDENT_CLOSED` / `INCIDENT_REOPENED` | `ops.outbox_events` |

> Plugins must **not** call AI, must **not** write to `incident.*`, must **not** mutate Neo4j (except topology plugins).

---

## Status Legend
🟢 Implemented · 🟡 In progress · 🔴 Blocked · ⚪ Not started

