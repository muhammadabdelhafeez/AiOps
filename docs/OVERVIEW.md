# KFH AIOps Command Center — Overview

## What this application is
KFH AIOps Command Center is the **banking-flow-aware causal AIOps platform** for KFH group entities (Kuwait, Bahrain, Egypt, future countries). It ingests alerts, logs, metrics, traces, and change events from every monitoring source, runs them through a **deterministic causal funnel**, and produces evidence-backed root cause analysis tied to the impacted banking journey.

> **One-line principle: Code finds the root cause. AI explains it.**
> See [CAUSAL_PIPELINE](./CAUSAL_PIPELINE.md) for the full design.

This is **not** an alert grouping tool. It does not group alerts by text similarity. It walks the real topology and timing of KFH banking flows (user → frontend → backend → database → storage → network → external) to identify the **upstream** root cause and report **which journey** is impacted.

## Goals
- Single command center for NOC / SRE / Country Admin operations across KW, BH, EG.
- Reduce MTTA/MTTR by replacing alert lists with **business-journey-impact + root cause**.
- Process **100,000 alerts every 20 minutes** with **1–3 Azure OpenAI 5.5 calls per cycle** (see [CAUSAL_PIPELINE §1, §4](./CAUSAL_PIPELINE.md)).
- Deterministic incident lifecycle (open / close / reopen) — AI never decides, only recommends.
- Multi-tenant + country-aware: KW operators cannot see BH/EG data unless granted.
- Full auditability: every write, every AI decision, every connector run.

## Non-goals (v1)
- Replace the monitoring tools (SCOM/SolarWinds/BMC/AppDynamics/etc.) — we ingest from them.
- Fully automated remediation.
- Replace ITSM (ServiceNow etc.) — we integrate and export evidence.
- Use Elasticsearch/OpenSearch — telemetry search runs on our **Custom Index Engine** (§10 of `copilot-instructions.md`).

## Users & personas
- **NOC Operator** — triage, verify, escalate.
- **SRE / App Owner** — deep incident analysis, recurrence review, RCA approval.
- **DB / Network / Storage Teams** — scoped views of their resources only.
- **Country Admin** — connectors, schedules, users, RBAC for one country.
- **KFH Global Admin** — cross-country governance.
- **Leadership** — KPIs, recurring-incident ratio, executive RCA summaries.

## Key concepts (aligned to copilot-instructions §8–§15)

| Concept | Meaning |
|---|---|
| `CanonicalTelemetryEvent` | Source-agnostic normalized event (alert/log/metric/trace/change). |
| Fingerprint | `sha256(country\|env\|src\|ci\|errorCode\|hourBucket)` for dedup in Redis (TTL 600 s). |
| Custom Index Engine | Custom shard-per-day/per-country store for searchable telemetry. **Not Elasticsearch.** |
| Topology Graph | Neo4j model of `Country → Journey → App → Service → Resource` with dependency edges. |
| Causal Score | 11-factor deterministic score (see §14) that ranks root cause candidates. |
| `EvidencePack` | ≤ 3 KB structured JSON; **the only payload the AI router accepts**. |
| AI Router | Cache → DeepSeek R1 local → Azure OpenAI 5.5 escalation, gated by `CostGuard`. |
| Incident Lifecycle | Deterministic state machine `NEW → OPEN → ACK → MONITORING → CLOSED / REOPENED`. AI never decides. |

## How it actually works (the funnel)

```text
100,000 raw alerts (20 min)
   → 100,000 CanonicalTelemetryEvent       (Normalization)
   → 5–15k unique fingerprints              (Redis dedup)
   → searchable shards                      (Custom Index Engine)
   → 50–200 incident candidates             (Neo4j blast radius)
   → 10–30 real incidents                   (Business-journey filter)
   → 5–15 root-cause candidates             (Causal scoring)
   → 3–8 cache misses                       (ai:summary:* lookup)
   → 1–3 Azure OpenAI 5.5 calls             (only criticals + novel + exec)
```

Full diagram and numbers in [CAUSAL_PIPELINE §2–§3](./CAUSAL_PIPELINE.md). Mermaid version in [FLOWS](./FLOWS.md).

## Tech stack
- **Backend:** Java 21, Spring Boot 3, Spring Security, Spring Data JPA, Spring Data Neo4j, Spring WebFlux, Resilience4j, virtual threads.
- **Frontend:** Static SPA (HTML/CSS/JS) under `src/main/resources/static/` — Command Center UI in KFH navy/gold.
- **System of record:** PostgreSQL (incidents, users, connectors, audit, RCA summaries, outbox).
- **Topology graph:** Neo4j (country/journey/app/service/resource + causal paths).
- **Hot cache + dedup + locks:** Redis 7+ (key-prefix isolation per country, never DB-index isolation).
- **Telemetry search:** Custom Index Engine (sharded NIO segment store, see [CAUSAL_PIPELINE §9](./CAUSAL_PIPELINE.md)).
- **Object storage:** Raw compressed telemetry + evidence snapshots.
- **AI:** DeepSeek R1 local (free, bulk) + Azure OpenAI 5.5 (paid, critical only) routed through `AiRouter` + `CostGuard`.
- **Notifications:** Teams, email, SMS, webhook (outbox-driven).

## Start here
1. [CAUSAL_PIPELINE](./CAUSAL_PIPELINE.md) — the funnel and the cost/accuracy contract.
2. [ARCHITECTURE](./ARCHITECTURE.md) — components, data ownership, degraded modes.
3. [SECURITY](./SECURITY.md) — OWASP + tenant + AI guardrails.
4. [API_CONTRACTS](./API_CONTRACTS.md) — REST surface.
5. [RUNBOOKS](./RUNBOOKS.md) — operator playbook.

