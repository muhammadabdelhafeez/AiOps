# Runbooks — Development & Operations

## Local development (IntelliJ)
### Prerequisites
- Java 21
- PostgreSQL (local) - running on port 5432 for the default datasource-backed profile
- Neo4j (local) - running on port 7687 for graph-backed integration work
- (Optional) local SharePoint mock or dev integration config
- For UI/API scaffold work without external datastores, use the `local` Spring profile documented below.

### Backend run (Spring Boot)
1. Configure environment variables or use defaults in `application.properties`:
   - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
   - `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`
   - SHAREPOINT_* (if enabled)
   - OPENAI_* (if enabled)
2. Run Flyway migrations on startup (includes identity country/auth migration `V4__identity_country_auth.sql` and user scope uniqueness migration `V5__identity_user_scope_uniqueness.sql`).
3. Start backend from IntelliJ run config or via Maven:
   ```bash
   ./mvnw spring-boot:run
   ```
   - Canonical main class: `org.kfh.aiops.AiOpsApplication`
   - WAR initializer: `org.kfh.aiops.ServletInitializer`
   - Legacy IntelliJ launcher: `org.aiopsanalysis.AiOpsAnalysisApplication` remains as a launcher-only compatibility shim for existing run configurations. New run configurations should use the canonical main class above.
4. **Access the application at: https://localhost:8443/**
   - The root URL (`/`) automatically serves `index.html`
   - Navigate via hash routes: `https://localhost:8443/#dashboard`

### Local HTTPS run with a PFX certificate
By default, port `8443` serves HTTPS because `SERVER_SSL_ENABLED` defaults to `true`. The development server uses the local testing PFX configured in `application.properties`; production deployments must provide TLS material through deployment secrets or external file mounts.

Recommended helper script:

```powershell
Set-Location -Path "E:\NetBeansProjects\AiOpsAnalysis"
.\scripts\run-local-https.ps1
```

The script prompts for the PFX password without echoing it and starts Spring Boot with `https-local` only. PostgreSQL/Flyway/JDBC stay enabled, so User Management create/update/toggle/delete persists login users to `identity.users`.

Manual PowerShell example:


```powershell
Set-Location -Path "E:\NetBeansProjects\AiOpsAnalysis"
$env:SERVER_SSL_ENABLED = "true"
$env:SERVER_SSL_KEY_STORE = "file:E:/NetBeansProjects/AiOpsAnalysis/src/main/resources/certs/UTVDISAP01_kfhtesting_local.pfx"
$env:SERVER_SSL_KEY_STORE_PASSWORD = "<your-local-pfx-password>"
$env:SERVER_SSL_KEY_STORE_TYPE = "PKCS12"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=https-local"
```

Then open:

```text
https://172.17.134.118:8443/
```

For IntelliJ, set **Active profiles** to:

```text
https-local
```

and set environment variable `SERVER_SSL_KEY_STORE_PASSWORD` to the local PFX password if not using the dev-server default. The build excludes `src/main/resources/certs/**` from packaged artifacts, so production deployments must provide TLS material through deployment secrets or external file mounts.

#### Troubleshoot `ERR_SSL_PROTOCOL_ERROR` on `https://...:8443/`
This usually means a plain HTTP Spring Boot process is already running on port `8443`. Verify the active protocol before changing browser settings:

```powershell
curl.exe -I --connect-timeout 3 --max-time 8 http://localhost:8443/
curl.exe -k -I --tlsv1.2 --connect-timeout 3 --max-time 8 https://localhost:8443/
```

Expected results:
- HTTP diagnostic override (`SERVER_SSL_ENABLED=false`): `http://localhost:8443/` returns `200`; `https://localhost:8443/` fails with a TLS/protocol error.
- HTTPS default: `https://localhost:8443/` returns `200`; `http://localhost:8443/` returns a Tomcat bad-request response because TLS is required.

To recover, stop the existing listener and start the HTTPS profile/script:

```powershell
$conn = Get-NetTCPConnection -LocalPort 8443 -State Listen -ErrorAction SilentlyContinue
$conn | Select-Object -ExpandProperty OwningProcess -Unique | ForEach-Object { Stop-Process -Id $_ -Force }

Set-Location -Path "E:\NetBeansProjects\AiOpsAnalysis"
.\scripts\run-local-https.ps1
```

