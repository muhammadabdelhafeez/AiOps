# Causal Funnel Pipeline — KFH AIOps

> **This is the master architecture doc for KFH Causal AIOps.**
> Every other doc must align with the principle and numbers stated here.
> Last revised: 2026-06-30.

---

## 0. One-line principle

> **Code reduces the flood and gathers grounded evidence. AI reasons the root cause. Code owns the incident lifecycle.**

Deterministic services (dedup + index + banking-flow topology + causal candidate ranking) reduce
100,000 alerts to a handful of impact-anchored incidents, each with a compact (~3 KB) `EvidencePack`
of the complete relevant evidence **plus** retrieved similar past incidents/runbooks. **AI (DeepSeek
R1 local, then Azure OpenAI 5.5) then reasons the root cause** from that pack — and may override the
deterministic candidate — writing the narrative + recommended action. AI never sees the raw firehose
(only the pack), never invents evidence (must cite evidence IDs), and never decides the incident
lifecycle (deterministic, for audit). See **§8A** for the correlation + AI-led RCA model.

---

## 1. Capacity target

| Metric | Target |
|---|---|
| Raw alerts per ingestion cycle | **100,000** |
| Cycle length | **20 minutes** (sustained 83 alerts/sec; bursts to ~1,000/sec) |
| End-to-end latency, alert → incident → narrative | **≤ 8 minutes** worst case |
| Azure OpenAI 5.5 calls per cycle | **1–3** (typical), **≤ 10** hard cap |
| DeepSeek R1 local calls per cycle | **3–8** typical |
| AI summary cache hit rate (steady state) | **≥ 70 %** |

This holds for one PROD environment (e.g. KW). BH/EG/UAT/DEV run their own ingestion pools — Redis key-prefix isolation keeps them from interfering (see [SECURITY](./SECURITY.md), §12 of `copilot-instructions.md`).

---

## 2. The funnel (5 reduction stages)

```text
┌─────────────────────────────────────────────────────────────────────────┐
│ STAGE 0  Ingestion Gateway              100,000 raw alerts / 20 min      │
│          org.kfh.aiops.ingestion                                         │
│                ↓ schema + tenant + country + auth                        │
│ STAGE 1  Normalization + enrichment     100,000 CanonicalTelemetryEvent  │
│          org.kfh.aiops.normalization                                     │
│                ↓ fingerprint = sha256(country|env|src|ci|code|hour)      │
│ STAGE 2  Fingerprint dedup (Redis)      ~5,000–15,000 unique fingerprints│
│          dedup:{country}:{env}:{src}:{ci}:{code}  SETNX EX 600           │
│                ↓ batched index write                                     │
│ STAGE 3  Custom Index Engine write      searchable shards (per country)  │
│          org.kfh.aiops.index                                             │
│                ↓ time + topology correlation                             │
│ STAGE 4  Topology grouping (Neo4j)      ~50–200 incident candidates      │
│          blast radius from common upstream resources                     │
│                ↓ business-journey impact filter                          │
│ STAGE 5  Business impact engine         ~10–30 real incidents            │
│          (only journeys with degraded transaction success rate)          │
│                ↓ deterministic causal scoring (11 factors, §14)          │
│ STAGE 6  Causal RCA scoring             ~5–15 root-cause candidates      │
│          first-bad-timestamp + upstream + severity + evidence            │
│                ↓ build EvidencePack (≤3 KB JSON)                         │
│ STAGE 7  AI summary cache check         ~3–8 cache MISSES                │
│          GET ai:summary:known-issue:{packHash}                           │
│                ↓ misses go to AI router                                  │
│ STAGE 8  AI Router (§15 copilot rules)                                   │
│          ├── DeepSeek R1 local   ~5 calls  (low/med, known patterns)    │
│          └── Azure OpenAI 5.5    1–3 calls (critical + new + exec)      │
└─────────────────────────────────────────────────────────────────────────┘
```

**Reduction: 100,000 → ~2 Azure calls per cycle (~50,000×).**

---

## 3. Per-stage time budget

