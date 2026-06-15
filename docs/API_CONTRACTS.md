# API Contracts (v1)

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
- Successful sign-in returns the user's `tenantId`, `userId`, `countryCode`, `environment`, role, and permission set. The frontend uses those values for subsequent tenant/country-aware API headers. `GLOBAL_ADMIN` users may sign in with `countryCode: "ALL"` when their identity profile is provisioned with all-country scope.
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
- The static shell supports a sidebar KFH Group country switcher. Switching country updates `X-Tenant-Id`, `X-User-Id`, `X-Country-Code`, and `X-Environment` headers before reloading page data; backend services still enforce country access and deny cross-country reads without `COUNTRY_GLOBAL_VIEW` or `*`.

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
  - Invalid credentials, disabled users, or wrong country/environment return an authorization error. All-country sign-in uses `countryCode: "ALL"` and requires a matching all-country identity row.
  - Passwords are checked against the stored BCrypt hash and are not returned.
  - Rejected sign-ins keep the client error generic, but backend logs include secret-safe counters for username, country/environment scope, active status, and password-hash readiness to support operations troubleshooting.
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
  - Audit: CONNECTOR_CREATED
  - Outbox: CONNECTOR_CREATED event
  - Permission: connectors:edit
  
- PUT /api/v1/connectors/{connectorId} — Update connector
  - Body: { name?, enabled?, config?, secretsPlain? }
  - Audit: CONNECTOR_UPDATED
  - Outbox: CONNECTOR_UPDATED event
  - Permission: connectors:edit
  
- DELETE /api/v1/connectors/{connectorId} — Delete connector
  - Cascades: secrets, runs, logs
  - Audit: CONNECTOR_DELETED
  - Outbox: CONNECTOR_DELETED event
  - Permission: connectors:edit
  
- POST /api/v1/connectors/{connectorId}/enable — Enable connector
- POST /api/v1/connectors/{connectorId}/disable — Disable connector
  - Audit: CONNECTOR_TOGGLED
  - Outbox: CONNECTOR_TOGGLED event
  - Permission: connectors:edit
  
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

Implemented frontend-aligned connector endpoints in this scaffold:
- GET `/api/v1/connectors?page=&size=`
- GET `/api/v1/connectors/{id}`
- POST `/api/v1/connectors`
- PUT `/api/v1/connectors/{id}`
- DELETE `/api/v1/connectors/{id}`
- PATCH `/api/v1/connectors/{id}/toggle` with `{ "enabled": true|false }`
- POST `/api/v1/connectors/{id}/test`
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
- PUT `/api/v1/settings`
- POST `/api/v1/settings/{section}/test`

### Audit
- Implemented path prefix: `/api/v1/audit`
- GET `/api/v1/audit?page=&size=`
- Returns tenant-scoped, country-aware application activity rows from PostgreSQL `identity.audit_log` first, with the Phase 1 in-memory read model used only as a fallback. Rows survive browser refresh and webserver restart when datasource-backed mode is running. `X-Country-Code: ALL` requires `COUNTRY_GLOBAL_VIEW` or `*`; physical country scopes return only matching country/environment activity.
- Activity rows include successful/failed sign-ins (`LOGIN_SUCCEEDED`, `LOGIN_FAILED`), settings changes/tests, identity/user actions, connector/schedule/application/inventory/report/incident/alert writes, and bootstrap admin provisioning when it changes state. Passwords, tokens, API keys, connector secrets, and raw request bodies must not be returned.
- GET `/api/v1/audit/{id}`
- Detail reads are scoped by tenant, country, and environment; unauthorized or out-of-scope IDs return not found.
- GET `/api/v1/audit/export`
