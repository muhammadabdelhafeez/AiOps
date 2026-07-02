# API Contracts (v1)

> **Architecture authority:** [`docs/CAUSAL_PIPELINE.md`](./CAUSAL_PIPELINE.md).
> Endpoints under `/api/v1/rca/**` and `/api/v1/ai/**` accept and return **only the `EvidencePack` / RCA result contracts** defined in CAUSAL_PIPELINE §6–§7. They never accept raw telemetry payloads. They never return secrets, tokens, credentials, or raw log lines. AI calls are dispatched asynchronously through the outbox (§4 OUTBOX); endpoints return `202 Accepted` + `correlationId` for AI-bound work.

## Global rules
### Runtime scope
- Full backend mode exposes the `/api/**` contracts below and requires tenant/user headers, RBAC enforcement, tenant-scoped persistence, and audit logging.
- User Management identity APIs are database-backed. PostgreSQL/JDBC/Flyway must be available at startup so `/api/v1/users` creates and reads persisted login users from `identity.users`.

### Required headers
- X-Tenant-Id: <tenant uuid>
- X-User-Id: <user uuid>
- X-Correlation-Id: <uuid/string safe for logs>
- X-Country-Code: KW|BH|EG for physical country scopes; `ALL` is accepted only for identity/admin all-country users with `*` or `COUNTRY_GLOBAL_VIEW`
- X-Environment: PROD|UAT|DEV (frontend sends from the selected environment)
- X-Permissions: comma-separated permission codes for the Phase 1 scaffold until enterprise SSO/JWT is wired

If missing: 400/401 (implementation choice) with standard error response.

### Authorization
- Permission-based RBAC enforced in service layer.
- Scope policies restrict data visibility by app/resource/env/tags.

### Authentication
- POST `/api/v1/auth/sign-in` does not require tenant headers because it resolves the persisted identity profile by username, password, country, and environment.
- Successful sign-in returns the user's `tenantId`, `userId`, `countryCode`, `environment`, role, and permission set. The frontend uses those values for subsequent tenant/country-aware API headers and stores the sign-in `countryCode` as the immutable home country for the browser session. `GLOBAL_ADMIN` users may sign in with `countryCode: "ALL"` when their identity profile is provisioned with all-country scope. `COUNTRY_ADMIN` users receive `AUDIT_READ` but not `COUNTRY_GLOBAL_VIEW`, so audit reads remain limited to their signed-in country/environment.
- In datasource-backed mode, User Management creates local identity users in PostgreSQL (`identity.users`, `identity.user_roles`, `identity.role_permissions`) with BCrypt password hashes. Passwords are never returned by APIs.
- Startup bootstrap ensures the configured tenant/default roles exist. When `KFH_BOOTSTRAP_ADMIN_PASSWORD` is supplied through environment/deployment configuration, it creates the configured bootstrap admin if missing or reactivates/resets that configured bootstrap admin if it already exists with a different stored password hash.
- The deprecated `local` profile no longer disables datasource/JDBC/Flyway; missing PostgreSQL should fail startup instead of serving memory-only login users.

### Pagination
- GET list endpoints support: page, size, sort
- time filtering: from, to (ISO-8601)

### Standard error shape
- code
- message
- correlationId
- details (optional)
- timestamp

## Module endpoints

### 2026-06-10 implementation note
- The static Command Center frontend contract in `src/main/resources/static/shared/js/api-client.js` is now backed by tenant-aware Spring controllers under `/api/v1/**`.
- The current implementation uses an in-memory command-center read model as a Phase 1 scaffold; it returns summarized incidents/alerts/resources/evidence references only and does **not** store raw telemetry in PostgreSQL or Neo4j.
- All endpoints require `X-Tenant-Id` and `X-User-Id` UUID headers through `TenantContext`; `X-Correlation-Id` is propagated when provided.
- Service methods enforce RBAC permission checks. Until enterprise identity/JWT is wired, `TenantContextResolver` grants scaffold requests `*` permissions unless an `X-Permissions` header is supplied.
- Write endpoints call `AuditService.recordWrite(...)`; the active adapter persists secret-safe activity rows to PostgreSQL `identity.audit_log` and also emits structured logs. The audit activity view is API-backed only and must not include seeded/demo audit rows. Authentication success/failure, bootstrap identity provisioning, settings update/test, and normal operator write actions append visible audit activity with secret-safe metadata only.
- The static shell supports a sidebar KFH Group country switcher only when the signed-in home country is `ALL` and the session has `COUNTRY_GLOBAL_VIEW` or `*`. Switching between `ALL`, `KW`, `BH`, and `EG` changes only the active country header; it preserves the authenticated `tenantId` and `userId` so all physical-country data remains under the same tenant boundary. Physical-country users see a locked Platform + assigned-country scope with no dropdown options for other countries; backend services still enforce country access and deny cross-country reads without `COUNTRY_GLOBAL_VIEW` or `*`.

### Auth
- Implemented path prefix: `/api/v1/auth`
- POST `/api/v1/auth/sign-in`
  - Request body:
    ```json
    {
      "username": "kw.operator",
      "password": "not-returned-or-logged",
      "countryCode": "KW",
      "environment": "PROD"
    }
    ```
  - Response body includes: `tenantId`, `userId`, `username`, `displayName`, `email`, `countryCode`, `countryName`, `countryGroupName`, `environment`, `roleId`, `userRole`, `permissions`, `authenticatedAt`.
  - Invalid credentials, disabled users, or wrong country/environment return an authorization error. All-country database identities can sign in with `countryCode: "ALL"`; when a physical country is submitted and no exact physical-country identity matches, the backend also checks an `ALL`-scoped identity row and returns `countryCode: "ALL"` on success. The configured bootstrap admin also accepts physical country selections when its configured country is `ALL`, but its successful response returns `countryCode: "ALL"` so the SPA opens the all-country command scope instead of locking to the selected physical country.
  - Passwords are checked against the stored BCrypt hash and are not returned.
  - Rejected sign-ins keep the client error generic, but backend logs include secret-safe bootstrap match flags plus database counters for username, country/environment scope, active status, and password-hash readiness to support operations troubleshooting. Logs never include submitted or configured passwords.
    - Sign-in first matches the request against the configured bootstrap admin (`kfh.identity.bootstrap.*`), then uses the mandatory database-backed lookup for persisted users.