Total cycle = 1,200 s. Stages are pipelined; numbers below are worst-case wall-clock per cycle:

| Stage | Worst-case latency | Why it fits |
|---|---|---|
| 0 Ingestion + 1 Normalization | 30–60 s | Virtual threads (Java 21, §32.7) + batched HTTP, parallel per source/country |
| 2 Fingerprint + Redis SETNX | 10–20 s | ~0.1 ms per `SETNX`, fully pipelined Lettuce |
| 3 Custom Index batched write | 30–90 s | Shard by `{country}/{env}/{date}`, parallel per shard |
| 4 Neo4j blast radius | 20–60 s | Country-scoped subgraphs, ≤ 200 candidates |
| 5 Business impact filter | 5–10 s | In-memory join with `cmdb.business_journeys` |
| 6 Causal RCA scoring | 20–60 s | Pure CPU, deterministic, no IO |
| 7 AI cache lookup | < 2 s | Redis `GET` |
| 8 DeepSeek R1 local (parallel) | 30–120 s | 3–6 calls in parallel, 10–25 s each |
| 8 Azure OpenAI 5.5 (1–3 calls) | 20–60 s | Streamed, parallel |
| Persist incident + outbox publish | 20–60 s | One Postgres tx per incident |
| **Total** | **~4–8 min** | **Leaves ≥ 12 min buffer for retries / spikes / backpressure** |

---

## 4. AI cost control — seven hard levers

| # | Lever | Effect | Where |
|---|---|---|---|
| 1 | Never send raw telemetry; only `EvidencePack` (~2 KB) | 1000×+ token reduction | `org.kfh.aiops.rca.evidence` |
| 2 | Hash-cache narratives: `ai:summary:known-issue:{sha256(pack)}` TTL 1–6 h | 70–90 % cache hits in mature ops | Redis, §12 |
| 3 | DeepSeek R1 local first for known patterns / low–medium severity | Removes ~70 % of Azure traffic | `org.kfh.aiops.ai.deepseek` |
| 4 | Azure OpenAI 5.5 only for: CRITICAL + customer-impacting, novel pattern, executive narrative | ≤ 3 Azure calls/cycle | `org.kfh.aiops.ai.router` |
| 5 | JSON structured outputs + tight system prompt (~400 tokens) | ~3k input + 0.5–0.8k output per call | `org.kfh.aiops.ai.prompt` |
| 6 | `CostGuard` per-tenant daily call + USD budget; soft cap demotes to DeepSeek | Hard upper bound; no surprise bills | `org.kfh.aiops.ai.cost` |
| 7 | Resilience4j circuit-breaker on Azure → degraded mode "AI pending" | UX never blocked on AI | §29 of `copilot-instructions.md` |

### 4.1 Indicative monthly cost (KW PROD only)

Assumes Azure OpenAI 5.5 ~ `$1.25/M input` + `$10/M output` (use your contract rate):

| Mode | Calls/day | Cost/day | Cost/month |
|---|---|---|---|
| ❌ Naive: 1 call/alert | 7,200,000 | $72,000 | ~$2.16 M |
| ✅ Funnel worst case (no cache) | 216 | $2.60 | ~$78 |
| ✅ Funnel with 70 % cache hit | ~65 | $0.80 | **~$25–35** |

---

## 5. AI router decision logic

```text
INPUT: EvidencePack + IncidentMeta (severity, journeyImpact, novelty, deterministicConfidence)

1. cached = redis.get("ai:summary:known-issue:" + pack.hash())
   if cached and pack.evidenceMatches(cached): return cached         # $0 / <1 s

2. if severity in (LOW, MEDIUM) or pack.matchesKnownPattern():
       return deepseek.summarize(pack)                                # local, free

3. draft = deepseek.draftRca(pack)
   if draft.confidence >= 0.85 and not pack.customerImpacting():
       return draft                                                   # local

4. if severity == CRITICAL or pack.customerImpacting() or pack.novel():
       return azureOpenAi.finalRca(pack, draft)                       # paid, audited
   else:
       return draft                                                   # local

GUARDS (applied at every step):
- CostGuard.daily(tenant) ok else demote to deepseek
- Resilience4j.circuitBreaker("azure-openai") closed else demote to deepseek + outbox retry
- AuditService.log(tenant, model, reason, tokens, cost, confidence)
```

