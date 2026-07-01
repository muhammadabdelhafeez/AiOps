# GitHub Agent Progress Snapshot — KFH Causal AIOps Platform

This file is a concise, agent-facing progress snapshot for GitHub Copilot/AI assistants.

- **Canonical full history:** `docs/PROGRESS.md`
- **Routing rules:** `.github/INDEX.md`
- **Global implementation rules:** `.github/copilot-instructions.md`
- **Last synced:** 2026-06-30

> Keep this file short and current. Append newest entries at the top of the Change Log. Do not store secrets, passwords, raw payloads, tokens, or PII here.

---

## Current Status Snapshot

| Area | Status | Notes |
|------|--------|-------|
| Phase 1 — Modular monolith skeleton | 🟡 In progress | Spring Boot + static Command Center scaffold active; tenant/country-aware `/api/v1/**` routes in place |
| Identity / User Management | 🟡 In progress | DB-backed users, simplified Admin/Operator/Viewer UI roles, ALL-country admin mapping, BCrypt password reset/update |
| Audit Activity | 🟡 In progress | Live API-backed activity page now shows scoped real actions including login success/failure and settings actions; persistence is still Phase 1 in-memory read model |
| Settings | 🟡 In progress | Settings update/test actions emit visible audit rows with secret-safe key metadata only |
| Phase 2 — Custom Log Index Engine | 🟢 Core complete | Store + writer + postings-cache searcher (country-guarded) + retention-with-archive (filesystem/NFS gzip) + Settings-driven path + `POST /api/v1/logs/search`. Cloud archive (S3/Azure) = drop-in `ArchiveStore` follow-up; then Alert Explorer UI |
| Phase 3 — Neo4j banking flow graph | ⚪ Not started | Relationship/topology traversal pending |
| Phase 4 — RCA evidence builder + causal scoring | ⚪ Not started | Evidence-first causal scoring pending |
| Phase 5 — AI Router | ⚪ Not started | DeepSeek/Azure routing pending |
| Phase 6 — Redis hot state/cache | 🟡 In progress | Runtime Redis client (`platform.redis`, Lettuce, DB 0) + fingerprint dedup (`SET NX EX`, fail-open) landed (Part D); ingestion wiring + dashboard health tile pending |
| Phase 7 — Worker extraction | ⚪ Not started | Future extraction after modular monolith matures |

---

## Recent Completed Work

### 2026-07-01 — Phase 2: Custom Index Engine searchable core (increment 1)
- **Summary:** Built the Elasticsearch-replacement core (`org.kfh.aiops.index`): typed model, sharded append-only `SegmentStore` (`{country}/{env}/{kind}/{date}/shard-NN`), `IndexWriterService` (batched, hash-routed), and `IndexSearchService` (time-partition prune → country/env → parallel filtered scan, newest-first, paginated). New `POST /api/v1/logs/search` (RBAC `ALERT_READ`). 14 unit tests pass; `mvn test` green. Increment 2 = inverted index, retention/archive, Settings-driven storage path.
- **Important files:** `org.kfh.aiops.index.*`, `IndexSearchController`, `IndexProperties`, application.properties `kfh.index.*`.

### 2026-06-30 — Part D: runtime Redis client + fingerprint dedup (Phase 6 start)
- **Summary:** Added `org.kfh.aiops.platform.redis` (RedisSettingsResolver + RedisConnectionProvider [Lettuce, DB 0, TLS/AUTH], RedisKeys, RedisHealthProbe, RedisErrors) consuming the Settings-stored encrypted Redis row per (tenant, country, env), and `normalization.fingerprint.FingerprintDedupService` (causal funnel Stage 2 `SET NX EX`, fail-open degraded mode). 15 new unit tests pass; `mvn compile` green. No new endpoints/migrations. Connection details come from Settings, not `spring.data.redis.*`.
- **Important files:** `org.kfh.aiops.platform.redis.*`, `org.kfh.aiops.normalization.fingerprint.FingerprintDedupService`, `SettingsService.resolveRedisConnection`.

### 2026-06-29 — Fix invalid Settings tenant/user header fallback in SPA
- **Summary:** Fixed the shared browser configuration module so UUID session validation is reachable again. The Settings SPA now preserves valid default tenant/user IDs and fails closed when session storage is corrupt, preventing repeated malformed `/api/v1/settings` requests that triggered `MISSING_OR_INVALID_CONTEXT` for `X-Tenant-Id`.
- **Important files:**
  - `src/main/resources/static/shared/js/config.js`
  - `docs/API_CONTRACTS.md`
  - `docs/SERVICES_SUPPORT.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** OWASP A01/A07 behavior is preserved and clarified: tenant/user UUID context remains mandatory, no secrets are logged or returned, and the browser now avoids sending invalid context headers when the local session is broken.
- **Tests:** IDE diagnostics confirmed the unreachable UUID validation bug is removed from `config.js`; remaining diagnostics are pre-existing unused-symbol warnings only.

### 2026-06-29 — Fix AI provider settings reload after restart
- **Summary:** Fixed PostgreSQL-backed Settings metadata reload semantics so AI provider rows saved from the Settings page remain durable after Tomcat restart. Scoped list-backed sections now reload with deterministic replacement semantics, preventing stale or conflicting provider rows from causing the UI to come back empty.
- **Important files:**
  - `src/main/java/org/kfh/aiops/platform/config/JdbcSettingsMetadataStore.java`
  - `src/test/java/org/kfh/aiops/platform/config/SettingsServiceTest.java`
  - `docs/API_CONTRACTS.md`
  - `docs/SERVICES_SUPPORT.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Tenant/user context, service-layer permissions, audit behavior, masked secret handling, and SSRF protections remain unchanged. No plaintext API keys are returned or logged.
- **Tests:** `./mvnw.cmd -q -Dtest=SettingsServiceTest test` passed.