### Deprecated local profile
If startup fails with `FATAL: password authentication failed for user "KFH_AiOps"` or `SCRAM-based authentication, but no password was provided`, Spring Boot is trying to run Flyway/JPA against PostgreSQL using invalid or missing credentials.

The `local` profile no longer disables datasource, JDBC, JPA, or Flyway. User Management is always database-backed; if PostgreSQL is not available, startup should fail instead of serving a read-only user screen.

### Database-backed identity users and sign-in
- In the default datasource-backed profile, User Management creates persisted login users in PostgreSQL under `identity.users` and role assignments under `identity.user_roles`.
- `V4__identity_country_auth.sql` adds `country_code`, `environment`, `password_hash`, `last_login_at`, and `identity.role_permissions` so each user belongs to one country/environment scope. `V5__identity_user_scope_uniqueness.sql` aligns local login uniqueness with that scope: tenant + country + environment + username.
- On datasource-backed startup, `IdentityBootstrapInitializer` ensures the configured tenant and default RBAC roles exist. When `KFH_BOOTSTRAP_ADMIN_PASSWORD` is supplied, it creates the configured bootstrap admin if missing, or reactivates/resets that configured bootstrap admin if the row already exists with a different stored password hash. The password is BCrypt-hashed and is never logged or returned.
- User and role listing/creation also ensure the current `X-Tenant-Id` exists in `public.tenants` before inserting tenant-scoped roles/users. If another tenant already uses the default display name `KFH Group`, the repository creates the current tenant with a UUID-suffixed display name instead of leaving the tenant missing; this prevents PostgreSQL FK failures that surface as 409 responses from `/api/v1/users` or `/api/v1/users/roles`.
- The `BootstrapInMemoryAuthenticator` accepts the configured bootstrap admin sign-in regardless of database state, using the configured username/password/country/environment. This guarantees the operator can always reach the UI to fix configuration, even when PostgreSQL is unreachable or the bootstrap row is out of sync. Disable by clearing `kfh.identity.bootstrap.password` once named admins exist.
- Passwords submitted by the Create User form or the User table **Password** reset action are hashed with BCrypt on the backend. The Edit User form intentionally does not include password fields; use **Password** / Reset Password for password changes. Do not log request bodies for `/api/v1/users`, `/api/v1/users/{id}/password`, or `/api/v1/auth/sign-in`.
- User Management create/update/toggle/delete operations require database-backed identity storage. They no longer create memory-only users when PostgreSQL/JDBC is unavailable, because memory-only users cannot sign in. The standalone `pages/users/users.html` page loads the shared API/session scripts so all create/toggle/delete actions call `/api/v1/users`.
- The login page calls `/api/v1/auth/sign-in`; on success, the returned tenant/user/country/role/permissions become the SPA session headers.
- User Management exposes simplified role choices: **Admin**, **Operator**, and **Viewer**. The backend maps **Admin** + physical country to `COUNTRY_ADMIN`, **Admin** + **All countries** to `GLOBAL_ADMIN`, **Operator** to `NOC_OPERATOR`, and **Viewer** to `VIEWER`.
- `GLOBAL_ADMIN` receives `*` permissions and can use All groups / country switching. To create a user that starts directly in all-country scope, open User Management as a global admin, choose **All countries** in the Create User country selector, and assign **Admin**. To move an existing user to another country group, use Edit User and change **Country**; the backend requires access to both the current and destination country groups. The user then signs in with the updated country code; physical country users continue to sign in with `KW`, `BH`, or `EG`.
- Country-scoped roles receive only their configured permissions and should see only their country data. The backend denies `ALL` scope creation/listing unless the request has `*` or `COUNTRY_GLOBAL_VIEW`.
- The frontend does not create memory-only login users. Create User posts to `/api/v1/users`, which inserts into PostgreSQL and returns the persisted row.

PowerShell smoke example after creating a user from the UI:

```powershell
$body = @{
  username = "kw.operator"
  password = "<password-entered-in-ui>"
  countryCode = "KW"
  environment = "PROD"
} | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri "https://localhost:8443/api/v1/auth/sign-in" `
  -ContentType "application/json" `
  -Body $body
```

Expected: response includes `tenantId`, `userId`, `countryCode`, `roleId`, `userRole`, and `permissions`, and does not include password or password hash.

For all-country global users, set `countryCode = "ALL"` and verify the response returns `countryCode: "ALL"`, `countryName: "All countries"`, and `permissions` containing `*`.

