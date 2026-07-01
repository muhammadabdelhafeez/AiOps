# KFH AIOps — Dynatrace-Parity Roadmap

> **Goal.** Close the Dynatrace feature gaps that fit our mission and redesign every page to a
> Dynatrace-class presentation — **with our mindset**: *Code reduces the flood and gathers grounded
> evidence; AI reasons the root cause; code owns the incident lifecycle.* We are the **AIOps
> correlation + AI-RCA brain over the bank's existing tools** (BMC, SCOM, vROps, AppDynamics, …), plus
> selective observability depth — not an agent-based APM vendor.

## Two tracks, delivered together each phase
- **Capability** — the engine (topology, RCA, AI, lifecycle, anomaly, depth).
- **Experience** — the Dynatrace-style UI for that capability (so every phase ships something visible).

## Phases at a glance
| # | Phase | Capability | Page(s) redesigned (Dynatrace style) | Gaps closed |
|---|---|---|---|---|
| 0 | Design System & Shell | shared visual language + app shell | nav rail, global filter/time bar, all pages restyled | modern presentation |
| 1 | Home / Overview | aggregation endpoints | **Home** (problem feed, health, top apps, ingest status) | at-a-glance ops |
| 2 | Topology & Correlation | Neo4j model + asset↔alert resolver + blast radius | **Service Map** (Smartscape-style live flow) | topology, dependency, correlation, multi-app |
| 3 | Causal RCA + Problems | 11-factor candidate ranking + EvidencePack + incident formation | **Problems/Incidents** (problem cards, impact, root-cause entity, timeline) | RCA, noise reduction (grouping) |
| 4 | AI-led RCA | AI router (DeepSeek→Azure) + RAG + narrative | RCA narrative + recommended-action panel in Problem detail | causal/generative AI, RCA explanation |
| 5 | Lifecycle + Automation | deterministic incident lifecycle + notify + remediation hooks | incident actions, workflow, notification config | auto-remediation, agentic workflows (rule-based) |
| 6 | Anomaly + Alerts | baselining/anomaly detection + correlation noise reduction | **Alerts/Events** (stream, grouping, "why grouped") | anomaly detection, noise reduction |
| 7 | Observability Depth | OpenTelemetry traces + metrics + logs + change events; expand collectors | **Connectors→Data Sources hub**, service-flow & trace waterfall, metric charts, **Inventory/Entities** | distributed tracing, metrics, code/txn-level RCA input, deployment analytics |
| 8 | Assistant + Predictive | NL query over index + forecasting + cost/ingest dashboards | global NL search, predictive indicators, ingest-cost dashboard | NL interface, predictive, cost dashboards |

Order rationale: **0–1** give the whole app a Dynatrace look fast; **2–5** build our core differentiator (the AIOps brain) end-to-end; **6–8** add smarter signal, depth, and foresight. Every phase is independently shippable.

---

## Phase detail

### Phase 0 — Design System & Shell
- **Capability:** design tokens (color/typography/spacing/density, light + optional dark), component library (cards, panels, entity header, data table, chart primitives, severity badges, event timeline), app-shell redesign with a **global filter bar** (management zone/country + environment + time-range picker, à la Dynatrace) and a **global search** box.
- **Experience:** left-nav polish; all existing pages inherit the new tokens/components (Settings/Users/Audit/Schedules get a restyle for free).
- **Why first:** every later page reuses it; avoids reworking styling repeatedly.

### Phase 1 — Home / Overview
- **Capability:** aggregation endpoints (problem feed, health rollups, ingest/source health, KPIs — mostly reuse existing data).
- **Experience:** Dynatrace-"Home" — problem feed, environment health tiles, **top impacted business applications**, source/ingest health, KPIs (open incidents, MTTR, alert volume, dedup ratio), quick actions.

### Phase 2 — Topology & Correlation engine + Service Map (live)  ← the differentiator
- **Capability:** Neo4j `(:BusinessApplication)-[:HAS_COMPONENT]->(:Component)-[:DEPENDS_ON]->(:Component)-[:BOUND_TO]->(:Asset)`; `TopologyService` (upsert/seed/import); **asset↔alert resolver** (`resourceId → Asset → Application(s)`, unmapped-CI report); **blast radius** + `impactedApplications[]` (multi-app).
- **Experience:** Service Map becomes an **interactive live flow graph** (Smartscape-style) — real-time health from ingested BMC/SCOM alerts, click asset → its alerts, shared components highlighted.