### 2026-06-29 — Persist Settings per country and environment
- **Summary:** Settings metadata is now permanently stored in PostgreSQL `config.integration_settings` by tenant, country scope, environment, and key. Database, SharePoint, and Servers & Index provider rows include `countryCodes`, so Kafka/Redis/index storage can be configured for Kuwait only (`KW`) or all countries (`ALL`). Settings-managed connector secrets are encrypted server-side and masked in API responses.
- **Important files:**
  - `src/main/java/org/kfh/aiops/platform/config/SettingsService.java`
  - `src/main/java/org/kfh/aiops/platform/config/JdbcSettingsMetadataStore.java`
  - `src/main/resources/db/migration/V12__country_environment_scoped_integration_settings.sql`
  - `src/main/resources/static/pages/settings/settings.js`
  - `src/test/java/org/kfh/aiops/platform/config/SettingsServiceTest.java`
  - `src/test/java/org/kfh/aiops/commandcenter/SettingsStaticUiTest.java`
  - `docs/API_CONTRACTS.md`
  - `docs/DATABASE_SCHEMA.md`
  - `docs/RUNBOOKS.md`
  - `docs/UI_PAGES.md`
  - `docs/SECURITY.md`
  - `docs/SERVICES_SUPPORT.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Tenant/user context and `SETTINGS_WRITE` remain enforced. Provider secrets are stored only as encrypted `apiKeySecret`, `passwordSecret`, or `secretSecret` values and are stripped from API responses; masked Test Connection requests decrypt saved secrets only server-side.
- **Tests:** `node --check src/main/resources/static/pages/settings/settings.js`, targeted `SettingsStaticUiTest,SettingsServiceTest,DefaultInfrastructureConnectionTesterTest`, `./mvnw.cmd -q -DskipTests package`, and full `./mvnw.cmd -q verify` passed.

### 2026-06-29 — Add provider Test Connection for Kafka, Neo4j, and Index Storage
- **Summary:** Settings provider popups and rows now support audited **Test Connection** for Kafka, Redis, custom index storage, and Neo4j. Popup tests use the unsaved draft payload without persisting it; saved row tests keep the existing row-level action.
- **Important files:**
  - `pom.xml`
  - `src/main/java/org/kfh/aiops/platform/config/InfrastructureConnectionTester.java`
  - `src/main/java/org/kfh/aiops/platform/config/DefaultInfrastructureConnectionTester.java`
  - `src/main/java/org/kfh/aiops/platform/config/SettingsService.java`
  - `src/main/resources/static/pages/settings/settings.js`
  - `src/test/java/org/kfh/aiops/platform/config/DefaultInfrastructureConnectionTesterTest.java`
  - `src/test/java/org/kfh/aiops/platform/config/SettingsServiceTest.java`
  - `src/test/java/org/kfh/aiops/commandcenter/SettingsStaticUiTest.java`
  - `docs/API_CONTRACTS.md`
  - `docs/RUNBOOKS.md`
  - `docs/UI_PAGES.md`
  - `docs/SECURITY.md`
  - `docs/SERVICES_SUPPORT.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Redis/Kafka tests block URL syntax, credential-bearing endpoint strings, localhost, loopback, link-local, multicast, and metadata targets before live probes. Local/NFS index-storage tests require absolute non-traversal paths; cloud storage is pointer-validation only in this phase. Secrets remain masked and omitted from audit/test output.
- **Tests:** `node --check src/main/resources/static/pages/settings/settings.js`, `./mvnw.cmd -q "-Dtest=SettingsStaticUiTest,SettingsServiceTest,DefaultInfrastructureConnectionTesterTest" test`, `./mvnw.cmd -q -DskipTests package`, and full `./mvnw.cmd -q verify` passed.

### 2026-06-29 — Add type-specific Servers & Index popup fields
- **Summary:** The Settings **Servers & Index** Add/Edit popup now changes fields based on the selected type: Redis host/port/ACL/password/DB/TLS, Kafka bootstrap/security/SASL/client/truststore fields, and custom index storage provider/path/bucket/region/access credentials.
- **Important files:**
  - `src/main/resources/static/pages/settings/settings.js`
  - `src/main/resources/static/pages/settings/settings.css`
  - `src/test/java/org/kfh/aiops/commandcenter/SettingsStaticUiTest.java`
  - `docs/API_CONTRACTS.md`
  - `docs/RUNBOOKS.md`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Frontend metadata-entry change only; secrets remain masked/sanitized by the existing Settings contract, and no new live outbound Redis/Kafka/index test call was added.
- **Tests:** `node --check src/main/resources/static/pages/settings/settings.js` and `./mvnw.cmd -q "-Dtest=SettingsStaticUiTest" test` passed.

### 2026-06-29 — Stabilize Settings cards during test/update actions
- **Summary:** Settings cards and connector rows now remain visually static while operators click **Test Connection**, **Test Only**, **Test & Save**, **Update**, or while auto-save re-renders. Status/result areas reserve space, database action buttons use stable sizing/wrapping, and Settings-scoped vertical hover/animation transforms are disabled to prevent up/down movement or vibration.
- **Important files:**
  - `src/main/resources/static/pages/settings/settings.js`
  - `src/main/resources/static/pages/settings/settings.css`
  - `src/test/java/org/kfh/aiops/commandcenter/SettingsStaticUiTest.java`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Frontend-only layout-stability change; tenant context, RBAC, audit, secret handling, SSRF validation, and API contracts remain unchanged.
- **Tests:** `node --check src/main/resources/static/pages/settings/settings.js` and `./mvnw.cmd -q "-Dtest=SettingsStaticUiTest" test` passed.

### 2026-06-29 — Harden Settings popup backdrop click handling
- **Summary:** Settings Add/Edit popups now explicitly absorb backdrop/outside clicks with `Settings.keepModalOpen(event)`, so clicks outside database/Neo4j/AI/Teams/SharePoint/infrastructure forms cannot bubble into parent/global handlers and close the dialog. Popups still close through explicit controls such as **Cancel**, the modal **X**, or successful save/update actions.
- **Important files:**
  - `src/main/resources/static/pages/settings/settings.js`
  - `src/test/java/org/kfh/aiops/commandcenter/SettingsStaticUiTest.java`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Frontend-only dismissal-path hardening; existing tenant context, RBAC, audit, secret handling, SSRF validation, and API contracts remain unchanged.
