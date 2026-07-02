# Runbooks — Development & Operations

> **Architecture authority:** [`docs/CAUSAL_PIPELINE.md`](./CAUSAL_PIPELINE.md). All operational levers below tune the funnel — they do not change its rules.

## Causal Funnel — operational quick reference

The platform handles **100,000 alerts every 20 minutes** with **1–3 Azure OpenAI 5.5 calls per cycle** by funneling raw events down to a single `EvidencePack` per real incident. Operators tune the funnel; the funnel itself is deterministic.

### Daily checks
- `/actuator/health` reports each datastore + AI provider. Any `degraded` flag means the platform is still serving, just with a fallback (see [CAUSAL_PIPELINE §11](./CAUSAL_PIPELINE.md)).
- Dashboard "AI cost today" tile vs `kfh.ai.router.azure.daily-call-budget-per-tenant` — if at > 70% by mid-day, ops should investigate cache hit rate.
- `incident.ai_router_decisions` last hour: model split (DeepSeek vs Azure), cache hits, escalations.
- Redis `INFO memory` + key counts on `dedup:*`, `health:*`, `ai:summary:*` per country/env.

### AI cost spike — first response
1. Check Redis `ai:summary:known-issue:*` count and TTL. A drop indicates cache invalidation. Confirm `kfh.ai.router.cache-ttl-hours` is `6`.
2. Check `incident.ai_router_decisions` for new `reason=NOVEL_PATTERN` — every truly new pattern costs an Azure call.
3. Confirm `CostGuard` thresholds: `kfh.ai.router.azure.daily-call-budget-per-tenant` and `monthly-usd-budget-per-tenant`. Lower temporarily if a runaway pattern is observed.
4. If escalation rate is unjustified, raise `kfh.ai.router.deepseek.confidence-threshold` (default `0.85`) to keep more cases on DeepSeek.

### Pipeline backlog — first response
1. Outbox table size: `SELECT event_type, count(*) FROM ops.outbox_events WHERE processed_at IS NULL GROUP BY 1;`
2. Confirm `OutboxPublisher` is running and virtual threads are healthy (`thread_count` metric).
3. Resilience4j circuit-breaker state on `azure-openai`, `deepseek`, `neo4j`. Open CB → events queue up; correct underlying issue or temporarily widen `failure-rate-threshold`.
4. Custom Index Engine shard lag: `kfh.index.write-batch-size` × shard count vs ingestion rate.