### Phase 3 — Causal RCA + EvidencePack + Problems page
- **Capability:** deterministic 11-factor **candidate ranking** (first-bad-time, upstream position, blast-radius containment, evidence count, severity), **business-impact filter** (journey success-rate drop), **EvidencePack** (≤3 KB), **incident formation** grouped by root-cause asset with `incident_key` continuity (New vs ongoing).
- **Experience:** Dynatrace-"Problems" page — problem cards (severity, impacted apps/journeys, root-cause entity, affected-entity list, event timeline).

### Phase 4 — AI-led RCA + narrative
- **Capability:** `ai.router` (DeepSeek local → Azure OpenAI), `ai.cost` guard, `ai.prompt`, **RAG** (top-K similar past incidents + runbooks). AI reasons/confirms/overrides root cause over the EvidencePack, **cites evidence IDs**, returns recommended action.
- **Experience:** Davis-style **root-cause narrative** + recommended-action + evidence-citation panel in the Problem detail.

### Phase 5 — Incident lifecycle + Notify + Remediation/Workflows
- **Capability:** deterministic `IncidentLifecycleEngine` (open/dedupe/close/reopen; New vs Recurring by fingerprint), Teams/email outbox, **remediation hooks** (webhook/runbook/ITSM ticket), rule-based workflow triggers.
- **Experience:** incident actions (ack/assign/close), lifecycle timeline, basic workflow config, notification routing UI.

### Phase 6 — Anomaly detection + Alerts/Events redesign
- **Capability:** **baselining/anomaly detection** (deviation from normal) on metrics/KPIs so we stop merely forwarding source thresholds; correlation-based **noise reduction** + event→problem linkage.
- **Experience:** Dynatrace-"Events" — event stream, grouping, filters, and a "**why grouped**" explanation.

### Phase 7 — Observability depth (OpenTelemetry)
- **Capability:** ingest **TRACES + METRICS** via OpenTelemetry; expand collectors to pull **metrics** (vROps, AppDynamics) and **logs**; **CHANGES/deployment** events. This unlocks code/transaction-level RCA input.
- **Experience:** **Connectors → Data Sources hub** (Dynatrace-Hub style), **service-flow + trace-waterfall** view, metric charts with deployment markers, **Inventory → Entities** pages.

### Phase 8 — NL Assistant + Predictive + polish
- **Capability:** **natural-language query** over the index (our "Assist", via the AI layer); **predictive/forecasting** (issues before impact); cost/ingest dashboards.
- **Experience:** global NL search bar, predictive indicators, ingest-cost dashboard.

---

## Decisions you must make (inputs that unblock phases)
1. **Design direction (Phase 0):** adopt a Dynatrace-like **dark theme** (with light option), or keep KFH light brand? Target density (compact vs comfortable)?
2. **Topology source (Phase 2):** hand-model applications in the UI first, or import from a **CMDB** (which one)? And the **first apps + journeys + component→asset mapping** to seed (Fund Transfer, KFHOnline, WAMD…).
3. **AI (Phase 4):** confirm the **DeepSeek local host** is available and the **Azure OpenAI** budget/limits; how much authority AI has to override the deterministic candidate.
4. **Remediation (Phase 5):** are **automated actions** allowed in the bank, or **notify-only**? Which **ITSM** for ticketing (ServiceNow / BMC Remedy)?
5. **Depth (Phase 7):** will you ingest **OpenTelemetry traces/metrics**, and from where — instrumented apps, or **AppDynamics/Dynatrace as a source**? Do we pull vROps/AppDynamics **metrics** (collectors currently test connectivity only)?
6. **Anomaly (Phase 6):** do we have **metric/time-series** data to baseline, or alerts only (which decides whether anomaly detection is metric-based or event-rate-based)?

## Deliberately out of scope — ingest, don't rebuild
These require being an **agent-based APM vendor** (years of R&D) and duplicate what the bank's existing tools already do. Recommendation: **ingest their output** rather than rebuild.
- OneAgent-style **auto-instrumentation**, **code profiling**, **live debugger**, **memory analysis**
- **Real-User Monitoring (RUM)** and **synthetic** monitoring
- **Smartscape auto-discovery** (we model topology explicitly / import from CMDB)
- **AI-observability product** (monitoring customers' LLM apps) — different mission
- **850+ turnkey integrations** (we build targeted bank connectors)
- Full **auto-remediation** autonomy (we do rule-based hooks + human-approved actions in a bank)

We compensate by **ingesting from Dynatrace/AppDynamics/OpenTelemetry as sources** (Phase 7) — so their depth flows into our correlation without us building agents.

---

*Execution: one phase at a time. Each phase ends with green tests + updated `API_CONTRACTS`/`RUNBOOKS` + a visible UI change. This roadmap is the index; per-phase specs are added as we start each.*
