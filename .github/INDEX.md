# Copilot Routing Index — KFH Causal AIOps Platform

This index is the **first routing file every AI agent (Copilot, Claude Code, OpenAI) should read** for a task.
Use `docs/AI_CODING_ASSISTANT_KNOWLEDGE_GRAPH.md` as the consolidated Graphify-style project knowledge graph, then load on-demand files below only when the task needs implementation detail.

> Always-load (every task, always):
> 1. `.github/copilot-instructions.md` — global rules (architecture, security, lifecycle, DoD)
> 2. `.github/INDEX.md` — this file
> 3. `.github/PROGRESS.md` — agent-facing current status snapshot + recent implementation change log
> 4. `docs/AI_CODING_ASSISTANT_KNOWLEDGE_GRAPH.md` — consolidated Graphify knowledge graph and source-document routing
> 5. `docs/PROGRESS.md` (or the highest-numbered `docs/PROGRESS-*.md`) — canonical full task history

---

## Routing Table — load on demand by task type

| Task type | Required files | Optional files |
|-----------|----------------|----------------|
| **Backend feature / refactor (Java, Spring Boot)** | `docs/BACKEND_MODULES.md`, `docs/CODE_TEMPLATES.md`, `docs/SERVICES_CORE.md`, `docs/SERVICES_SUPPORT.md` | `docs/ARCHITECTURE.md`, `docs/FLOWS.md` |
| **Database migration / schema change** | `docs/DATABASE_SCHEMA.md`, `docs/DATA_MODEL.md` | `src/main/resources/db/migration/` (latest `V{n}__*.sql`) |
| **API contract (new/changed endpoint)** | `docs/API_CONTRACTS.md`, `docs/CODE_TEMPLATES.md` | `docs/SERVICES_CORE.md`, `docs/SERVICES_SUPPORT.md` |
| **Frontend page / component (React + TS + Tailwind)** | `docs/FRONTEND_MODULES.md`, `docs/UI_PAGES.md`, `docs/CODE_TEMPLATES.md` | `docs/ARCHITECTURE.md` |
| **Ingestion / Plugin connector** | `docs/BACKEND_MODULES.md`, `docs/FLOWS.md` | `docs/SECURITY.md` (for SSRF + secret handling) |
| **Custom Index Engine** | `docs/ARCHITECTURE.md` §Custom Index Engine | `docs/DATA_MODEL.md` |
| **Neo4j topology / RCA causal graph** | `docs/ARCHITECTURE.md` §Neo4j, `docs/FLOWS.md` | `src/main/resources/db/neo4j/` |
| **Incident lifecycle / RCA scoring** | `.github/copilot-instructions.md` §13–§14 | `docs/FLOWS.md` |
| **AI Router (DeepSeek / Azure OpenAI)** | `.github/copilot-instructions.md` §15 | `docs/ARCHITECTURE.md` §AI |
| **Security review / audit** | `docs/SECURITY.md`, `.github/copilot-instructions.md` §16, §20–§21 | `docs/security-assessment-*.md` |
| **Operations / runbook update** | `docs/RUNBOOKS.md` | `docs/OUTBOX.md` |
| **Architecture decision** | `docs/adr/README.md`, `docs/ARCHITECTURE.md` | `docs/ROADMAP.md` |
| **Documentation-only change** | `docs/AI_CODING_ASSISTANT_KNOWLEDGE_GRAPH.md` | target docs file |

> Progress source of truth: `docs/PROGRESS.md` remains the canonical full audit trail. `.github/PROGRESS.md` is a concise routing/status companion for AI agents and must be kept in sync for recent/high-impact work.

> If a task spans multiple types, load the union of their required files.

---

## Update Targets — after every task

The agent must update the relevant files **after** completing work:

| If you changed… | …also update |
|---|---|
| Code that adds/changes classes or endpoints | `docs/SERVICES_CORE.md` or `docs/SERVICES_SUPPORT.md` |
| Public/inter-service API | `docs/API_CONTRACTS.md` |
| DB schema | `docs/DATABASE_SCHEMA.md` (and add `V{n}__*.sql`) |
| Architecture rules | `.github/copilot-instructions.md` |
| Operational behavior | `docs/RUNBOOKS.md` |
| **Anything at all** | `docs/PROGRESS.md` (or the active `PROGRESS-*.md` volume) — **always** |
| Agent routing/status snapshot | `.github/INDEX.md` and/or `.github/PROGRESS.md` |

---

## Quick File Map