### Users / Identity
- Implemented path prefix: `/api/v1/users`
- GET `/api/v1/users?page=&size=&country=&environment=`
- GET `/api/v1/users/{id}`
- POST `/api/v1/users`
  - Requires `IDENTITY_WRITE`.
  - Requires database-backed identity storage. The endpoint ensures the current request tenant exists in `public.tenants`, ensures default roles for that tenant, inserts a country-scoped user in `identity.users`, stores a BCrypt password hash, assigns one role in `identity.user_roles`, and returns a sanitized user row without password fields. If database identity infrastructure is unavailable, application startup should fail instead of creating a memory-only login user.
  - Request body uses the existing generic UI write shape:
    ```json
    {
      "name": "Fatima Al-Salem",
      "status": "Active",
      "attributes": {
        "username": "f.alsalem",
        "email": "fatima@example.invalid",
        "password": "not-returned-or-logged",
        "roleIds": ["ADMIN|OPERATOR|VIEWER"]
      }
    }
    ```
  - The UI exposes only `Admin`, `Operator`, and `Viewer`. The backend canonicalizes submitted role aliases by country scope: `Admin` + physical country becomes `COUNTRY_ADMIN`; `Admin` + `ALL` becomes `GLOBAL_ADMIN`; `Operator` becomes `NOC_OPERATOR`; `Viewer` becomes `VIEWER`.
    - User Management country filters and Create/Edit User country selectors are visible only for sessions whose home country is `ALL` and whose permissions include `COUNTRY_GLOBAL_VIEW` or `*`. Physical-country users see a read-only Platform + assigned-country label and cannot select or submit other countries from the UI.
  - Country and environment come from the tenant context headers, not from user payload fields. A global identity admin may use `X-Country-Code: ALL` to create an all-country sign-in user; service-layer authorization requires `*` or `COUNTRY_GLOBAL_VIEW` in addition to `IDENTITY_WRITE`.
  - Missing required identity fields such as `attributes.username` or `attributes.password` return a validation problem response. Duplicate usernames in the same tenant/country scope return `409 CONFLICT`; database integrity conflicts return a safe problem response and never include SQL details or password data. Framework status exceptions are preserved so expected operational failures do not collapse into generic `500 INTERNAL_ERROR` responses.
- PATCH `/api/v1/users/{id}/toggle`
- PUT `/api/v1/users/{id}`
  - Requires `IDENTITY_WRITE` and tenant/country access to the existing user. If `attributes.countryCode` changes the user's country group, the service also requires access to the requested destination country; moving a user to or from `ALL` requires `*` or `COUNTRY_GLOBAL_VIEW`.
  - Updates username, display name, email, active/disabled status, country group, and a single role assignment in PostgreSQL. The User Management edit form does not submit password fields; password changes use the dedicated `PATCH /api/v1/users/{id}/password` endpoint.
  - Role changes replace existing `identity.user_roles` rows for that user and then assign the submitted `attributes.roleIds[0]`, `attributes.roles[0]`, or `attributes.roleId`. Submitted UI role aliases are canonicalized against the destination country group, so `Admin` maps to `GLOBAL_ADMIN` for `ALL` and `COUNTRY_ADMIN` for physical countries.
- PATCH `/api/v1/users/{id}/password`
  - Requires `IDENTITY_WRITE` and tenant/country access to the existing user.
  - Request body uses the generic UI write shape with `attributes.password`. The password is BCrypt-hashed in `identity.users.password_hash`, is not logged, and is not returned in the response.
- DELETE `/api/v1/users/{id}`
- GET `/api/v1/users/roles`
- GET `/api/v1/users/{userId}/permissions`
- GET `/api/v1/users/storage-status`
  - Requires `X-Tenant-Id` and `X-User-Id` headers (resolved via `TenantContext`).
  - Returns `{"databaseBacked": true, "writesEnabled": true, "reason": "OK"}` when the application is running. The body intentionally contains no secrets, profile names, credentials, or environment variables.

### Dashboard
- Implemented path prefix: `/api/v1/dashboard`
- GET `/api/v1/dashboard/kpis?country=&environment=`
- GET `/api/v1/dashboard/trends?country=&environment=`
- GET `/api/v1/dashboard/top-apps?country=&environment=`
- GET `/api/v1/dashboard/summary`
- GET `/api/v1/dashboard/sources`

### Alerts
- Implemented path prefix: `/api/v1/alerts`
- GET `/api/v1/alerts?page=&size=&country=&environment=`
- GET `/api/v1/alerts/{id}`
- POST `/api/v1/alerts/acknowledge` with `{ "ids": [uuid] }`
- GET `/api/v1/alerts/activity`

### Alert Activity
- GET `/api/v1/alerts/activity`

### Incidents
- Implemented path prefix: `/api/v1/incidents`
- All endpoints require valid UUID `X-Tenant-Id` and `X-User-Id` headers.
- All ID-based reads/writes are tenant-scoped (`incidentId + tenantId`) to prevent IDOR.
- GET `/api/v1/incidents?page=&size=&country=&environment=`
- GET `/api/v1/incidents/{id}`
- POST `/api/v1/incidents`
- PUT `/api/v1/incidents/{id}`
- PATCH `/api/v1/incidents/{id}/status` with `{ "status": "OPEN|ACKNOWLEDGED|..." }`
- GET `/api/v1/incidents/{id}/evidence`
- GET `/api/v1/incidents/{id}/related`
- GET `/api/v1/incidents/{id}/timeline`
- Legacy/planned RCA generation endpoints remain pending: retrieval pack, evidence pack, AI summary, topology detail.

### Connectors
- GET /api/connectors
  - Permission: config.connector.read
  - Query: page, size, sort, search
  - Returns: Page<ConnectorResponse>
- GET /api/connectors/{id}
  - Permission: config.connector.read
  - Returns: ConnectorResponse (no secrets)
- POST /api/connectors
  - Permission: config.connector.write
  - Body: ConnectorCreateRequest
  - Returns: ConnectorResponse
  - Creates audit log + outbox event
- PUT /api/connectors/{id}
  - Permission: config.connector.write
  - Body: ConnectorCreateRequest
  - Returns: ConnectorResponse
  - Creates audit log + outbox event
- DELETE /api/connectors/{id}
  - Permission: config.connector.write
  - Creates audit log + outbox event
- POST /api/connectors/test
  - Permission: config.connector.test
  - Body: ConnectorTestRequest
  - Returns: CompletableFuture<TestResult>
  - Async via outbox pattern
- GET /api/connectors/types
  - Permission: config.connector.read
  - Returns: Available connector types and schemas

### Applications
- Implemented path prefix: `/api/v1/applications`
- GET `/api/v1/applications?page=&size=&country=&environment=`
- GET `/api/v1/applications/{id}`
- POST `/api/v1/applications`
- PUT `/api/v1/applications/{id}`
- DELETE `/api/v1/applications/{id}`
- GET `/api/v1/applications/{id}/inventory`
- GET `/api/v1/applications/{id}/incidents`
- GET `/api/v1/applications/{id}/health`
- Planned: Neo4j-backed `/topology` and hourly analysis outbox trigger.

### Inventory
- Implemented path prefix: `/api/v1/inventory`
- GET `/api/v1/inventory?page=&size=&country=&environment=`
- GET `/api/v1/inventory/{id}`
- POST `/api/v1/inventory`
- PUT `/api/v1/inventory/{id}`
- DELETE `/api/v1/inventory/{id}`
- GET `/api/v1/inventory/{id}/dependencies`
- GET `/api/v1/inventory/{id}/alerts`

### Reports
- Implemented path prefix: `/api/v1/reports`
- GET `/api/v1/reports?page=&size=&country=&environment=`
- GET `/api/v1/reports/{id}`
- GET `/api/v1/reports/runs`
- GET `/api/v1/reports/runs/{runId}/artifacts`
- GET `/api/v1/reports/artifacts/{artifactId}/download`
- POST `/api/v1/reports/generate`