- **Tests:** `node --check src/main/resources/static/pages/settings/settings.js` and `./mvnw.cmd -q "-Dtest=SettingsStaticUiTest" test` passed.

### 2026-06-29 — Prevent Settings popup auto-close on backdrop interaction
- **Summary:** Settings Add/Edit popups no longer close when operators click the blurred page backdrop. The settings dialogs now require explicit close controls such as **Cancel** or the modal **X**, reducing accidental dismissal while editing tenant/country-scoped configuration.
- **Important files:**
  - `src/main/resources/static/pages/settings/settings.js`
  - `src/test/java/org/kfh/aiops/commandcenter/SettingsStaticUiTest.java`
  - `docs/RUNBOOKS.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Frontend-only dismissal-path change; existing tenant context, RBAC, audit, secret handling, and SSRF protections remain unchanged.
- **Tests:** Targeted `SettingsStaticUiTest` update added; edited file diagnostics passed with no new errors.

### 2026-06-21 — Fix connector secret key-mismatch recovery
- **Summary:** Fixed connector Save/Test recovery after a new `KFH_AIOPS_SECRET_KEY` was introduced. Secret rotation now preserves existing encrypted secret entries without decrypting them, connector live tests return a clear failed result when old saved credentials cannot be decrypted with the current master key, and the Connectors UI marks affected credentials as needing re-entry.
- **Important files:**
  - `src/main/java/org/kfh/aiops/plugin/service/JdbcConnectorPersistenceStore.java`
  - `src/main/java/org/kfh/aiops/plugin/service/ConnectorService.java`
  - `src/main/java/org/kfh/aiops/platform/security/SecretCipherService.java`
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `src/test/java/org/kfh/aiops/plugin/service/JdbcConnectorPersistenceStoreTest.java`
  - `src/test/java/org/kfh/aiops/commandcenter/CommandCenterBackendServiceTest.java`
  - `src/test/java/org/kfh/aiops/commandcenter/ConnectorStaticUiTest.java`
  - `src/test/java/org/kfh/aiops/platform/security/SecretCipherServiceTest.java`
- **Security notes:** No plaintext secrets are returned or logged. Operators must restore the original stable key or re-enter affected connector credentials; tests now fail closed with actionable key-mismatch guidance.
- **Tests:** `node --check`, targeted secret/connector/UI tests, and full `./mvnw.cmd -q verify` passed.

### 2026-06-21 — Add deployment secret-file fallback for connector master key
- **Summary:** Fixed connector Save/Test failures where `KFH_AIOPS_SECRET_KEY` was not visible to the running backend JVM by allowing `SecretCipherService` to also read a protected deployment secret file (`KFH_AIOPS_SECRET_KEY_FILE` / `kfh.security.master-key-file`, with a local default under user-home `.kfh-aiops/secret-key.txt`). Settings now reports a safe key source label without returning key material.
- **Important files:**
  - `src/main/java/org/kfh/aiops/platform/security/SecretCipherService.java`
  - `src/main/java/org/kfh/aiops/platform/config/SettingsService.java`
  - `src/main/resources/application.properties`
  - `scripts/run-with-database.ps1`
  - `scripts/run-local-https.ps1`
  - `src/test/java/org/kfh/aiops/platform/security/SecretCipherServiceTest.java`
  - `docs/RUNBOOKS.md`
  - `docs/API_CONTRACTS.md`
  - `docs/SECURITY.md`
- **Security notes:** No default key was added. The key remains deployment-owned, stable, and secret; the API exposes only boolean/source diagnostics and never logs or returns the key or file content.
- **Tests:** Targeted secret/settings/connector tests passed; PowerShell startup helpers parsed successfully.

### 2026-06-21 — Implement EMCO Ping Monitor connector
- **Summary:** Enabled EMCO Ping Monitor as a SQL Server-backed connector with catalog metadata, DB allowlist migration, server-side validation, encrypted KFH/CCTV SQL credentials, secret-safe JDBC readiness probes, and Command Center UI configuration/test support.
- **Important files:**
  - `pom.xml`
  - `src/main/java/org/kfh/aiops/plugin/implementations/emco/EmcoConnectorConfigValidator.java`
  - `src/main/java/org/kfh/aiops/plugin/implementations/emco/JdbcEmcoConnectorLiveTester.java`
  - `src/main/java/org/kfh/aiops/plugin/service/ConnectorCatalogService.java`
  - `src/main/java/org/kfh/aiops/plugin/service/ConnectorService.java`
  - `src/main/resources/db/migration/V10__allow_emco_connector_type.sql`
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/test/java/org/kfh/aiops/plugin/implementations/emco/EmcoConnectorConfigValidatorTest.java`
  - `src/test/java/org/kfh/aiops/plugin/implementations/emco/JdbcEmcoConnectorLiveTesterTest.java`
  - `docs/PROGRESS-003.md`
- **Security notes:** EMCO rejects unsafe endpoint strings and blocked literal hosts, validates database names/timeouts, stores KFH/CCTV credentials write-only/encrypted, and returns sanitized readiness steps without raw SQL rows or credential values.
- **Tests:** `node --check` passed for `connectors.js`; targeted EMCO/connector UI/backend tests passed; full `./mvnw.cmd -q verify` passed.