First-admin bootstrap example for an empty PostgreSQL database:

```powershell
Set-Location -Path "E:\NetBeansProjects\AiOpsAnalysis"
$env:DB_URL = "jdbc:postgresql://localhost:5432/KFH_AiOps"
$env:DB_USERNAME = "KFH_AiOps"
$env:DB_PASSWORD = "<your-local-postgres-password>"
$env:KFH_BOOTSTRAP_ADMIN_USERNAME = "admin"
$env:KFH_BOOTSTRAP_ADMIN_PASSWORD = "<temporary-admin-password>"
$env:KFH_BOOTSTRAP_ADMIN_COUNTRY = "KW"
$env:KFH_BOOTSTRAP_ADMIN_ENVIRONMENT = "PROD"
.\mvnw.cmd spring-boot:run
```

After the first successful login, create named operator/admin users in User Management and rotate or disable the temporary bootstrap admin according to the environment access policy. If the login page says the user is invalid, restart the datasource-backed application after setting the bootstrap password so startup can create or reconcile the stored BCrypt hash. Also verify that the application is not running with the `local` profile.

If login still fails after restart:
- Check whether `KFH_BOOTSTRAP_ADMIN_PASSWORD` is set in the process environment; environment variables override `application.properties`. In PowerShell, use single quotes for passwords containing `$`, otherwise PowerShell may expand `$...` as a variable before Spring receives it.
- Check startup logs for `Identity bootstrap admin created`, `Identity bootstrap admin updated`, or `Identity bootstrap admin is ready`. If none appears, the running app is using an old build, a different profile, or no datasource-backed `JdbcTemplate`.
- If the log says `reason=no-database-and-not-bootstrap-admin`, the datasource is unavailable and the in-memory bootstrap fallback also rejected the request. Newer builds include non-secret bootstrap diagnostics in the same log line:
  - `bootstrapEnabled=false`: `kfh.identity.bootstrap.enabled` or `KFH_BOOTSTRAP_ENABLED` disabled the fallback.
  - `bootstrapPasswordConfigured=false`: no bootstrap password reached the JVM.
  - `usernameMatched=false`: the submitted username does not match the configured bootstrap username.
  - `countryMatched=false`: the selected country does not match the configured bootstrap country. Configure `KFH_BOOTSTRAP_ADMIN_COUNTRY=ALL` for global bootstrap access.
  - `environmentMatched=false`: the selected environment does not match the configured bootstrap environment.
  - `passwordMatched=false`: the submitted password differs from the configured value. On PowerShell, set passwords containing `$` with single quotes, for example `$env:KFH_BOOTSTRAP_ADMIN_PASSWORD = 'value-with-$-literal'`.
- Check failed sign-in logs for `sign-in rejected ... usernameMatches=... scopedMatches=... activeScopedMatches=... passwordReadyScopedMatches=...`:
  - `usernameMatches=0`: username is not in `identity.users`.
  - `scopedMatches=0`: username exists, but not for the selected country/environment.
  - `activeScopedMatches=0`: account is disabled.
  - `passwordReadyScopedMatches=0`: account has no stored password hash.
  - all values positive: password mismatch or an environment override changed the configured password.