### Settings
- Implemented path prefix: `/api/v1/settings`
- GET `/api/v1/settings`
  - Requires tenant/user/country/environment headers. The backend loads permanent metadata from `config.integration_settings` for `(tenant_id, ALL, ALL)`, `(tenant_id, ALL, environment)`, `(tenant_id, countryCode, ALL)`, and `(tenant_id, countryCode, environment)` in fallback-to-specific order.
  - Country-scoped list rows are filtered again at response time using the active request country. `azureOpenAI.integrations[]`, `databases.connections[]`, `sharepoint.connections[]`, and `infrastructure.connections[]` return only rows whose `countryCodes`/`countryCode` contain the active physical country or `ALL`, so switching the sidebar country shows only the providers/connectors allowed for that country while all-country rows remain visible everywhere.
  - When multiple scope layers exist for the same section key, list-backed payloads are reloaded with deterministic replacement semantics instead of additive merge semantics. This prevents stale AI provider rows from surviving an overwrite and ensures the latest saved provider list is what the UI receives after Tomcat restart.
  - The SPA now fails closed when browser session storage does not contain valid UUID `tenantId` and `userId` values: it does not intentionally send malformed Settings requests and instead prompts the operator to sign in again.
  - The backend load query selects only `WHERE tenant_id = ?` and performs scope filtering and least-specific-first ordering in Java (2026-06-29). The earlier prepared statement bound 5 arguments against 7 `?` placeholders, which caused PgJDBC to throw and `SettingsService.loadMetadataSettings` to silently return an empty map (`settings metadata load unavailable … errorType=DataIntegrityViolationException`), so the UI rendered blank even when `config.integration_settings` already contained the saved rows. If that WARN ever returns, treat it as a regression of this fix rather than a configuration issue.
- PUT `/api/v1/settings`
  - Requires `SETTINGS_WRITE` and writes a secret-safe audit entry.
  - Metadata-owned sections are permanently upserted into `config.integration_settings` using `(tenant_id, country_code, environment, key)`. Use country `ALL` for group-wide Settings and physical country codes such as `KW`, `BH`, or `EG` for country-specific Settings.
  - AI provider metadata is stored under `azureOpenAI.integrations[]` with `countryCodes`/`countryCode` scope and masked API key responses only.
  - Database, SharePoint, and infrastructure metadata rows also support `countryCodes`/`countryCode`; examples include Kafka for Kuwait only (`countryCodes:["KW"]`) or all countries (`countryCodes:["ALL"]`). Submitted row `secret` values are encrypted server-side into `secretSecret`, masked as `••••••••••••` in responses, and reused for masked Test Connection requests.
  - Persistence scope follows the row payload, not only the current browser country header: when `azureOpenAI.integrations[]`, `databases.connections[]`, `sharepoint.connections[]`, or `infrastructure.connections[]` declare `countryCodes:["ALL"]`, the backend stores that section under `ALL` scope so it still reloads after a web-server restart for any matching country session. When rows declare physical countries such as `KW` or `BH`, the backend stores the section under those physical scopes so the same provider remains visible after restart only for those countries.
  - Saving a scoped list-backed section replaces the previously persisted list for that same scope/key combination. Operators can edit or replace AI provider rows without leaving orphaned rows that disappear or conflict after restart.
  - When persistence fails, the backend returns a problem response such as `SETTINGS_PERSISTENCE_UNAVAILABLE` or `SETTINGS_PERSISTENCE_FAILED`; the Settings UI now surfaces that backend message directly in the popup/toast instead of replacing it with a generic save error.
  - `SETTINGS_PERSISTENCE_UNAVAILABLE` must not occur in a normally provisioned deployment: the `JdbcSettingsMetadataStore` `@Repository` is always wired when the primary PostgreSQL datasource is configured. The earlier `@ConditionalOnBean(JdbcTemplate.class)` guard was removed (2026-06-29) because it was evaluated during component scan, before Spring Boot's `JdbcTemplate` auto-configuration, and silently disabled Settings persistence even with PostgreSQL fully configured. If this code ever returns again, verify the primary datasource and Flyway migrations (including `V12__country_environment_scoped_integration_settings.sql`) before any other action.
  - If the browser session is missing valid UUID tenant/user context, the SPA blocks the write locally and shows a re-authentication message instead of repeatedly issuing malformed `PUT /api/v1/settings` calls.
- POST `/api/v1/settings/{section}/test`
  - Requires `SETTINGS_WRITE` and writes a secret-safe audit entry.
  - For `section` beginning with `azureOpenAI`, the backend validates Azure endpoint allowlists/SSRF controls and returns a sanitized test result with `correlationId`.
  - For `section=neo4j` or `databases.connections.{index}` with `type=NEO4J`, the backend runs a bounded Neo4j Java Driver readiness probe and never returns the submitted password.
  - The `neo4j` payload now also carries `countryCode` and `countryCodes` (one of `ALL`, `KW`, `BH`, `EG`). `PUT /api/v1/settings` persists the Neo4j row under that scope in `config.integration_settings` using the same `(tenant_id, country_code, environment, key)` unique row as `azureOpenAI.integrations[]` and `databases.connections[]`. A row saved with `countryCodes:["ALL"]` is reloaded for any country session for the same tenant/environment; `countryCodes:["KW"]` is reloaded only for Kuwait sessions (Bahrain / Egypt fall back to startup defaults unless an `ALL` row is also present).
  - The Settings → Databases popups (Add/Edit Database, Add/Edit SharePoint, Add/Edit Server, and Edit Neo4j Topology Graph) now expose a `Cancel | Test Only | Test & Save` (or `Test & Update` when editing) button row instead of a separate save button. `Test & Save`/`Test & Update` invokes the matching `POST /api/v1/settings/{section}/test` call and only commits the row into the section list (followed by the normal debounced `PUT /api/v1/settings` autosave) when the test returns `Pass`. A `Fail` keeps the popup open with the secret-safe failure message rendered in the inline test status banner.
  - For `section=infrastructure.connections.{index}` or draft preview payloads with `type=REDIS`, `KAFKA`, or `INDEX_STORAGE`, the backend returns a secret-safe result containing `section`, `status`, `pass`, `latencyMs`, `message`, `checkedEndpoint`, `type`, `testedAt`, and `correlationId`.
    - `REDIS`: requires host/IP plus optional port/ACL fields; blocks URL syntax, loopback, link-local, multicast, and metadata targets before running bounded RESP `AUTH`/`PING`.
    - `KAFKA`: requires comma-separated `host:port` bootstrap entries; blocks URL syntax, loopback, link-local, multicast, and metadata targets before running a bounded Kafka AdminClient metadata probe. If the client sends the masked secret placeholder for a saved infrastructure row, the backend decrypts the stored `secretSecret` only for the test call and never returns it.
    - `INDEX_STORAGE`: for `LOCAL`/`NFS`, requires an absolute non-traversal path and checks directory readability/writability by the application process; for `S3`/`AZURE_BLOB`, validates allowed pointer schemes only in this phase and guards HTTPS metadata/loopback/link-local targets without performing a cloud object-storage call.
  - The Settings UI exposes only operational GPT 5.4 integration fields: provider/model labels, country scope, endpoint URL, deployment/model name, API key, token controls, timeout, and enabled state. `purpose=GPT`, `authMode=API_KEY`, and `apiStyle=RESPONSES` are fixed internally and are not selectable controls in the popup.
  - Azure OpenAI GPT 5.4 ready connector fields:
    ```json
    {
      "provider": "AZURE_OPENAI",
      "purpose": "GPT",
      "modelName": "gpt-5.4",
      "deployment": "gpt-5.4",
      "endpoint": "https://<resource>.services.ai.azure.com/openai/v1",
      "authMode": "API_KEY",
      "apiStyle": "RESPONSES",
      "apiKey": "not-returned-or-logged",
      "countryCodes": ["ALL"],
      "maxOutputTokens": 4096,
      "monthlyTokenLimit": 1000000,
      "timeoutSeconds": 5
    }
    ```
  - Submitted API keys are encrypted server-side, masked in responses, omitted from audit/test details, and used only for the bounded Azure OpenAI Responses API readiness test. For GPT 5.4 / Azure AI Foundry `apiStyle=RESPONSES` tests, the endpoint may be the Azure portal resource root (`https://<resource>.cognitiveservices.azure.com/`) or the OpenAI v1 base (`https://<resource>.services.ai.azure.com/openai/v1`); root endpoints are normalized server-side to `/openai/v1/responses` and existing `/openai/v1` endpoints append `/responses` exactly once. The backend first sends the Azure `api-key` header and, if Azure returns `401 Unauthorized`, retries once with OpenAI v1-compatible `Authorization: Bearer <api-key>` authentication. Token limit fields are metadata for governed usage controls and are bounded by the frontend before save.