### 2026-06-21 — Add explicit SCOM certificate-validation bypass option
- **Summary:** Added an explicit SCOM UI option, **Disable certificate validation for this SCOM test**, that maps to `verifySsl=false` while keeping HTTPS/5986. The SCOM probe then uses `-SkipCACheck`, `-SkipCNCheck`, and `-SkipRevocationCheck`, matching the successful manual PowerShell remoting test path.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/test/java/org/kfh/aiops/commandcenter/ConnectorStaticUiTest.java`
  - `src/test/java/org/kfh/aiops/plugin/implementations/scom/PowerShellScomConnectorLiveTesterTest.java`
  - `docs/API_CONTRACTS.md`
  - `docs/RUNBOOKS.md`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Verification remains enabled by default. The bypass is explicit and temporary for governed dev/hybrid tests; HTTPS transport, tenant/country scope, RBAC, audit, secret redaction, and SSRF controls remain enforced.
- **Tests:** `node --check` passed for `connectors.js`; targeted connector UI/backend SCOM tests passed.

### 2026-06-21 — Clarify SCOM 5986 cross-domain TLS path
- **Summary:** Clarified that `UTVDISAP01.KFHTesting.local` can connect to the single corporate SCOM endpoint over WinRM HTTPS/5986 without opening HTTP/5985, provided the SCOM WinRM certificate is renewed, trusted by the dev connector host, and issued for the configured FQDN/SAN. TLS enrichment is now idempotent so expired-certificate guidance is not duplicated.
- **Important files:**
  - `src/main/java/org/kfh/aiops/plugin/security/ConnectorTlsSupport.java`
  - `src/test/java/org/kfh/aiops/plugin/implementations/scom/PowerShellScomConnectorLiveTesterTest.java`
  - `docs/RUNBOOKS.md`
  - `docs/SECURITY.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** No TLS/SSRF controls were relaxed and HTTP/5985 is not required. The runbook directs renewal/rebind and CA trust for HTTPS/5986.
- **Tests:** Focused SCOM live-tester test passed.

### 2026-06-21 — Explain SCOM WinRM expired certificate failures
- **Summary:** Added explicit connector TLS enrichment for SCOM/WinRM failures where PowerShell reports `The SSL certificate is expired`, making it clear the HTTPS listener certificate on the destination server must be renewed/rebound before remoting can continue.
- **Important files:**
  - `src/main/java/org/kfh/aiops/plugin/security/ConnectorTlsSupport.java`
  - `src/test/java/org/kfh/aiops/plugin/implementations/scom/PowerShellScomConnectorLiveTesterTest.java`
  - `docs/RUNBOOKS.md`
  - `docs/SECURITY.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** No TLS/SSRF controls were relaxed. The guidance says certificate expiry requires renewal; HTTP/5985 is documented only as an explicitly approved environment exception.
- **Tests:** Targeted SCOM live-tester test passed.

### 2026-06-21 — Harden SCOM cross-domain credential probe
- **Summary:** Updated the SCOM connector live-test probe for testing-domain-to-corporate-domain scenarios by precomputing the qualified Windows principal in Java and passing only `KFH_AIOPS_SCOM_QUALIFIED_USERNAME` to PowerShell. The script no longer declares `$domain` or `$username`, so it cannot fail with the previous `$domain$username` parser error.
- **Important files:**
  - `src/main/java/org/kfh/aiops/plugin/implementations/scom/PowerShellScomConnectorLiveTester.java`
  - `src/test/java/org/kfh/aiops/plugin/implementations/scom/PowerShellScomConnectorLiveTesterTest.java`
  - `docs/SERVICES_CORE.md`
  - `docs/RUNBOOKS.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Credential handling remains secret-safe through child-process environment variables; redaction now covers access-key/access-secret labels and no SSRF/TLS controls were relaxed.
- **Tests:** Targeted SCOM/connector Maven tests passed; local PowerShell parser smoke test passed for the revised credential preamble.

### 2026-06-21 — Fix SCOM PowerShell username parser error
- **Summary:** Corrected the SCOM connector live-test PowerShell probe so domain-qualified credentials are built with `[string]::Concat($domain, [char]92, $username)` instead of a parser-fragile `$domain\$username` token. This resolves the reported `Unexpected token '\$username'` failure before WinRM validation begins.
- **Important files:**
  - `src/main/java/org/kfh/aiops/plugin/implementations/scom/PowerShellScomConnectorLiveTester.java`
  - `src/test/java/org/kfh/aiops/plugin/implementations/scom/PowerShellScomConnectorLiveTesterTest.java`
  - `docs/SERVICES_CORE.md`
  - `docs/RUNBOOKS.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Credential handling remains secret-safe through child-process environment variables; live-test output stays sanitized and no SSRF/TLS controls were relaxed.
- **Tests:** Targeted SCOM/connector Maven tests passed; local PowerShell parser smoke test passed for the updated username qualification expression.

### 2026-06-21 — Enable Microsoft SCOM connector
- **Summary:** Enabled SCOM in the Connector Marketplace and backend connector service with pending install support, WinRM/PowerShell configuration, encrypted username/password handling, endpoint/SSRF validation, and a bounded live test that probes `OperationsManager`/`Get-SCOMAlert` without returning raw alert payloads.
- **Important files:**
  - `src/main/java/org/kfh/aiops/plugin/implementations/scom/ScomConnectorConfigValidator.java`
  - `src/main/java/org/kfh/aiops/plugin/implementations/scom/PowerShellScomConnectorLiveTester.java`
  - `src/main/java/org/kfh/aiops/plugin/service/ConnectorCatalogService.java`
  - `src/main/java/org/kfh/aiops/plugin/service/ConnectorService.java`
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `docs/API_CONTRACTS.md`
  - `docs/RUNBOOKS.md`
  - `docs/SECURITY.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** SCOM credentials remain write-only/encrypted and are passed to PowerShell only through child-process environment variables. Live-test output is sanitized; localhost/metadata/link-local/loopback/multicast endpoints and unsafe WinRM paths are rejected.
- **Tests:** Targeted SCOM validator/live-tester, connector UI static, and backend connector service tests passed; backend compile, full `./mvnw.cmd -q verify`, and `node --check` passed.