```
.github/
  copilot-instructions.md      # global rules (§1–§31)
  INDEX.md                     # this file
  PROGRESS.md                  # concise agent-facing status + recent change log
docs/
  AI_CODING_ASSISTANT_KNOWLEDGE_GRAPH.md  # consolidated Graphify knowledge graph for AI assistants
  PROGRESS.md                  # task log (rotate at 3000 lines → PROGRESS-002.md)
  ARCHITECTURE.md
  BACKEND_MODULES.md
  FRONTEND_MODULES.md
  UI_PAGES.md
  API_CONTRACTS.md
  DATABASE_SCHEMA.md           # canonical schema (PostgreSQL)
  DATA_MODEL.md
  CODE_TEMPLATES.md            # naming + boilerplate templates
  SERVICES_CORE.md             # core-domain services / endpoints catalog
  SERVICES_SUPPORT.md          # platform/support services catalog
  FLOWS.md
  SECURITY.md
  RUNBOOKS.md
  OUTBOX.md
  OVERVIEW.md
  ROADMAP.md
  adr/                         # architecture decision records
src/main/resources/
  db/migration/                # Flyway V{n}__*.sql
  db/neo4j/                    # Cypher schema/seed
```

---

## OWASP Quick Reference (A01–A10, 2021)

Every backend task must consider:

- **A01 Broken Access Control** — tenant + RBAC checks at service layer; no IDOR.
- **A02 Cryptographic Failures** — encrypt secrets at rest; TLS everywhere; no plaintext tokens in logs/DB.
- **A03 Injection** — parameterized queries (JPA / JdbcTemplate); validate all inputs (Bean Validation).
- **A04 Insecure Design** — threat-model new flows; deny-by-default.
- **A05 Security Misconfiguration** — disable stack traces in error responses; secure Spring Security defaults.
- **A06 Vulnerable & Outdated Components** — track dependency CVEs.
- **A07 Identification & Authn Failures** — enforce `X-Tenant-Id`/`X-User-Id`; session/token rotation.
- **A08 Software & Data Integrity Failures** — sign artifacts; verify webhook signatures.
- **A09 Logging & Monitoring Failures** — structured logs with correlationId; audit all writes.
- **A10 SSRF** — validate every external URL in connector config against an allowlist.

---

## Recommended Task Prompt Template

```
Graphify Task Prompt — KFH Causal AIOps Platform

Before starting:
1. Orient with Graphify FIRST. A knowledge graph of the codebase lives in `graphify-out/`.
   Before grepping or reading source files, query it to locate the impacted code:
   - `graphify query "<question>"`   → scoped subgraph for the area you're touching
   - `graphify explain "<symbol>"`   → a class/method/file and its neighbors
   - `graphify path "<A>" "<B>"`     → how two nodes relate
   The graph is code-only (AST), so it tells you WHERE code is, not WHY it exists. Use it to
   find impacted code fast, then read the design docs below for intent. (Graphify CLI is the
   pipx package `graphifyy`; if `graphify` isn't on PATH, invoke it from the pipx bin dir.)
2. Read the always-load files in this order:
   - `.github/copilot-instructions.md`
   - `.github/INDEX.md`
   - `docs/AI_CODING_ASSISTANT_KNOWLEDGE_GRAPH.md`
   - `.github/PROGRESS.md`
   - the active `docs/PROGRESS-*.md` volume (the highest-numbered one)
3. Use the Graphify graph and the `.github/INDEX.md` routing table to load only the detailed docs required for the task type.
4. Identify the impacted graph nodes before editing: module/page, data source, table/schema, API contract, RBAC permission, tenant/country/environment scope, audit/outbox need, and operational runbook impact.
5. Enforce the platform invariants: OWASP A01-A10, tenant and country isolation, service-layer RBAC, audit for writes, safe structured logging, SSRF protection for outbound URLs, no secrets/PII/raw payloads in logs or prompts, deterministic incident lifecycle, and custom-index-only telemetry search.
6. When schema or persistence is involved, follow `docs/DATABASE_SCHEMA.md` and add a Flyway `V{n}__*.sql` migration when needed.
7. When generating code, follow `docs/CODE_TEMPLATES.md`, package boundaries, constructor injection, DTO/entity separation, tests, and the Definition of Done.

After completion:
1. Validate the targeted change with diagnostics, tests, or docs-only checks as appropriate.
2. If any code changed, refresh the knowledge graph: run `graphify update .` (AST-only, no API cost) so future queries stay accurate. (`graphify-out/` is gitignored and rebuilt per machine — do not commit it.)
3. Append a newest-on-top entry to the active `docs/PROGRESS-*.md` file.
4. Update `.github/PROGRESS.md` for recent/high-impact work or agent-routing/status changes.
5. Update the authoritative docs that match the change:
   - `docs/SERVICES_CORE.md` or `docs/SERVICES_SUPPORT.md` for new/changed classes or endpoints
   - `docs/API_CONTRACTS.md` for public or inter-service API changes
   - `docs/DATABASE_SCHEMA.md` for schema changes
   - `docs/RUNBOOKS.md` for operational behavior changes
   - `docs/SECURITY.md` for security-control changes
   - `docs/UI_PAGES.md` or `docs/FRONTEND_MODULES.md` for UI/front-end behavior changes
   - `docs/AI_CODING_ASSISTANT_KNOWLEDGE_GRAPH.md`, `.github/INDEX.md`, or `task` for routing/prompt changes
   - `.github/copilot-instructions.md` only when global architecture or coding rules change

Task:
<your task here>
```

