# GitHub Copilot Instructions â€” KFH Enterprise Causal AIOps Platform

## 1. Project Identity

This project is an **enterprise-grade AIOps platform** for KFH group entities such as Kuwait, Bahrain, Egypt, and future countries.

The platform goal is to build a high-level **banking-flow-aware AIOps solution** similar in ambition to Dynatrace, but customized for KFH banking operations.

The system **must not** be a traditional alert grouping tool. It must not simply group alerts by similarity. It must understand the full banking flow from user experience â†’ frontend â†’ backend â†’ database â†’ storage â†’ network â†’ external dependencies, and produce **evidence-backed root cause analysis**.

- **Primary product name:** KFH Causal AIOps Platform
- **Alternative internal name:** KFH AIOps Command Center

---

## 2. Core Product Goal

The platform must answer:

- Which banking journey is impacted?
- Which country is impacted?
- Which application/service/resource caused the issue?
- Which alerts are symptoms?
- Which dependency failed first?
- What is the evidence?
- Should the incident stay open, move to monitoring, close, or reopen?

Example RCA the system must produce:

> **Root Cause:** SAN Storage 02 latency caused Oracle DB wait time, which caused Transfer Service timeout, which caused API errors and frontend transfer failures.
> **Impacted Flow:** KFH Kuwait > Mobile Banking > Fund Transfer
> **Symptoms:** API Gateway 5xx, Transfer Service DB timeout, frontend transfer failure
> **Evidence:** Storage latency started at 10:12. DB wait time increased at 10:14. Transfer Service timeout started at 10:15. Frontend errors started at 10:16.
> **Confidence:** 91%

The system must **never claim 100% certainty** unless evidence is complete and deterministic. Always use confidence scoring.

---

## 3. Non-Negotiable Architecture Principles

Copilot **must** follow these rules when generating code:

- Do **not** build traditional alert similarity grouping as the main logic.
- Build a **custom log/alert/trace indexing layer**.
- Do **not** store huge raw logs directly in PostgreSQL.
- Do **not** store raw logs in Neo4j.
- Do **not** send raw log floods directly to Azure OpenAI or DeepSeek.
- **PostgreSQL** is the system of record for workflow, incidents, config, users, audit, and RCA summaries.
- **Neo4j** is the relationship graph for topology, banking flow, dependency mapping, blast radius, and causal path analysis.
- **Redis** is only for hot cache, health state, deduplication windows, rate limiting, locks, dashboard cache, and short-lived AI/evidence cache.
- The **custom index engine** stores searchable logs, alerts, traces, metrics, and change events.
- **Object storage** stores compressed raw telemetry archives and evidence snapshots.
- AI models **explain** evidence; they must not blindly decide incident lifecycle.
- Incident open/close/reopen logic must be **deterministic** and evidence-based.
- All APIs must be **multi-tenant** and **country-aware**.
- All generated code must be production-style, secure, testable, and modular.

---

## 4. Target High-Level Architecture

```
Collectors / Agents / API Connectors
        â†“
Country Collector Gateway
        â†“
Ingestion Gateway
        â†“
Durable Stream / Queue
        â†“
Normalizer + Enrichment Engine
        â†“
Custom Log Index Engine
        â†“
Health State Engine
        â†“
Neo4j Banking Flow Graph
        â†“
Causal RCA Engine
        â†“
AI Router: DeepSeek Local + Azure OpenAI
        â†“
Incident Lifecycle Engine
        â†“
PostgreSQL System of Record
        â†“
KFH AIOps Command Center UI
```

---

## 5. Technology Stack

### Backend
- Java, Spring Boot, Spring Security, Spring Data JPA, Spring Data Neo4j
- Spring WebFlux/WebClient (async external API calls)
- Spring Scheduling / Quartz
- Resilience4j (retry/circuit breaker/bulkhead)

### Databases and Storage
- **PostgreSQL:** incidents, users, roles, permissions, connectors, audit logs, RCA summaries, workflow states, application configuration
- **Neo4j:** country graph, banking journey graph, application dependency graph, service/resource topology, causal paths, blast radius, incident topology mirror
- **Redis:** hot health state, dashboard cache, short deduplication keys, rate limiting, distributed locks, short-lived RCA evidence cache
- **Custom Index Engine:** logs, alerts, traces, metric events, change events, searchable telemetry
- **Object Storage:** raw compressed logs, raw alerts, large evidence files, archived telemetry

### AI
- **DeepSeek R1 local:** low-cost reasoning, summarization, known issue explanation, draft RCA, bulk log explanation
- **Azure OpenAI:** critical incident RCA, final executive RCA, high-risk recommendation, confidence validation, cross-checking DeepSeek results

### Frontend
- React, HTML, CSS, KFH theme, Recharts, Lucide icons, Command Center UI

---

## 6. Recommended Backend Package Structure