### 2026-06-21 — Fix AppDynamics verifySsl=false save and test path
- **Summary:** Hardened the Connectors UI boolean normalization so AppDynamics `verifySsl=false` remains unchecked after backend reload and is submitted before Test Connection. Added backend coverage proving saved AppDynamics connectors pass `verifySsl=false` into the live tester, allowing governed dev/hybrid tests to bypass certificate-chain validation while HTTPS and SSRF controls remain enforced.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/test/java/org/kfh/aiops/commandcenter/ConnectorStaticUiTest.java`
  - `src/test/java/org/kfh/aiops/commandcenter/CommandCenterBackendServiceTest.java`
  - `docs/RUNBOOKS.md`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** No blanket TLS disablement was added. `verifySsl=false` remains explicit per connector; HTTPS transport, tenant/country scope, RBAC, audit, secret redaction, and SSRF blocks remain enforced.
- **Tests:** Targeted Maven connector/UI/AppDynamics tests passed; `node --check` passed for `connectors.js`.

### 2026-06-21 — Correct Graphify-style task prompt template
- **Summary:** Reworded the active `task` prompt and `.github/INDEX.md` recommended template into a clearer Graphify-style workflow with explicit always-load order, impacted graph-node identification, platform invariants, validation, and authoritative docs update routing.
- **Important files:**
  - `task`
  - `.github/INDEX.md`
  - `docs/AI_CODING_ASSISTANT_KNOWLEDGE_GRAPH.md`
  - `.github/PROGRESS.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Docs-only prompt update. The prompt explicitly preserves OWASP A01-A10, tenant/country isolation, service-layer RBAC, audit, safe logging, SSRF, no-secrets guidance, deterministic lifecycle, and custom-index-only telemetry search.
- **Tests:** Markdown/prompt diagnostics and content validation passed for changed files.

### 2026-06-21 — Consolidate Graphify docs routing and remove redundant docs
- **Summary:** Made `docs/AI_CODING_ASSISTANT_KNOWLEDGE_GRAPH.md` the consolidated Graphify-style AI assistant instruction map, updated `.github/INDEX.md` and `task` to route agents through it first, and removed redundant/generated planning docs from `docs/` while preserving authoritative contracts, schema, security, runbooks, service catalogs, and progress history.
- **Important files:**
  - `docs/AI_CODING_ASSISTANT_KNOWLEDGE_GRAPH.md`
  - `.github/INDEX.md`
  - `.github/PROGRESS.md`
  - `task`
  - `docs/PROGRESS-003.md`
- **Security notes:** Docs-only routing/cleanup. OWASP, tenant/country isolation, RBAC, audit, safe logging, SSRF, and no-secrets rules remain explicit in Graphify and prompt guidance.
- **Tests:** Markdown diagnostics and link/reference validation passed for the changed docs and prompt files.

### 2026-06-18 — Allow private connector endpoints without allowlist requirement
- **Summary:** Corrected the hybrid connector policy so valid private/internal BMC, AppDynamics, and vROps endpoints are accepted by default. The guard now blocks only unsafe SSRF targets such as metadata, localhost, loopback, link-local, and multicast addresses, removing the earlier `KFH_SSRF_ALLOWLIST` requirement for normal private connector IPs.
- **Important files:**
  - `src/main/java/org/kfh/aiops/plugin/security/ConnectorEndpointGuard.java`
  - `src/main/java/org/kfh/aiops/plugin/implementations/vrops/VropsConnectorConfigValidator.java`
  - `src/main/java/org/kfh/aiops/plugin/implementations/vrops/HttpVropsConnectorLiveTester.java`
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/test/java/org/kfh/aiops/plugin/implementations/appdynamics/AppDynamicsConnectorConfigValidatorTest.java`
  - `src/test/java/org/kfh/aiops/plugin/implementations/bmc/BmcConnectorConfigValidatorTest.java`
  - `src/test/java/org/kfh/aiops/plugin/implementations/vrops/VropsConnectorConfigValidatorTest.java`
  - `docs/API_CONTRACTS.md`
  - `docs/RUNBOOKS.md`
  - `docs/SERVICES_CORE.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** This allows public/private enterprise connector endpoints but still denies metadata, localhost, loopback, link-local, and multicast SSRF targets.
- **Tests:** Targeted connector policy tests passed; `node --check` passed for `connectors.js`; full `./mvnw.cmd test` passed with 151 tests, 0 failures.
- **Follow-up:** Restart the backend so the corrected connector guard is loaded, then run AppDynamics **Test** again.

### 2026-06-18 — Allow explicit hybrid connector endpoints
- **Summary:** BMC and AppDynamics endpoint validation now supports private/internal hybrid hostnames and IPs when explicitly approved through `KFH_SSRF_ALLOWLIST` / `kfh.security.ssrf.allowed-host-suffixes`, matching the existing vROps hybrid behavior. Metadata, localhost, and link-local targets remain blocked for SSRF protection.
- **Important files:**
  - `src/main/java/org/kfh/aiops/plugin/security/ConnectorEndpointGuard.java`
  - `src/main/java/org/kfh/aiops/plugin/implementations/appdynamics/AppDynamicsConnectorConfigValidator.java`
  - `src/main/java/org/kfh/aiops/plugin/implementations/appdynamics/HttpAppDynamicsConnectorLiveTester.java`
  - `src/main/java/org/kfh/aiops/plugin/implementations/bmc/BmcConnectorConfigValidator.java`
  - `src/main/java/org/kfh/aiops/plugin/implementations/bmc/HttpBmcConnectorLiveTester.java`
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/test/java/org/kfh/aiops/plugin/implementations/bmc/BmcConnectorConfigValidatorTest.java`
  - `docs/API_CONTRACTS.md`
  - `docs/RUNBOOKS.md`
  - `docs/SERVICES_CORE.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** This is not a blanket SSRF bypass; private/internal endpoints require explicit allowlist entries and metadata/localhost/link-local remain denied.
- **Tests:** Targeted Maven connector validator/live-test/static UI tests passed; `node --check` passed for `connectors.js`; full `./mvnw.cmd test` passed with 154 tests, 0 failures.
- **Follow-up:** Configure the runtime `KFH_SSRF_ALLOWLIST` with approved internal connector hostnames/suffixes/exact IPs and restart the backend before retesting.