Every decision is recorded in `incident.ai_router_decisions` with: `model`, `reason`, `inputTokens`, `outputTokens`, `latencyMs`, `confidence`, `cacheKey`, `correlationId`.

---

## 6. The `EvidencePack` contract (AI input)

The `EvidencePack` is the **only** payload the AI router accepts. It is built by `org.kfh.aiops.rca.evidence.EvidencePackBuilder` and must be ≤ 3 KB.

```json
{
  "packId": "EVP-20260630-001",
  "packHash": "sha256:9f3a...",
  "country": "KW",
  "environment": "PROD",
  "tenantId": "00000000-0000-4000-8000-000000000001",
  "businessJourney": "Mobile Banking > Fund Transfer",
  "impact": "Transaction success 99.2% → 81.4% (10:00–10:16)",
  "firstBadTime": "2026-06-30T10:00:00Z",
  "proposedRootCause": {
    "entityId": "SAN-STORAGE-02",
    "entityType": "STORAGE",
    "confidence": 0.74,
    "reason": "First-bad upstream, blast radius covers all symptoms"
  },
  "candidateRootCauses": ["SAN-STORAGE-02", "ORACLE-CORE-01", "SVC-TRANSFER"],
  "evidence": [
    { "id": "E1", "ts": "10:00", "entity": "SAN-STORAGE-02", "fact": "write latency 2ms → 82ms (20x baseline)" },
    { "id": "E2", "ts": "10:14", "entity": "ORACLE-CORE-01", "fact": "buffer-busy waits +480%, IO wait +12s avg" },
    { "id": "E3", "ts": "10:15", "entity": "SVC-TRANSFER",   "fact": "DB timeout error_count=1247 (baseline 3/min)" },
    { "id": "E4", "ts": "10:16", "entity": "API-GATEWAY",    "fact": "HTTP 502 from /transfer rate=412/min (baseline 0)" }
  ],
  "topology": [
    "SAN-STORAGE-02 -[STORES]-> ORACLE-CORE-01",
    "ORACLE-CORE-01 -[BACKS]-> SVC-TRANSFER",
    "SVC-TRANSFER   -[EXPOSED_BY]-> API-GATEWAY /transfer"
  ],
  "excludedCauses": [
    "No deployment on SVC-TRANSFER in last 24h",
    "API-GATEWAY healthy until 10:16 (4 min after storage spike)",
    "No network device alerts in path"
  ],
  "schemaVersion": "1.0"
}
```

**Mandatory rules (enforced by `EvidencePackValidator`):**
1. No secrets, tokens, credentials, PII, or raw log lines.
2. ≤ 3 KB serialized (`kfh.ai.evidence-pack.max-bytes`).
3. Every evidence item has a stable `id` so the AI narrative can cite it.
4. `proposedRootCause` is a deterministic **candidate/hint**; the AI confirms or overrides it (AI-led RCA, §8A).
5. `excludedCauses` lists rejected candidates with reason — gives AI permission to disagree.

---

## 7. AI output contract

The AI router returns a strongly-typed JSON response (structured outputs):

```json
{
  "rootCauseConfirmed": true,
  "rootCauseEntityId": "SAN-STORAGE-02",
  "confidence": 0.91,
  "executiveNarrative": "SAN Storage 02 latency rose 20x at 10:00, causing Oracle buffer-busy waits at 10:14, Transfer Service DB timeouts at 10:15, and API gateway 502s at 10:16. Mobile Banking Fund Transfer success rate dropped to 81.4%.",
  "recommendedAction": "Engage Storage team for SAN-STORAGE-02 LUN-04 controller investigation. Failover transfer workloads to SAN-STORAGE-03 if latency persists > 15 min.",
  "citedEvidenceIds": ["E1","E2","E3","E4"],
  "uncertaintyNotes": "Storage controller telemetry not in pack; pull SAN array health metrics for definitive confirmation.",
  "modelUsed": "azure-openai-gpt-5.5",
  "tokensIn": 2940,
  "tokensOut": 612
}
```

