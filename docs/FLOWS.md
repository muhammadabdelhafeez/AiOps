# System Flows (Mermaid)

> Evidence-first AI note: AI narratives must be derived from a retrieval/evidence pack. No invented facts.

## A) Hourly Scheduled Run (end-to-end)

```mermaid
flowchart LR
  S[Scheduler] --> CR[Create connector runs]
  CR --> COL[Collect raw alerts]
  COL --> NORM[Normalize + fingerprint]
  NORM --> DB[(PostgreSQL: canonical alerts/incidents)]
  NORM --> OB[(Outbox: events)]

  OB -->|graph.upsert| G[Graph upsert worker]
  G --> N4J[(Neo4j hot graph)]

  OB -->|ai.embed| E[Embeddings worker]
  E --> V[(Vector store / embeddings index)]

  OB -->|retrieval.pack| RP[Retrieval pack builder]
  RP --> DB
  RP --> SP[(SharePoint: raw/evidence/output)]

  OB -->|ai.summary| AI[AI summary worker]
  AI --> DB

  OB -->|evidence.csv| EV[Evidence CSV generator]
  EV --> SP
  EV --> DB

  OB -->|notify.teams| NT[Teams notifier]
  NT --> T[Teams]
```

## B) On-demand Investigation

```mermaid
sequenceDiagram
  autonumber
  actor U as Operator
  participant UI as Web UI
  participant API as Spring Boot API
  participant SVC as Service Layer
  participant DB as PostgreSQL
  participant OB as Outbox
  participant W as Workers
  participant N4J as Neo4j
  participant SP as SharePoint

  U->>UI: Open Incident / Alert Group
  UI->>API: GET /api/incidents/{id}
  API->>SVC: authorize + tenant scope
  SVC->>DB: read incident (tenant_id scoped)
  DB-->>SVC: incident
  SVC-->>API: response
  API-->>UI: incident detail

  U->>UI: Generate evidence / AI summary
  UI->>API: POST /api/incidents/{id}/generate-evidence
  API->>SVC: authorize + audit
  SVC->>DB: write job request + outbox event
  SVC->>OB: enqueue (tenant_id scoped)
  API-->>UI: 202 Accepted (correlationId)

  W->>OB: poll
  W->>DB: load incident + related alerts
  W->>N4J: fetch topology/correlation (degraded if down)
  W->>SP: write evidence artifacts
  W->>DB: update report pack index + status
```

## C) Write path invariants (must hold for every write)

```mermaid
flowchart TD
  REQ[HTTP request] --> H{Has X-Tenant-Id & X-User-Id?}
  H -- No --> DENY[Reject 400/401]
  H -- Yes --> AUTHZ{RBAC permission ok?}
  AUTHZ -- No --> FORBID[Reject 403]
  AUTHZ -- Yes --> AUDIT[Write audit log]
  AUDIT --> WRITE[Perform write (tenant scoped)]
  WRITE --> OUTBOX[Emit outbox event (if async)]
  OUTBOX --> RESP[Return response]
```