### 2026-06-18 — Surface connector and notification test failure messages
- **Summary:** AppDynamics connector live-test failures now include compact redacted Java/HTTP details, including sanitized controller response text when available. Connector heartbeat and Settings/notification test failures now show the backend failure message in the UI instead of only generic “Test Failed” feedback.
- **Important files:**
  - `src/main/java/org/kfh/aiops/plugin/implementations/appdynamics/HttpAppDynamicsConnectorLiveTester.java`
  - `src/main/java/org/kfh/aiops/plugin/service/ConnectorService.java`
  - `src/main/java/org/kfh/aiops/platform/config/SettingsService.java`
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/settings/settings.js`
  - `src/test/java/org/kfh/aiops/plugin/implementations/appdynamics/HttpAppDynamicsConnectorLiveTesterTest.java`
  - `src/test/java/org/kfh/aiops/platform/config/SettingsServiceTest.java`
  - `src/test/java/org/kfh/aiops/commandcenter/SettingsStaticUiTest.java`
  - `docs/API_CONTRACTS.md`
  - `docs/RUNBOOKS.md`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Failure messages are redacted for passwords, usernames, API keys, webhook URLs, tokens, authorization headers, and credential terms; no SSRF controls were relaxed and no plaintext secrets are returned.
- **Tests:** Targeted connector/settings tests passed; `node --check` passed for edited connector/settings JS; full `./mvnw.cmd test` passed with 146 tests, 0 failures.
- **Follow-up:** Implement a dedicated Teams notification adapter before sending real Settings test notifications; current Teams test response explicitly says no webhook call was sent.

### 2026-06-18 — Modern connector drawer header and lower feedback
- **Summary:** Connector detail drawer now has a compact modern identity header, status-aware icon, accessible close button, and slim save/test/update feedback strip positioned lower in the drawer. Global fallback toasts are slimmer and bottom-right.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `src/test/java/org/kfh/aiops/commandcenter/ConnectorStaticUiTest.java`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Frontend rendering/styling only; message text is escaped and no API, tenant/RBAC, connector secret, SSRF, audit, or lifecycle behavior changed.
- **Tests:** `node --check src/main/resources/static/pages/connectors/connectors.js` passed; `./mvnw.cmd -q "-Dtest=ConnectorStaticUiTest" test` passed; scoped `git --no-pager diff --check -- <changed-files>` passed.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) to load updated CSS/JS and verify drawer feedback placement.

### 2026-06-18 — Fully grey out disabled connector cards
- **Summary:** Disabled connector cards no longer keep green-tinted card/body sections when a connector is paused. The card visual class, lag color, header divider, detail rows, stats panel, and scope/country chips now use neutral grey when collection is disabled.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `src/test/java/org/kfh/aiops/commandcenter/ConnectorStaticUiTest.java`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Frontend styling/rendering fix only; no API, tenant/RBAC, connector secret, SSRF, audit, or lifecycle behavior changed.
- **Tests:** `node --check src/main/resources/static/pages/connectors/connectors.js` passed; `./mvnw.cmd -q "-Dtest=ConnectorStaticUiTest" test` passed; scoped `git --no-pager diff --check -- <changed-files>` passed.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) to load updated CSS/JS.

### 2026-06-18 — Neutral grey disabled connector styling
- **Summary:** Disabled connector enablement visuals now use a polished neutral grey palette instead of amber/orange, so disabled/paused collection is not confused with warning/degraded states.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `src/test/java/org/kfh/aiops/commandcenter/ConnectorStaticUiTest.java`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-003.md`
- **Security notes:** Frontend styling/test/doc update only; no API, tenant/RBAC, connector secret, SSRF, audit, or lifecycle behavior changed.
- **Tests:** `./mvnw.cmd -q "-Dtest=ConnectorStaticUiTest" test` passed; scoped `git --no-pager diff --check -- <changed-files>` passed. Full diff check is blocked by an unrelated existing `connector.txt` blank-line-at-EOF issue.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) to load the updated CSS.

### 2026-06-17 — Fix connector marketplace country handler ReferenceError
- **Summary:** Fixed the Connectors route crash caused by exporting `setMarketplaceCountry` without defining it. Marketplace install country state is now implemented and used for BMC install/uninstall scope lookup.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `docs/PROGRESS-002.md`
- **Security notes:** Frontend runtime fix only; backend `CountryAccessGuard` remains authoritative for country/RBAC enforcement and BMC URL/secret validation is unchanged.
- **Tests:** `node --check src/main/resources/static/pages/connectors/connectors.js` passed; IDE diagnostics reported no errors for changed JS/CSS.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and verify the marketplace page loads without `setMarketplaceCountry` errors.

### 2026-06-17 — Add marketplace install country selection
- **Summary:** Global/all-country admins can now choose the physical country (`KW`, `BH`, `EG`) on the connector marketplace detail page before installing BMC. Country admins remain locked to their signed-in country.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-002.md`
- **Security notes:** Frontend selector only; backend `CountryAccessGuard` remains authoritative for cross-country enforcement.
- **Tests:** IDE diagnostics and `node --check src/main/resources/static/pages/connectors/connectors.js` reported no syntax errors.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and verify global admin can select country while country admin cannot.

### 2026-06-17 — Modernize Connector Marketplace and inventory UI
- **Summary:** Refreshed Connectors and Connector Marketplace with a more modern enterprise look: gradient hero cards, marketplace stats, category pills, improved connector product cards, richer detail page hero/actions, capability/security cards, and responsive layouts.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-002.md`
- **Security notes:** Visual-only frontend polish; marketplace displays metadata only and BMC configuration still uses existing secret-safe backend validation.
- **Tests:** IDE diagnostics and `node --check src/main/resources/static/pages/connectors/connectors.js` reported no syntax errors.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and visually confirm the Marketplace/product-detail pages.