### Redis (runtime hot state) — configure & verify
1. Configure the server in **Settings → Servers & Index → Add Server → Redis Server**: host = the **private IP** the backend can reach (not `localhost` — the test's SSRF guard blocks loopback/metadata), port, password; leave **Username blank** unless the server uses an ACL user; enable **TLS** only if the server presents a JVM-trusted cert. Use **Test & Save** — a green result means the backend reached Redis (`PING` → `PONG`).
2. The runtime client (`RedisConnectionProvider`) reads this saved, encrypted row per `(tenant, country, environment)` — **not** `spring.data.redis.*` (bootstrap fallback only). Logical **DB 0 only**; isolation is by key prefix `dedup:{country}:{env}:…` / `health:{country}:{env}:…`.
3. Tunables: `kfh.dedup.window-seconds` (default 600; keep 120–600 per §12), `kfh.redis.command-timeout-ms`, `kfh.redis.connect-timeout-ms`.
4. **Degraded mode:** if Redis is unconfigured or unreachable, `FingerprintDedupService` fails **open** — every event is treated as new and a secret-safe WARN is logged — so ingestion is never blocked. Expect more duplicate alerts until Redis is restored; no data loss.

### Forbidden operational moves
- ❌ Do **not** raise `kfh.ai.router.azure.daily-call-budget-per-tenant` above the contracted Azure TPM (you will burn quota for the whole tenant).
- ❌ Do **not** point Redis at a logical DB > 0 for "country isolation" — always key-prefix on DB 0.
- ❌ Do **not** disable `EvidencePackValidator` to "unblock" a hallucination incident — fix the pack content instead.
- ❌ Do **not** copy/paste `EvidencePack` JSON containing customer data into screenshots, tickets, or chat. Pack IDs only.

---



## Local development (IntelliJ)
### Prerequisites
- Java 21
- PostgreSQL (local) - running on port 5432 for the default datasource-backed profile
- Neo4j (local) - running on port 7687 for graph-backed integration work
- Docker Desktop or another local Docker daemon when running PostgreSQL-backed Testcontainers integration tests such as `JdbcSettingsMetadataStoreTest`
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

### Targeted PostgreSQL settings-store integration test
- `JdbcSettingsMetadataStoreTest` now validates the real PostgreSQL `config.integration_settings` persistence path by starting PostgreSQL with Testcontainers and applying the normal Flyway migrations from `classpath:db/migration`.
- Local command:
  ```powershell
  Set-Location -Path "E:\NetBeansProjects\AiOpsAnalysis"
  .\mvnw.cmd -q "-Dtest=JdbcSettingsMetadataStoreTest" test
  ```
- Docker is required for this targeted test. In environments where Docker is unavailable, the test is skipped instead of falling back to H2 SQL rewriting, because the goal is to validate the production PostgreSQL upsert and scope behavior only.

### Configure Azure OpenAI GPT 5.4 provider from Settings
1. Sign in with a role that has `SETTINGS_WRITE`.
2. Open `https://localhost:8443/#settings` or the environment URL, then choose **Add AI Provider**.
3. Select **Provider = Azure OpenAI**; it is the only provider shown in the Add AI Provider dialog.
4. In **Azure OpenAI Model**, keep **GPT 5.4**; it is the only model shown for this ready connector.
5. Complete only the integration fields shown in the popup:
   - **Endpoint URL:** `https://<resource>.services.ai.azure.com/openai/v1` or the Azure portal resource root such as `https://<resource>.cognitiveservices.azure.com/`
   - **Deployment / Model Name:** `gpt-5.4`
   - **API Key:** paste the Azure OpenAI key for Test/Save
   - **Max Output Tokens:** per-call output token cap
   - **Monthly Token Limit:** governed usage cap; use `0` for no configured monthly cap
   - **Timeout Seconds:** bounded live-test timeout
6. The popup intentionally does not show **Model Usage**, **Authentication**, or **API Style** controls. The backend fixes these as GPT + Azure OpenAI API key + Responses API for this ready connector.
7. Use **Test Only** to validate the endpoint/deployment/API key without saving; the popup shows a **Testing…** state and then an inline pass/fail result with latency and checked endpoint. Use **Test & Save** after the same readiness check should persist the provider.
8. The API key is encrypted server-side and returned only as a mask. The live test sends only a minimal readiness prompt to the Responses API and never includes telemetry, incidents, API keys, bearer tokens, passwords, or raw payloads in the response/audit details. If the operator pastes a portal resource root endpoint, the backend calls `/openai/v1/responses`; if the operator pastes `/openai/v1`, it appends `/responses` exactly once. For Azure AI Foundry/OpenAI v1 endpoints that reject the Azure `api-key` header with `401 Unauthorized`, the backend retries once using `Authorization: Bearer <api-key>` without logging or returning the key.
9. Persistence follows the provider row scope. A provider saved with `countryCodes=["ALL"]` is stored under all-country Settings scope and must still appear after a web-server restart for any matching country session. A provider saved with `countryCodes=["KW"]` remains visible after restart for Kuwait sessions only; Bahrain/Egypt sessions should not see it.
10. Multi-country providers follow the same rule. Example: `countryCodes=["KW","BH"]` must appear when the active country is Kuwait or Bahrain, and must disappear when the operator switches the sidebar country to Egypt. The backend filters the returned Settings rows by the active request country on every load, including after restart.

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
- `GLOBAL_ADMIN` receives `*` permissions. The sidebar country switcher and User Management country filters/dropdowns are shown only when the signed-in session's home country is `ALL`; physical-country users see a locked Platform + assigned-country label with no dropdown options for other countries. To create a user that starts directly in all-country scope, sign in with `countryCode=ALL`, open User Management, choose **All countries** in the Create User country selector, and assign **Admin**. To move an existing user to another country group, use Edit User and change **Country**; the backend requires access to both the current and destination country groups. The user then signs in with the updated country code; physical country users continue to sign in with `KW`, `BH`, or `EG`.
- Country-scoped roles receive only their configured permissions and should see only their country data. `COUNTRY_ADMIN` includes `AUDIT_READ` so country admins can review audit activity for their signed-in country/environment, but it does not include `COUNTRY_GLOBAL_VIEW`. The backend denies `ALL` scope creation/listing unless the request has `*` or `COUNTRY_GLOBAL_VIEW`.
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

For all-country global users, set `countryCode = "ALL"` and verify the response returns `countryCode: "ALL"`, `countryName: "All countries"`, and `permissions` containing `*`. If the configured bootstrap admin or a database-backed identity is scoped to `ALL`, the backend also accepts a physical country selection from the login screen for operational convenience when no exact physical-country login matches, but the response still returns `countryCode: "ALL"` so the sidebar country switcher and all-country audit/user filters are visible after login.

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
- Check startup logs for `Identity bootstrap admin created`, `Identity bootstrap admin updated`, or `Identity bootstrap admin is ready`. If none appears, the running app is using an old build, a different profile, or no datasource-backed `JdbcTemplate`. If the browser still receives `403` after a code change, stop the existing Java process and restart the webserver so the new WAR/classes are loaded.
- If the log says `reason=no-database-and-not-bootstrap-admin`, the datasource is unavailable and the in-memory bootstrap fallback also rejected the request. Newer builds include non-secret bootstrap diagnostics in the same log line:
  - `bootstrapEnabled=false`: `kfh.identity.bootstrap.enabled` or `KFH_BOOTSTRAP_ENABLED` disabled the fallback.
  - `bootstrapPasswordConfigured=false`: no bootstrap password reached the JVM.
  - `usernameMatched=false`: the submitted username does not match the configured bootstrap username.
  - `countryMatched=false`: the selected country does not match the configured bootstrap country. Configure `KFH_BOOTSTRAP_ADMIN_COUNTRY=ALL` for global bootstrap access.
  - `environmentMatched=false`: the selected environment does not match the configured bootstrap environment.
  - `passwordMatched=false`: the submitted password differs from the configured value. On PowerShell, set passwords containing `$` with single quotes, for example `$env:KFH_BOOTSTRAP_ADMIN_PASSWORD = 'value-with-$-literal'`.
- Check failed sign-in logs for `sign-in rejected ... usernameMatches=... scopedMatches=... activeScopedMatches=... passwordReadyScopedMatches=...`:
  - Bootstrap diagnostics in the same log line use booleans such as `bootstrapUsernameMatched`, `bootstrapCountryMatched`, `bootstrapEnvironmentMatched`, and `bootstrapPasswordMatched`. These flags are safe to share in tickets; do not share the password or browser request body.
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

### Database-backed connector installation persistence
- Connector install/configuration writes are durable PostgreSQL operations against `config.connectors`; the backend must not acknowledge a connector install by storing it only in the in-memory `CommandCenterReadModel`.
- If PostgreSQL/JDBC connector persistence is not wired, `POST /api/v1/connectors`, `PUT /api/v1/connectors/{id}`, `DELETE /api/v1/connectors/{id}`, and `PATCH /api/v1/connectors/{id}/toggle` fail closed with `503 application/problem+json` and code `CONNECTOR_PERSISTENCE_UNAVAILABLE`. Fix `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`, restart the webserver, and retry the install.
- To verify an installed BMC/AppDynamics/vROps/SCOM/EMCO connector after restart, sign in with the same tenant/country/environment scope and call `GET /api/v1/connectors`. The row should still be returned from `config.connectors`; plaintext secrets must never be returned. Saved credentials are represented only by `secretsMask=configured` and are encrypted in `config.connector_secrets.secret_enc`.
- The sidebar country switcher changes `X-Country-Code` only. It must preserve the authenticated `X-Tenant-Id`, so a global user choosing **All countries** sees all same-tenant `KW`, `BH`, and `EG` connector rows for the selected environment. `V9__normalize_connector_country_scope_tenant.sql` repairs older Phase 1 scaffold connector rows that were accidentally saved under the hard-coded Bahrain/Egypt scaffold tenant IDs.
- If a connector exists in PostgreSQL but does not show in the UI, compare the request headers `X-Tenant-Id`, `X-Country-Code`, and `X-Environment` with the connector row's tenant and config scope. Country-scoped users see only their physical country; all-country users require `ALL` scope plus global permission. If `X-Country-Code=ALL` shows Kuwait rows only, verify the browser has loaded the fixed `shared/js/config.js`, clear the old local storage session if needed, and confirm Flyway applied `V9__normalize_connector_country_scope_tenant.sql`.

### Editing connector details after a passed test
- A **PASS** result on the connector Health tab means the currently saved profile validated successfully. It is not a configuration lock and does not prevent endpoint, schedule, owner, notes, or credential rotation updates.
- From the Health tab, choose **Edit Configuration** to return to the editable form. Alternatively, open the connector drawer and choose the **configuration** tab.
- To change only non-secret details, update the form and leave credential fields blank. The backend keeps the existing encrypted BMC access keys, AppDynamics Basic Auth username/password, vROps username/password, SCOM WinRM username/password, or EMCO KFH/CCTV SQL credentials.
- The UI intentionally blanks credential inputs after Save; do not expect passwords or access keys to reappear. Use the **Credential Status** row (`Encrypted credentials saved`) plus a successful **Test Connection** to confirm saved encrypted secrets are being used.
- To rotate BMC or AppDynamics credentials, enter the fields that changed, save, and run **Test Connection** again. To rotate vROps or SCOM credentials, enter both username and password together. To rotate EMCO credentials, enter the KFH username/password pair and/or the CCTV username/password pair together. Plaintext credentials are never returned to the browser, written to logs, pasted into runbook output, or included in evidence packs.
- If **Test Connection** fails immediately after changing only endpoint/schedule/owner/notes fields, verify the running backend includes the connector secret metadata merge fix so `config.connector_secrets.secret_enc` preserves the encrypted `secrets` object during metadata-only updates. Do not inspect or print decrypted secret values; validate only `secretsMask`, test status, and correlation ID.
- If Save fails after a PASS, verify the signed-in user still has connector write permission, the request country/environment matches the connector scope, and BMC URL/path validation did not reject an unsafe endpoint.

### EMCO Ping Monitor connector setup
- Open `https://<host>:8443/index.html#connectors`, choose **Connector Marketplace**, open **EMCO Ping Monitor**, select the physical country scope (`KW`, `BH`, or `EG`), and install the connector. The install creates an enabled placeholder with `configurationStatus=PENDING` and no plaintext secrets returned to the browser.
- Configure the connector with the SQL Server host/IP (default `DCVSAMDB01`), port (`11433` unless changed by DBA), KFH database, CCTV database, lookback minutes, SQL login timeout, query timeout, SQL encryption flag, and write-only KFH/CCTV SQL username/password pairs. Do not paste credentials into notes, tickets, screenshots, command output, or docs.
- The backend constructs JDBC URLs server-side using `jdbc:sqlserver://<host>:<port>;databaseName=<database>;encrypt=<true|false>;trustServerCertificate=<true|false>;loginTimeout=<seconds>` and never stores that URL with credentials. Flyway migration `V10__allow_emco_connector_type.sql` must be applied before saving EMCO rows because `config.connectors.type` is allowlisted.
- The **Test Connection** action opens separate JDBC connections for the KFH and CCTV EMCO databases, sets `PreparedStatement#setQueryTimeout`, runs bounded `TOP (1)` host-state/connection-quality probes against `db_owner.tb_host_events`/`db_owner.tb_hosts` for KFH and `dbo.tb_host_events`/`dbo.tb_hosts` for CCTV, and returns only pass/fail, sanitized messages, endpoint, correlation ID, and step status. It does not return raw EMCO rows, labels, host descriptions, usernames, passwords, or SQL payloads.
- Keep SQL encryption enabled by default. Use **Trust SQL Server certificate for this test** only for governed dev/hybrid troubleshooting while SQL Server certificate trust is remediated; return to `trustServerCertificate=false` after the connector host trusts the issuing CA chain.
- If an EMCO test fails, validate network reachability from the application host to the configured SQL Server port, confirm both database names and schemas (`db_owner` for KFH, `dbo` for CCTV), and confirm the service accounts have read access to `tb_host_events` and `tb_hosts`. Never run diagnostic SQL that prints credentials or raw host-event payloads into shared logs.

### Microsoft SCOM connector setup
- Open `https://<host>:8443/index.html#connectors`, choose **Connector Marketplace**, open **Microsoft SCOM**, select the physical country scope (`KW`, `BH`, or `EG`), and install the connector. The install creates an enabled placeholder with `configurationStatus=PENDING` and no plaintext secrets returned to the browser.
- Configure the connector with the SCOM management server FQDN/IP or WinRM URL ending in `/wsman`, domain, WinRM port (`5986` for HTTPS or `5985` for HTTP), PowerShell authentication method (Kerberos preferred), lookback hours, timeout, and write-only username/password.
- The **Test Connection** action runs a bounded local PowerShell readiness probe: `Invoke-Command` to the management server, `Import-Module OperationsManager`, `New-SCOMManagementGroupConnection`, and `Get-SCOMAlert | Select-Object -First 1` to validate the expected SCOM field shape. The probe returns only pass/fail, sanitized messages, endpoint, correlation ID, and step status; it does not return raw SCOM alerts or credentials.
- For cross-domain testing, for example when the application host belongs to a testing/local domain and the SCOM server/service account belongs to a corporate domain, either set **Domain** to the corporate domain and enter the short service account name, or enter a pre-qualified username (`domain\user` or UPN). The backend now precomputes the qualified principal in Java and passes only `KFH_AIOPS_SCOM_QUALIFIED_USERNAME` to the PowerShell child process.
- If the SCOM test still reports `Unexpected token '\$username'` or shows `$domain$username`, the running server is still using an older probe script. Rebuild/redeploy or restart the backend/WAR so the updated script is loaded; this parser error occurs before WinRM credential validation starts.
- For the current dev topology (`UTVDISAP01.KFHTesting.local` connecting to the single production SCOM endpoint `dcvscoap12.corp.kfh.kw`), keep WinRM HTTPS on port `5986`; do not require port `5985`. The supported path is: renew/rebind a non-expired WinRM HTTPS certificate on the SCOM server with SAN/CN matching `dcvscoap12.corp.kfh.kw`, install the issuing corporate root/intermediate CA chain into the dev server's Local Computer trust stores, ensure DNS from the dev server resolves the corp FQDN, then retest with `verifySsl=true` where possible.
- If the SCOM test reports `The SSL certificate is signed by an unknown certificate authority`, import the issuing corporate root/intermediate CA chain on the connector host. If it reports `CN does not match the hostname`, configure the connector with the hostname in the certificate SAN/CN or reissue the WinRM certificate with SAN `dcvscoap12.corp.kfh.kw`. If it reports `The SSL certificate is expired`, renew and rebind the WinRM HTTPS certificate on the SCOM management server; clearing **Verify TLS Certificate Chain** can only relax CA/CN/revocation checks and does not make an expired WinRM HTTPS certificate valid.
- If `Test-NetConnection dcvscoap12.corp.kfh.kw -Port 5986` succeeds from `UTVDISAP01.KFHTesting.local`, and local `Test-WSMan` succeeds on the SCOM server, but dev-host `Test-WSMan` reports `The SSL certificate could not be checked for revocation`, the remaining blocker is CRL/OCSP reachability from the dev connector host. Export/copy the SCOM WinRM public certificate to the dev host and run `certutil -urlfetch -verify <cert-file.cer>` to identify the CRL/AIA/OCSP URLs, then allow the dev host to reach those URLs or publish reachable CRLs. As a governed temporary connector test only, enable **Disable certificate validation for this SCOM test** in the SCOM connector UI; the probe keeps HTTPS/5986 encryption but passes `-SkipCACheck`, `-SkipCNCheck`, and `-SkipRevocationCheck` to PowerShell.
- For the current KFH SCOM certificate chain, `certutil -urlfetch -verify C:\temp\dcvscoap12-winrm.cer` from `UTVDISAP01.KFHTesting.local` confirmed `CRYPT_E_REVOCATION_OFFLINE`: the dev host cannot retrieve KFH CA revocation material. Windows must be able to reach the CA distribution endpoints, especially `http://pki.kfh.kw/cdp/SHVSORCA01-CA.crl` and `http://pki.kfh.kw/aia/SHVSORCA01-CA.crt`, or an equivalent reachable CRL/AIA publication path. If the environment uses an HTTP proxy, configure the machine WinHTTP proxy used by WinRM/cert chain validation (`netsh winhttp show proxy`, then `netsh winhttp import proxy source=ie` or an approved explicit proxy). Browser access alone is not enough.
- If there is no domain trust between `KFHTesting.local` and `corp.kfh.kw`, Kerberos may fail after TLS is corrected. In that case, keep HTTPS/5986 and test `Negotiate` (or the enterprise-approved remoting authentication method) with a pre-qualified corporate service account; avoid HTTP/5985 unless explicitly approved.
- To rebind WinRM HTTPS when a valid certificate already exists in `Cert:\LocalMachine\My`, first verify the candidate certificate has `Server Authentication`, `HasPrivateKey=True`, is not expired, and has SAN/CN matching the configured SCOM hostname. Then use WinRM's native `KEY="VALUE"` syntax, not PowerShell single-quoted hashtable values:

  ```powershell
  $thumbprint = "<VALID_SERVER_AUTH_CERT_THUMBPRINT>"
  $listener = "winrm/config/Listener?Address=*+Transport=HTTPS"

  winrm set $listener "@{Hostname=`"dcvscoap12.corp.kfh.kw`";CertificateThumbprint=`"$thumbprint`"}"
  Restart-Service WinRM
  winrm get $listener
  ```

  If native `winrm set/get winrm/config/Listener?Address=*+Transport=HTTPS` returns `0x80338000` / `cannot find the resource identified by the resource URI and selectors`, manage the listener through the PowerShell WSMan provider instead:

  ```powershell
  $thumbprint = "<VALID_SERVER_AUTH_CERT_THUMBPRINT>"
  $hostname = "dcvscoap12.corp.kfh.kw"

  Get-ChildItem WSMan:\Localhost\Listener |
    Where-Object { $_.Keys -contains "Transport=HTTPS" } |
    Remove-Item -Recurse -Force

  New-Item -Path WSMan:\Localhost\Listener `
    -Transport HTTPS `
    -Address * `
    -Hostname $hostname `
    -CertificateThumbPrint $thumbprint `
    -Force

  Restart-Service WinRM
  Get-ChildItem WSMan:\Localhost\Listener
  Test-WSMan $hostname -UseSSL -Port 5986
  ```

- SCOM prerequisites:
  - WinRM enabled on the SCOM management server and reachable from the application host.
  - The service account has permission to run SCOM PowerShell cmdlets.
  - The `OperationsManager` PowerShell module is installed on the SCOM server.
  - Kerberos/WinRM SPNs, firewall rules, and certificate trust are configured for the selected transport.
- Keep SCOM certificate validation enabled by default. Use **Disable certificate validation for this SCOM test** only for governed dev/hybrid troubleshooting while WinRM certificate trust or CRL/AIA reachability is remediated, then disable that bypass after importing the relevant corporate CA/trust chain and restoring revocation reachability. Do not paste PowerShell command output into tickets if it contains hostnames or operational context that is not required; never include credentials.

Safe PostgreSQL verification example (does not print secrets):

```sql
SELECT connector_id, tenant_id, type, name, enabled,
       config->>'countryCode' AS country_code,
       config->>'environment' AS environment,
       config->>'configurationStatus' AS configuration_status,
       config->>'secretsMask' AS secrets_mask,
       created_at, updated_at
FROM config.connectors
ORDER BY updated_at DESC;
```

Every HTTP request now emits one secret-safe Java console line from `HttpActionLoggingFilter`, for example:

```text
http action method=POST path=/api/v1/users status=503 durationMs=12 tenantId=<uuid> userId=<uuid> countryCode=KW environment=PROD correlationId=<correlation-id>
```

.\mvnw.cmd spring-boot:run
### Audit Activity page operations
- Open `https://<host>:8443/index.html#audit` to view the redesigned **Audit Activity** console.
- The page calls `GET /api/v1/audit` and displays only real application actions recorded by authentication and write flows such as successful/failed login, user management, app settings update/test, connector, schedule, alert acknowledgement, incident, inventory, application, report, and bootstrap identity provisioning operations. It reads PostgreSQL `identity.audit_log` first so activity survives browser refresh and webserver restart. `V6__repair_audit_log_activity_schema.sql` repairs older dev databases so the table has the expected durable activity columns. It does not render seeded, sample, generated, or dummy audit rows.
- If `GET /api/v1/audit` previously returned `500` after enabling durable audit activity, restart on a build that includes `V6__repair_audit_log_activity_schema.sql` so Flyway can repair the audit table. If PostgreSQL is temporarily unavailable or the audit table is still being repaired, the API logs a degraded audit persistence warning and falls back to in-memory rows instead of failing the page.
- If the page is empty in datasource-backed mode, perform a real action (for example sign in, intentionally fail a sign-in test with a non-production account, update settings, test a connector, run a schedule, update a user, or generate a report), then click **Refresh**. Persistent rows should remain visible after restart for the same tenant/country/environment scope.
- Login audit rows should show a readable actor (display name or username), `Security` / `Authentication` as the target, a `Login succeeded/failed for <actor>` message, the country/environment scope, and the `AUTH-*` correlation ID. If older durable rows were created before the readable display fields existed, the API/UI derives the display actor from the secret-safe stored username and suppresses UUID-only login targets in the table.
- Connector audit rows should appear for install/create, configure/update, enable/disable, uninstall, test requested, test succeeded/failed, heartbeat requested, and per-connector heartbeat succeeded/failed activity. Rows include secret-safe connector metadata such as connector name, plugin type, country, environment, endpoint, status, error code, and correlation ID; they must never include credential values. If `actor_user_id` no longer resolves to `identity.users`, the UI displays the actor as `System` and the audit row still persists.
- Audit list/detail/export reads are tenant-scoped and country-aware. `COUNTRY_ADMIN` users should sign in with a physical country (`KW`, `BH`, or `EG`) and will see only that country/environment's audit activity; the sidebar country menu remains locked and does not show other countries. All-country audit review requires signing in with `countryCode=ALL` plus `*` or `COUNTRY_GLOBAL_VIEW`; without that privilege, the API rejects the request instead of returning cross-country rows.
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
5. The SPA router handles navigation via URL hash (e.g., `#dashboard`, `#incidents`, `#users`). `index.html` is the only supported application shell/sidebar; legacy direct page URLs under `pages/**.html` redirect back to `index.html#<page>` so operators always see one menu, one active state, and one shared hover/current-page style.
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
- Use the country filter to switch the active country scope and reload users only after signing in with all-country scope (`countryCode=ALL`) and global country permission. Physical-country users do not see the country filter dropdown; they see a locked Platform + assigned-country label and remain constrained by backend country guards.
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
- Use UI Connectors page to configure BMC Helix monitoring source integrations. Other connector cards may be visible as future/disabled options until their backend plugins are implemented.
- All connector operations require tenant/user headers and proper RBAC permissions:
  - `config.connector.read`: view connectors
  - `config.connector.write`: create/update/delete connectors  
  - `config.connector.test`: test connector connections
- Connector tests run async via outbox pattern to prevent UI blocking
- BMC connector configuration includes SSRF-oriented validation: HTTPS-only base URL, no credentials/query/fragment/API path in the base URL, safe relative BMC endpoint paths, and blocking of localhost/private/link-local/metadata IP targets.
- Installed connectors are persisted in PostgreSQL `config.connectors` and returned by `GET /api/v1/connectors` for the same tenant, country, and environment after browser refresh or application restart. If JDBC persistence is unavailable, connector create/update/delete/toggle writes fail closed with `CONNECTOR_PERSISTENCE_UNAVAILABLE` instead of creating temporary memory-only connector rows.
- Uninstall/delete uses `DELETE /api/v1/connectors/{id}` and removes the row from `config.connectors`; the connector should then disappear from the UI after the inventory reload.
- Secrets encrypted at rest; never logged or returned in API responses
- All connector changes generate audit logs with before/after summaries
- Failed connectors enter degraded mode; system continues operating without them

### Settings validation
- Production startup requires only the platform bootstrap properties: primary PostgreSQL datasource (`DB_*`), Flyway, server/TLS (`SERVER_*`), bootstrap identity, country scope, and security master key. Optional tools such as Neo4j, Azure OpenAI, SharePoint, Teams, Redis, Kafka, and index storage must not block application boot; configure them after login from Settings as tenant-scoped metadata.
- The primary PostgreSQL datasource is configured from `application.properties`/environment because it must exist before the application can read any database metadata. Settings metadata is then stored inside that same primary PostgreSQL system-of-record database in `config.integration_settings`; do not model the primary PostgreSQL boot datasource as a Settings metadata connector.
- Open `https://<host>:8443/index.html#settings` to view the Settings page. `GET /api/v1/settings` reads safe bootstrap values from Spring `Environment` / `application.properties`, overlays tenant metadata from PostgreSQL `config.integration_settings`, and falls back to sanitized runtime overrides if metadata persistence is temporarily unavailable; it does not expose plaintext secrets.
- Configure deployment-real startup values with environment variables or deployment secrets: `DB_*`, `SERVER_*`, `KFH_BOOTSTRAP_*`, `KFH_AIOPS_SECRET_KEY`, `KFH_DASHBOARD_REFRESH_SECONDS`, `KFH_INCIDENT_QUIET_PERIOD_MINUTES`, and `KFH_AI_MODE`. New optional integration variables default to empty and are bootstrap fallback only; preserve the existing dev-server-only password defaults unless explicitly rotating them.
- `KFH_AIOPS_SECRET_KEY` is the server-side master key for encrypting connector credentials in `config.connector_secrets`. It must be configured before saving or testing connector credentials, must never be logged or committed, and must remain stable across restarts so previously encrypted connector secrets can be decrypted. If it is missing and no deployment secret file is readable, connector Save/Test fails closed with `SECRET_MASTER_KEY_MISSING` instead of storing plaintext credentials.
- If the backend JVM was started by an IDE, Windows service, Tomcat, or another launcher that does not see the shell environment variable, use a deployment secret file instead of committing a key. By default the backend also checks `%USERPROFILE%\.kfh-aiops\secret-key.txt` on each connector Save/Test; alternatively set `KFH_AIOPS_SECRET_KEY_FILE` / `kfh.security.master-key-file` to a protected file path visible to the Java process. The file must contain only the stable key value, must be readable only by the service account where possible, and must not be placed under the repository or pasted into logs/tickets.
- To create the local Windows dev secret file without printing the key, run this from PowerShell under the same Windows account that runs the backend, then retry connector Save/Test. If connector secrets were already encrypted with another key, keep using that original key value instead of generating a new one:
  ```powershell
  $secretDir = Join-Path $env:USERPROFILE '.kfh-aiops'
  New-Item -ItemType Directory -Force -Path $secretDir | Out-Null
  $secure = Read-Host -Prompt 'Stable KFH_AIOPS_SECRET_KEY' -AsSecureString
  $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  try {
      [IO.File]::WriteAllText((Join-Path $secretDir 'secret-key.txt'), [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr), [Text.UTF8Encoding]::new($false))
  } finally {
      [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
  }
  icacls (Join-Path $secretDir 'secret-key.txt') /inheritance:r /grant:r ("{0}:F" -f $env:USERNAME) | Out-Null
  ```
- Local PowerShell startup helpers `scripts/run-with-database.ps1` and `scripts/run-local-https.ps1` prompt for `KFH_AIOPS_SECRET_KEY` without echoing it when the variable is not already present. For manual starts, set the variable in the same PowerShell session before launching Spring Boot, then restart the application:
  ```powershell
  $env:KFH_AIOPS_SECRET_KEY = '<stable-secret-from-deployment-vault-or-local-secure-note>'
  .\mvnw.cmd spring-boot:run
  ```
- Setting `$env:KFH_AIOPS_SECRET_KEY` in PowerShell only affects processes started from that same PowerShell session after the assignment. It does not update an already-running Java process, a Windows service, Tomcat, IIS/reverse proxy worker, or a NetBeans/IntelliJ run configuration that was launched separately. If connector Save/Test still reports `SECRET_MASTER_KEY_MISSING`, either stop the running backend and set the key in the launcher that actually starts Java, or create the protected deployment secret file and retry.
- `GET /api/v1/settings` returns `system.secretMasterKeyConfigured` as a secret-safe boolean and `system.secretMasterKeySource` as a safe source label. `secretMasterKeyConfigured` must be `true` before connector Save/Test can encrypt or decrypt credentials. The API never returns the actual key value or file content.
- If connectors that previously tested successfully now fail after creating a new `KFH_AIOPS_SECRET_KEY` / `secret-key.txt`, those saved credentials were encrypted with the previous master key. Preferred recovery is to restore the original stable key value and restart/retry. If the original key is unavailable, open each affected connector and re-enter **all required credential fields for that connector** so the backend can encrypt them with the current key. Non-secret connector updates now preserve old encrypted secret entries without decrypting them, and live tests return a clear failed result explaining key mismatch instead of incorrectly reporting that the source access key/password was simply missing.
- To configure multiple named Azure OpenAI integrations from deployment configuration, use indexed variables backed by `kfh.ai.azure-openai.integrations[0..9].*` such as `AZURE_OPENAI_0_NAME`, `AZURE_OPENAI_0_PROVIDER`, `AZURE_OPENAI_0_PURPOSE`, `AZURE_OPENAI_0_ENDPOINT`, `AZURE_OPENAI_0_API_KEY`, `AZURE_OPENAI_0_DEPLOYMENT`, `AZURE_OPENAI_0_API_VERSION`, `AZURE_OPENAI_0_TIMEOUT_SECONDS`, and `AZURE_OPENAI_0_ENABLED`. Optional `kfh.ai.azure-openai.integrations[n].country-code` scopes a bootstrap provider to `ALL`, `KW`, `BH`, `EG`, or a future country code. Blank Azure OpenAI legacy entries are not shown unless their endpoint/deployment/key values are configured.
- The Settings page uses an enterprise connector pattern for Azure OpenAI, databases, SharePoint, Teams, Redis, Kafka, and custom index storage: click **Add**, complete the popup form, then manage the created connector as a compact row with Test/Edit/Remove actions. AI provider country scope is configured only inside the Add/Edit popup. Select **All Countries** to make a provider usable for KW, BH, and EG, or select one or more individual countries such as KW + BH. Country admins add or edit providers only for their active country.
- Before adding or updating an AI provider from the popup, use **Test Only** or **Test & Save**. **Test & Save** calls the backend live Azure OpenAI deployment metadata test first and persists the row only after a passing result. API keys submitted through Settings are encrypted server-side with the platform master key, stored in `config.integration_settings` metadata, returned only as `••••••••••••`, and reused server-side for future tests when the client sends the masked placeholder. If the platform master key is missing or mismatched, re-enter the key for that provider and retry **Test & Save**; do not paste keys into logs or tickets.
- Redis, Kafka, and custom index storage are separated from **System Variables** into the **Servers & Index** Settings menu. The UI intentionally starts this section empty and does not create dummy rows from default values such as Redis `localhost` or index path `/data/aiops-index`; operators add the real server metadata explicitly. Settings metadata is permanently stored in PostgreSQL `config.integration_settings` by tenant, country scope, environment, and key; it is not only browser state or an in-memory test value.
- Each database/infrastructure provider popup now includes **Test Connection** next to **Add/Update Connector**, and saved rows also keep their row-level **Test Connection** action. The popup test uses the current unsaved draft payload with tenant/user/correlation headers and does not persist the row unless the operator clicks **Add/Update Connector**. Test responses show only status, latency, safe checked endpoint, correlation ID, and secret-free messages. Redis/Kafka tests reject URL syntax and loopback/link-local/multicast/metadata targets before opening any socket or Kafka AdminClient probe.
- The built-in Neo4j row and optional Neo4j metadata rows run a bounded Neo4j Java Driver `RETURN 1` probe. Neo4j remains the relationship/topology graph only; do not validate raw log search by querying Neo4j.
- To add the current Redis server from the Linux host shown in the operator check, open **Settings → Servers & Index → Add Server → Redis Server** and enter **Redis Host / IP** `172.17.133.47`, **Redis Port** `6379` unless Redis was configured differently, optional **Redis Username / ACL User**, optional **Redis Password**, **Redis Database** `0`, and **TLS Enabled** only if Redis TLS is configured. `redis-cli ping` returning `PONG` confirms local Redis health, but remote application connectivity also requires Redis to listen on the approved private interface and firewall rules to allow the application host.
- To add Kafka, first confirm the broker listener from the Linux server without printing secrets: check Kafka `advertised.listeners`, `listeners`, `security.inter.broker.protocol`, and `sasl.enabled.mechanisms`, and confirm the broker port with `ss -lntp | grep -E '9092|9093'`. In **Settings → Servers & Index → Add Server → Kafka Server**, enter **Kafka Bootstrap Servers** such as `172.17.133.47:9092` for plaintext/internal dev listeners or the approved `host:9093` TLS/SASL listener, choose **Countries** as `KFH Kuwait` for Kuwait-only Kafka or `All countries` for a group-wide Kafka entry, choose **Security Protocol** (`PLAINTEXT`, `SSL`, `SASL_PLAINTEXT`, or `SASL_SSL`), choose **SASL Mechanism** only when SASL is enabled, then enter username/principal, password/secret, client ID, and truststore/CA path only when required by the broker security configuration. **Test Connection** runs a bounded Kafka AdminClient metadata probe; it does not return topic names, JAAS config, credentials, or broker internals. Do not paste Kafka passwords, JAAS configs, keytabs, truststore passwords, or raw broker config into tickets or docs.
- To add custom index storage, choose **Index Storage Server** and enter the provider (`LOCAL`, `S3`, `AZURE_BLOB`, or `NFS`), path/URI, optional bucket/share, optional region, and optional access credentials. For `LOCAL`/`NFS`, **Test Connection** requires an absolute path without traversal segments, then verifies the path exists and is readable/writable by the application process. For cloud providers, the current phase validates allowed pointer schemes (`s3://` or approved HTTPS for S3, HTTPS/ABFS/WASB-style pointers for Azure Blob) and blocks HTTPS metadata/loopback/link-local targets until SDK-backed live object-storage probes are configured. This is only a pointer for custom index shards/segments; do not use PostgreSQL, Neo4j, or Redis as raw telemetry/index storage.
- Settings changes are item-level auto-saved: field changes debounce `PUT /api/v1/settings`, and connector Add/Edit/Remove actions save immediately after the row is changed. There is no per-section Save Changes toolbar; System Variables are shown in one compact card.
- The Settings local menu remembers the last selected section such as **Databases** in browser local storage, so refreshing `#settings` returns the operator to the same Settings subsection. Database/Neo4j rows keep **Test Connection**, **Edit**, and **Remove** aligned in the same Actions column for faster NOC operation.
- Settings Add/Edit popups stay open until the operator uses an explicit close control such as **Cancel** or the modal **X** button. Clicking on the blurred page backdrop outside the popup no longer auto-closes the dialog, which prevents accidental loss of in-progress configuration edits while navigating or hovering elsewhere on the page.
- Kafka and custom index storage display values can be entered in Settings as `infrastructure.connections[]` metadata with `countryCodes` such as `["KW"]` or `["ALL"]`. Environment variables `KFH_KAFKA_BOOTSTRAP_SERVERS`, `KFH_KAFKA_SECURITY_PROTOCOL`, `KFH_KAFKA_USERNAME`, `KFH_KAFKA_PASSWORD`, `KFH_INDEX_STORAGE_PROVIDER`, `KFH_INDEX_STORAGE_PATH`, and `KFH_INDEX_STORAGE_BUCKET` remain optional bootstrap fallbacks only. Index storage values are pointers for the custom index engine and must not be used to store raw logs in PostgreSQL or Neo4j.
- Use the UI Settings page to trigger configuration tests via `POST /api/v1/settings/{section}/test` with tenant/user/correlation headers and `SETTINGS_WRITE` permission. Azure OpenAI sections run a live bounded test. The Databases tab includes a built-in **Neo4j Topology Graph** row; **Test Connection** calls `section=neo4j` and runs a bounded Neo4j Java Driver probe without returning the password. Use **Edit** to update Bolt URL, username, password, database, and health-indicator metadata; submitted passwords are encrypted server-side and returned only as a mask. Use **Remove** to clear tenant Settings metadata for the built-in Neo4j row; deployment/startup environment values can still repopulate defaults after restart. Optional database connector rows with `type=NEO4J` can also be tested from the same tab. Non-live sections such as Teams notification mappings currently return a clear failed result explaining that no webhook call was sent until a dedicated notification adapter is implemented.
- Azure OpenAI Settings tests validate HTTPS-only endpoint format, block SSRF-risk hosts, require an allowed Azure OpenAI host suffix, and use bounded timeout/retry. Classic deployment-style entries call the deployment metadata endpoint; GPT 5.4 / Responses API entries normalize Azure portal root endpoints to `/openai/v1/responses`, preserve submitted `/openai/v1` base endpoints, and avoid duplicating `/responses` when a full Responses endpoint is submitted. Responses tests call first with `api-key` and then once with OpenAI v1 bearer-style API-key auth if the first response is `401 Unauthorized`. Test responses include status, latency, checked endpoint origin/path, correlation ID, and a redacted Java failure message when an exception occurs; API keys are never logged or returned.
- Saves use `PUT /api/v1/settings` and are audited with secret-safe updated key names only. The endpoint sanitizes `password`, `apiKey`, `clientSecret`, `webhookUrl`, token, and credential fields before storing or returning values; metadata-owned sections are persisted in `config.integration_settings`, while startup-owned properties are never written back to `application.properties`.
- If `PUT /api/v1/settings` returns HTTP 503 with `code=SETTINGS_PERSISTENCE_UNAVAILABLE` and the backend log shows `settings metadata save skipped: no SettingsMetadataStore bean available`, the `JdbcSettingsMetadataStore` `@Repository` was not registered in the Spring context even though the primary PostgreSQL datasource is configured. Resolution (in order):
  1. Verify the primary datasource (`spring.datasource.url`, `spring.datasource.username`, password env var) resolves to the correct PostgreSQL host and the application user can connect.
  2. Verify Flyway ran successfully up to `V12__country_environment_scoped_integration_settings.sql` (`select * from flyway_schema_history order by installed_rank desc limit 5;`).
  3. Confirm the `org.kfh.aiops.platform.config.JdbcSettingsMetadataStore` class is **not** annotated with `@ConditionalOnBean(JdbcTemplate.class)`. That guard was removed on 2026-06-29 because it was evaluated during component scan, before Spring Boot's `JdbcTemplate` auto-configuration registered the bean, which silently skipped the repository and broke Settings persistence even with PostgreSQL fully configured. If it has been reintroduced, remove it and redeploy. Do not paste credentials or raw API keys into the ticket during triage.

### Connector management
- The Connectors page supports Table and Cards views. Cards view lays out **3 wider cards per row** on desktop and paginates at **10 connectors per page** for NOC readability; use the pagination controls below the cards when more than 10 connectors match, or switch to Table view for the full filtered inventory.

#### Adding a new connector
1. Navigate to Admin > Connectors
2. Click "Add Connector" button
3. Select **BMC Helix**, **AppDynamics**, or **VMware vROps** and click **Install**. Future connector types are shown with icons but remain disabled until their connector phases are implemented.
4. The installed connector is saved in PostgreSQL `config.connectors` and appears on the Connectors page as enabled with pending configuration. It should remain visible after browser refresh for the same tenant/country/environment scope.
5. Click **Configure** on the installed connector and fill in the source-specific configuration.
   BMC configuration includes:
   - Name (unique within tenant)
   - Physical country (`KW`, `BH`, `EG`) and environment (`PROD`, `UAT`, `DEV`)
   - BMC base URL (SSRF validated)
   - Login endpoint and events msearch endpoint
   - **Verify TLS Certificate Chain** is enabled by default; clear it only for governed dev/hybrid testing if Java does not yet trust the corporate CA.
   - BMC access key and access secret key
   - Collection window, page size, max events, timeout, and sync interval
   AppDynamics configuration includes:
   - Name (unique within tenant)
   - Physical country (`KW`, `BH`, `EG`) and environment (`PROD`, `UAT`, `DEV`)
   - AppDynamics Controller URL ending with `/controller` (HTTPS and SSRF validated)
   - **Verify TLS Certificate Chain** is enabled by default; clear it only for governed dev/hybrid testing if Java reports `PKIX path building failed` while the endpoint is known to be correct.
   - AppDynamics Basic Auth username and password
   - Duration minutes, timeout, max workers, event-family toggles for errors/violations/slow transactions, and sync interval
   - Internal corporate hostnames and private IPs such as `appd.corp.kfh.kw` or `10.17.134.118` are supported for KFH hybrid environments. Localhost, loopback, link-local, multicast, and cloud metadata targets remain blocked.
   vROps configuration includes:
   - Name (unique within tenant)
   - Physical country (`KW`, `BH`, `EG`) and environment (`PROD`, `UAT`, `DEV`)
   - vROps host/IP or HTTPS URL ending with `/suite-api/api` (HTTPS and SSRF validated)
   - vROps auth source, typically `KFH AD`
   - **Verify TLS Certificate Chain** is enabled by default; clear it only for governed dev/hybrid testing while JVM truststore CA import is pending.
   - vROps username and password, stored encrypted server-side
   - Window hours, page size, max pages, max workers, timeout, and sync interval
   - Private/internal BMC, AppDynamics, and vROps hosts are valid in the KFH hybrid environment. Save, **Test Connection**, and heartbeat block only unsafe SSRF targets such as localhost, loopback, link-local, multicast, and cloud metadata endpoints.
6. Click **Save** in the Configuration drawer footer. Pending BMC setup must include the BMC base URL, access key, and access secret key. Pending AppDynamics setup must include the Controller URL, username, and password. Pending vROps setup must include the vROps host/base URL, auth source, username, and password. Missing fields are highlighted in the drawer and the API encrypts credentials in `config.connector_secrets` using `KFH_AIOPS_SECRET_KEY` or the protected deployment secret file. If neither is configured, set the environment variable in the actual Java launcher or create `%USERPROFILE%\.kfh-aiops\secret-key.txt`, then retry Save/Test. The API strips `secretsPlain` from all responses.
7. Use the connector row/card **Test** action or the Configuration drawer **Test Connection** action to perform a live readiness test. From the Configuration drawer, **Test Connection** first saves the current form values, then switches to the Health tab and shows the latest test result. The **Enabled/Disabled** pill controls collection eligibility only; the **Status/Health** badge is updated by test/heartbeat results.

#### Testing connectors
- POST /api/v1/connectors/{id}/test
- BMC connectors now use the saved configuration and encrypted credentials to perform live communication checks: HTTPS base URL validation, hybrid-safe SSRF validation, access-key login at `loginEndpoint`, `json_web_token` extraction, and an events `msearch` readiness probe at `eventsEndpoint`.
- AppDynamics connectors use the saved Controller URL and encrypted Basic Auth credentials to perform a live readiness check: HTTPS Controller validation, hybrid-safe SSRF validation, and `GET /rest/applications?output=JSON` application discovery.
- vROps connectors use the saved suite API URL and encrypted username/password credentials to perform a live readiness check: HTTPS suite API validation, hybrid-safe SSRF validation, `POST /suite-api/api/auth/token/acquire`, and `GET /suite-api/api/alerts?page=0&pageSize=1&_no_links=true` using a secret-safe vROps token.
- BMC, AppDynamics, and vROps each persist `verifySsl` with secure default `true`. When the drawer checkbox is cleared, the live test still uses HTTPS transport encryption and keeps SSRF blocking, but skips certificate-chain validation for that connector test. Use this only as a temporary dev/hybrid workaround; the preferred fix is importing the KFH corporate/AppDynamics CA chain into the Java truststore and re-enabling verification.
- For AppDynamics PKIX failures, clear **Verify TLS Certificate Chain**, click **Test Connection** or **Save**, then reopen the connector and confirm the Overview shows `TLS verification: Disabled for test`. The UI preserves explicit `verifySsl=false` values returned by the backend, including string/JSON variants, so the checkbox should remain cleared after refresh when the save succeeds.
- A passing result means BMC authentication succeeded and the events endpoint accepted a readiness query, so the connector is ready for the future scheduled collection worker to bring data.
- A passing AppDynamics result means Basic Auth succeeded and the Controller returned an application list, so the connector is ready for future application/error/violation/slow-transaction collection.
- A passing vROps result means token acquisition succeeded and the alerts endpoint accepted a readiness request, so the connector is ready for future vROps alert/resource-health collection.
- Failed results remain secret-safe and indicate whether configuration, authentication, token extraction, TLS certificate trust, or endpoint communication failed. AppDynamics failures include the compact redacted Java/HTTP reason, including a sanitized response message from `/rest/applications?output=JSON` when the controller returns one. PKIX failures include truststore guidance. A failed test persists `lastTestStatus=FAIL` and marks the enabled connector `DOWN` in the inventory so operators see a red warning even though the connector remains enabled for retry after remediation. Do not paste access keys, access secret keys, AppDynamics/vROps usernames/passwords, vROps tokens, bearer tokens, Basic Auth headers, or raw source payloads into tickets.
- When running local PowerShell TLS/TCP probes for AppDynamics, do not write `$AppdHost:$AppdPort` inside a double-quoted string. PowerShell parses the colon as a scope/drive separator after the variable name and raises `InvalidVariableReferenceWithDrive`. Use either `${AppdHost}:$AppdPort` or the safer format operator:
  ```powershell
  Write-Host ("`nChecking TCP connectivity to {0}:{1} ..." -f $AppdHost, $AppdPort) -ForegroundColor Cyan
  throw ("TCP connection failed to {0}:{1}. Fix DNS/network/firewall first." -f $AppdHost, $AppdPort)
  Write-Host ("`nExporting certificate chain from https://{0}:{1} ..." -f $AppdHost, $AppdPort) -ForegroundColor Cyan
  Write-Host "`nAfter restart, run AppDynamics Test Connection again." -ForegroundColor Green
  ```

#### Connector heartbeat
- POST `/api/v1/connectors/heartbeat`
- The Connectors page **Heartbeat enabled** action runs live readiness tests for every enabled connector in the signed-in tenant/country/environment scope.
- Passing heartbeat results persist `health=HEALTHY`; failed heartbeat results persist `health=DOWN` and appear in the Warning/Down KPI cards and connector card warning message.
- Disabled connectors are skipped because they are intentionally not eligible for collection.
- Use heartbeat after backend restart, network changes, AppDynamics/BMC credential rotation, proxy/DNS changes, or before enabling scheduled connector collection.

#### Connector-driven ingestion bridge (enable a connector → pull real alerts)
End-to-end path so enabling a connector in the UI actually pulls alerts, no env vars / restart needed:
1. **Configure + enable a connector** (Settings → Connections): base-url + credentials for BMC, or management-server/username/password for SCOM; set its **Ingestion schedule** (`attributes.intervalMin`); toggle **Enable**. Persisted in `config.connectors` (requires PostgreSQL — see connector persistence).
2. **`ConnectorIngestionScheduler`** (`org.kfh.aiops.ingestion.ConnectorIngestionScheduler`) ticks every `kfh.ingestion.bridge.tick-ms` (default 60s). Each tick it calls `ConnectorPersistenceStore.listEnabled()`, and for every connector whose `intervalMin` is **due** it builds a per-connector `BmcHelixClient`/`ScomWinRmClient` from the stored config + **decrypted secrets** and runs one collect cycle → `IngestionService` (normalize → Redis dedup → `IndexWriterService`). Scope = the connector's `(country, environment)`.
3. **Alerts read live from the index**: `AlertService.list()` queries `IndexSearchService` for `TelemetryKind.ALERTS` over the last `kfh.alerts.window-hours` (default 168h) and maps `TelemetryDocument` → the Alert Explorer row shape. Falls back to the (empty) read model if the index is unavailable.
- Config: `kfh.ingestion.bridge.enabled` (default `true`; no-op when no DB store), `kfh.ingestion.bridge.tick-ms`, `kfh.ingestion.bridge.initial-delay-ms`, `kfh.alerts.window-hours`.
- Trace with markers: `[BRIDGE]` (per-connector collect), `[INGEST]`, `[CORRELATE]`. A connector that's enabled but missing credentials logs `not fully configured … skipping`.
- Prereqs: PostgreSQL (connector persistence + `listEnabled`), reachable BMC/SCOM endpoints, Redis (dedup degrades gracefully), `@EnableScheduling` (already on).

#### Manual data collection
- POST /api/v1/connectors/{id}/collect-now
- Creates QUEUED run record
- Worker picks up via outbox pattern
- Supports custom window override

#### NOC pages are live (no demo data)
- The Alerts, Incidents, and Logs Explorer pages render **only real data** from the backend — all illustrative/demo rows were removed.
  - Alerts (`pages/alerts/alerts.js`) → `GET /api/v1/alerts` → **served live from the Custom Index** (`AlertService.list` → `IndexSearchService`, trailing `kfh.alerts.window-hours`), mapped defensively (TelemetryDocument fields), Title-case severities. The page uses the shared **slim page header** (see below) with a Filters popover holding Severity/Source/Kind + Impact sort; hourly activity strip is computed from real alert timestamps.

- **Unified slim page header (`.kfh-phdr`)** — every page renders one consistent ~54px header (title + subtitle + search + optional scope/filters/sort/actions) defined once in `shared/css/kfh-design-system.css` (`.kfh-phdr*`, `.kfh-fchip`). Any page containing a `.kfh-phdr` auto-hides the shell `#global-bar` via `main.kfh-main-content:has(.kfh-phdr) #global-bar{display:none}`, so there is exactly one header per page. Applied to Dashboard, Incidents, Alerts, Log Explorer, Applications, Inventory, Reports, Users, Audit, Schedules and Settings. NOTE: the shared stylesheet was renamed from `kfh-dynatrace-system.css` → `kfh-design-system.css` (no third-party product names in shipped code); the roadmap doc is `docs/UI_PARITY_ROADMAP.md`.
  - Incidents (`pages/incidents/incidents.js`) → `GET /api/v1/incidents` (correlation/RCA output), defensively mapped to the detail shape.
  - Logs Explorer (`pages/explorer/explorer.js`) → `POST /api/v1/logs/search` (already live).
- Until a connector is enabled and has ingested, each page shows a clean **empty state** ("No alerts/incidents yet — enable a connector under Settings → Connections"). On API failure a red error badge is shown with the empty state.
- Connection editor (Settings → Connections): the **Share access** tab and the **Manage External Requests** banner were removed; the editor is a single Set-up form (identity/scope/schedule/credentials).

#### Revealing secrets (ADMIN only)
- POST /api/v1/connectors/{id}/reveal
- Requires reason for audit trail
- Decrypts and returns single field
- Critical audit log generated

#### Connector health monitoring
Health badges computed from:
- Last test/heartbeat result (`PASS` => `HEALTHY`, `FAIL` => `DOWN`)
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

## Settings save troubleshooting

- If **Test & Save** or a normal Settings save fails, the popup/toast now shows the backend persistence message directly.
- If the message indicates settings persistence is unavailable, verify PostgreSQL connectivity and the `config.integration_settings` table path before retrying.
- Popup **Test & Save** success still proves only the bounded `POST /api/v1/settings/{section}/test` path. Durable persistence is performed by the subsequent `PUT /api/v1/settings` call into PostgreSQL-backed `config.integration_settings`.
- Current automated validation status: targeted runtime-facing Settings tests pass except for the dedicated `JdbcSettingsMetadataStoreTest` H2 harness, which still cannot execute the PostgreSQL `ON CONFLICT` upsert path reliably. Treat that as a test-environment limitation, not proof that PostgreSQL runtime persistence is broken; the follow-up is to replace this harness with a PostgreSQL-backed integration test.
- Do not inspect browser logs for secrets; the UI and API keep connector/API secrets masked.

## Redis 7+ on Red Hat Enterprise Linux 10 — minimum setup for KFH AIOps

This is the operator-facing recipe for the values the **Settings → Servers & Index → Add Server → Redis Server** popup expects. Per §12 of `.github/copilot-instructions.md`, Redis stores only **hot health state, dashboard cache, short alert dedup, distributed locks, rate limiting, short-lived RCA evidence cache, AI summary cache, and WebSocket live state** — never raw logs, raw alerts, or system-of-record data.

### 1. Install the Redis 7 RPM stream (one-time on the RHEL 10 host)

```bash
sudo dnf module reset -y redis
sudo dnf module enable  -y redis:7
sudo dnf install        -y redis
sudo systemctl enable redis
```

### 2. Pin Redis to the approved private interface and require auth

Edit `/etc/redis/redis.conf` (RHEL 10 path; on some images it is `/etc/redis.conf`):

```conf
# Bind to the private NIC the application uses. Never 0.0.0.0 in production.
bind 172.17.133.47 127.0.0.1 -::1
protected-mode yes
port 6379

# Cache profile (do not use Redis as system of record). Pick a size your
# RHEL host can dedicate without paging; this is hot-state only.
maxmemory 2gb
maxmemory-policy allkeys-lru

# No persistent journal needed for hot cache; turn AOF off and shrink RDB.
appendonly no
save ""

# ACL file is the source of truth; do NOT put `requirepass` here once ACL
# users exist (ACL users replace the legacy single-password model).
aclfile /etc/redis/users.acl
```

> **Why not `requirepass`?** Project §16 requires per-user audit. ACL users with distinct passwords let you tell platform traffic apart from operator console traffic in `redis-cli ACL LOG`. The Settings → Redis popup is built to send an **ACL user + password** pair, not a shared password.

### 3. Open the firewall to the application host only (no public exposure)

```bash
sudo firewall-cmd --permanent --add-rich-rule='rule family=ipv4 \
  source address=172.17.134.0/24 port port=6379 protocol=tcp accept'
sudo firewall-cmd --reload
```

Replace the CIDR with the actual KFH application subnet. Do **not** open 6379 to `0.0.0.0/0` or the public NIC.

### 4. Create ACL users — one application user **per country**, plus a read-only and a break-glass

`/etc/redis/users.acl` (root-owned, mode `0640`, owner `redis:redis`). Each country gets its own ACL user locked to its own logical DB with the `&<index>` selector and explicit `+select` permission, so one country's credentials cannot enter another country's keyspace.

```acl
# Anonymous default disabled
user default off nopass nocommands

# --- KW: Kuwait, DB 0 (Settings popup -> Redis Username / ACL User) ---
user kfh_aiops_kw on >REPLACE_WITH_STRONG_KW_PASSWORD &0 \
  ~health:* ~dedup:* ~lock:* ~rate-limit:* ~dashboard:* ~rca:* ~ai:* ~ws:* \
  +@read +@write +@string +@hash +@set +@sortedset +@list +@bitmap +@hyperloglog \
  +@stream +@scripting +@keyspace +@connection +@geo +@transaction +select \
  -@dangerous -keys -scan -flushdb -flushall -shutdown -config -debug \
  -replicaof -slaveof -cluster -acl -module

# --- BH: Bahrain, DB 1 ---
user kfh_aiops_bh on >REPLACE_WITH_STRONG_BH_PASSWORD &1 \
  ~health:* ~dedup:* ~lock:* ~rate-limit:* ~dashboard:* ~rca:* ~ai:* ~ws:* \
  +@read +@write +@string +@hash +@set +@sortedset +@list +@bitmap +@hyperloglog \
  +@stream +@scripting +@keyspace +@connection +@geo +@transaction +select \
  -@dangerous -keys -scan -flushdb -flushall -shutdown -config -debug \
  -replicaof -slaveof -cluster -acl -module

# --- EG: Egypt, DB 2 ---
user kfh_aiops_eg on >REPLACE_WITH_STRONG_EG_PASSWORD &2 \
  ~health:* ~dedup:* ~lock:* ~rate-limit:* ~dashboard:* ~rca:* ~ai:* ~ws:* \
  +@read +@write +@string +@hash +@set +@sortedset +@list +@bitmap +@hyperloglog \
  +@stream +@scripting +@keyspace +@connection +@geo +@transaction +select \
  -@dangerous -keys -scan -flushdb -flushall -shutdown -config -debug \
  -replicaof -slaveof -cluster -acl -module

# Read-only NOC operator (diagnostic redis-cli only; never the Settings popup)
user kfh_noc on >REPLACE_WITH_STRONG_NOC_PASSWORD ~* &* \
  +@read +@connection +@keyspace +ping +info +client +select \
  -@dangerous -keys -scan -flushdb -flushall -config -debug -acl

# Break-glass admin — disabled by default. Enable only during a P1 with an
# approved change ticket, rotate the password immediately after, then set
# the user back to `off`.
user kfh_break_glass off >ROTATE_ON_EVERY_USE ~* &* +@all
```

Apply file permissions, reload the live ACL list, and clear the per-country passwords from your shell history:

```bash
sudo chown redis:redis /etc/redis/users.acl
sudo chmod 0640 /etc/redis/users.acl
sudo systemctl restart redis

# Smoke-test each country user against its own DB
redis-cli -h 172.17.133.47 --user kfh_aiops_kw -a "$REDIS_KW_PASS" -n 0 PING   # -> PONG
redis-cli -h 172.17.133.47 --user kfh_aiops_bh -a "$REDIS_BH_PASS" -n 1 PING   # -> PONG
redis-cli -h 172.17.133.47 --user kfh_aiops_eg -a "$REDIS_EG_PASS" -n 2 PING   # -> PONG

unset REDIS_KW_PASS REDIS_BH_PASS REDIS_EG_PASS
history -c 2>/dev/null || true
```

### 5. Pick the right logical database number

The Settings popup field **Redis Database** maps directly to a Redis logical DB index (0–15 by default). The current KFH AIOps deployment uses **one shared Redis instance** for the single active environment (the DE host is the production environment until UAT/DEV are split). Allocate **one DB per country** and leave the rest reserved:

| Country | Redis DB | ACL user (step 4) |
|---|---|---|
| KW | `0` | `kfh_aiops_kw` |
| BH | `1` | `kfh_aiops_bh` |
| EG | `2` | `kfh_aiops_eg` |
| _Reserved_ | `3–15` | (do not use yet; reserved for future UAT/DEV split) |

Each ACL user is locked to its own DB with the `&<index>` channel-pattern selector and the explicit `+select` permission, so a misbehaving build in one country cannot `SELECT` into another country's DB even if it somehow obtained that country's password. If you later need UAT or DEV, run a **separate Redis instance** rather than packing more scopes into the same logical-DB list — do not exceed `databases 16`.

### 6. (Recommended) TLS

Tick the **TLS Enabled** toggle in the Settings popup only after Redis is built with TLS support and the server certificate is trusted by the application JVM. Minimum `redis.conf` snippet for TLS-only operation:

```conf
port 0
tls-port 6379
tls-cert-file /etc/redis/tls/redis.crt
tls-key-file  /etc/redis/tls/redis.key
tls-ca-cert-file /etc/redis/tls/kfh-ca.crt
tls-auth-clients yes
```

Until TLS is approved by the security team, leave **TLS Enabled** off and rely on the private-interface firewall rule from step 3.

### 7. Smoke test from the RHEL host and from the application host

```bash
# Local (host where Redis runs)
redis-cli -h 172.17.133.47 -p 6379 --user kfh_aiops -a "$REDIS_KFH_AIOPS_PASS" PING
#   -> PONG
redis-cli -h 172.17.133.47 -p 6379 --user kfh_aiops -a "$REDIS_KFH_AIOPS_PASS" \
  ACL WHOAMI
#   -> "kfh_aiops"

# From the KFH AIOps application host, repeat the same two commands. If the
# second one fails with NOAUTH/WRONGPASS, regenerate the password and update
# the Settings → Servers & Index → Redis Server row via "Test & Update".
```

### 8. Plug the values into Settings → Servers & Index → Redis Server

Add **three rows** (one per country). Click **Test Only** first; if the inline banner shows `Pass`, click **Test & Save** to persist the row to `config.integration_settings`.

| Popup field | Row 1 (KW) | Row 2 (BH) | Row 3 (EG) |
|---|---|---|---|
| **Countries** | `KFH Kuwait` | `KFH Bahrain` | `KFH Egypt` |
| **Redis Host / IP** | `172.17.133.47` (the private interface from step 2) | same | same |
| **Redis Port** | `6379` (or your TLS port from step 6) | same | same |
| **Redis Username / ACL User** | `kfh_aiops_kw` | `kfh_aiops_bh` | `kfh_aiops_eg` |
| **Redis Password** | the per-country password from step 4 (encrypted server-side, masked `••••••••••••` on every reload) | the BH password | the EG password |
| **Redis Database** | `0` | `1` | `2` |
| **TLS Enabled** | Off until step 6 + KFH CA in JVM truststore | same | same |
| **Enabled** | On | On | On |

> Do **not** pick **All Countries** for any of these rows. Each country uses a different ACL user and a different DB index, so an `ALL` row would be unreachable from any country session. If you later need one shared Redis row visible across every country, add a different ACL user dedicated to that purpose and a separate logical DB for it.

### 9. Operational hardening checklist (per §16 security rules)

- [ ] `bind` is the private NIC, not `0.0.0.0`; `protected-mode yes`.
- [ ] `requirepass` is **not** set; only `aclfile` users authenticate.
- [ ] `default` ACL user is `off nopass nocommands`.
- [ ] `kfh_aiops` keyspace pattern (`~health:* ~dedup:* ~lock:* ~rate-limit:* ~dashboard:* ~rca:* ~ai:* ~ws:*`) blocks accidental writes to other keyspaces.
- [ ] `FLUSHDB`, `FLUSHALL`, `CONFIG`, `DEBUG`, `SHUTDOWN`, `REPLICAOF`, `SLAVEOF`, `CLUSTER`, `MODULE`, and `ACL` are denied to `kfh_aiops`.
- [ ] Firewall allows port 6379 only from the KFH AIOps application subnet.
- [ ] Passwords stored only in the Settings encrypted blob (`secretSecret`) and in `/etc/redis/users.acl` with mode `0640`. Never committed to git, never echoed in logs.
- [ ] `redis-cli ACL LOG` is collected by the host's log shipper and routed to the same audit pipeline that ingests Postgres `identity.audit_log`.

### 10. Rotation

Rotate each country password every 90 days, or immediately on suspected exposure. Rotate only the country whose password changed; the other two stay untouched.

```bash
# Example: rotate Kuwait only
NEW=$(openssl rand -base64 24)
redis-cli -h 172.17.133.47 --user kfh_break_glass -a "$BREAK_GLASS_PASS" \
  ACL SETUSER kfh_aiops_kw on ">${NEW}"
redis-cli -h 172.17.133.47 --user kfh_break_glass -a "$BREAK_GLASS_PASS" \
  ACL SAVE
unset NEW BREAK_GLASS_PASS
history -c 2>/dev/null || true
```

Then in the Settings → Servers & Index → Redis Server popup, edit the **matching country row** (`KW`, `BH`, or `EG`), paste the new password into **Redis Password**, click **Test & Update**. Disable `kfh_break_glass` again immediately after rotation (`ACL SETUSER kfh_break_glass off`).

---

## BMC Helix Ingestion (Phase 4 — Stage 0 collection)

Pulls BMC Helix events into the causal funnel (normalize → dedup → index → Log Explorer). Reference API flow: `docs/BMC_Helix_response.md`.

### Configure (secrets via environment only — never commit)
```powershell
# Windows Tomcat host (app), User scope persists across sessions
[Environment]::SetEnvironmentVariable('BMC_BASE_URL', 'https://kfh-itom.onbmc.com', 'User')
[Environment]::SetEnvironmentVariable('BMC_ANALYSIS_BMC_ACCESS_KEY', '<access-key>', 'User')
[Environment]::SetEnvironmentVariable('BMC_ANALYSIS_BMC_ACCESS_SECRET_KEY', '<secret-key>', 'User')
# Optional: enable the 20-min scheduled poll (leave unset for manual-only)
[Environment]::SetEnvironmentVariable('BMC_INGESTION_ENABLED', 'true', 'User')
```
All tunables have safe defaults (`minutes-back=30`, `max-events=500`, `poll-interval-ms=1200000`, scope `KW`/`PROD`). With no credentials the feature is inert (`enabled=false`), so a fresh deploy never makes outbound BMC calls by accident.

### First live test (manual trigger)
Authenticate as an operator with `ALERT_INGEST` (GLOBAL_ADMIN has `*`), then:
```bash
curl -sk -X POST https://<app-host>:8443/api/v1/ingestion/bmc/collect-now \
  -H "Authorization: Bearer <session-jwt>" -H "X-Country-Code: KW"
# → {"received":142,"normalized":142,"duplicatesDropped":18,"indexed":124,"failed":0}
```
Then open **Log Explorer**, filter `sourceSystem = BMC`, and confirm the events landed. `duplicatesDropped>0` proves Redis dedup is live; if it stays `0` while re-running within the window, check Redis connectivity (dedup fails open → everything treated as new).

### Interpreting `IngestionResult`
- `received` = raw BMC events pulled; `normalized` = mapped OK; `failed` = unmappable (logged, never fatal); `duplicatesDropped` = suppressed by short-window fingerprint; `indexed` = written to the Custom Index.
- `received = normalized + failed` and `normalized = indexed + duplicatesDropped` always hold — if they don't, a downstream write partially failed; check app logs for the `IngestionService` line.

### Troubleshooting
| Symptom | Likely cause | Action |
|---|---|---|
| `500 … BMC ingestion is not configured` | missing base-url/keys | set the three env vars, restart the app |
| `500 … BMC authentication failed` | wrong access key/secret, or clock skew | re-check credentials in BMC admin console |
| Sporadic `504`/`PrematureClose` on poll | BMC edge proxy drops idle TCP | already mitigated (keep-alive + eviction); if persistent, lower `poll-interval-ms` |
| `indexed=0` but `received>0` | all duplicates, or index storage unwritable | check `duplicatesDropped`; verify `kfh.index.storage.path` is writable |
| Scheduled poll silent | `kfh.ingestion.bmc.enabled` not `true` | set `BMC_INGESTION_ENABLED=true`, restart |

---

## SCOM Ingestion (Phase 4 — Stage 0 collection, Windows-only)

Pulls SCOM alerts via local `powershell.exe` → WinRM `Get-SCOMAlert` → same pipeline. Reference: `docs/SCOM_Collectors.md`. **Runs only on the Windows Tomcat host** (needs `powershell.exe`, PS remoting to the SCOM MS, and the service account in the SCOM Operators role).

### Configure (secrets via environment only)
```powershell
[Environment]::SetEnvironmentVariable('SCOM_MANAGEMENT_SERVER','scom-mgmt.corp.kfh.local','User')
[Environment]::SetEnvironmentVariable('SCOM_DOMAIN','KFH','User')
[Environment]::SetEnvironmentVariable('BMC_ANALYSIS_SCOM_USERNAME','svc_scom','User')
[Environment]::SetEnvironmentVariable('BMC_ANALYSIS_SCOM_PASSWORD','<password>','User')
# Optional: enable the scheduled poll
[Environment]::SetEnvironmentVariable('SCOM_INGESTION_ENABLED','true','User')
```
Defaults: `winrm-port=5986` + `use-https=true` + `auth-method=Kerberos`; for HTTP/Negotiate set `SCOM_WINRM_PORT=5985`, `SCOM_USE_HTTPS=false`, `SCOM_AUTH_METHOD=Negotiate`. `server-local-offset-hours=3` (Kuwait) drives the PowerShell over-fetch; Java still filters precisely to `hours-back` in UTC.

### First live test
```bash
curl -sk -X POST https://<app-host>:8443/api/v1/ingestion/scom/collect-now \
  -H "Authorization: Bearer <session-jwt>" -H "X-Country-Code: KW"
# → {"received":37,"normalized":37,"duplicatesDropped":4,"indexed":33,"failed":0}
```
Then open **Log Explorer**, filter `sourceSystem = SCOM`. Now BMC + SCOM land in the same index — the two-source correlation input the RCA stage needs.

### Troubleshooting
| Symptom | Likely cause | Action |
|---|---|---|
| `500 … SCOM ingestion is not configured` | missing server/user/password | set the four env vars, restart |
| `500 … SCOM PowerShell failed (exit …)` | WinRM/Kerberos/module error on MS | run the same `Invoke-Command` by hand on the app host; check SPN/port/module |
| `500 … timed out after 60s` | slow MS or large window | raise `SCOM_CONNECTION_TIMEOUT_SECONDS`, lower `SCOM_HOURS_BACK` |
| Alerts missing near window edge | server-vs-UTC offset wrong | confirm `SCOM_SERVER_LOCAL_OFFSET_HOURS` matches the SCOM server's real offset |
| Runs on Linux → fails | `powershell.exe` not present | SCOM collection is Windows-only by design; run it from the Windows Tomcat host |

---

## Enabling real collection + tracing the flow in the web-server logs

### The real backend flow (enable → collect → ingest → correlate)
1. **Verify** each connector (Settings → Connections → Test) so credentials/endpoints are good.
2. **Enable ingestion** so the scheduled poller runs automatically:
   `BMC_INGESTION_ENABLED=true` (and/or `SCOM_INGESTION_ENABLED=true`) + the collector env vars, then restart. The poller then collects every `poll-interval-ms` (default 20 min) and ingests. Manual runs remain available via `POST /api/v1/ingestion/{bmc,scom}/collect-now`.
3. Alerts land in the **Custom Index** (visible in Log Explorer).
4. **Correlate** on demand: `GET /api/v1/correlation?minutes=120` → candidate incidents.

### Every stage is logged (grep the Tomcat log to trace)
All actions write INFO lines with a greppable stage marker and the request `correlationId` (also in the log MDC via `logging.pattern.level`). Trace an end-to-end cycle with:
```
grep -E "\[COLLECT\]|\[INGEST\]|\[CORRELATE\]" catalina.out
```
| Marker | Emitted by | What it records |
|---|---|---|
| `[COLLECT] BMC/SCOM start` / `complete` | `BmcCollector` / `ScomCollector` | window, scope, **fetched** count, indexed/duplicates/failed, **took=…ms**, correlationId |
| `[INGEST] {source} batch start` / `complete` | `IngestionService` | received / normalized / duplicates / indexed / failed, correlationId |
| `[INGEST] dropping unmappable …` | `IngestionService` | per-event normalization failure (never aborts the batch) |
| `[CORRELATE] window … -> alerts=… mapped=… unmappedCIs=… incidents=… took=…ms` | `CorrelationService` | correlation run summary |
| `[CORRELATE] incident key=… rootCause=… apps=… alerts=…` | `CorrelationService` | one line per formed incident |
| `[CORRELATE] N unmapped CI(s) not in topology (CMDB gap): […]` | `CorrelationService` | the CIs to add to the topology so incidents form |

The scheduled pollers also log `BMC/SCOM scheduled poll complete: {IngestionResult}` (or a swallowed error that retries next tick). Redis-down and index issues are logged as `WARN` but never drop alerts (fail-open dedup).