Validation:
- `citedEvidenceIds` ⊆ `pack.evidence[].id` — otherwise reject as hallucination.
- `confidence` ∈ `[0, 1]`, never `1.0`.
- `executiveNarrative` ≤ 600 chars (operator readability).
- Persisted to `incident.rca_results`, cached to `ai:summary:known-issue:{packHash}` TTL 6 h.

---

## 8. Why this is **more accurate** than naive "send all to AI"

| Hard problem | What solves it | Why an LLM can't |
|---|---|---|
| Upstream vs downstream direction | Neo4j topology + first-bad timestamp | LLMs are bidirectional pattern matchers |
| Blast-radius counting (1 cause → N symptoms) | Graph traversal | Combinatorial, not textual |
| Time-window correlation across sources | Custom index time-pruning | LLMs see prompts unordered, have no clock |
| Statistical confidence | Evidence count + baseline deviation | LLMs produce prose, not numbers |
| Consistency for recurring incidents | Pack-hash cache | LLMs give different stories each call |
| 100k events at once | Funnel reduction to 1 pack | Context window blows up; truncation loses signal |

**AI's actual superpower is language**: turning the deterministic evidence into a human-readable RCA, exec summary, recommended action, runbook update. That is exactly what Azure OpenAI 5.5 is paid for.

---

## 8A. Correlation & AI-led RCA — the banking-flow model

> The differentiator vs BMC/enterprise observability. We do **not** correlate by text similarity or static severity
> weights (the failure mode of normal AIOps). We correlate by the **banking transaction flow** and
> let **AI reason the root cause** over the reduced evidence.

### Correlation = causal path on the flow graph (not similarity)
Grouping key = **shared causal path in the flow topology + causal time-order + business-journey
impact** — never "these alerts look alike". Per 20-min cycle:
1. **Anchor on impact.** Only journeys whose transaction success rate degraded become incidents (cuts 100k noise to the real few).
2. **Walk the flow graph upstream** (Neo4j) from each degrading journey; collect alerting nodes on/under the path in the window.
3. **Resolve cause vs effect** by topology direction + first-bad-timestamp + blast-radius containment — the most-upstream node that went bad first and whose downstream covers all symptoms.
4. **Merge by root, not by look:** one failing node breaking N journeys = one incident spanning N journeys; different roots = different incidents.
5. Build the `EvidencePack` (+ RAG: top-K similar past incidents + runbooks from the knowledge base).

### AI does the judgment code can't
Over the pack + knowledge base, AI: **confirms/overrides** the root cause; **splits** coincidental
overlaps into separate incidents; **merges** flows with hidden shared dependencies; **rejects**
temporally-close-but-not-causal alerts; reasons **novel** failures with no modeled path. AI must
**cite evidence IDs** (grounded — no hallucinated root cause) and never returns confidence `1.0`.

### Business-application topology (the model correlation walks)
Each business application (Fund Transfer, KFHOnline, WAMD, …) is modelled as a **component flow bound
to assets**, stored in Neo4j:
`(:BusinessApplication)-[:HAS_COMPONENT]->(:Component)-[:DEPENDS_ON]->(:Component)` and
`(:Component)-[:BOUND_TO]->(:Asset)`, where `Asset.ciKeys` match alert `resourceId`s.

- **Alert → asset → application resolution:** an alert's CI (BMC `source_hostname`, SCOM
  `NetbiosComputerName`/`MonitoringObjectName`) matches a bound `Asset`, which resolves the
  application(s) it impacts — this is what *focuses* an issue onto an application. Unmatched CIs are
  reported as "unmapped", never silently dropped.