```
org.kfh.aiops
â”œâ”€â”€ bootstrap
â”‚   â””â”€â”€ AiOpsApplication.java
â”œâ”€â”€ platform
â”‚   â”œâ”€â”€ security
â”‚   â”œâ”€â”€ tenant
â”‚   â”œâ”€â”€ country
â”‚   â”œâ”€â”€ audit
â”‚   â”œâ”€â”€ config
â”‚   â”œâ”€â”€ exception
â”‚   â”œâ”€â”€ validation
â”‚   â””â”€â”€ observability
â”œâ”€â”€ ingestion
â”‚   â”œâ”€â”€ api
â”‚   â”œâ”€â”€ service
â”‚   â”œâ”€â”€ queue
â”‚   â”œâ”€â”€ dto
â”‚   â””â”€â”€ model
â”œâ”€â”€ plugin
â”‚   â”œâ”€â”€ api
â”‚   â”œâ”€â”€ registry
â”‚   â”œâ”€â”€ scheduler
â”‚   â””â”€â”€ implementations
â”‚       â”œâ”€â”€ scom
â”‚       â”œâ”€â”€ solarwinds
â”‚       â”œâ”€â”€ bmc
â”‚       â”œâ”€â”€ appdynamics
â”‚       â”œâ”€â”€ database
â”‚       â”œâ”€â”€ network
â”‚       â”œâ”€â”€ storage
â”‚       â”œâ”€â”€ vmware
â”‚       â””â”€â”€ opentelemetry
â”œâ”€â”€ normalization
â”‚   â”œâ”€â”€ model
â”‚   â”œâ”€â”€ mapper
â”‚   â”œâ”€â”€ enrichment
â”‚   â””â”€â”€ fingerprint
â”œâ”€â”€ index
â”‚   â”œâ”€â”€ api
â”‚   â”œâ”€â”€ writer
â”‚   â”œâ”€â”€ reader
â”‚   â”œâ”€â”€ shard
â”‚   â”œâ”€â”€ analyzer
â”‚   â”œâ”€â”€ query
â”‚   â”œâ”€â”€ retention
â”‚   â”œâ”€â”€ archive
â”‚   â””â”€â”€ model
â”œâ”€â”€ topology
â”‚   â”œâ”€â”€ neo4j
â”‚   â”œâ”€â”€ model
â”‚   â”œâ”€â”€ repository
â”‚   â”œâ”€â”€ service
â”‚   â””â”€â”€ cypher
â”œâ”€â”€ health
â”‚   â”œâ”€â”€ state
â”‚   â”œâ”€â”€ redis
â”‚   â”œâ”€â”€ scoring
â”‚   â”œâ”€â”€ baseline
â”‚   â””â”€â”€ model
â”œâ”€â”€ rca
â”‚   â”œâ”€â”€ evidence
â”‚   â”œâ”€â”€ causal
â”‚   â”œâ”€â”€ scoring
â”‚   â”œâ”€â”€ prompt
â”‚   â”œâ”€â”€ result
â”‚   â””â”€â”€ service
â”œâ”€â”€ ai
â”‚   â”œâ”€â”€ router
â”‚   â”œâ”€â”€ deepseek
â”‚   â”œâ”€â”€ azureopenai
â”‚   â”œâ”€â”€ prompt
â”‚   â”œâ”€â”€ cost
â”‚   â””â”€â”€ model
â”œâ”€â”€ incident
â”‚   â”œâ”€â”€ api
â”‚   â”œâ”€â”€ lifecycle
â”‚   â”œâ”€â”€ repository
â”‚   â”œâ”€â”€ workflow
â”‚   â”œâ”€â”€ model
â”‚   â””â”€â”€ dto
â”œâ”€â”€ notification
â”‚   â”œâ”€â”€ email
â”‚   â”œâ”€â”€ teams
â”‚   â”œâ”€â”€ sms
â”‚   â””â”€â”€ webhook
â””â”€â”€ commandcenter
    â”œâ”€â”€ dashboard
    â”œâ”€â”€ incidents
    â”œâ”€â”€ alerts
    â”œâ”€â”€ topology
    â””â”€â”€ reports
```

Avoid dumping all classes into generic `service`, `controller`, or `model` packages.

---

## 7. Main Data Flow

Copilot must preserve this flow when generating services:

1. Source system creates alert/log/metric/trace/change event.
2. Collector or plugin receives data.
3. Ingestion Gateway validates tenant, country, source, timestamp, schema, and auth.
4. Raw data is archived to object storage.
5. Event is pushed to durable stream/queue.
6. Normalizer converts source-specific data to `CanonicalTelemetryEvent`.
7. Enrichment engine adds app, service, country, business journey, owner, criticality, and topology context.
8. Custom Index Engine writes searchable telemetry into time-based shards.
9. Health State Engine updates Redis hot state.
10. Neo4j Topology Engine updates or reads dependency graph.
11. Business Impact Engine detects impacted banking journey.
12. RCA Evidence Builder queries the custom index for relevant logs, alerts, traces, metrics, and changes.
13. Causal RCA Engine walks topology graph and scores root cause candidates.
14. AI Router sends compact evidence pack to DeepSeek or Azure OpenAI.
15. AI returns human-readable RCA explanation.
16. Incident Lifecycle Engine creates, updates, monitors, closes, or reopens incident.
17. PostgreSQL stores incident, RCA, evidence summary, audit, and workflow status.
18. Command Center UI displays incident, flow map, topology, root cause, evidence, and recommended actions.

---

## 8. Canonical Telemetry Model

All source data must be normalized to a common model:

```java
public class CanonicalTelemetryEvent {
    private UUID eventId;
    private String eventType; // ALERT, LOG, METRIC, TRACE, CHANGE
    private UUID tenantId;
    private String countryCode; // KW, BH, EG
    private String environment; // PROD, UAT, DEV
    private Instant timestamp;

    private String sourceSystem;
    private String collectorId;
    private String connectorId;

    private String businessDomain;
    private String businessJourney;
    private String applicationId;
    private String applicationName;
    private String serviceId;
    private String serviceName;

    private String resourceId;
    private String resourceName;
    private String resourceType; // SERVER, DB, STORAGE, NETWORK, API, APP
    private String resourceRole; // WEB_SERVER, APP_SERVER, DB_SERVER, STORAGE_ARRAY

    private String severity; // CRITICAL, HIGH, MEDIUM, LOW, INFO
    private String status;   // OPEN, CLOSED, RECOVERED, UNKNOWN

    private String message;
    private String normalizedMessage;
    private String errorCode;
    private String exceptionType;

    private String traceId;
    private String spanId;
    private String correlationId;
    private String transactionId;

    private Map<String, Object> metrics;
    private Map<String, String> attributes;

    private String rawRef;
    private String schemaVersion;
}
```