If User Management fails to list roles or create a user:
- `422 VALIDATION_ERROR`: confirm the form sent both username and password in the request attributes.
- `409 CONFLICT`: the same username already exists for the selected tenant/country/environment scope, or PostgreSQL rejected a unique/integrity constraint. Confirm `V5__identity_user_scope_uniqueness.sql` has run and restart the app on the updated build so tenant bootstrap can repair missing `public.tenants` rows before role/user writes.
- Startup failure or connection errors: PostgreSQL is not reachable or credentials are invalid. Fix `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, then restart; the application should not run User Management in read-only mode.
- Any unexpected server error should be correlated with the request `X-Correlation-Id`; do not log or paste passwords or full request bodies while troubleshooting.

At startup, `IdentityStorageReadinessLogger` prints this line so you can confirm whether `/api/v1/users` writes will work:

```text
INFO  ... IdentityStorageReadinessLogger : Identity storage ready: database-backed login user create/update/toggle/delete enabled (activeProfiles=[])
```

If startup cannot connect to PostgreSQL, restart with real PostgreSQL credentials:

```powershell
Set-Location -Path "E:\NetBeansProjects\AiOpsAnalysis"
$env:DB_URL = "jdbc:postgresql://localhost:5432/Kfh_AiOps"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<your-postgres-password>"
.\mvnw.cmd spring-boot:run
```

Do not disable datasource, JDBC, or Flyway when you need login users.

The easiest way to start in datasource-backed mode is the helper script, which forces the `local` profile off, prompts for the DB password securely, and never prints secrets:

```powershell
Set-Location -Path "E:\NetBeansProjects\AiOpsAnalysis"
./scripts/run-with-database.ps1
```

The default bootstrap admin is now a true global admin (`kfh.identity.bootstrap.country-code=ALL`, `role-name=GLOBAL_ADMIN`). The bootstrap initializer also provisions the configured bootstrap row in PostgreSQL on datasource-backed startup, so named DB admins can be created immediately from User Management.

Every HTTP request now emits one secret-safe Java console line from `HttpActionLoggingFilter`, for example:

```text
http action method=POST path=/api/v1/users status=503 durationMs=12 tenantId=<uuid> userId=<uuid> countryCode=KW environment=PROD correlationId=<correlation-id>
```

The action log intentionally excludes query strings, request bodies, cookies, authorization headers, passwords, tokens, connector secrets, and raw telemetry payloads.

### Audit Activity page operations
- Open `https://<host>:8443/index.html#audit` to view the redesigned **Audit Activity** console.
- The page calls `GET /api/v1/audit` and displays only real application actions recorded by authentication and write flows such as successful/failed login, user management, app settings update/test, connector, schedule, alert acknowledgement, incident, inventory, application, report, and bootstrap identity provisioning operations. It reads PostgreSQL `identity.audit_log` first so activity survives browser refresh and webserver restart. It does not render seeded, sample, generated, or dummy audit rows.
- If the page is empty in datasource-backed mode, perform a real action (for example sign in, intentionally fail a sign-in test with a non-production account, update settings, test a connector, run a schedule, update a user, or generate a report), then click **Refresh**. Persistent rows should remain visible after restart for the same tenant/country/environment scope.
- Audit list/detail reads are tenant-scoped and country-aware. `countryCode=ALL` requires global country visibility; country-scoped users should see only their country/environment activity.
- CSV/JSON buttons export the currently loaded and filtered activity rows in the browser. Do not paste exported payloads into tickets if they contain usernames, user IDs, correlation IDs, or operational context not needed for the investigation.
- Authentication and settings audit rows are secret-safe: passwords, tokens, API keys, connector secrets, and raw request bodies must not appear in logs, UI rows, or exports. Settings audit rows include key names only, not submitted secret values.

For Phase 1 default-profile launches, Redis and Neo4j actuator health probes are disabled by default to avoid noisy `localhost:6379` / `localhost:7687` connection warnings when those services are not running. When validating real Redis or Neo4j connectivity, enable them explicitly:

```powershell
$env:REDIS_HEALTH_ENABLED = "true"
$env:NEO4J_HEALTH_ENABLED = "true"
.\mvnw.cmd spring-boot:run
```