### Admin: Connectors
- GET /api/v1/connectors — List connectors with optional filters
  - Query params: type, enabled, search, page, size, sort
  - Returns: List<ConnectorResponse> with configSummary, lastTest, lastRun, health
  - Permission: connectors:view
  
- GET /api/v1/connectors/{connectorId} — Get connector details
  - Returns: Full ConnectorResponse with config, secretsMask (never plaintext)
  - Permission: connectors:view
  
- POST /api/v1/connectors — Create new connector
  - Body: { type, name, enabled, config, secretsPlain }
  - Validates: config via plugin, SSRF for URLs
  - Persistence: writes to PostgreSQL `config.connectors`; if connector persistence is unavailable, returns `503 application/problem+json` with code `CONNECTOR_PERSISTENCE_UNAVAILABLE` instead of creating a volatile memory-only connector
  - Audit: CONNECTOR_CREATED
  - Outbox: CONNECTOR_CREATED event
  - Permission: connectors:edit
  
- PUT /api/v1/connectors/{connectorId} — Update connector
  - Body: { name?, enabled?, config?, secretsPlain? }
  - SCOM uses the existing boolean config field `verifySsl`; `false` means a governed test-only WinRM certificate-validation bypass while keeping HTTPS/5986 transport and using PowerShell `SkipCACheck`, `SkipCNCheck`, and `SkipRevocationCheck`.
  - Persistence: updates PostgreSQL `config.connectors`; if connector persistence is unavailable, returns `503 application/problem+json` with code `CONNECTOR_PERSISTENCE_UNAVAILABLE`
  - Audit: CONNECTOR_UPDATED
  - Outbox: CONNECTOR_UPDATED event
  - Permission: connectors:edit
  
- DELETE /api/v1/connectors/{connectorId} — Delete connector
  - Cascades: secrets, runs, logs
  - Persistence: deletes from PostgreSQL `config.connectors`; if connector persistence is unavailable, returns `503 application/problem+json` with code `CONNECTOR_PERSISTENCE_UNAVAILABLE`
  - Audit: CONNECTOR_DELETED
  - Outbox: CONNECTOR_DELETED event
  - Permission: connectors:edit
  
- POST /api/v1/connectors/{connectorId}/enable — Enable connector
- POST /api/v1/connectors/{connectorId}/disable — Disable connector
  - Implemented Phase 1 toggle path: `PATCH /api/v1/connectors/{connectorId}/toggle` with body `{ "enabled": true|false }`
  - Persistence: updates PostgreSQL `config.connectors`; if connector persistence is unavailable, returns `503 application/problem+json` with code `CONNECTOR_PERSISTENCE_UNAVAILABLE`
  - Audit: CONNECTOR_TOGGLED
  - Outbox: CONNECTOR_TOGGLED event
  - Permission: connectors:edit
  - UI: Settings → Connections lists each connection as **Connection · Country · Schedule · Last sync · Status** with an inline Enable/Disable action that calls `PATCH /toggle`. The connection editor exposes an **Ingestion schedule** control persisted as `attributes.intervalMin` (integer minutes, bounded 5–1440; omitted/`null` → platform default 15, matching `BmcConnectorConfigValidator`/`ScomConnectorConfigValidator`). NOTE: enabling a connection + its `intervalMin` are persisted and audited, but the scheduled pollers (`BmcScheduledPoller`/`ScomScheduledPoller`) still run off the global `kfh.ingestion.*` properties — the per-connector enable/interval do not yet drive collection cadence (pending: connector-instance-driven ingestion bridge).
  
- POST /api/v1/connectors/{connectorId}/test — Test connection
  - Creates run record (runType=TEST)
  - Executes via plugin testConnection()
  - Returns: TestResultResponse { connectorRunId, pass, latencyMs, message }
  - Audit: CONNECTOR_TEST
  - Permission: connectors:edit
  
- POST /api/v1/connectors/{connectorId}/collect-now — Manual collect
  - Body: { windowMinutes?, reason? }
  - Creates queued run (runType=COLLECT)
  - Emits outbox for async worker
  - Returns: { connectorRunId }
  - Audit: CONNECTOR_COLLECT_NOW
  - Outbox: CONNECTOR_COLLECT_REQUESTED
  - Permission: connectors:edit
  
- POST /api/v1/connectors/{connectorId}/reveal — Reveal secret (ADMIN)
  - Body: { field, reason }
  - Decrypts and returns single field value
  - Audit: SECRET_REVEALED (critical)
  - Permission: connectors:admin
  
- GET /api/v1/connectors/{connectorId}/history — Run history
  - Query: page, size
  - Returns: List<ConnectorRun>
  
- GET /api/v1/connector-runs/{runId} — Run details
- GET /api/v1/connector-runs/{runId}/logs — Run logs
  - Query: limit (default 500)

- GET /api/v1/connectors/types — List available plugin types
  - Returns: List<PluginMetadata> with configSchema for UI forms