Do not create source-specific RCA logic directly. Convert source-specific data to this model first.

---

## 9. Plugin Architecture

Every external system integration must be a **plugin**.

### Plugin Interface

```java
public interface AiOpsConnectorPlugin {
    String pluginType();
    PluginMetadata metadata();
    PluginTestResult testConnection(PluginContext context);
    List<CanonicalTelemetryEvent> collect(PluginContext context, CollectionWindow window);
    PluginHealth health();
}
```

### Required Plugins
`SCOMConnectorPlugin`, `SolarWindsConnectorPlugin`, `BmcHelixConnectorPlugin`, `AppDynamicsConnectorPlugin`, `PrometheusConnectorPlugin`, `OracleDbConnectorPlugin`, `SqlServerConnectorPlugin`, `PostgreSqlConnectorPlugin`, `F5ConnectorPlugin`, `VmwareConnectorPlugin`, `StorageConnectorPlugin`, `NetworkConnectorPlugin`, `OpenTelemetryConnectorPlugin`.

### Plugin Rules
- Plugins must **not** write directly to incidents.
- Plugins must **not** call AI directly.
- Plugins must **not** update Neo4j directly unless they are topology-specific plugins.
- Plugins must return normalized telemetry or raw source DTOs for normalization.
- Plugin secrets must be encrypted.
- Plugin execution must be audited.
- Plugin failures must be recorded as connector run logs.
- Plugin collection must support pagination, retries, timeout, and circuit breaker.
- Plugin output must include country, tenant, environment, source system, and collection window.

---

## 10. Custom Log Index Engine

The project must **not** depend on Elasticsearch or OpenSearch.

Build a custom index layer with the following concepts: Index, Shard, Segment, Document, Field, Analyzer, Tokenizer, Inverted index, Postings list, Time partition, Replica, Retention policy, Raw reference.

### Capabilities
Exact match filters; full-text search; time-range search; severity/country/application/service/resource filtering; trace ID lookup; correlation ID lookup; aggregation counts; top N error messages; pagination; retention; archiving.

### Index Naming
```
logs-kw-prod-2026-06-07
alerts-kw-prod-2026-06-07
traces-kw-prod-2026-06-07
metrics-kw-prod-2026-06-07
changes-kw-prod-2026-06-07
```

### Shard Path
```
/data/aiops-index/
  kw/prod/logs/2026-06-07/
    shard-00/  shard-01/  shard-02/  shard-03/
```

### Indexed Field Types
- **keyword:** tenant_id, country_code, environment, source_system, application_id, service_id, resource_id, resource_type, severity, trace_id, correlation_id, transaction_id
- **text:** message, normalized_message, exception_stack
- **numeric:** duration_ms, cpu_usage, memory_usage, db_latency_ms, network_latency_ms, error_count
- **date:** timestamp
- **stored:** raw_ref, summary

### Index Rules
- Do **not** scan PostgreSQL to search raw logs.
- Store raw log pointer in index, not always the full raw log.
- Store raw compressed payload in object storage.
- Use time partition pruning before searching.
- Use country and environment filters before text search.
- Use shard parallelism for high-volume search.
- Use retention rules per country and environment.
- **Never** send full index result sets to AI; always build a compact evidence pack.

---

## 11. Neo4j Topology and Causal Graph

Neo4j models **relationships**, not raw telemetry.

### Core Nodes
`Country`, `BusinessDomain`, `BusinessJourney`, `Application`, `Service`, `ApiEndpoint`, `Server`, `Database`, `Storage`, `NetworkDevice`, `NetworkLink`, `ExternalSystem`, `Team`, `IncidentRef`, `AlertGroup`, `EvidenceRef`.

### Core Relationships
```
(:Country)-[:HAS_DOMAIN]->(:BusinessDomain)
(:BusinessDomain)-[:HAS_JOURNEY]->(:BusinessJourney)
(:BusinessJourney)-[:USES_APP]->(:Application)
(:Application)-[:HAS_SERVICE]->(:Service)
(:Service)-[:EXPOSES_API]->(:ApiEndpoint)
(:Service)-[:RUNS_ON]->(:Server)
(:Service)-[:DEPENDS_ON]->(:Service)
(:Service)-[:DEPENDS_ON]->(:Database)
(:Database)-[:STORES_ON]->(:Storage)
(:Server)-[:CONNECTED_BY]->(:NetworkLink)
(:NetworkLink)-[:CONNECTS]->(:NetworkDevice)
(:Service)-[:CALLS_EXTERNAL]->(:ExternalSystem)
(:Team)-[:OWNS]->(:Application)
(:IncidentRef)-[:IMPACTS_JOURNEY]->(:BusinessJourney)
(:IncidentRef)-[:ROOT_CAUSE]->(:Resource)
(:IncidentRef)-[:HAS_SYMPTOM]->(:Resource)
(:IncidentRef)-[:HAS_EVIDENCE]->(:EvidenceRef)
```

### Neo4j Rules
- Neo4j is used for graph traversal, topology, blast radius, and causal path.
- PostgreSQL remains the incident system of record.
- `IncidentRef` in Neo4j is a graph mirror, not authoritative workflow storage.
- Do not store huge logs in Neo4j.
- Do not use Neo4j for full-text log search.
- Use Neo4j to select which resources, services, and dependencies the index engine should search.
- Causal RCA must use topology direction, timing, severity, and evidence.

---

## 12. Redis Usage

Redis is required for **hot state and caching only**.

