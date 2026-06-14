# GitHub Agent Progress Snapshot — KFH Causal AIOps Platform

This file is a concise, agent-facing progress snapshot for GitHub Copilot/AI assistants.

- **Canonical full history:** `docs/PROGRESS.md`
- **Routing rules:** `.github/INDEX.md`
- **Global implementation rules:** `.github/copilot-instructions.md`
- **Last synced:** 2026-06-14

> Keep this file short and current. Append newest entries at the top of the Change Log. Do not store secrets, passwords, raw payloads, tokens, or PII here.

---

## Current Status Snapshot

| Area | Status | Notes |
|------|--------|-------|
| Phase 1 — Modular monolith skeleton | 🟡 In progress | Spring Boot + static Command Center scaffold active; tenant/country-aware `/api/v1/**` routes in place |
| Identity / User Management | 🟡 In progress | DB-backed users, simplified Admin/Operator/Viewer UI roles, ALL-country admin mapping, BCrypt password reset/update |
| Audit Activity | 🟡 In progress | Live API-backed activity page now shows scoped real actions including login success/failure and settings actions; persistence is still Phase 1 in-memory read model |
| Settings | 🟡 In progress | Settings update/test actions emit visible audit rows with secret-safe key metadata only |
| Phase 2 — Custom Log Index Engine | ⚪ Not started | Required for raw telemetry search; do not use PostgreSQL/Neo4j for raw logs |
| Phase 3 — Neo4j banking flow graph | ⚪ Not started | Relationship/topology traversal pending |
| Phase 4 — RCA evidence builder + causal scoring | ⚪ Not started | Evidence-first causal scoring pending |
| Phase 5 — AI Router | ⚪ Not started | DeepSeek/Azure routing pending |
| Phase 6 — Redis hot state/cache | ⚪ Not started | Hot health/cache behavior pending |
| Phase 7 — Worker extraction | ⚪ Not started | Future extraction after modular monolith matures |

---

## Recent Completed Work

### 2026-06-14 — Login/settings/user/application actions visible in Audit Activity
- **Summary:** Audit Activity now records successful sign-ins, failed sign-ins, settings updates/tests, bootstrap identity provisioning, and existing operator write actions.
- **Important files:**
  - `src/main/java/org/kfh/aiops/platform/identity/IdentityAuthService.java`
  - `src/main/java/org/kfh/aiops/platform/identity/IdentityBootstrapInitializer.java`
  - `src/main/java/org/kfh/aiops/platform/config/SettingsService.java`
  - `src/main/java/org/kfh/aiops/commandcenter/support/CommandCenterReadModel.java`
  - `src/main/java/org/kfh/aiops/commandcenter/alerts/AlertService.java`
- **Security notes:** Passwords and raw request bodies are not logged or returned. Settings audit stores safe key names only. Failed-login audit avoids using submitted username as the logged entity ID.
- **Tests:** Full `mvnw.cmd test` passed with 57 tests, 0 failures.
- **Follow-up:** Replace Phase 1 in-memory audit activity with PostgreSQL-backed `identity.audit_log` adapter for restart durability.

### 2026-06-14 — Audit Activity page redesign and compaction
- **Summary:** `index.html#audit` was redesigned as a compact live application-activity console backed by `/api/v1/audit`. Seeded/dummy audit rows were removed from the audit view. KPI cards/header were later shortened further after visual review.
- **Important files:**
  - `src/main/resources/static/pages/audit/audit.js`
  - `src/main/resources/static/pages/audit/audit.css`
  - `src/main/java/org/kfh/aiops/platform/audit/AuditQueryService.java`
  - `src/test/java/org/kfh/aiops/platform/audit/AuditQueryServiceTest.java`
- **API behavior:** `GET /api/v1/audit` and `GET /api/v1/audit/{id}` are tenant-, country-, and environment-scoped. `X-Country-Code: ALL` requires global country visibility.
- **Tests:** Audit query tests and full Maven suite passed.
- **Follow-up:** Persist audit rows to PostgreSQL system-of-record when the audit adapter is implemented.

### 2026-06-14 — Modern User popup, simplified roles, and password reset
- **Summary:** User Management create/edit UI was modernized and compacted. UI roles show Admin/Operator/Viewer only. Backend maps Admin + ALL to `GLOBAL_ADMIN`, Admin + physical country to `COUNTRY_ADMIN`, Operator to `NOC_OPERATOR`, Viewer to `VIEWER`. Password reset/update uses BCrypt and does not return secrets.
- **Important files:**
  - `src/main/resources/static/pages/users/users.js`
  - `src/main/resources/static/pages/users/users.css`
  - `src/main/resources/static/shared/js/api-client.js`
  - `src/main/java/org/kfh/aiops/platform/identity/UserController.java`
  - `src/main/java/org/kfh/aiops/platform/identity/IdentityAdminService.java`
  - `src/main/java/org/kfh/aiops/platform/identity/IdentityJdbcRepository.java`
- **API behavior:** Added `PATCH /api/v1/users/{id}/password`; `POST/PUT /api/v1/users` accept simplified role aliases and canonicalize by country scope.
- **Security notes:** Passwords are BCrypt-hashed and never returned/logged.
- **Tests:** Identity repository and command-center backend tests updated; full Maven suite passed.

### 2026-06-14 — Fast Users search and all-country user creation
- **Summary:** User Management search now updates only the table region instead of reloading/re-rendering the full page per keystroke. ALL-country user creation persists `countryCode=ALL` and defaults to `GLOBAL_ADMIN` when permitted.
- **Important files:**
  - `src/main/resources/static/pages/users/users.js`
  - `src/main/java/org/kfh/aiops/platform/identity/IdentityAdminService.java`
  - `src/test/java/org/kfh/aiops/commandcenter/CommandCenterBackendServiceTest.java`
- **Security notes:** ALL scope requires global country visibility.

---

## Active Follow-ups / TODO

1. **Audit persistence:** Implement PostgreSQL-backed audit adapter using the system-of-record audit schema; the current visible Audit Activity read model is in-memory Phase 1 scaffold.
2. **Custom Index Engine:** Begin Phase 2 before implementing large raw telemetry search. Do not route raw logs through PostgreSQL, Neo4j, Redis, or AI prompts.
3. **Service catalog docs:** Keep `docs/SERVICES_CORE.md` / `docs/SERVICES_SUPPORT.md` synchronized whenever new services/controllers/endpoints are introduced.
4. **Browser validation:** After static asset changes, restart/hard-refresh browser (`Ctrl+F5`) to avoid cached JS/CSS.
5. **Mockito warning:** Test runs currently show Mockito dynamic agent warnings under Java 21; consider configuring Mockito as a Java agent in the build later.

---

## Change Log

### 2026-06-14 — Create GitHub progress snapshot and update routing index
- **Type:** docs
- **Summary:** Created `.github/PROGRESS.md` and updated `.github/INDEX.md` so agents load both the concise GitHub progress snapshot and canonical `docs/PROGRESS.md` before future work.
- **Files touched:**
  - `.github/INDEX.md`
  - `.github/PROGRESS.md`
- **Validation:** Markdown files inspected; no code or schema changes.
- **Author:** copilot-agent
- **Correlation:** user-request-2026-06-14-github-progress-sync

