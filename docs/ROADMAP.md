# Roadmap — KFH Causal AIOps

> Phases match `copilot-instructions.md` §26 (Development Strategy).
> Every phase is graded against the targets in [CAUSAL_PIPELINE §1](./CAUSAL_PIPELINE.md).

---

## Now (stabilize Phase 1 — modular monolith skeleton) 🟡

- Enforce `X-Tenant-Id`, `X-User-Id`, `X-Correlation-Id` on every `/api/v1/**` endpoint (service-layer check, not only filter).
- Permission-based RBAC at service layer on every write.
- Audit log on every write (before + after state, correlation id).
- Outbox tables exist (V1, V2) — **build the `OutboxPublisher`** (`@Scheduled(fixedDelay=2000)` + virtual-thread workers).
- Settings infrastructure connection testers hardened (Redis / Postgres / Neo4j / Azure OpenAI / Kafka).
- Degraded-mode flags on `/actuator/health` for Neo4j / Redis / Custom Index / AI.

## Phase 2 — Custom Log Index Engine ⚪
- Shard-per-`{country}/{env}/{date}` NIO append-only segment store.
- Inverted index (keyword / text / numeric / date fields per §10).
- Reader: time-range + country + severity + service + trace-id + correlation-id filters.
- Retention + archive to object storage with `rawRef` pointer.
- Properties: `kfh.index.shard-count-per-day`, `kfh.index.write-batch-size`, `kfh.index.search-parallelism`, retention per data type.
- Target: 1,000 docs/sec sustained write, p95 search < 500 ms on a single shard.

## Phase 3 — Neo4j banking flow graph ⚪
- Node ontology per §11: `Country → BusinessDomain → BusinessJourney → Application → Service → ApiEndpoint`, plus `Server / Database / Storage / NetworkDevice / NetworkLink / ExternalSystem / Team`.
- Cypher queries: `blastRadius(rootCandidate)`, `upstreamDependencies(resource)`, `journeyImpact(journeyId, window)`.
- Country-scoped subgraphs (label + property filter).
- Topology upsert from CMDB + plugin topology output.

## Phase 4 — Ingestion → Normalization → Fingerprint → RCA ⚪
- `org.kfh.aiops.ingestion` — country gateway + per-source intake endpoints.
- `CanonicalTelemetryEvent` record + per-source mappers.
- `FingerprintService` + Redis `SETNX dedup:*` (TTL 600 s).
- `EvidencePackBuilder` (≤ 3 KB, no secrets, stable evidence IDs).
- `CausalScoringService` (11 factors, deterministic, pure CPU).
- Contract test: `EvidencePackContractTest` (size cap, no PII regex, stable IDs).

## Phase 5 — AI Router (DeepSeek + Azure OpenAI 5.5) 🟡 (Azure connectivity tester only)
- `org.kfh.aiops.ai.router.AiRouter` — implements decision table in [CAUSAL_PIPELINE §5](./CAUSAL_PIPELINE.md).
- `org.kfh.aiops.ai.deepseek.DeepSeekClient` — local HTTP client.
- `org.kfh.aiops.ai.azureopenai.AzureOpenAiClient` — full chat + structured outputs (extend the existing connectivity tester).
- `org.kfh.aiops.ai.cost.CostGuard` — per-tenant daily call + USD budget; soft cap → demote; hard cap → page + outbox.
- Redis cache `ai:summary:known-issue:{packHash}` TTL 6 h.
- Audit table `incident.ai_router_decisions`.
- Targets: ≥ 70 % cache hit, ≤ 10 Azure calls/cycle, ≤ $80/month/PROD env indicative.

## Phase 6 — Redis hot health state + dashboard cache ⚪
- `HealthStateService` writes `health:{country}:{env}:{kind}:{id}` TTL 5–15 min on every relevant event.
- Dashboard cache `dashboard:{country}:{env}:{view}` TTL 30 s.
- Distributed locks `lock:{scope}:{id}` 30 s – 5 min.
- Rate limits `rate-limit:tenant:{country}:{path}`.
- Connection pool sizing + Lettuce pipelining defaults documented.

## Phase 7 — Worker extraction ⚪
- Extract ingestion / index writer / AI router into separately scalable Spring Boot workers when load justifies (per §26).
- Same packages, same DTOs, different deployment unit.
- Outbox + Kafka as the integration bus.

---

## Cross-cutting non-functional targets

| Target | Value | Phase |
|---|---|---|
| Raw alerts per 20-min cycle | 100,000 | Phase 4 |
| End-to-end latency | ≤ 8 min worst case | Phase 4 |
| Azure OpenAI calls / cycle | 1–3 typical, ≤ 10 hard cap | Phase 5 |
| AI summary cache hit | ≥ 70 % | Phase 5 |
| Index write throughput | ≥ 1,000 docs/sec/shard | Phase 2 |
| Causal scoring throughput | 1k+ candidates / sec / core | Phase 4 |
| Test coverage (lifecycle + RCA + router) | ≥ 85 % branch + mutation (PIT) | continuous |

---

## Engineering roadmap
- OpenAPI generation published to `docs/generated/openapi.json`.
- Dependency / package graph generation under `docs/generated/`.
- ADR process for every architecture decision (`docs/adr/`).
- Spotless + Checkstyle + SpotBugs + Error Prone + PMD + OWASP Dependency-Check in CI.
- Testcontainers (PostgreSQL + Neo4j + Redis) for repository / integration tests — no H2.
- PIT mutation testing on `incident.lifecycle` + `rca.causal` + `ai.router`.

---

## Out of scope (v1)
- Replacing source monitoring tools.
- Automated remediation.
- ITSM replacement (export only).
- Elasticsearch / OpenSearch (Custom Index Engine only — §3 + §10).
- Sending raw telemetry to Azure OpenAI (forbidden — see [CAUSAL_PIPELINE §12](./CAUSAL_PIPELINE.md)).