### Use Redis for
dashboard cache; current health state; short alert deduplication; distributed locks; rate limiting; short-lived RCA evidence cache; AI summary cache; WebSocket live state.

### Do NOT use Redis as
system of record; main event bus; raw log store; incident database; topology graph.

### Redis Key Examples
```
health:KW:PROD:server:KW-APP-014
health:KW:PROD:app:MobileBanking
health:KW:PROD:service:TransferService

dedup:KW:SCOM:KW-APP-014:CPU_HIGH
lock:connector:solarwinds:KW
rate-limit:tenant:KW:/api/v1/incidents
dashboard:KW:PROD:overview
rca:evidence:INC-20260607-001
ai:summary:known-issue:hash
```

### TTL Rules
- Dashboard cache: 15â€“60 seconds
- Health state: 5â€“15 minutes
- Dedup keys: 2â€“10 minutes
- RCA evidence cache: 10â€“30 minutes
- AI summary cache: 1â€“6 hours
- Distributed locks: 30 secondsâ€“5 minutes

---

## 13. Incident Lifecycle

Incident lifecycle must be **deterministic**.

**Allowed states:** `NEW`, `OPEN`, `ACKNOWLEDGED`, `MITIGATED`, `MONITORING`, `CLOSED`, `REOPENED`, `SUPPRESSED`.

### Open Incident When
- business journey is impacted
- critical dependency is degraded
- high/critical alert exists on critical service
- transaction error rate crosses threshold
- same known causal path appears again
- customer experience is impacted

### Keep Incident Open When
- current severity is HIGH or CRITICAL
- business transaction success rate is still below baseline
- root cause resource remains unhealthy
- new alerts continue in same causal path
- same trace/correlation errors continue
- operator locked incident open

### Move to Monitoring When
- no new high/critical alerts for configured quiet period
- business success rate recovered
- latency returned near baseline
- root cause resource health improved
- symptom alerts reduced

### Close Incident When
- business flow is healthy
- root cause entity is healthy
- no new high/critical alert in quiet window
- no new relevant errors in custom index
- operator did not block auto-close
- minimum monitoring period passed

### Reopen Incident When
- same causal path returns
- same business journey is impacted
- same root cause resource or dependency fails again
- incident is within reopen window

> **Important:** AI can *recommend* closure, but only the **Incident Lifecycle Engine** can close, reopen, or suppress an incident.

---

## 14. Causal RCA Engine

The RCA engine must **not** rely only on similarity. It must combine: Business impact, Topology graph, Dependency direction, Alert timing, Trace timing, Metric degradation, Log evidence, Change events, Known incidents, Runbooks, Health state.

### RCA Candidate Scoring
1. First bad timestamp
2. Upstream/downstream topology position
3. Severity
4. Blast radius
5. Error count
6. Metric deviation from baseline
7. Log evidence match
8. Trace span failure
9. Recent change/deployment correlation
10. Historical known issue match
11. Recovery correlation

### Root Cause Candidate Must
- start before symptoms
- be upstream or shared dependency
- have direct telemetry evidence
- explain majority of downstream symptoms
- match impacted business flow window
- not be caused by another upstream component

### RCA Output Model
```java
public class RcaResult {
    private UUID incidentId;
    private String countryCode;
    private String environment;
    private String impactedJourney;
    private String rootCauseEntityId;
    private String rootCauseEntityType;
    private String rootCauseSummary;
    private List<String> symptomEntityIds;
    private List<EvidenceItem> evidence;
    private BigDecimal confidence;
    private String recommendedAction;
    private String aiNarrative;
    private String modelUsed;
}
```

---

## 15. AI Router Rules

Do **not** call AI with raw telemetry floods. Always create an **evidence pack** first.

### Evidence Pack
```json
{
  "country": "KW",
  "environment": "PROD",
  "businessJourney": "Mobile Banking > Fund Transfer",
  "impact": "Transaction success rate dropped from 99.2% to 81.4%",
  "firstBadTime": "2026-06-07T10:12:00Z",
  "candidateRootCauses": ["SAN-STORAGE-02", "DB-CORE-ORACLE-01", "SVC-TRANSFER"],
  "evidence": [
    "Storage latency started at 10:12",
    "DB wait time increased at 10:14",
    "Transfer Service timeout started at 10:15",
    "Frontend errors started at 10:16"
  ],
  "excludedCauses": [
    "No deployment on Transfer Service",
    "API Gateway healthy before DB timeout"
  ]
}
```

### Model Routing
- **DeepSeek R1:** low severity incidents, repeated known incidents, bulk summarization, draft RCA, local/private reasoning
- **Azure OpenAI:** critical incidents, new unknown issues, final executive RCA, high-confidence validation, user-facing production RCA

### AI Rules
- AI must not invent evidence.
- AI must not decide incident lifecycle directly.
- AI must cite evidence IDs internally in RCA result.
- AI must return confidence and uncertainty.
- AI must distinguish root cause from symptoms.
- AI must explain why alternative causes were rejected.
- AI must **never** receive secrets, credentials, tokens, or unnecessary PII.

---

## 16. Security and Multi-Tenancy

Every API request must include tenant and user context.

### Required Headers
- `X-Tenant-Id`
- `X-User-Id`
- `X-Correlation-Id`

### Country-aware Roles
KFH Global Admin, Country Admin, Domain Owner, Application Owner, DB Team, Network Team, Storage Team, Security/Audit, NOC Operator, Viewer.

### Access Control Rules
- Kuwait users must not see Bahrain/Egypt data unless explicitly permitted.
- Application owners see only assigned applications.
- DB team sees DB-related resources and incidents.
- Network team sees network devices, links, and related incidents.
- Security/audit sees audit trails based on role.
- All write actions must be audited.
- All connector secret actions must be audited.
- Never return plaintext secrets in API responses.
- Use encrypted storage for connector secrets.
- Validate external URLs against SSRF attacks.
- Use correlation IDs in logs and audit entries.