For default datasource-backed startup, set valid PostgreSQL credentials instead:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/KFH_AiOps"
$env:DB_USERNAME = "KFH_AiOps"
$env:DB_PASSWORD = "<your-local-postgres-password>"
.\mvnw.cmd spring-boot:run
```

### Backend API scaffold smoke checks
The Phase 1 backend exposes frontend-aligned `/api/v1/**` endpoints backed by a tenant-aware in-memory read model. Use these checks after startup; replace UUIDs with your test tenant/user when available.

```powershell
$headers = @{
  "X-Tenant-Id" = "11111111-1111-1111-1111-111111111111"
  "X-User-Id" = "22222222-2222-2222-2222-222222222222"
  "X-Correlation-Id" = "local-smoke-001"
}
Invoke-RestMethod -Uri "http://localhost:8443/api/v1/dashboard/kpis" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8443/api/v1/incidents" -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8443/api/v1/applications" -Headers $headers
```

- API requests without valid `X-Tenant-Id` and `X-User-Id` UUID headers are rejected.
- The scaffold grants all permissions unless `X-Permissions` is supplied; production must replace this with enterprise JWT/OIDC authorities before go-live.
- Connector create/update responses strip `secretsPlain` and expose only `secretsMask`; do not put real credentials into local smoke-test payloads.
- The scaffold is not the telemetry store. Raw logs must still flow through object storage + the custom index engine in later phases.

### Frontend run
1. Start Spring Boot server (see above)
2. Open browser at **http://localhost:8443/** (port 8443 as configured)
3. **No external dependencies required** - all resources are local:
   - React from `vendor/js/react.production.min.js`
   - Outfit font from `vendor/css/outfit-font.css`
   - DOMPurify from `dompurify/purify.min.js` (XSS protection)
   - HTML Sanitizer from `vendor/js/html-sanitizer.js`
4. The SPA shows a normal login form before routing: enter username, enter password, and use the single country chooser button for `KW`, `BH`, or `EG`. In datasource-backed mode the credentials are verified through `/api/v1/auth/sign-in`; local scaffold mode can fall back to browser-only session derivation for frontend work.
5. The SPA router handles navigation via URL hash (e.g., `#dashboard`, `#incidents`, `#users`)
5. File structure:
   ```
   static/
   ├── index.html              # Main SPA entry point
   ├── shared/                 # Shared resources
   │   ├── css/base.css        # Complete CSS utilities (Tailwind-like, offline)
   │   ├── js/
   │   │   ├── config.js       # App configuration
   │   │   ├── api-client.js   # API wrapper (tenant/user headers)
   │   │   ├── utils.js        # Utility functions (XSS protection via DOMPurify)
   │   │   └── router.js       # SPA navigation
   │   └── components/
   │       ├── sidebar.js      # Navigation sidebar
   │       └── header.js       # Top header bar
   ├── pages/                  # Page modules
   │   ├── dashboard/          # Operations overview
   │   ├── incidents/          # Incident management
   │   ├── connectors/         # Data connector management
   │   ├── alerts/             # Alert explorer
   │   ├── applications/       # Application catalog
   │   ├── inventory/          # Resource inventory
   │   ├── reports/            # Report packs
   │   ├── connectors/         # Data connectors (admin)
   │   ├── schedules/          # Job schedules (admin)
   │   ├── users/              # User management (admin)
   │   ├── settings/           # System settings (admin)
   │   └── audit/              # Audit logs (admin)
   ├── vendor/                 # Third-party libraries (local)
   │   ├── css/outfit-font.css # Local font
   │   ├── fonts/              # Font files
   │   └── js/                 # React, sanitizers
   └── dompurify/              # XSS sanitization library
   ```
6. Each page module has:
   - `index.html` - Page HTML template
   - `script.js` - Page JavaScript logic (PageXxx module)
   - `styles.css` - Page-specific styles
7. API client automatically includes tenant, user, country, environment, permission, and correlation headers from the login session.

### User Management local operation
- Open `http://localhost:8443/index.html#users` after signing in to view User Management.
- To open the create-user modal directly, use `http://localhost:8443/index.html#users?create=1`.
- Use the country filter to switch the active country scope and reload users; global users can select **All countries** to see all-country identity profiles plus per-country profiles, while users without cross-country permission remain constrained by backend country guards.
- Use the role filter for lightweight client-side filtering of the users returned for the active country/environment scope.
- The create form posts to `POST /api/v1/users` with `UiWriteRequest` and current country/environment scope. The page no longer creates local fallback login users; creation requires the API and database-backed identity storage so new users can sign in for their created country/environment.
- Do not enter passwords or secrets in the user form. Production authentication must be provided by enterprise SSO/JWT and backend RBAC, not by the local browser scaffold.

### Database-backed local UI/API run
Run the normal database-backed application for local UI/API work. User Management requires PostgreSQL so created users are inserted into `identity.users` and can sign in later.

```powershell
.\mvnw.cmd spring-boot:run
```

- Open: `https://localhost:8443/`
- Configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` before startup when defaults do not match your PostgreSQL instance.
- Do not disable datasource, JDBC, or Flyway for User Management testing.

## Operational runbook
### Hourly scheduled run
- Schedule triggers connector runs + normalization + correlation + evidence/report jobs.
- Output:
  - new incidents
  - updated recurring incidents
  - evidence CSVs
  - report pack index
  - Teams summary notification

### On-demand investigation
- Operator triggers:
  - evidence pack generation
  - AI summary generation
  - correlation refresh (if needed)

## Troubleshooting
### If Neo4j is down
- Expected behavior:
  - incident engine still works using fingerprints
  - correlation/topology features degraded
- Action:
  - restore Neo4j, re-run graph upsert jobs

### If OpenAI is down
- Expected behavior:
  - AI summaries marked pending
  - jobs queued for retry
- Action:
  - restore OpenAI, replay outbox AI jobs

### If SharePoint is down
- Evidence artifacts may fail to upload.
- Action:
  - retry artifact upload jobs, ensure report pack index still created.

## Support checks
- check last connector_run status
- check outbox queue depth
- check schedule_run status
- check report pack index generation

### Dependency security validation
- Before releasing backend builds, run Maven tests and dependency resolution after dependency upgrades.
- Automated context tests use an embedded H2 datasource in PostgreSQL compatibility mode; local PostgreSQL is not required for `mvn test`.
- PostgreSQL JDBC must remain at `42.7.11` or newer to remediate the SCRAM PBKDF2 CPU exhaustion DoS advisory.
- Azure Identity must remain at `1.12.2` or newer to remediate the Azure Identity/MSAL elevation-of-privilege advisory.
- If PostgreSQL endpoints or JDBC URLs become tenant/user configurable in the future, require trusted endpoints, TLS server identity verification, SSRF controls, and tenant-scoped audit logs for any configuration writes.

### Connector management operations
- Use UI Connectors page to configure BMC and other monitoring source integrations
- All connector operations require tenant/user headers and proper RBAC permissions:
  - `config.connector.read`: view connectors
  - `config.connector.write`: create/update/delete connectors  
  - `config.connector.test`: test connector connections
- Connector tests run async via outbox pattern to prevent UI blocking
- BMC connector includes SSRF protection - only corporate domains allowed
- Secrets encrypted at rest; never logged or returned in API responses
- All connector changes generate audit logs with before/after summaries
- Failed connectors enter degraded mode; system continues operating without them

### Settings validation
- Use UI Settings page to trigger configuration tests (Azure OpenAI, Neo4j, PostgreSQL, SharePoint, Teams) — each test enqueues an outbox job via POST /api/admin/settings/test with required tenant/user headers and `settings:write` permission.
- Saves POST /api/admin/settings are audited (tenantId, userId, action=settings.update, correlationId). Ensure secrets are masked client-side and not logged.

### Connector management

#### Adding a new connector
1. Navigate to Admin > Connectors
2. Click "Add Connector" button
3. Select connector type (BMC, SCOM, SolarWinds, etc.)
4. Fill in configuration:
   - Name (unique within tenant)
   - Base URL (SSRF validated)
   - Authentication credentials
   - Collection window and settings
5. Click "Test Connection" to verify
6. Click "Save" to create

#### Testing connectors
- POST /api/v1/connectors/{id}/test
- Creates a TEST run record
- Validates authentication and API connectivity
- Results logged in connector run history

#### Manual data collection
- POST /api/v1/connectors/{id}/collect-now
- Creates QUEUED run record
- Worker picks up via outbox pattern
- Supports custom window override

#### Revealing secrets (ADMIN only)
- POST /api/v1/connectors/{id}/reveal
- Requires reason for audit trail
- Decrypts and returns single field
- Critical audit log generated

#### Connector health monitoring
Health badges computed from:
- Last test result (SUCCESS/FAILED)
- Last collect result
- Enabled/disabled state

#### Troubleshooting connector issues
1. Check connector run history for errors
2. Review run logs for detailed steps
3. Test connection manually
4. Verify credentials haven't expired
5. Check SSRF validation for URL changes
6. Review audit log for recent changes

#### Connector secrets encryption
- Secrets encrypted using AES-256-GCM
- Master key from INSIGHT_MASTER_KEY env var
- Encrypted values prefixed with "$enc$"
- Never logged or returned in responses

#### Runtime credentials and TLS material
- Do not commit database, Neo4j, Azure OpenAI, SharePoint, or TLS keystore secrets to source control.
- Provide runtime values through environment variables or deployment secret stores:
  - `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
  - `NEO4J_URI`, `NEO4J_USERNAME`, `NEO4J_PASSWORD`, `NEO4J_DATABASE`
  - `AZURE_OPENAI_*`, `AZURE_SHAREPOINT_*`
  - `SERVER_SSL_ENABLED`, `SERVER_SSL_KEY_STORE`, `SERVER_SSL_KEY_STORE_PASSWORD`
  - `INSIGHT_MASTER_KEY`
- TLS keystores must be mounted/provided by the deployment platform; packaged keystores are prohibited.
- For local development, use a local untracked secrets file or shell environment variables and never print secret values in logs or build output.