### Ingestion (Phase 4 — BMC Helix → pipeline)
- POST `/api/v1/ingestion/bmc/collect-now` — Manual BMC collection trigger
  - Permission: `ALERT_INGEST` (checked before any outbound BMC call).
  - No body. Pulls the current BMC event window (`kfh.ingestion.bmc.minutes-back`, `max-events`) under the caller's tenant/country/environment scope, then runs the shared ingestion pipeline: BMC Helix JWT login → Events `msearch` → `BmcNormalizer` (canonical `TelemetryDocument`) → `FingerprintDedupService` (Redis SETNX, fail-open) → `IndexWriterService` (Custom Index, searchable in Log Explorer).
  - Returns `IngestionResult`: `{ received, normalized, duplicatesDropped, indexed, failed }` (invariant: `received = normalized + failed`, `normalized = indexed + duplicatesDropped`).
  - Errors: `500` with a secret-safe message when BMC is unreachable or credentials are missing (`BMC ingestion is not configured …`). Redis being down does NOT fail the call (dedup fails open; events still index).
  - A parallel opt-in scheduled poll (`kfh.ingestion.bmc.enabled=true`) runs the same `collect()` on `poll-interval-ms` (default 20 min) under a configured system scope; failures are logged and retried next tick.
  - Config (env-only secrets, never committed): `kfh.ingestion.bmc.base-url`, `access-key` (`BMC_ANALYSIS_BMC_ACCESS_KEY`), `access-secret-key` (`BMC_ANALYSIS_BMC_ACCESS_SECRET_KEY`), `login-endpoint`, `events-endpoint`, `minutes-back`, `max-events`, `tenant-id`, `country-code`, `environment`.
- POST `/api/v1/ingestion/scom/collect-now` — Manual SCOM collection trigger (**Windows-only**)
  - Permission: `ALERT_INGEST` (checked before any WinRM/PowerShell session is spawned).
  - No body. Spawns local `powershell.exe` → `Invoke-Command` to the SCOM management server (WinRM 5986/HTTPS + Kerberos by default) → `Get-SCOMAlert` → compressed JSON → `ScomWinRmClient` (WCF `/Date(ms)/` → epoch, precise UTC window filter, dedup-by-Id) → `ScomNormalizer` → `FingerprintDedupService` → `IndexWriterService`.
  - Returns the same `IngestionResult` shape as BMC.
  - Errors: `500` (secret-safe) when SCOM is unconfigured/unreachable or PowerShell fails/times out. PowerShell-injection-hardened: interpolated server/user/password single-quote-escaped, auth-method whitelisted, port range-checked.
  - Opt-in scheduled poll: `kfh.ingestion.scom.enabled=true` (`poll-interval-ms`, default 20 min).
  - Config (env-only secrets): `kfh.ingestion.scom.management-server`, `username` (`BMC_ANALYSIS_SCOM_USERNAME`), `password` (`BMC_ANALYSIS_SCOM_PASSWORD`), `domain`, `hours-back`, `winrm-port`, `use-https`, `auth-method` (Kerberos/Negotiate/CredSSP/Default), `server-local-offset-hours` (Kuwait=3), `tenant-id`, `country-code`, `environment`.

### Correlation (Phase 2 — Stages 4–6)
- GET `/api/v1/correlation?minutes=120` — Correlate the recent alert window into candidate incidents
  - Permission: `ALERT_READ` (enforced at the service layer). Tenant/country/environment-scoped.
  - `minutes` (default 120, floored at 1, capped at 7 days) sets the window `[now-minutes, now]`.
  - Reads that window's `ALERTS` from the Custom Index → resolves each alert's CI via the topology (`resourceId → Asset → Component → Application(s)`) → groups failing components by shared blast radius → picks the most-upstream failing component as the candidate root cause.
  - Returns `CorrelationResult`: `{ incidents[], unmappedCis[], alertsProcessed, alertsMapped }` where each incident is `{ incidentKey (country|env|rootComponent), title, severity, started, rootCauseComponentId, rootCauseComponentName, rootCauseAssetCi, impactedApplications[], alertCount, evidence[] }` and each evidence is `{ resourceId, componentId, componentName, severity, source, timestamp, message }`.
  - `unmappedCis` lists alert CIs not present in the topology (CMDB gaps). Topology is a hand-modelled KFH seed for now (CMDB/agent/Neo4j-backed later, no contract change).

Implemented frontend-aligned connector endpoints in this scaffold:
- GET `/api/v1/connectors?page=&size=`
  - With `X-Country-Code: ALL` and `COUNTRY_GLOBAL_VIEW` / `*`, returns all connectors for the current tenant/environment across physical country configs (`KW`, `BH`, `EG`). With a physical country header, returns only that country.
- GET `/api/v1/connectors/{id}`
- GET `/api/v1/connectors/types`
  - Requires `CONNECTOR_READ`.
  - Returns connector type metadata for the Add Connector picker. `BMC`, `APPDYNAMICS`, `VROPS`, `SCOM`, and `EMCO` are enabled in this phase; Lansweeper is returned as a visible future connector card so operators can see the roadmap but cannot create it yet.