### Dev Server Configuration Exception
- `src/main/resources/application.properties` may contain intentional **dev-server-only** password defaults for the currently provisioned development server.
- AI agents must **not automatically remove or blank those existing dev-server password defaults** during unrelated tasks. Preserve them unless the user explicitly asks to rotate, externalize, or remove them.
- This exception does **not** allow logging passwords, returning them from APIs, copying them into documentation, test output, screenshots, prompts, progress entries, or AI evidence packs.
- Production, UAT, connector secrets, TLS keystore material, and any new credential-bearing configuration must still use environment variables, encrypted secret storage, or deployment secrets.

---

## 17. PostgreSQL Ownership

PostgreSQL stores authoritative workflow and business data.

```
identity.users
identity.roles
identity.permissions
identity.audit_log

config.connectors
config.connector_secrets
config.schedules

cmdb.applications
cmdb.services
cmdb.resources
cmdb.business_journeys
cmdb.ownership

incident.incidents
incident.incident_status_history
incident.incident_evidence
incident.incident_groups
incident.rca_results

ops.connector_runs
ops.connector_run_logs
ops.jobs
```

PostgreSQL must **not** store every raw log row.

---

## 18. API Design Rules

Use REST APIs with clear versioning:

```
/api/v1/incidents
/api/v1/alerts
/api/v1/logs/search
/api/v1/topology
/api/v1/rca
/api/v1/connectors
/api/v1/applications
/api/v1/business-journeys
/api/v1/health
/api/v1/audit
```

- All endpoints must validate tenant and user context.
- Use DTOs; do not expose JPA entities directly.
- Use pagination for lists.
- Use filtering by country, environment, severity, app, service, and time.
- Return `problem+json` style errors.
- Include correlation ID in responses.
- Audit write operations.
- Validate all user input.
- Avoid returning secrets.
- Avoid returning huge raw logs directly; return page results and raw references.

---

## 19. Frontend Rules

The UI is the **KFH AIOps Command Center**.

### Main Pages
Dashboard, Incidents, Alerts, Inventory, Applications, Business Journeys, Topology, RCA, Reports, Connectors, Schedules, Users & RBAC, Settings, Audit Logs.

### UI Must Show
business impact, country, environment, journey, application, root cause, confidence, evidence, topology path, timeline, symptoms, recommended action, incident lifecycle state, owner/team.

> Do **not** show only alert lists. Always connect alerts to business impact and topology when possible.

---

## 20. Logging and Observability

### All backend services must log
`tenantId`, `countryCode`, `userId` (when available), `correlationId`, `requestId`, `incidentId` (when available), `connectorId` (when available), `durationMs`, `status`, `errorCode`.

### Do NOT log
passwords, API keys, tokens, PII, secret connector config, full raw payload when sensitive.

All important actions must be auditable.

---

## 21. Error Handling

Use structured errors:

```json
{
  "code": "MISSING_OR_INVALID_CONTEXT",
  "message": "Required headers X-Tenant-Id and X-User-Id must be valid UUID values",
  "timestamp": "2026-06-07T10:00:00Z",
  "correlationId": "abc-123"
}
```

Do **not** return stack traces to clients.

---

## 22. Code Style Rules

When generating Java code:

- Use constructor injection.
- Avoid field injection.
- Use records for simple immutable DTOs.
- Use services for business logic.
- Keep controllers thin.
- Use repositories only for persistence.
- Use clear package boundaries.
- Use meaningful class names.
- Avoid God classes.
- Avoid static mutable state.
- Add validation annotations.
- Use transactions where needed.
- Use optimistic locking where concurrent updates are possible.
- Add unit tests for business logic.
- Add integration tests for repository and API layers where relevant.

---

## 23. Testing Requirements

Copilot should generate tests for: normalization, plugin collection, index writing, index searching, RCA candidate scoring, incident open/close/reopen rules, RBAC authorization, tenant isolation, connector secret encryption, SSRF validation, AI router selection, Redis cache TTL behavior, Neo4j topology traversal.

### Test name examples
- `shouldCreateIncidentWhenBusinessJourneyImpacted`
- `shouldNotCloseIncidentWhenRootCauseStillUnhealthy`
- `shouldReopenIncidentWhenSameCausalPathReturns`
- `shouldRejectApiRequestWithoutTenantHeader`
- `shouldRouteCriticalRcaToAzureOpenAI`
- `shouldUseDeepSeekForKnownLowSeverityIncident`

---

## 24. What Copilot Must Avoid

Do **not** generate code that:

- uses Elasticsearch or OpenSearch dependencies
- stores huge logs in PostgreSQL
- stores raw logs in Neo4j
- uses AI to blindly decide open/close
- groups alerts only by text similarity
- ignores country/tenant isolation
- returns connector secrets
- logs passwords or API keys
- bypasses audit logging
- creates one giant service class
- creates source-specific RCA logic inside plugins
- hardcodes Kuwait only
- hardcodes production only
- does not support pagination
- does not support correlation ID

---

## 25. Definition of Done

A feature is complete only when:

1. It supports tenant and country context.
2. It has clear DTOs and validation.
3. It follows package/module boundaries.
4. It writes audit logs for write actions.
5. It does not expose secrets.
6. It has tests for core logic.
7. It supports correlation ID.
8. It does not break incident lifecycle rules.
9. It does not bypass the custom index engine for raw telemetry search.
10. It supports future extraction into microservices.

---

## 26. Development Strategy

Phased approach:

- **Phase 1:** Spring Boot modular monolith with strong packages.
- **Phase 2:** Add custom index engine as separate module or worker.
- **Phase 3:** Add Neo4j banking flow graph and topology visualization.
- **Phase 4:** Add RCA evidence builder and causal scoring.
- **Phase 5:** Add DeepSeek + Azure OpenAI AI Router.
- **Phase 6:** Add Redis hot health state and dashboard cache.
- **Phase 7:** Extract heavy ingestion/index/RCA workers into independent services if required.

> Do **not** start with too many microservices too early. Build modular first, then extract when the module becomes heavy or independently scalable.

---

## 27. Final Architecture Rule

Whenever generating new code, Copilot must remember:

> **This platform is not an alert grouping system.**
> **This platform is a banking-flow-aware causal AIOps platform.**

- **PostgreSQL** = system of record.
- **Neo4j** = relationship and causal topology.
- **Redis** = hot cache and current state.
- **Custom Index Engine** = fast telemetry search.
- **Object Storage** = raw archive.
- **DeepSeek + Azure OpenAI** = evidence explanation and RCA narrative.
- **Spring Boot** = enterprise backend platform.
- **React Command Center** = operator experience.

### The Goal Is Always
1. Understand the banking flow.
2. Find the true root cause from evidence.
3. Show why it happened.
4. Show what is impacted.
5. Keep incident open, close, or reopen based on deterministic health evidence.

---

## 28. Progress Tracking Rule (Mandatory)

Every completed task **must** be recorded in `docs/PROGRESS.md`.

### When to update
- After finishing any feature, fix, refactor, migration, doc change, test addition, or infra change.
- Before considering a task "done" â€” updating `PROGRESS.md` is part of the Definition of Done.

### How to update
- **Append** a new entry to the **Task Log** section (newest on top). Never delete or rewrite past entries.
- Use the entry template defined at the top of `docs/PROGRESS.md` (date, phase, module, type, country scope, summary, files, migrations, API changes, tests, docs updated, security checklist, DoD checklist, follow-ups, author, correlation).
- Update the **Status Snapshot** table when a phase or major area changes state (âšª â†’ ðŸŸ¡ â†’ ðŸŸ¢ / ðŸ”´).

### Rules
- Do **not** skip the progress entry, even for small changes.
- Do **not** include secrets, tokens, credentials, raw payloads, or PII in the entry.
- Always reference correlation ID, ticket, PR, or commit SHA when available.
- If the task changed an API: also update `docs/API_CONTRACTS.md` and note it in the entry.
- If the task changed operational behavior: also update `docs/RUNBOOKS.md` and note it in the entry.
- If a DB migration was added: reference the new `V{n}__name.sql` file in the entry.

### File rotation (â‰¤ 3000 lines per progress file)
- A single progress file must **never exceed 3000 lines** â€” this keeps it readable for AI assistants (Copilot, Claude Code, OpenAI) and humans.
- Before appending, check the active file's line count. If appending would push it past 3000 lines, create the next volume:
  - `docs/PROGRESS.md` â†’ `docs/PROGRESS-002.md` â†’ `docs/PROGRESS-003.md` â†’ â€¦
- Only the **highest-numbered** volume is writable; older volumes are read-only history.
- Each new volume must copy the **How to Add an Entry** template + **Status Snapshot** table and link back to the previous volume.
- Update the **Progress File Index** table inside `docs/PROGRESS.md` whenever a new volume is created.
- Never split one task entry across two files â€” rotate first, then write the entry in the new file.

> A task is **not complete** until the active `docs/PROGRESS-*.md` file has a corresponding Task Log entry with the security and Definition of Done checklists filled in.

---

## 29. Backend Instructions (Java 21 / Spring Boot 3)

Applies to: `**/backend/**`, `**/*.java`, `**/*.kt`, `**/src/main/**`, `**/src/test/**`.

### Architecture
- Controllers: thin, validate inputs, map DTOs, call services.
- Services: business logic, tenant scoping, RBAC enforcement, audit, outbox events.
- Repositories: `tenant_id` in every query; never fetch cross-tenant.
- DB: PostgreSQL is system-of-record; Neo4j is hot analytics only.

### Security (OWASP)
- Validate all inputs (Bean Validation), deny-by-default.
- Use Spring Security; enforce permission checks at the service layer.
- Avoid IDOR: never trust client-provided IDs without tenant + scope validation.
- Protect against SSRF for any URL-based connector config.
- Implement rate limiting for write-heavy endpoints where applicable.
- Log safely: no secrets, no tokens, no raw payload dumps.

### Platform rules
- Required headers: `X-Tenant-Id`, `X-User-Id` (reject if missing).
- Audit every write (who/what/when/before-after/correlationId).
- Use `outbox_events` for async processing (AI generation, evidence CSV generation, scheduled runs).
- Degraded mode:
  - If Neo4j unavailable: still produce incidents but mark correlation as **degraded**.
  - If AI unavailable: queue AI tasks and mark **"AI pending"**.

### API conventions
- Consistent pagination/filtering: `page`, `size`, `sort`, `timeRange`.
- Standard error format (`application/problem+json` recommended).
- Idempotency on run triggers where needed.
- Use OpenAPI annotations where possible.

---

## 30. Database Instructions (Flyway / PostgreSQL)

Applies to: `**/db/migration/**`, `**/*.sql`.

- Use Flyway versioned migrations: `V{n}__description.sql`.
- Never modify applied migrations; create a new `V{n+1}` migration instead.
- Always include `tenant_id` where applicable.
- Add indexes for:
  - `tenant_id` + time (alerts/incidents)
  - fingerprint fields
  - foreign keys used in filters
