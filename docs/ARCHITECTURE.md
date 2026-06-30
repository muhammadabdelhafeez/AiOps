# KFH AIOps Command Center — Architecture

> **Master design:** see [CAUSAL_PIPELINE](./CAUSAL_PIPELINE.md). This doc gives the component view and the cross-cutting rules.

## Architecture principles
1. **Code finds the root cause. AI explains it.** Deterministic graph + index + causal scoring produces the proposed root cause; AI only writes the narrative from a compact `EvidencePack`.
2. **Multi-tenant + country-aware** at the service layer, not just at the controller. Every query is scoped by `(tenantId, country, environment)`.
3. **Permission-based RBAC** enforced in services. Filters are defense-in-depth, not the only check.
4. **AI never decides incident lifecycle.** State transitions are deterministic (`incident.lifecycle`).
5. **Outbox-driven async** for AI, notifications, evidence persistence — never in the request path.
6. **Degraded-mode by design.** Neo4j / Redis / DeepSeek / Azure OpenAI can each be down without taking the platform down.
7. **No raw telemetry in PostgreSQL or Neo4j.** Postgres = system of record; Neo4j = relationships; Custom Index Engine = searchable telemetry; object storage = raw archive.
8. **No Elasticsearch / OpenSearch.** Custom Index Engine only (see §10 of `copilot-instructions.md`).

## High-level components

```text
Collectors / Agents / API Connectors
        ↓
Country Collector Gateway     (org.kfh.aiops.plugin)
        ↓
Ingestion Gateway             (org.kfh.aiops.ingestion)
        ↓
Durable Stream / Queue        (ops.outbox_events + Kafka-ready)
        ↓
Normalizer + Enrichment       (org.kfh.aiops.normalization)
        ↓
Fingerprint + Redis dedup     (normalization.fingerprint)
        ↓
Custom Log Index Engine       (org.kfh.aiops.index)
        ↓
Health State Engine (Redis)   (org.kfh.aiops.health)
        ↓
Neo4j Banking Flow Graph      (org.kfh.aiops.topology)
        ↓
Causal RCA Engine             (org.kfh.aiops.rca.causal)
        ↓
EvidencePack Builder          (org.kfh.aiops.rca.evidence)
        ↓
AI Router (DeepSeek + Azure)  (org.kfh.aiops.ai.router)
        ↓
Incident Lifecycle Engine     (org.kfh.aiops.incident.lifecycle)
        ↓
PostgreSQL system of record
        ↓
KFH AIOps Command Center UI   (static SPA)
```

### Frontend (Static SPA)
- Pages: Dashboard, Alerts, Incidents, Applications, Inventory, Business Journeys, Topology, RCA, Reports, Admin (Connectors / Schedules / Users / Settings / Audit).
- KFH navy/gold theme; reads `/api/v1/**` only.
- Shows **business impact + topology + evidence + recommended action**, not just alert lists.

### Backend (Spring Boot 3 + Java 21)
- API controllers (thin) → application services (RBAC + audit + tenant scope) → repositories (always tenant-scoped) → adapters (WebClient, JPA, Neo4j, Redis, Custom Index).
- `ApiContextFilter` rejects `/api/**` requests without `X-Tenant-Id`, `X-User-Id`, and a safe `X-Correlation-Id`.
- Virtual threads (Java 21) for blocking IO; WebFlux/`Flux` for backpressure-heavy plugin polling.
- Resilience4j circuit-breaker + retry + bulkhead on every external call.

### Local development profile
- `org.kfh.aiops.AiOpsApplication` serves the static SPA from `classpath:/static/` and exposes scaffold `/api/v1/**`.
- The `local` Spring profile disables datasource, JPA, Flyway, Neo4j, Redis so UI work can run offline.
- Local profile is **not** a production substitute — no RBAC, no audit, no integrations.

### Datastores

| Store | Purpose | Rule |
|---|---|---|
| **PostgreSQL** | System of record (identity, config, cmdb, incidents, rca_results, audit, outbox) | Never store raw alerts/logs/metrics |
| **Neo4j** | Topology + dependency graph + causal paths + blast radius | Never store raw telemetry; mirror only `IncidentRef`, `EvidenceRef` |
| **Redis 7+** | Hot health state + dedup + dashboard cache + distributed locks + short AI summary cache | Key-prefix isolation per `(country, env)`; **DB 0 only** (see [CAUSAL_PIPELINE §11](./CAUSAL_PIPELINE.md) and `copilot-instructions.md` §12) |
| **Custom Index Engine** | Searchable alerts/logs/metrics/traces/changes (time + country shards) | Replaces Elasticsearch/OpenSearch |
| **Object storage** | Raw compressed telemetry + evidence snapshots | Pointed at by `rawRef` in index |

## Data ownership