- POST `/api/v1/connectors`
  - Requires `CONNECTOR_WRITE`.
  - Current implemented create types: `BMC` / BMC Helix, `APPDYNAMICS` / AppDynamics, `VROPS` / VMware vROps / Aria Operations, `SCOM` / Microsoft SCOM, and `EMCO` / EMCO Ping Monitor.
  - Request uses `UiWriteRequest` with BMC attributes: `pluginType=BMC`, `countryCode`, `environment`, `baseUrl`, `loginEndpoint`, `eventsEndpoint`, `minutesBack`, `pageSize`, `maxEvents`, `timeoutSeconds`, `verifySsl`, `intervalMin`, `ownerTeam`, `notes`, and `secretsPlain.accessKey` / `secretsPlain.accessSecretKey`.
  - Request uses `UiWriteRequest` with AppDynamics attributes: `pluginType=APPDYNAMICS`, `countryCode`, `environment`, `controllerUrl`, `durationMinutes`, `timeoutSeconds`, `verifySsl`, `maxWorkers`, `fetchErrors`, `fetchViolations`, `fetchSlowTransactions`, `intervalMin`, `ownerTeam`, `notes`, and `secretsPlain.username` / `secretsPlain.password` for Basic Auth.
  - Request uses `UiWriteRequest` with vROps attributes: `pluginType=VROPS`, `countryCode`, `environment`, `host` or HTTPS `baseUrl` ending with `/suite-api/api`, `authSource`, `hours`, `pageSize`, `maxPages`, `maxWorkers`, `timeoutSeconds`, `verifySsl`, `intervalMin`, `ownerTeam`, `notes`, and `secretsPlain.username` / `secretsPlain.password`.
  - Request uses `UiWriteRequest` with SCOM attributes: `pluginType=SCOM`, `countryCode`, `environment`, `managementServer` or WinRM `baseUrl` ending with `/wsman`, `domain`, `winrmPort`, `useHttps`, `verifySsl`, `authMethod`, `hoursBack`, `connectionTimeoutSeconds`, `intervalMin`, `ownerTeam`, `notes`, and `secretsPlain.username` / `secretsPlain.password` for the WinRM/PowerShell service account.
  - Request uses `UiWriteRequest` with EMCO attributes: `pluginType=EMCO`, `countryCode`, `environment`, `sqlServer`, `sqlPort`, `kfhDatabase`, `cctvDatabase`, `minutesBack`, `connectionTimeoutSeconds`, `queryTimeoutSeconds`, `encrypt`, `trustServerCertificate`, `intervalMin`, `ownerTeam`, `notes`, and `secretsPlain.kfhUsername` / `secretsPlain.kfhPassword` / `secretsPlain.cctvUsername` / `secretsPlain.cctvPassword` for the two SQL Server EMCO domains.
  - Compatibility rule: the service also normalizes known credential aliases submitted as top-level attributes (`accessKey`/`accessSecretKey` for BMC, `username`/`password` for AppDynamics Basic Auth, vROps, and SCOM, and `kfhUsername`/`kfhPassword`/`cctvUsername`/`cctvPassword` for EMCO) into `secretsPlain` before validation/persistence. They are encrypted into `config.connector_secrets.secret_enc` and are not stored in `config.connectors.config` or returned to the browser.
  - Install-only marketplace flow may create an enabled placeholder using `attributes.installOnly=true` with `pluginType`, `countryCode`, and `environment` only. The response has `enabled=true`, `configurationStatus=PENDING`, and `secretsMask=not_configured`; operators then open Configure to enter BMC/AppDynamics/vROps/SCOM/EMCO connection details before live collection can run.
  - Connector country must be a physical enabled country (`KW`, `BH`, `EG`); all-country sessions may select a physical country only when they have `COUNTRY_GLOBAL_VIEW` or `*`.
  - BMC URL validation is HTTPS-only, rejects user-info/query/fragment/API path on `baseUrl`, rejects unsafe relative endpoint paths, and supports public or private KFH hybrid endpoints. Localhost, loopback, link-local, multicast, and metadata targets remain blocked for SSRF protection.
  - AppDynamics URL validation is HTTPS-only, requires `controllerUrl` to end with `/controller`, rejects user-info/query/fragment/API paths beyond `/controller`, supports public or private KFH hybrid Controller hosts/IPs, and requires at least one fetch family to remain enabled. Localhost, loopback, link-local, multicast, and metadata targets remain blocked.
  - `verifySsl` defaults to `true` for BMC, AppDynamics, vROps, and SCOM. When explicitly set to `false`, live tests keep endpoint SSRF protections but skip remote certificate-chain validation only for the relevant connector transport to support governed dev/hybrid testing while the corporate CA is not yet imported into the JVM/WinRM truststore.
  - vROps URL validation is HTTPS-only, accepts a hostname/IP or HTTPS URL ending with `/suite-api/api`, rejects user-info/query/fragment and API paths beyond `/suite-api/api`, and supports public or private KFH hybrid endpoints. Localhost, loopback, link-local, multicast, and metadata targets remain blocked.
  - SCOM WinRM validation accepts a management server FQDN/IP or `http(s)://host:port/wsman`, rejects credentials/query/fragment and paths beyond `/wsman`, supports public or private KFH hybrid management servers, and blocks localhost, loopback, link-local, multicast, and metadata targets.
  - EMCO SQL Server validation accepts a SQL Server host/IP and optional `host:port`, rejects JDBC/HTTP URLs, user-info, paths, query strings, fragments, localhost, loopback, link-local, multicast, and metadata targets, bounds SQL login/query timeouts, and allows only safe database-name characters before constructing JDBC URLs server-side.
- PUT `/api/v1/connectors/{id}`
  - BMC, AppDynamics, vROps, SCOM, and EMCO updates may change non-secret configuration values before or after a successful connection test. A `PASS` result records runtime validation only; it does not lock connector endpoint, schedule, ownership, or credential rotation updates.
  - Secrets are write-only and never returned. To keep existing encrypted credentials, omit `secretsPlain`/leave credential fields blank; to rotate BMC credentials, submit non-blank `secretsPlain.accessKey` and/or `secretsPlain.accessSecretKey`; to rotate AppDynamics Basic Auth, submit non-blank `secretsPlain.username` and/or `secretsPlain.password`; to rotate vROps or SCOM credentials, submit both `secretsPlain.username` and `secretsPlain.password` together; to rotate EMCO credentials, submit KFH username/password together and/or CCTV username/password together. Submitted secrets are validated, encrypted server-side, and stripped from the response.
  - Submitted secret rotation preserves any existing encrypted secret keys that were not resubmitted without decrypting those old entries. This allows operators to recover after a platform master-key change by re-entering affected connector credentials instead of being blocked by old encrypted payloads.
- DELETE `/api/v1/connectors/{id}`
- PATCH `/api/v1/connectors/{id}/toggle` with `{ "enabled": true|false }`
- POST `/api/v1/connectors/{id}/test`
  - Performs a live BMC Helix readiness test for BMC connectors using the saved connector configuration and encrypted server-side credentials: HTTPS base URL validation, access-key login at `loginEndpoint`, `json_web_token` extraction, and an events `msearch` readiness probe at `eventsEndpoint`.
  - Performs a live AppDynamics readiness test for AppDynamics connectors using the saved controller URL and encrypted Basic Auth credentials: HTTPS controller URL validation and `GET /rest/applications?output=JSON` application discovery.
  - Performs a live vROps readiness test for VMware vROps / Aria Operations connectors using the saved host/base URL and encrypted username/password credentials: HTTPS suite API validation, `POST /suite-api/api/auth/token/acquire` token acquisition using `authSource`, and `GET /suite-api/api/alerts?page=0&pageSize=1&_no_links=true` API probe with `Authorization: vRealizeOpsToken <token>`.
  - Performs a live SCOM readiness test for Microsoft SCOM connectors using the saved WinRM endpoint and encrypted username/password credentials: local `powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass`, secret values passed only through child-process environment variables, `Invoke-Command` to the SCOM management server, `Import-Module OperationsManager`, `New-SCOMManagementGroupConnection`, and a bounded `Get-SCOMAlert | Select-Object -First 1` probe that validates the required SCOM field shape without returning raw alert payloads.
  - Performs a live EMCO readiness test for EMCO connectors using saved SQL Server host/port, KFH and CCTV database names, and encrypted KFH/CCTV SQL credentials: JDBC URL construction with `encrypt`, `trustServerCertificate`, and `loginTimeout`, bounded `PreparedStatement#setQueryTimeout`, a KFH `db_owner.tb_host_events`/`db_owner.tb_hosts` probe, and a CCTV `dbo.tb_host_events`/`dbo.tb_hosts` probe. Probe results report only pass/fail and whether recent matching rows exist; raw EMCO rows are not returned.
  - Returns a secret-safe result containing `pass`, `readyToCollect`, `status`, `latencyMs`, `message`, `checkedEndpoint`, `verifySsl` or connector-specific transport flags, `testedAt`, `correlationId`, and step details. Failed live tests include the sanitized Java/HTTP/PowerShell/JDBC failure reason when available; AppDynamics HTTP error bodies are compacted and redacted before being included. PKIX/certificate-chain failures add truststore guidance. Plain access keys, access secret keys, usernames, passwords, Basic Auth values, bearer tokens, vROps tokens, authorization headers, SQL credential data, PowerShell credential data, and raw response bodies are never returned.
  - If saved connector credentials cannot be decrypted because the current `KFH_AIOPS_SECRET_KEY` / deployment secret file does not match the key used when the credentials were saved, the test records `pass=false`, `status=FAIL`, `errorCode=SECRET_DECRYPTION_FAILED`, and a recovery message instructing operators to restore the original key or re-enter all credential fields for that connector. The live source-system tester is not called with empty credentials.
  - Persists a secret-safe health snapshot: `PASS` maps enabled connectors to `health=HEALTHY`; `FAIL` maps enabled connectors to `health=DOWN` so the connector inventory immediately shows warning/down state instead of only the enabled collection flag.