### 2026-06-17 — Add full Connector Marketplace page
- **Summary:** The Connectors primary action now opens an in-page marketplace instead of an Add Connector modal. Each connector card opens a full connector details page with overview, capabilities, governance, installation scope, and BMC Install/Configure/Uninstall actions.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-002.md`
- **Security notes:** Marketplace displays connector metadata only; BMC configuration still goes through existing secret-safe backend validation.
- **Tests:** IDE diagnostics on connector JS/CSS show no syntax errors; only existing non-blocking warnings remain.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and click **Connector Marketplace**.

### 2026-06-17 — Convert Add Connector to install-first catalog
- **Summary:** Add Connector now opens a catalog where BMC Helix can be installed first. Installed BMC connectors appear on the Connectors page as disabled/pending and open the configuration drawer for required BMC connection details; installed connectors can also be uninstalled from the catalog.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `src/main/java/org/kfh/aiops/plugin/implementations/bmc/BmcConnectorConfigValidator.java`
  - `src/test/java/org/kfh/aiops/commandcenter/CommandCenterBackendServiceTest.java`
  - `docs/PROGRESS-002.md`
- **Security notes:** Install-only BMC placeholders require no secrets; actual connection details remain validated server-side and secrets are still stripped from responses.
- **Tests:** Targeted `mvnw.cmd -Dtest=CommandCenterBackendServiceTest test` passed with 21 tests, 0 failures.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and use Add Connector → Install BMC Helix → Configure.

### 2026-06-17 — Fix Add Connector modal host null error
- **Summary:** `connectors.js` now creates the connector modal and drawer host elements when the SPA router loads only the JS module, fixing `Cannot read properties of null (reading 'classList')` after clicking **Add Connector**.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `docs/PROGRESS-002.md`
- **Security notes:** Static DOM-host fix only; BMC secret handling and backend SSRF validation unchanged.
- **Tests:** IDE diagnostics on `connectors.js` show no syntax errors; only non-blocking warnings remain.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and click **Add Connector** again.

### 2026-06-17 — Remove dummy SolarWinds connector seed
- **Summary:** Removed the seeded `SolarWinds KW` connector so the Connectors page starts empty until a real BMC connector is added through the Add Connector form.
- **Important files:**
  - `src/main/java/org/kfh/aiops/commandcenter/support/CommandCenterReadModel.java`
  - `src/test/java/org/kfh/aiops/commandcenter/CommandCenterBackendServiceTest.java`
  - `docs/PROGRESS-002.md`
- **Security notes:** Removed demo data only; BMC secret handling and SSRF validation remain unchanged.
- **Tests:** Targeted `mvnw.cmd -Dtest=CommandCenterBackendServiceTest test` passed with 19 tests, 0 failures.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) to clear the old seeded card from browser cache.

### 2026-06-17 — Fix Connectors page JavaScript parse error
- **Summary:** Removed the stray `deleteConnector,` token left outside the Connectors module and restored the BMC add/save/remove handlers inside `connectors.js`, resolving the browser `Unexpected end of input` error when navigating to `#connectors`.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `docs/PROGRESS-002.md`
- **Security notes:** Static JS parse fix only; BMC secrets remain submitted only via `secretsPlain` and stripped by backend responses.
- **Tests:** IDE diagnostics on `connectors.js` show no syntax errors; only an existing simplification warning remains.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) to clear the broken cached script.

### 2026-06-17 — Start BMC Connector add/configuration flow
- **Summary:** Connectors now has a type-card Add Connector window backed by `GET /api/v1/connectors/types`. BMC Helix is enabled first with country/environment-specific settings, access-key credential fields submitted as `secretsPlain`, and backend validation for BMC HTTPS base URL, safe endpoints, bounded collection settings, tenant/country scope, and secret stripping.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `src/main/java/org/kfh/aiops/plugin/service/ConnectorService.java`
  - `src/main/java/org/kfh/aiops/plugin/service/ConnectorCatalogService.java`
  - `src/main/java/org/kfh/aiops/plugin/implementations/bmc/BmcConnectorConfigValidator.java`
  - `src/test/java/org/kfh/aiops/commandcenter/CommandCenterBackendServiceTest.java`
  - `docs/PROGRESS-002.md`
- **Security notes:** BMC connector creation is physical-country scoped, cross-country writes are guarded, URL/path validation blocks SSRF-risk inputs, and access keys/secrets are never returned.
- **Tests:** Targeted `CommandCenterBackendServiceTest` passed with 18 tests, 0 failures. Full `mvnw.cmd test` passed with 91 tests, 0 failures.
- **Follow-up:** Implement live BMC plugin execution/persistence: encrypted `config.connector_secrets`, BMC login/msearch pagination, canonical telemetry normalization, raw archive refs, connector run logs, and scheduler/outbox worker.

### 2026-06-15 — Replace Connectors dropdowns with smart filter
- **Summary:** Removed the visible Refresh action from the Connectors header and replaced separate status/type/scope dropdowns with one smart filter popover using grouped pill chips for Status, Type, and Scope.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-002.md`
- **Security notes:** Static UI filter refinement only; no API, tenant/RBAC, connector secret, logging, or outbound URL behavior changed.
- **Tests:** `get_errors` showed only existing style/lint warnings; `mvnw.cmd -DskipTests package` passed; full `mvnw.cmd test` passed with 87 tests, 0 failures.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and visually confirm the smart filter popover.

### 2026-06-15 — Flatten Connectors header into one row
- **Summary:** Connectors header now renders the title, Refresh, search, status/type/scope filters, Table/Cards toggle, and Add Connector in one horizontal desktop row instead of splitting controls across two header lines.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-002.md`
- **Security notes:** Static UI layout refinement only; no API, tenant/RBAC, connector secret, logging, or outbound URL behavior changed.
- **Tests:** `get_errors` showed only existing style/lint warnings; `mvnw.cmd -DskipTests package` passed; full `mvnw.cmd test` passed with 87 tests, 0 failures.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and visually confirm the one-line header.