| Concern | Authoritative store |
|---|---|
| Tenants, users, RBAC, audit | PostgreSQL `identity.*` |
| Connectors, schedules, secrets (encrypted) | PostgreSQL `config.*` |
| CMDB (apps, services, resources, journeys, ownership) | PostgreSQL `cmdb.*` |
| Incidents + lifecycle + RCA summaries | PostgreSQL `incident.*` |
| Outbox events | PostgreSQL `ops.outbox_events`, `config.outbox_events` |
| Topology / dependency / causal paths | Neo4j (graph mirror only) |
| Searchable telemetry | Custom Index Engine |
| Raw payloads | Object storage |
| Hot health state, dedup, locks, dashboard cache, AI summary cache | Redis (TTLs per §12 of `copilot-instructions.md`) |

## Main workflow (deep) — the causal funnel

Full pipeline in [CAUSAL_PIPELINE §2](./CAUSAL_PIPELINE.md). Summary:

1. **Admin config** — connectors/schedules/journeys created and audited.
2. **Ingestion** — plugins poll/listen and push `CanonicalTelemetryEvent` to the outbox.
3. **Normalization + enrichment** — source-specific DTO → canonical; enriches with country, env, app, service, journey, owner from `cmdb.*`.
4. **Fingerprint + dedup** — Redis `SETNX dedup:{country}:{env}:{src}:{ci}:{code} EX 600`. Reduces 100k → ~5–15k unique fingerprints per cycle.
5. **Custom Index write** — sharded by `{country}/{env}/{date}`; raw payload reference goes to object storage.
6. **Health state update** — Redis `health:{country}:{env}:{kind}:{id}` TTL 5–15 min.
7. **Topology + blast radius** — Neo4j returns candidate root causes that explain the observed symptoms.
8. **Business impact filter** — only journeys with degraded transaction success rate progress.
9. **Causal scoring** — 11 deterministic factors (`copilot-instructions.md` §14).
10. **EvidencePack build** — ≤ 3 KB JSON, no secrets, every fact has a stable ID.
11. **AI router** — Redis cache → DeepSeek R1 local → (escalate if CRITICAL / customer-impact / novel) → Azure OpenAI 5.5. Gated by `CostGuard`.
12. **Incident lifecycle engine** — deterministic open/ack/monitor/close/reopen; AI may recommend but cannot decide.
13. **Persist + notify** — incident, evidence, RCA in PostgreSQL; graph mirror in Neo4j; Teams/email via outbox.

## Cost & throughput targets

| Target | Value | Source |
|---|---|---|
| Raw alerts per 20-min cycle | 100,000 | [CAUSAL_PIPELINE §1](./CAUSAL_PIPELINE.md) |
| End-to-end latency | ≤ 8 min worst case | [CAUSAL_PIPELINE §3](./CAUSAL_PIPELINE.md) |
| Azure OpenAI calls per cycle | 1–3 typical, ≤ 10 hard cap | `kfh.ai.router.azure.daily-call-budget-per-tenant` |
| AI summary cache hit rate | ≥ 70 % steady state | Redis `ai:summary:known-issue:*` TTL 6 h |
| Monthly Azure cost (one PROD env) | ~$25–80 indicative | [CAUSAL_PIPELINE §4.1](./CAUSAL_PIPELINE.md) |

## Degraded-mode behavior

| Failure | Behavior |
|---|---|
| Redis down | Bypass cache + dedup; log WARN; request still succeeds. |
| Neo4j down | Build incidents from index + fingerprint only; RCA flagged `correlation=degraded`. |
| Custom Index down | Buffer in outbox; ingestion not blocked; search results delayed. |
| DeepSeek down | Route everything to Azure OpenAI (CostGuard may slow it). |
| Azure OpenAI down | Use DeepSeek; mark final exec narrative `AI pending`; queue Azure retry. |
| CostGuard tripped | Refuse Azure call; demote to DeepSeek; alert ops. |

All degraded modes are visible at `/actuator/health` and on the Dashboard "System Health" tile.

## Observability
- **Metrics** — per-source ingestion rate; dedup hit ratio; index write latency; Neo4j query latency; causal-scoring time; AI calls per model; Azure cost per tenant per day; incidents opened/closed/reopened; recurring ratio.
- **Logs** — structured; carry `tenantId`, `countryCode`, `userId`, `correlationId`, `incidentId`, `connectorId`, `durationMs`, `status`. Never log secrets, tokens, PII, or raw payloads.
- **Tracing** — single trace from ingestion → normalization → index write → topology → RCA → AI → incident lifecycle.

## Related docs
- [CAUSAL_PIPELINE](./CAUSAL_PIPELINE.md) — funnel, time budget, AI cost levers, EvidencePack contract.
- [FLOWS](./FLOWS.md) — Mermaid diagrams of the funnel and AI router.
- [OUTBOX](./OUTBOX.md) — async event names aligned to the funnel.
- [SERVICES_CORE](./SERVICES_CORE.md), [SERVICES_SUPPORT](./SERVICES_SUPPORT.md) — per-package service catalog.
- [SECURITY](./SECURITY.md) — OWASP + AI/secret guardrails.
- [RUNBOOKS](./RUNBOOKS.md) — operator playbook.