- POST `/api/v1/connectors/heartbeat`
  - Requires `CONNECTOR_TEST`.
  - Runs the same live readiness test for every enabled connector visible in the current tenant/country/environment scope.
  - Returns `checkedAt`, `correlationId`, `totalEnabled`, `healthy`, `down`, and per-connector secret-safe test results.
  - Disabled connectors are skipped. Failed heartbeat results persist `lastTestStatus=FAIL` and `health=DOWN`; successful results persist `lastTestStatus=PASS` and `health=HEALTHY`.
- GET `/api/v1/connectors/{id}/logs`
- Secret rule: `secretsPlain` is stripped and never returned; responses expose `secretsMask` only.

### Admin: Schedules
- Implemented path prefix: `/api/v1/schedules`
- GET `/api/v1/schedules?page=&size=`
- GET `/api/v1/schedules/{id}`
- POST `/api/v1/schedules`
- PUT `/api/v1/schedules/{id}`
- DELETE `/api/v1/schedules/{id}`
- PATCH `/api/v1/schedules/{id}/toggle`
- POST `/api/v1/schedules/{id}/run`
- GET `/api/v1/schedules/{id}/runs`

### Admin: Users & RBAC
- Implemented path prefixes: `/api/v1/users`, `/api/v1/roles`
- GET `/api/v1/users?page=&size=&country=&environment=` — country/environment are optional; if omitted, the backend uses the `TenantContext` headers. Country access is enforced at the service layer.
- POST `/api/v1/users`
- Frontend create request uses `UiWriteRequest`:
  ```json
  {
    "name": "Fatima Al-Salem",
    "title": "NOC Operator",
    "status": "Active",
    "attributes": {
      "username": "f.alsalem",
      "email": "fatima.alsalem@kfh.com",
      "phone": "+965 ...",
      "teams": ["NOC"],
      "roleIds": ["role-002"],
      "roles": ["NOC Operator"],
      "countryCode": "KW",
      "environment": "PROD"
    }
  }
  ```
- GET/PUT/DELETE `/api/v1/users/{id}`
- ID-based user reads/writes are guarded by the user row `countryCode`; request payload `countryCode`, `countryName`, `tenantId`, and `environment` cannot override the active header scope during user creation/update in the Phase 1 scaffold.
- PATCH `/api/v1/users/{id}/toggle`
- GET `/api/v1/users/roles`
- GET `/api/v1/users/{userId}/permissions`
- GET/POST `/api/v1/roles`
- GET/PUT/DELETE `/api/v1/roles/{id}`

### Admin: Settings
- Implemented path prefix: `/api/v1/settings`
- GET `/api/v1/settings`
  - Requires tenant/user/country/environment headers. The backend loads permanent metadata from `config.integration_settings` for `(tenant_id, ALL, ALL)`, `(tenant_id, ALL, environment)`, `(tenant_id, countryCode, ALL)`, and `(tenant_id, countryCode, environment)` in fallback-to-specific order.
  - Country-scoped list rows are filtered again at response time using the active request country. `azureOpenAI.integrations[]`, `databases.connections[]`, `sharepoint.connections[]`, and `infrastructure.connections[]` return only rows whose `countryCodes`/`countryCode` contain the active physical country or `ALL`, so switching the sidebar country shows only the providers/connectors allowed for that country while all-country rows remain visible everywhere.
  - When multiple scope layers exist for the same section key, list-backed payloads are reloaded with deterministic replacement semantics instead of additive merge semantics. This prevents stale AI provider rows from surviving an overwrite and ensures the latest saved provider list is what the UI receives after Tomcat restart.
  - The SPA now fails closed when browser session storage does not contain valid UUID `tenantId` and `userId` values: it does not intentionally send malformed Settings requests and instead prompts the operator to sign in again.
  - The backend load query selects only `WHERE tenant_id = ?` and performs scope filtering and least-specific-first ordering in Java (2026-06-29). The earlier prepared statement bound 5 arguments against 7 `?` placeholders, which caused PgJDBC to throw and `SettingsService.loadMetadataSettings` to silently return an empty map (`settings metadata load unavailable … errorType=DataIntegrityViolationException`), so the UI rendered blank even when `config.integration_settings` already contained the saved rows. If that WARN ever returns, treat it as a regression of this fix rather than a configuration issue.
- PUT `/api/v1/settings`
  - Requires `SETTINGS_WRITE` and writes a secret-safe audit entry.
  - Metadata-owned sections are permanently upserted into `config.integration_settings` using `(tenant_id, country_code, environment, key)`. Use country `ALL` for group-wide Settings and physical country codes such as `KW`, `BH`, or `EG` for country-specific Settings.
  - AI provider metadata is stored under `azureOpenAI.integrations[]` with `countryCodes`/`countryCode` scope and masked API key responses only.
  - Database, SharePoint, and infrastructure metadata rows also support `countryCodes`/`countryCode`; examples include Kafka for Kuwait only (`countryCodes:["KW"]`) or all countries (`countryCodes:["ALL"]`). Submitted row `secret` values are encrypted server-side into `secretSecret`, masked as `••••••••••••` in responses, and reused for masked Test Connection requests.
  - Persistence scope follows the row payload, not only the current browser country header: when `azureOpenAI.integrations[]`, `databases.connections[]`, `sharepoint.connections[]`, or `infrastructure.connections[]` declare `countryCodes:["ALL"]`, the backend stores that section under `ALL` scope so it still reloads after a web-server restart for any matching country session. When rows declare physical countries such as `KW` or `BH`, the backend stores the section under those physical scopes so the same provider remains visible after restart only for those countries.
  - Saving a scoped list-backed section replaces the previously persisted list for that same scope/key combination. Operators can edit or replace AI provider rows without leaving orphaned rows that disappear or conflict after restart.
  - When persistence fails, the backend returns a problem response such as `SETTINGS_PERSISTENCE_UNAVAILABLE` or `SETTINGS_PERSISTENCE_FAILED`; the Settings UI now surfaces that backend message directly in the popup/toast instead of replacing it with a generic save error.
  - `SETTINGS_PERSISTENCE_UNAVAILABLE` must not occur in a normally provisioned deployment: the `JdbcSettingsMetadataStore` `@Repository` is always wired when the primary PostgreSQL datasource is configured. The earlier `@ConditionalOnBean(JdbcTemplate.class)` guard was removed (2026-06-29) because it was evaluated during component scan, before Spring Boot's `JdbcTemplate` auto-configuration, and silently disabled Settings persistence even with PostgreSQL fully configured. If this code ever returns again, verify the primary datasource and Flyway migrations (including `V12__country_environment_scoped_integration_settings.sql`) before any other action.
  - If the browser session is missing valid UUID tenant/user context, the SPA blocks the write locally and shows a re-authentication message instead of repeatedly issuing malformed `PUT /api/v1/settings` calls.