### 2026-06-15 — Remove Connectors Degraded KPI card
- **Summary:** Removed the Degraded KPI from the Connectors top KPI strip and changed the KPI grid to five columns while keeping Degraded status filtering/badges available for connector health states.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-002.md`
- **Security notes:** Static UI KPI refinement only; no API, tenant/RBAC, connector secret, logging, or outbound URL behavior changed.
- **Tests:** `get_errors` showed only existing style/lint warnings; `mvnw.cmd -DskipTests package` passed; full `mvnw.cmd test` passed with 87 tests, 0 failures.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and visually confirm the KPI strip has five cards.

### 2026-06-15 — Align Connectors header controls in one row
- **Summary:** Moved Add Connector into the same header row as search, status/type/scope filters, and table/card toggle; removed the separate below-KPI Add Connector row.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-002.md`
- **Security notes:** Static UI layout refinement only; no API, tenant/RBAC, connector secret, logging, or outbound URL behavior changed.
- **Tests:** `get_errors` showed only existing style/lint warnings; `mvnw.cmd -DskipTests package` passed; full `mvnw.cmd test` passed with 87 tests, 0 failures.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and visually confirm Add Connector is next to the search/filter controls.

### 2026-06-15 — Move Connectors Add action below KPI strip
- **Summary:** Kept search, filters, and the table/card toggle inside the Connectors header, moved the single Add Connector action directly below the KPI cards, and removed duplicate Add buttons from the header/table/empty state.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-002.md`
- **Security notes:** Static UI layout refinement only; no API, tenant/RBAC, connector secret, logging, or outbound URL behavior changed.
- **Tests:** `get_errors` showed only existing style/lint warnings; `mvnw.cmd -DskipTests package` passed; full `mvnw.cmd test` passed with 87 tests, 0 failures.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and visually confirm the Add Connector placement.

### 2026-06-15 — Compact Connectors header and KPI cards
- **Summary:** Removed the Connectors header eyebrow, long subtitle, and health chips; moved search/filters/view toggle into the header; reduced header padding and KPI card height for a cleaner Audit/User Management-style layout.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-002.md`
- **Security notes:** Static UI layout refinement only; no API, tenant/RBAC, connector secret, logging, or outbound URL behavior changed.
- **Tests:** `get_errors` showed only existing style/lint warnings; `mvnw.cmd -DskipTests package` passed; full `mvnw.cmd test` passed with 87 tests, 0 failures.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and visually confirm the compact header in browser.

### 2026-06-15 — Redesign Connectors management page
- **Summary:** Connectors now uses the same modern management-page visual language as Audit Activity and User Management, including a compact header card, health chips, KPI strip, filter card, default table inventory, responsive card view, and polished action buttons.
- **Important files:**
  - `src/main/resources/static/pages/connectors/connectors.js`
  - `src/main/resources/static/pages/connectors/connectors.css`
  - `docs/UI_PAGES.md`
  - `docs/PROGRESS-002.md`
- **Security notes:** Static UI redesign only; no API, tenant/RBAC, connector secret, logging, or outbound URL behavior changed.
- **Tests:** `get_errors` showed only existing style/lint warnings; `mvnw.cmd -DskipTests package` passed; full `mvnw.cmd test` passed with 87 tests, 0 failures. Direct `node --check` was unavailable because Node.js is not installed in the current shell.
- **Follow-up:** Hard-refresh `index.html#connectors` (`Ctrl+F5`) and visually review in the browser.

### 2026-06-15 — Rename sidebar collapse toggle labels
- **Summary:** Sidebar collapse control now uses `Collapse in` for expanded state and `Collapse out` for collapsed state across visible text, title, and aria-label.
- **Important files:**
  - `src/main/resources/static/index.html`
  - `src/main/resources/static/shared/js/sidebar-collapse.js`
  - `docs/PROGRESS-002.md`
- **Security notes:** Static shell label-only change; no API, tenant/RBAC, secret, logging, or data handling behavior changed.
- **Tests:** `get_errors` showed no JS errors and only existing SPA hash-route/style warnings in `index.html`; `mvnw.cmd -DskipTests package` passed; full `mvnw.cmd test` passed with 87 tests, 0 failures.
- **Follow-up:** Hard-refresh open browser sessions (`Ctrl+F5`) if cached HTML/JS keeps old labels.

### 2026-06-15 — Remove collapsed sidebar Audit Logs artifact
- **Summary:** Removed an accidental literal `l` before the sidebar user profile markup that rendered as a small vertical artifact/gap below Audit Logs in collapsed mode.
- **Important files:**
  - `src/main/resources/static/index.html`
  - `docs/PROGRESS-002.md`
- **Security notes:** Static markup-only change; no API, tenant/RBAC, secret, logging, or data handling behavior changed.
- **Tests:** `get_errors` showed only existing SPA hash-route/style warnings; `mvnw.cmd -DskipTests package` passed; full `mvnw.cmd test` passed with 87 tests, 0 failures.
- **Follow-up:** Hard-refresh open browser sessions (`Ctrl+F5`) if cached HTML remains visible.

### 2026-06-15 — Remove collapsed sidebar icon padding gap
- **Summary:** Collapsed sidebar now applies an ID-specific compact width/flex override to `#sidebar-container`, preventing the expanded 253px fixed-width parity rule from leaving a wide icon-only menu column.
- **Important files:**
  - `src/main/resources/static/shared/css/kfh-aiops-parity.css`
  - `docs/PROGRESS-002.md`
- **Security notes:** Static CSS-only change; no API, tenant/RBAC, secret, logging, or data handling behavior changed.
- **Tests:** CSS diagnostics passed; `mvnw.cmd -DskipTests package` passed; full `mvnw.cmd test` passed with 87 tests, 0 failures.
- **Follow-up:** Hard-refresh open browser sessions (`Ctrl+F5`) if cached CSS keeps the old sidebar width.

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