- Use constraints (FK / UNIQUE) to protect integrity.
- Avoid long locks; prefer additive changes.

---

## 31. Frontend Instructions (React + TypeScript + Tailwind)

Applies to: `**/frontend/**`, `**/*.ts`, `**/*.tsx`, `**/*.css`.

### UI principles
- Modern enterprise AIOps look aligned with KFH identity (navy/gold accents).
- Clear separation: pages vs components vs API client.
- Provide filters, drilldowns, and drawers for details (NOC-friendly).
- Emphasize **New vs Recurring** incidents and hourly analysis.

### Data & state
- All requests include tenant + user context (from auth/session layer).
- API client wrapper with:
  - base URL, headers, error handling, typed responses
  - retry only for safe GETs, not for writes

### Components
- Reusable tables, KPI cards, trend charts, health badges.
- Empty states, loading skeletons, error boundaries.
- Accessibility: keyboard nav, readable contrast, aria labels.

### Pages relationship
```
Dashboard â†’ (Incidents, Alert Explorer, Applications, Reports)
Applications â†’ Application Details â†’ (Incidents, Inventory, Evidence)
Inventory â†’ Resource Drilldown â†’ (Incidents, Alerts)
```

---

## 32. Expert Java Architecture & Design Mandate

When generating or reviewing Java code in this repository, Copilot must **act as a principal Java architect**, not a script writer. Every change must be evaluated against the principles below before being proposed.

### 32.1 Engineering Mindset
- Think in **bounded contexts** first, classes second.
- Prefer **explicit, intention-revealing** designs over clever ones.
- Optimize for **readability, change-cost, and testability** â€” not LOC.
- Always justify a new abstraction; do not abstract speculatively (YAGNI).
- Treat every public method as an API contract â€” name it, document it, version it.

### 32.2 SOLID (non-negotiable)
- **S â€” Single Responsibility:** one reason to change per class. Split God services into focused services (`IncidentLifecycleService`, `IncidentQueryService`, `IncidentNotifier`).
- **O â€” Open/Closed:** extend via strategies/plugins (e.g. `AiOpsConnectorPlugin`), not by editing core classes.
- **L â€” Liskov Substitution:** subtypes must honor parent contracts; never throw `UnsupportedOperationException` from an implemented interface.
- **I â€” Interface Segregation:** small role interfaces (`EvidenceReader`, `EvidenceWriter`) instead of one fat interface.
- **D â€” Dependency Inversion:** depend on abstractions defined in the **same module** as the consumer. Frameworks and infrastructure live at the edges.

### 32.3 Domain-Driven Design
- Model **Aggregates** with clear roots (e.g. `Incident` is the aggregate root for `IncidentEvidence`, `IncidentStatusHistory`).
- Aggregates protect invariants â€” never mutate child state from outside the root.
- Use **Value Objects** for compound concepts: `CountryCode`, `Severity`, `IncidentNumber`, `CorrelationId`, `EvidenceRef`. Make them immutable Java `record`s with validation in the canonical constructor.
- Encapsulate ubiquitous language in `org.kfh.aiops.<module>.model`; never leak JPA entities across module boundaries â€” map to domain types or DTOs.
- **Domain events** (`IncidentOpened`, `RootCauseIdentified`, `IncidentClosed`) flow through `ops.outbox_events`, not direct calls.

### 32.4 Clean / Hexagonal Architecture
- Each module follows: `api` (controllers/inbound adapters) â†’ `service`/`usecase` (application layer) â†’ `domain` (pure model + policies) â†’ `infra` (JPA repos, WebClients, Neo4j, Redis).
- Inner layers must **not** import outer layers. Use the Maven module / package-private boundaries to enforce this.
- Define **ports** (interfaces) in the domain or application layer; implement **adapters** in `infra`.
  - Example: `RcaEvidenceRepository` (port, in `rca.evidence`) â† `IndexEvidenceAdapter` (adapter, in `index.reader`).
- Controllers never touch JPA entities directly; they receive DTOs and delegate.

### 32.5 Design Patterns to Reach For
| Need | Pattern | Where it fits here |
|---|---|---|
| Plugin extension | **Strategy + Registry** | `AiOpsConnectorPlugin` + `PluginRegistry` |
| Long lifecycle | **State machine** | `IncidentLifecycleService` (NEW â†’ OPEN â†’ â€¦ â†’ CLOSED/REOPENED) |
| Async side effects | **Outbox + Domain Events** | `OutboxPublisher` â†’ notification/AI workers |
| Build complex objects | **Builder** (Lombok `@Builder` ok on internal entities) | `EvidencePack`, `RcaResult` |
| Pick model per condition | **Chain of Responsibility / Router** | `AiRouter` (DeepSeek vs Azure OpenAI) |
| Decouple producers/consumers | **Mediator / Application Service** | RCA orchestration |
| Snapshot before/after | **Memento** | Audit `before_state`/`after_state` |
| Cross-cutting concerns | **Decorator / AOP** | `@Auditable`, retry, rate limiting |
| Anti-corruption layer | **Adapter** | Source-system DTO â†’ `CanonicalTelemetryEvent` |

### 32.6 Patterns / Anti-patterns to Avoid
- âŒ Anemic domain model (logic lives only in services with naked getters/setters).
- âŒ Service Locator / static singletons holding state.
- âŒ Field injection (`@Autowired` on fields) â€” always constructor injection.
- âŒ `Optional` as a method parameter or field.
- âŒ Checked exceptions in business APIs â€” use unchecked, meaningful subtypes of `RuntimeException`.
- âŒ Returning `null` collections â€” return `List.of()`.
- âŒ Catching `Exception` and swallowing it.
- âŒ Public mutable static fields.
- âŒ Reflection or class-loading tricks in business code.
- âŒ Cyclic dependencies between packages/modules.