- **Multi-application impact is first-class:** a shared component (core Oracle, API gateway, SAN)
  belongs to multiple flows, so one root-cause asset yields `impactedApplications[]` (a ranked list),
  not N per-app incidents.
- **Surface:** the **Service Map** page (Analysis & Topology menu group) renders each application's
  component flow + bound assets + live health; it is the operator view of this graph.

### No cap; incident continuity
- **No artificial limit** on incident count — as many *distinct real* `(root-cause × journey)`
  incidents as the data warrants (5, 30, 50…). The only gate is *quality* (impact-anchored + causally
  grounded), never a fixed number.
- **Continuity via `incident_key = f(root-cause node + impacted-journey set)`:** the same ongoing
  fault yields the same key each cycle → **one incident that updates**, not a new one per cycle. New
  incidents only for new root causes. (Redis dedup handles within-cycle repeats.)

### Depth roadmap (match enterprise observability)
The index already carries `LOGS/ALERTS/TRACES/METRICS/CHANGES`. Ingesting **distributed traces
(OpenTelemetry / APM spans)** gives code/query-level RCA (enterprise observability-class depth) **plus** whole-estate
breadth enterprise observability can't see (mainframe, storage, network, external, BMC/SCOM). Other RCA tools —
including enterprise observability — can be **ingested as sources**.

