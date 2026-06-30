# Data Model (Logical)

This is a logical view to help readers. The physical schema is defined by Flyway migrations in `src/main/resources/db/migration/`.

> **Architecture authority:** [`docs/CAUSAL_PIPELINE.md`](./CAUSAL_PIPELINE.md). Every entity below must be `(tenant_id, country_code, environment)` scoped. Raw telemetry **never** lives in PostgreSQL or Neo4j — it goes to the Custom Index Engine (searchable) and object storage (`rawRef`). PostgreSQL holds canonical incidents, RCA results, evidence summaries, audit, and outbox.

## Core entities (PostgreSQL)

```mermaid
erDiagram
  TENANT ||--o{ USER : has
  TENANT ||--o{ ROLE : has
  ROLE ||--o{ ROLE_PERMISSION : grants
  USER ||--o{ USER_ROLE : assigned

  TENANT ||--o{ CONNECTOR : owns
  CONNECTOR ||--o{ CONNECTOR_RUN : produces

  TENANT ||--o{ ALERT_OCCURRENCE : owns
  TENANT ||--o{ ALERT_GROUP : owns
  ALERT_GROUP ||--o{ INCIDENT : creates

  TENANT ||--o{ REPORT_PACK : owns
  REPORT_PACK ||--o{ REPORT_ARTIFACT : contains

  TENANT ||--o{ AUDIT_LOG : records
  TENANT ||--o{ OUTBOX_EVENT : queues

  TENANT {
    uuid id PK
    string name
  }
  USER {
    uuid id PK
    uuid tenant_id FK
    string email
  }
  CONNECTOR {
    uuid id PK
    uuid tenant_id FK
    string type
    string name
    boolean enabled
  }
  CONNECTOR_RUN {
    uuid id PK
    uuid tenant_id FK
    uuid connector_id FK
    string run_type
    string status
    datetime started_at
    datetime finished_at
  }
  ALERT_GROUP {
    uuid id PK
    uuid tenant_id FK
    string exact_fingerprint
    string family_fingerprint
    datetime window_start
    datetime window_end
  }
  INCIDENT {
    uuid id PK
    uuid tenant_id FK
    uuid alert_group_id FK
    string status
    boolean recurring
  }
  OUTBOX_EVENT {
    uuid id PK
    uuid tenant_id FK
    string event_type
    string status
    datetime created_at
  }
  AUDIT_LOG {
    uuid id PK
    uuid tenant_id FK
    uuid user_id
    string action
    datetime created_at
  }
```

## Neo4j (hot graph)
- Nodes: `Country`, `BusinessDomain`, `BusinessJourney`, `Application`, `Service`, `ApiEndpoint`, `Server`, `Database`, `Storage`, `NetworkDevice`, `NetworkLink`, `ExternalSystem`, `Team`, `IncidentRef`, `AlertGroup`, `EvidenceRef`.
- Relationships: `HAS_DOMAIN`, `HAS_JOURNEY`, `USES_APP`, `HAS_SERVICE`, `DEPENDS_ON`, `STORES_ON`, `RUNS_ON`, `CONNECTED_BY`, `CALLS_EXTERNAL`, `OWNS`, `IMPACTS_JOURNEY`, `ROOT_CAUSE`, `HAS_SYMPTOM`, `HAS_EVIDENCE`.
- Neo4j is a **graph mirror** for topology + blast radius + causal paths. PostgreSQL remains the system of record for incidents.

```mermaid
flowchart LR
  C[Country] -->|HAS_DOMAIN| D[BusinessDomain]
  D -->|HAS_JOURNEY| J[BusinessJourney]
  J -->|USES_APP| A[Application]
  A -->|HAS_SERVICE| S[Service]
  S -->|DEPENDS_ON| S2[Service]
  S -->|DEPENDS_ON| DB[Database]
  DB -->|STORES_ON| ST[Storage]
  IR[IncidentRef] -->|IMPACTS_JOURNEY| J
  IR -->|ROOT_CAUSE| ST
  IR -->|HAS_SYMPTOM| S
  IR -->|HAS_EVIDENCE| ER[EvidenceRef]
```

## Hot state (Redis — DB 0 only)
Key-prefix isolation per country/environment — never logical DB > 0.

| Key pattern | Purpose | TTL |
|---|---|---|
| `dedup:{country}:{env}:{src}:{ci}:{code}` | Alert dedup (SETNX) | 10 min |
| `health:{country}:{env}:{kind}:{id}` | Hot health state | 5–15 min |
| `dashboard:{country}:{env}:{view}` | Dashboard cache | 30–60 s |
| `lock:{scope}:{id}` | Distributed lock | 30 s – 5 min |
| `rate-limit:tenant:{country}:{path}` | API rate limit | 60 s |
| `ai:summary:known-issue:{packHash}` | AI narrative cache | 1–6 h |
| `rca:evidence:{incidentId}` | EvidencePack short cache | 10–30 min |

## EvidencePack (AI input contract)
Built in-memory; **not** a Postgres entity. Persisted only via `incident.incident_evidence` summary and object storage (`rawRef`). Full shape and rules in [CAUSAL_PIPELINE §6](./CAUSAL_PIPELINE.md).