### 32.7 Concurrency & Reactive
- Default to **virtual threads** (Java 21 `Executors.newVirtualThreadPerTaskExecutor()`) for blocking IO; reserve **WebFlux/`Mono`/`Flux`** for backpressure-heavy streams (e.g. plugin polling, ingestion fan-in).
- Never hold a DB transaction across an external HTTP call â€” use the **outbox**.
- All external IO has **timeouts**, **retries with backoff**, and **circuit breakers** via Resilience4j.
- Shared mutable state must be `final` + immutable, or guarded by `java.util.concurrent` primitives. Prefer `ConcurrentHashMap`, `AtomicReference`, `LongAdder`.
- Cancellation: respect `Thread.interrupted()` and propagate.

### 32.8 Performance
- Profile before optimizing; never optimize against a hunch.
- Avoid N+1 in JPA â€” use `@EntityGraph` or explicit `JOIN FETCH`.
- Stream large result sets (`Stream<T>` repo methods or pagination) â€” never `findAll()` on event tables.
- Use Redis only for hot, small, expirable values (see Â§12).
- For the Custom Index Engine, parallelize **per shard**, not per document.
- Cache LLM evidence-pack summaries (`ai:summary:known-issue:{hash}`) before re-invoking AI.

### 32.9 Error Handling & Resilience
- Distinguish **business errors** (`IncidentAlreadyClosedException`) from **technical errors** (`PluginUnavailableException`).
- Map each to a stable `code` in `problem+json` (Â§21).
- Degraded modes (per Â§29 platform rules):
  - Neo4j down â†’ still create incident, mark correlation **degraded**.
  - AI down â†’ queue task, mark **AI pending**.
  - Redis down â†’ bypass cache, log warning, never fail user request.
- Idempotency keys on all write endpoints that can be retried (plugin runs, RCA triggers).

### 32.10 Testing Strategy
- **Test pyramid:** many unit, fewer integration, very few end-to-end.
- Unit tests are **pure** (no Spring context) â€” instantiate the SUT with mocks (Mockito).
- Use **fixtures** (`TestFixtures.openIncident()`) â€” never repeat object-construction noise.
- Repository/integration tests use **Testcontainers** (PostgreSQL, Neo4j, Redis) â€” no H2.
- For RCA scoring, lifecycle rules, and routing: write **table-driven tests** with one assertion per scenario.
- Test names: `should{Behavior}When{Condition}` (Â§23).
- **Mutation testing** (PIT) recommended for the lifecycle + RCA scoring modules.
- Contract tests between modules: validate the canonical `EvidencePack`/`RcaResult` shapes don't drift.

### 32.11 Observability by Design
- Every public service method emits a single structured log line with the Â§20 fields, including `durationMs`.
- Counters for: incidents opened/closed/reopened, plugin runs success/failure, AI calls per model, RCA confidence distribution.
- Tracing spans across: ingestion â†’ normalization â†’ index write â†’ RCA â†’ AI â†’ incident lifecycle. Propagate `traceId`.
- Health endpoints (`/actuator/health`) must reflect Neo4j/Redis/index-engine degraded states truthfully.

### 32.12 API & Versioning Discipline
- Public REST endpoints live under `/api/v1` (Â§18). Breaking changes â‡’ `/api/v2` with deprecation window.
- DTOs are **separate** from entities and from outbox event payloads. Three layers, three shapes.
- Pagination is **always** Spring `Page<T>` with `page,size,sort`.
- All write endpoints accept `Idempotency-Key` header where retries are expected.
- All endpoints documented with `@Operation`, `@ApiResponses` (springdoc-openapi).

### 32.13 Security by Default (compounds Â§16)
- Apply checks at the **service layer**, not only filters â€” defense in depth.
- Always pass `TenantContext` to services; never read tenant from a static holder for security decisions.
- Validate every cross-tenant or cross-country lookup with `findByIdAndTenantId(...)`.
- Encrypt secrets with `SecretCipherService`; never log encrypted blobs either.
- All outbound URLs pass `SsrfGuard.check(url)` before any `WebClient` call.

### 32.14 Code Style Specifics
- Java 21 idioms: `record`, pattern matching for `switch`, `var` only when the type is obvious from the right-hand side.
- Method length target: **â‰¤ 30 lines**; class length target: **â‰¤ 250 lines**; cyclomatic complexity per method: **â‰¤ 8**.
- Public APIs documented with Javadoc; private helpers documented when not self-explanatory.
- Use **Lombok sparingly**: `@RequiredArgsConstructor` and `@Builder` are fine; avoid `@Data` on domain objects.
- Static analysis is part of CI: **Spotless**, **Checkstyle**, **SpotBugs**, **Error Prone**, **PMD**, **OWASP Dependency-Check**.
- Run `./mvnw verify` locally before declaring a task done.

### 32.15 Architect's Self-Review Checklist
Before sending code, Copilot must mentally answer **yes** to all of these:
1. Does this respect the module boundaries from Â§6?
2. Are tenant + country enforced at the service layer?
3. Are aggregates protecting their invariants?
4. Is every dependency injected via constructor and depending on an abstraction?
5. Are domain events emitted through the outbox (not in-band HTTP)?
6. Are timeouts, retries, and circuit breakers configured on every external call?
7. Are there no N+1 queries, no `findAll()` on event tables, no full result sets sent to AI?
8. Are tests covering the happy path, the lifecycle rule, and at least one failure mode?
9. Are logs structured, secrets-free, and carrying `correlationId`?
10. Is `docs/PROGRESS-*.md` updated with the OWASP + DoD checklists ticked?

> If any answer is **no**, the code is not ready. Iterate before proposing it.
