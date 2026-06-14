# Copilot Routing Index — KFH Causal AIOps Platform

This index is the **first file every AI agent (Copilot, Claude Code, OpenAI) should read** for a task.
It tells the agent which on-demand files to load based on task type, so the context window stays small and focused.

> Always-load (every task, always):
> 1. `.github/copilot-instructions.md` — global rules (architecture, security, lifecycle, DoD)
> 2. `.github/INDEX.md` — this file
> 3. `.github/PROGRESS.md` — agent-facing current status snapshot + recent implementation change log
> 4. `docs/PROGRESS.md` (or the highest-numbered `docs/PROGRESS-*.md`) — canonical full task history

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
| **Documentation-only change** | (none beyond the always-load set) | target docs file |

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
  EXECUTIVE_SUMMARY.md
  ROADMAP.md
  NEXT_STEPS_ENTERPRISE_AIOPS.md
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
Before starting:
1. Read .github/copilot-instructions.md and .github/INDEX.md.
2. Load on-demand files per the INDEX routing table for the task type below.
3. Read .github/PROGRESS.md and the active docs/PROGRESS-*.md to check current status.
4. Follow OWASP Top 10 (A01–A10) and copilot-instructions §16, §20–§21.
5. Follow the schema in docs/DATABASE_SCHEMA.md.
6. Follow naming + boilerplate in docs/CODE_TEMPLATES.md.

After completion:
- Append a new entry to the active docs/PROGRESS-*.md (rotate if > 3000 lines).
- Update .github/PROGRESS.md for recent/high-impact completed work or agent-routing status changes.
- Update docs/SERVICES_CORE.md or docs/SERVICES_SUPPORT.md if classes/endpoints changed.
- Update docs/API_CONTRACTS.md if APIs changed.
- Update docs/DATABASE_SCHEMA.md if schema changed (and add V{n}__*.sql).
- Update .github/copilot-instructions.md if architecture rules changed.

Task:
<your task here>
```