- POST `/api/v1/settings/{section}/test`
  - Requires `SETTINGS_WRITE` and writes a secret-safe audit entry.
  - For `section` beginning with `azureOpenAI`, the backend validates Azure endpoint allowlists/SSRF controls and returns a sanitized test result with `correlationId`.
  - For `section=neo4j` or `databases.connections.{index}` with `type=NEO4J`, the backend runs a bounded Neo4j Java Driver readiness probe and never returns the submitted password.
  - The `neo4j` payload now also carries `countryCode` and `countryCodes` (one of `ALL`, `KW`, `BH`, `EG`). `PUT /api/v1/settings` persists the Neo4j row under that scope in `config.integration_settings` using the same `(tenant_id, country_code, environment, key)` unique row as `azureOpenAI.integrations[]` and `databases.connections[]`. A row saved with `countryCodes:["ALL"]` is reloaded for any country session for the same tenant/environment; `countryCodes:["KW"]` is reloaded only for Kuwait sessions (Bahrain / Egypt fall back to startup defaults unless an `ALL` row is also present).
  - The Settings → Databases popups (Add/Edit Database, Add/Edit SharePoint, Add/Edit Server, and Edit Neo4j Topology Graph) now expose a `Cancel | Test Only | Test & Save` (or `Test & Update` when editing) button row instead of a separate save button. `Test & Save`/`Test & Update` invokes the matching `POST /api/v1/settings/{section}/test` call and only commits the row into the section list (followed by the normal debounced `PUT /api/v1/settings` autosave) when the test returns `Pass`. A `Fail` keeps the popup open with the secret-safe failure message rendered in the inline test status banner.
  - For `section=infrastructure.connections.{index}` or draft preview payloads with `type=REDIS`, `KAFKA`, or `INDEX_STORAGE`, the backend returns a secret-safe result containing `section`, `status`, `pass`, `latencyMs`, `message`, `checkedEndpoint`, `type`, `testedAt`, and `correlationId`.
    - `REDIS`: requires host/IP plus optional port/ACL fields; blocks URL syntax, loopback, link-local, multicast, and metadata targets before running bounded RESP `AUTH`/`PING`.
    - `KAFKA`: requires comma-separated `host:port` bootstrap entries; blocks URL syntax, loopback, link-local, multicast, and metadata targets before running a bounded Kafka AdminClient metadata probe. If the client sends the masked secret placeholder for a saved infrastructure row, the backend decrypts the stored `secretSecret` only for the test call and never returns it.
    - `INDEX_STORAGE`: for `LOCAL`/`NFS`, requires an absolute non-traversal path and checks directory readability/writability by the application process; for `S3`/`AZURE_BLOB`, validates allowed pointer schemes only in this phase and guards HTTPS metadata/loopback/link-local targets without performing a cloud object-storage call.
  - The Settings UI exposes only operational GPT 5.4 integration fields: provider/model labels, country scope, endpoint URL, deployment/model name, API key, token controls, timeout, and enabled state. `purpose=GPT`, `authMode=API_KEY`, and `apiStyle=RESPONSES` are fixed internally and are not selectable controls in the popup.
  - Azure OpenAI GPT 5.4 ready connector fields:
    ```json
    {
      "provider": "AZURE_OPENAI",
      "purpose": "GPT",
      "modelName": "gpt-5.4",
      "deployment": "gpt-5.4",
      "endpoint": "https://<resource>.services.ai.azure.com/openai/v1",
      "authMode": "API_KEY",
      "apiStyle": "RESPONSES",
      "apiKey": "not-returned-or-logged",
      "countryCodes": ["ALL"],
      "maxOutputTokens": 4096,
      "monthlyTokenLimit": 1000000,
      "timeoutSeconds": 5
    }
    ```
  - Submitted API keys are encrypted server-side, masked in responses, omitted from audit/test details, and used only for the bounded Azure OpenAI Responses API readiness test. For GPT 5.4 / Azure AI Foundry `apiStyle=RESPONSES` tests, the endpoint may be the Azure portal resource root (`https://<resource>.cognitiveservices.azure.com/`) or the OpenAI v1 base (`https://<resource>.services.ai.azure.com/openai/v1`); root endpoints are normalized server-side to `/openai/v1/responses` and existing `/openai/v1` endpoints append `/responses` exactly once. The backend first sends the Azure `api-key` header and, if Azure returns `401 Unauthorized`, retries once with OpenAI v1-compatible `Authorization: Bearer <api-key>` authentication. Token limit fields are metadata for governed usage controls and are bounded by the frontend before save.

### Audit
- Implemented path prefix: `/api/v1/audit`
- GET `/api/v1/audit?page=&size=`
- Returns tenant-scoped, country-aware application activity rows from PostgreSQL `identity.audit_log` first, with the Phase 1 in-memory read model used only as a degraded fallback if the persisted audit store is temporarily unavailable. Rows survive browser refresh and webserver restart when datasource-backed mode is running. `COUNTRY_ADMIN` with `AUDIT_READ` can read only matching country/environment activity and the UI keeps their country scope locked; `X-Country-Code: ALL` requires `COUNTRY_GLOBAL_VIEW` or `*` and returns all countries for the tenant/environment. Connector lifecycle activity includes install/create, configure/update, enable/disable, uninstall, test requested, test succeeded/failed, heartbeat requested, and per-connector heartbeat succeeded/failed rows with secret-safe connector metadata only. `actor_user_id` is stored as an immutable audit reference and display fields resolve to the matching user when available or `System` when the actor row is unavailable, so audit persistence is not blocked by identity cleanup or synthetic/system actors.
- Activity rows include successful/failed sign-ins (`LOGIN_SUCCEEDED`, `LOGIN_FAILED`), settings changes/tests, identity/user actions, connector/schedule/application/inventory/report/incident/alert writes, and bootstrap admin provisioning when it changes state. Rows may include secret-safe display fields such as `actorName`, `actorUsername`, `targetName`, `targetType`, `targetId`, `message`, `result`, and `severity` so the UI can render meaningful activity instead of UUID-only storage identifiers; login rows use `targetId=AUTHENTICATION` for display while retaining tenant/country/environment/correlation scope. Passwords, tokens, API keys, connector secrets, and raw request bodies must not be returned.
- GET `/api/v1/audit/{id}`
- Detail reads are scoped by tenant, country, and environment; unauthorized or out-of-scope IDs return not found.
- GET `/api/v1/audit/export`
- Export uses the same `AUDIT_READ` plus all-country guard as list/detail reads; `X-Country-Code: ALL` without `COUNTRY_GLOBAL_VIEW` or `*` is rejected.