### Accuracy is measured, not claimed
KPIs: **root-cause precision** (opened incidents with the correct cause vs post-mortems), **recall**
(real outages caught + correctly RCA'd), **MTTR reduction**. An operator confirm/correct **feedback
loop** feeds the knowledge base so accuracy climbs over time. No "100%" promises — the levers are
topology-graph quality, source coverage, clock sync, and knowledge freshness.

---

## 9. Mapping to project modules

| Stage | Package | Status today |
|---|---|---|
| 0 Ingestion Gateway | `org.kfh.aiops.ingestion` | ❌ to build |
| 0 Connectors (collect → queue) | `org.kfh.aiops.plugin` | 🟡 connectivity testers only |
| 1 Normalization + enrichment | `org.kfh.aiops.normalization` | ❌ to build |
| 2 Fingerprint + dedup | `org.kfh.aiops.normalization.fingerprint` | 🟡 dedup service built (`FingerprintDedupService`: Redis `SET NX EX`, country/env keys, fail-open degraded mode) + runtime Redis client (`platform.redis`); ingestion wiring pending |
| 3 Custom Index Engine | `org.kfh.aiops.index` | 🟢 store + writer + postings-cache searcher (country-guarded) + retention-with-archive (filesystem/NFS gzip) + Settings-driven path + `POST /api/v1/logs/search`; cloud archive (S3/Azure) is a drop-in `ArchiveStore` follow-up |
| 4 Topology + blast radius | `org.kfh.aiops.topology` | ❌ to build (driver tester only) |
| 5 Business impact filter | `org.kfh.aiops.rca.service` | ❌ to build |
| 6 Causal scoring | `org.kfh.aiops.rca.causal` | ❌ to build |
| 6 EvidencePack builder | `org.kfh.aiops.rca.evidence` | ❌ to build |
| 7 AI summary cache | Redis (`ai:summary:*`) + `org.kfh.aiops.ai.router` | ❌ to build |
| 8 AI Router | `org.kfh.aiops.ai.router` | ❌ to build |
| 8 DeepSeek client | `org.kfh.aiops.ai.deepseek` | ❌ to build |
| 8 Azure OpenAI client | `org.kfh.aiops.ai.azureopenai` | 🟡 connectivity tester only |
| 8 CostGuard | `org.kfh.aiops.ai.cost` | ❌ to build |
| Incident lifecycle (deterministic) | `org.kfh.aiops.incident.lifecycle` | ❌ to build (CRUD stub only) |
| Outbox publisher | scheduled `@Component` | ❌ tables exist (V1/V2), no publisher |
| Notifications | `org.kfh.aiops.notification` | ❌ to build |

See [SERVICES_CORE](./SERVICES_CORE.md) for the per-service catalog.

---

## 10. Required `application.properties` knobs

The funnel needs explicit throttles so cost and latency are tunable from config, not code:

```properties
# Ingestion throughput
kfh.ingestion.batch-size=500
kfh.ingestion.flush-interval-ms=2000
kfh.ingestion.virtual-threads.enabled=true
kfh.ingestion.per-country-pool-size=200

# Dedup
kfh.dedup.window-seconds=600
kfh.dedup.fingerprint-fields=country,env,source,resourceId,errorCode,hourBucket

# Custom Index Engine
kfh.index.shard-count-per-day=4
kfh.index.write-batch-size=1000
kfh.index.search-parallelism=8
kfh.index.retention-days.alerts=30
kfh.index.retention-days.logs=14
kfh.index.retention-days.metrics=7

# AI Router
kfh.ai.router.cache-ttl-hours=6
kfh.ai.router.deepseek.confidence-threshold=0.85
kfh.ai.router.azure.daily-call-budget-per-tenant=200
kfh.ai.router.azure.monthly-usd-budget-per-tenant=500
kfh.ai.router.escalate-when=CRITICAL,CUSTOMER_IMPACT,NOVEL_PATTERN
kfh.ai.evidence-pack.max-bytes=3072
kfh.ai.prompt.system-tokens=400
kfh.ai.prompt.max-output-tokens=800

# Resilience4j (Azure OpenAI)
resilience4j.circuitbreaker.instances.azure-openai.failure-rate-threshold=40
resilience4j.circuitbreaker.instances.azure-openai.sliding-window-size=20
resilience4j.retry.instances.azure-openai.max-attempts=2
resilience4j.retry.instances.azure-openai.wait-duration=2s
```

---

## 11. Degraded modes

Per §29 of `copilot-instructions.md` (also see [ARCHITECTURE](./ARCHITECTURE.md)):

| Failure | Behaviour | UX impact |
|---|---|---|
| Redis down | Bypass cache + dedup, log WARN, request still succeeds | Slightly noisier alerts |
| Neo4j down | Build incidents from index + fingerprint, mark RCA `correlation=degraded` | Causal path not shown |
| Custom Index down | Buffer in outbox; do not block ingestion | Search results delayed |
| DeepSeek down | Route everything to Azure OpenAI (CostGuard may slow it) | Higher cost |
| Azure OpenAI down | Use DeepSeek; mark final narrative `AI pending`; queue Azure retry in outbox | Operator gets DeepSeek draft now, exec narrative later |
| CostGuard tripped | Refuse Azure call, demote to DeepSeek, alert ops | Cost capped |

---

## 12. What AI never does (hard rules)

Straight from §3 + §15 of `copilot-instructions.md`:

1. ❌ AI never sees raw telemetry — only `EvidencePack`.
2. ❌ AI never decides incident lifecycle (open/close/reopen). Deterministic engine only.
3. ❌ AI never invents evidence. `citedEvidenceIds` MUST be subset of pack evidence IDs.
4. ❌ AI never receives secrets, tokens, credentials, or unnecessary PII.
5. ❌ AI never returns `confidence == 1.0`.
6. ❌ AI never runs in the user request path — always async via outbox.

If any of these breaks, the build fails CI (contract tests in `ai.router.AiRouterContractTest`).

---

## 13. Related docs

- [OVERVIEW](./OVERVIEW.md) — product framing
- [ARCHITECTURE](./ARCHITECTURE.md) — component diagram + degraded modes
- [FLOWS](./FLOWS.md) — Mermaid funnel + AI router decision
- [SERVICES_CORE](./SERVICES_CORE.md) — per-package service catalog
- [OUTBOX](./OUTBOX.md) — async event names aligned to the funnel
- [SECURITY](./SECURITY.md) — AI/secret/PII guardrails
- [CODE_TEMPLATES](./CODE_TEMPLATES.md) — EvidencePack record + AiRouter skeleton
- [RUNBOOKS](./RUNBOOKS.md) — operator playbook for AI budgets and degraded modes
- `.github/copilot-instructions.md` §3, §10, §11, §12, §13, §14, §15

