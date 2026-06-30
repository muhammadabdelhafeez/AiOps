# Architecture & Maintainability Review — KFH AIOps

> Snapshot review (2026-06-30) of backend (Java), frontend (JS), and CSS, to validate the
> codebase is clean/scalable/maintainable **before** building the Causal Funnel + Part D
> (runtime Redis). Verdict: **good bones, real debt — worth building on, with fixes scheduled.**

## Scorecard

| Layer | Grade | Headline |
|---|---|---|
| Backend (Java) | C+ | Excellent security/RBAC/SSRF discipline + correct layering *intent*; but domain is untyped `Map<String,Object>` everywhere, and 2 god classes |
| Frontend (JS) | C+ | Strong shared core (`api-client`/`config`/`utils`); but **two competing UI paradigms**, in-browser Babel, and 2,000-line page files |
| CSS | C− | Real token system exists but is bypassed; had a 505 KB dead file (removed) + a `!important` override layer |

## What's already good (preserve)
- Security hygiene: every service starts with `requirePermission`; secrets masked; **nothing sensitive logged**; real SSRF defense (blocks metadata/loopback/link-local).
- Thin controllers, constructor injection, problem+json errors, connector-adapter isolation (the part most ready to scale).
- `api-client.js` (tenant headers, `APIError`, correlation IDs) and `config.js` (session/scope) are the strongest frontend files.
- A genuine CSS token system in `kfh-theme.css` + a clean `base.css` reset; pages are class-scoped (no global leakage).

---

## Remediation plan (prioritized)

> ⚠️ Verification note: the dedup/refactor items below **must be built/run to verify**
> (`mvn clean install` for Java; open the SPA in a browser for JS). They were intentionally
> NOT applied in a JDK/Node-less environment to avoid shipping unverifiable regressions
> (e.g. the duplicated `esc()` helpers have *subtly different null handling* — getting that
> wrong is an XSS regression).

### Tier 0 — quick wins (low risk)
- [x] **Delete dead `app.css`** (505 KB, repo-root, unreferenced, stale `#00634A` palette). — done, commit `ba998b4`
- [x] **Delete legacy `shell.js`** (unreferenced; emoji sidebar + hardcoded fake user, contradicts SPA router). — done, commit `ba998b4`
- [ ] **De-duplicate JS helpers into `shared/js`** (verify in browser):
  - `esc()` is redefined in `settings.js:113`, `connectors.js:46`, `users.js:27`, `reports.js:31`, `audit.js:33` — converge on `KFHUtils.escapeHtml`/`sanitizeHtml` (match null handling first).
  - `pageContent()` duplicated in 8 files (alerts, incidents, reports, applications, connectors, audit, users, applicationconfig) → `shared/js/api-helpers.js`.
  - `toast()` (`settings.js:459`, `auth.js:384`) → `KFHUtils.showToast`.
  - `icons{}` + `icon(name,size)` (settings, connectors, audit) → `shared/js/icons.js`.
- [ ] **De-duplicate Java helpers** (verify with `mvn compile`): extract copy-pasted `isSecretLike` / `normalize` / `safe` / `firstNonBlank` (in `SettingsService:974`, `CommandCenterReadModel:383`, `JdbcConnectorPersistenceStore:389`, `ConnectorService:643`) into `platform.security.SecretKeyMatcher` + `platform.util.Maps`.

### Tier 1 — structural (do before the broad Causal Funnel build)
- [ ] **Introduce a typed domain model.** Replace `Map<String,Object>` at service boundaries with records (DTOs) + JPA entities per `CODE_TEMPLATES.md`. Start: `IncidentEntity`/`IncidentSummaryDto`, `ConnectorEntity`/`ConnectorDto`. This unblocks the typed `EvidencePack`/`RcaResult`/`CanonicalTelemetryEvent` contracts the funnel needs.
- [ ] **Split `SettingsService.java` (1023 → ~5)**: `SettingsSnapshotAssembler`, `SettingsSanitizer`, `SettingsTestRouter`, `CountryScopeFilter`, and a thin orchestrating `SettingsService`.
- [ ] **Flatten `ConnectorService.java` (651)**: replace the 5 telescoping constructors with `Map<String,ConnectorConfigValidator>` + `Map<String,ConnectorLiveTester>` injected as `List<…>` and keyed by plugin type; collapses `executeLiveTest`/`validate`/`credentialAliases` switches to lookups (new connector = drop-in `@Component`).
- [ ] **Break the `platform ↔ commandcenter` package cycle**: move shared web types (`PageResponse`, `UiWriteRequest`, `UiQuerySupport`, `TenantContext`) and `CommandCenterReadModel` (infrastructure, not a feature) into a `shared`/`platform.web` package. Retire the in-memory demo-seeded `CommandCenterReadModel` per-domain as JPA repos land.
- [ ] **`IdentityJdbcRepository.java` (452)**: move business logic (`provisionBootstrapUser`, role seeding, password-decision) up into the identity services; keep the repo pure SQL.

### Tier 2 — frontend/CSS overhaul (parallel track; does not block backend)
- [ ] **Converge on one UI paradigm.** 5 pages are vanilla-IIFE + `innerHTML` templates, 6 are React (UMD). Standardize on React (already vendored); this also fixes the manual-`esc` XSS surface and full-subtree re-render workarounds.
- [ ] **Add a build step (esbuild/Vite).** Precompile JSX → remove client-side `babel-standalone` and the per-navigation `Babel.transform` in `router.js`; enables ES modules + the file splits below + tree-shaking + real HTTP caching (drop `?v=Date.now()`).
- [ ] **Split giant page files** along their seams: `settings.js` (2080) → `state`/`normalize`/`api`/`sections/*`/`modals/*`/`render`; same shape for `connectors.js` (2042, drawer-tab seam) and `inventory.js` (1723).
- [ ] **Shared CSS component layer** (`shared/css/components.css`): canonical `.btn`/`.card`/`.modal`/`.drawer`/`.badge`/`.empty-state`/`.table` on tokens; strip the 6×`.drawer`/5×`.btn`/3×`.modal` page copies.
- [ ] **Token sweep** of `connectors.css`/`reports.css`/`settings.css`/`users.css` (replace literal hex with `var(--…)`); remove the stray `:root` in `inventory.css:14`.
- [ ] **Detox `kfh-aiops-parity.css`** (2788 lines, ~1,150 `!important`): fold shell/sidebar rules into a proper `shell.css`, remove `!important` in passes; add a stylelint gate (no `:root` outside the token file, no raw-hex-matching-token, cap `!important`).

---

## Recommended sequencing
1. Finish Tier 0 dedup on a JDK/Node machine (fast, low risk once verifiable).
2. **Part D (runtime Redis client + health + dedup)** can proceed in parallel — it's new, typed infrastructure code and does **not** depend on the legacy refactor.
3. Tier 1 (typed domain model + service splits + cycle break) before the broad Causal Funnel implementation.
4. Tier 2 frontend/CSS as a dedicated hardening pass.
