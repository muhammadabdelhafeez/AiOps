# System Flows (Mermaid)

> **Master design:** see [CAUSAL_PIPELINE](./CAUSAL_PIPELINE.md).
> Core rule for every diagram below: **Code finds the root cause. AI explains it.** AI never sees raw telemetry; only an `EvidencePack` (≤ 3 KB).

---

## A) Causal funnel — 100,000 alerts every 20 minutes

```mermaid
flowchart LR
  COL[Collectors / Plugins per country] --> ING[Ingestion Gateway]
  ING -->|tenant + country + schema validated| NORM[Normalizer + Enrichment]
  NORM -->|CanonicalTelemetryEvent| FP[Fingerprint sha256]
  FP -->|SETNX dedup:* EX 600| RDD[(Redis dedup)]
  FP -->|deduped batch| IDX[(Custom Index Engine shard per country/env/date)]
  IDX --> HS[Health State Engine]
  HS -->|health keys| RHS[(Redis hot state)]
  IDX --> TOPO[Neo4j Topology + Blast Radius]
  TOPO --> BIZ[Business-Journey Impact Filter]
  BIZ --> CAUS[Causal RCA Scoring 11 factors]
  CAUS --> PACK[EvidencePack Builder ≤ 3 KB]
  PACK --> AIC{Cache hit ai:summary:known-issue:hash?}
  AIC -- yes --> NARR[Reuse cached narrative $0 less than 1s]
  AIC -- no --> ROUTER[AI Router]
  ROUTER -->|low/med or known| DS[DeepSeek R1 local free]
  ROUTER -->|CRITICAL + customer impact + novel + exec| AOA[Azure OpenAI 5.5 paid]
  DS --> LCY[Incident Lifecycle Engine DETERMINISTIC]
  AOA --> LCY
  NARR --> LCY
  LCY -->|persist| PG[(PostgreSQL incident + rca_results)]
  LCY -->|mirror| TOPO
  LCY -->|outbox| NOTIF[Teams / Email / Webhook]

  classDef cost fill:#ffe5e5,stroke:#cc0000
  class AOA cost
```

**Reduction:** 100,000 raw → ~14k fingerprints → ~200 candidates → ~25 incidents → ~10 root-cause candidates → ~5 cache misses → **1–3 Azure calls per cycle**.

---

## B) AI Router decision

```mermaid
flowchart TD
  A[EvidencePack ready] --> C{Redis cache hit?}
  C -- yes --> R1[Return cached narrative $0]
  C -- no --> CG{CostGuard within daily quota?}
  CG -- no --> DS1[Force DeepSeek log cost-cap event]
  CG -- yes --> SEV{Severity LOW or MEDIUM or known pattern?}
  SEV -- yes --> DS2[DeepSeek summarize]
  SEV -- no --> DRAFT[DeepSeek draft RCA]
  DRAFT --> CONF{confidence at least 0.85 and not customer-impacting?}
  CONF -- yes --> R2[Return DeepSeek draft]
  CONF -- no --> ESC{CRITICAL or customer-impact or novel?}
  ESC -- no --> R3[Return DeepSeek draft]
  ESC -- yes --> AOA[Azure OpenAI 5.5 final exec RCA]
  AOA --> CACHE[Write Redis cache TTL 6h]
  CACHE --> R4[Return Azure narrative]
  DS1 --> CACHE
  DS2 --> CACHE
```

Every decision is audited with `model`, `reason`, `tokens`, `cost`, `confidence`, `correlationId` (see [CAUSAL_PIPELINE §5](./CAUSAL_PIPELINE.md)).

---

## C) Write-path invariants (every write)

```mermaid
flowchart TD
  REQ[HTTP request] --> H{X-Tenant-Id + X-User-Id + safe X-Correlation-Id?}
  H -- no --> DENY[Reject 400/401]
  H -- yes --> AZ{RBAC permission at service layer?}
  AZ -- no --> FBD[Reject 403]
  AZ -- yes --> AUD[Write audit_log before + after state]
  AUD --> WR[Perform write tenant + country scoped]
  WR --> OB{Async work needed?}
  OB -- yes --> OBE[Insert outbox_event same transaction]
  OB -- no --> OK[Return DTO + correlationId]
  OBE --> OK
```

---

## D) Degraded-mode flow (AI / Neo4j / Redis down)

```mermaid
flowchart LR
  IN[New incident candidate] --> R{Redis up?}
  R -- no --> R_BYP[Bypass dedup + cache log WARN] --> N
  R -- yes --> N{Neo4j up?}
  N -- no --> N_BYP[Build pack from index + fingerprint only mark correlation=degraded] --> AI
  N -- yes --> AI{Azure OpenAI up?}
  AI -- no --> DEEP[Use DeepSeek mark exec narrative AI pending queue Azure retry] --> SAVE
  AI -- yes --> CG{CostGuard ok?}
  CG -- no --> DEEP
  CG -- yes --> NORM[Normal AI router] --> SAVE
  SAVE[Persist incident + RCA result + outbox notification]
```

---

## E) On-demand investigation (operator triggers re-analysis)

```mermaid
sequenceDiagram
  autonumber
  actor U as Operator
  participant UI as Command Center UI
  participant API as Spring Boot API
  participant SVC as RCA Service
  participant IDX as Custom Index
  participant N4J as Neo4j
  participant PACK as EvidencePack Builder
  participant ROUT as AI Router
  participant DB as PostgreSQL
  participant OB as outbox_events

  U->>UI: Open incident → "Re-run RCA"
  UI->>API: POST /api/v1/rca/{incidentId}/rerun
  API->>SVC: authorize + audit + tenant scope
  SVC->>DB: write rca_rerun request
  SVC->>OB: enqueue RCA_REQUESTED (correlationId)
  API-->>UI: 202 Accepted + correlationId

  OB->>SVC: poll RCA_REQUESTED
  SVC->>IDX: search alerts/logs/metrics in window
  SVC->>N4J: blast radius + upstream dependencies
  SVC->>PACK: build EvidencePack ≤ 3 KB
  PACK->>ROUT: route(pack)
  ROUT-->>SVC: narrative + confidence + citedEvidenceIds
  SVC->>DB: write rca_results + audit
  SVC->>OB: enqueue NOTIFY_TEAMS
```

---

## F) Connector run (collect → queue)

```mermaid
sequenceDiagram
  autonumber
  participant SCH as Scheduler
  participant PL as Plugin
  participant ING as Ingestion Gateway
  participant OB as outbox_events
  participant W as Normalizer Worker
  participant FP as Fingerprint
  participant RDS as Redis
  participant IDX as Custom Index

  SCH->>PL: collect(window, tenant, country)
  PL->>PL: poll source (HTTP/JDBC/PowerShell) with timeout + retry + CB
  PL-->>ING: List<CanonicalTelemetryEvent>
  ING->>OB: enqueue INGESTION_BATCH (tenant scoped)
  W->>OB: poll
  W->>FP: compute fingerprint per event
  FP->>RDS: SETNX dedup:* EX 600
  RDS-->>FP: new vs duplicate
  FP-->>W: deduped batch
  W->>IDX: append-only write to shard {country}/{env}/{date}
```

---

Diagrams complement, never replace, the [CAUSAL_PIPELINE](./CAUSAL_PIPELINE.md) numbers and `EvidencePack` contract.

