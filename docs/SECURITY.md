# Security (OWASP) — KFH AIOps Command Center

## Baseline security requirements
- TLS everywhere
- Strict multi-tenancy: tenant_id in every query and record
- Country isolation: every persistence + cache key is `(tenant_id, country, environment)` scoped (Redis uses key-prefix isolation on **DB 0 only** — never logical DB > 0)
- RBAC permission checks for every protected action, at the **service layer** (filters are defense-in-depth only)
- Audit log for every write (before + after state, correlation id)
- Secrets encrypted at rest; never logged; never returned by APIs; **never placed in `EvidencePack` or any AI prompt**
- Input validation on all APIs (DTO validation)

## AI / Funnel guardrails (CAUSAL_PIPELINE §12)
- AI **never** receives raw alerts, logs, metrics, traces, change events, secrets, tokens, credentials, or unnecessary PII.
- AI only receives a compact `EvidencePack` (≤ 3 KB) built by `org.kfh.aiops.rca.evidence.EvidencePackBuilder`. The builder runs `EvidencePackValidator` to reject any pack containing forbidden patterns (regex sweep for secret-like values, JWTs, bearer tokens, IBANs, civil IDs, card PANs).
- AI **never** decides incident lifecycle. Only the deterministic `IncidentLifecycleEngine` may open / acknowledge / monitor / close / reopen.
- AI **never** runs in the user request thread. All AI work is dispatched via outbox events (`AI_NARRATIVE_REQUESTED`) on virtual threads.
- AI output is validated against the pack: `citedEvidenceIds` must be a subset of `pack.evidence[].id`. Any hallucinated reference is rejected and audited as `AI_HALLUCINATION_BLOCKED`.
- `CostGuard` per-tenant daily call + USD budget gates every Azure OpenAI call; soft cap demotes to DeepSeek, hard cap pages on-call.

## Dependency security maintenance
- Keep Maven dependencies on CVE-remediated versions before release and before production deployment.
- PostgreSQL JDBC is pinned to `42.7.11` or newer to cap SCRAM PBKDF2 iterations and reduce client-side CPU exhaustion risk during authentication.
- Azure Identity is pinned to `1.12.2` or newer to remediate the Azure Identity/MSAL elevation-of-privilege advisory.
- Prefer minimal compatible security upgrades, then run dependency resolution and backend tests before promoting a build.

## OWASP guidance mapping (practical)
### Access Control (Broken Access Control / IDOR)
- Never trust client-provided IDs alone.
- Validate (tenant_id + scope) before returning or mutating any object.

### Injection
- Use parameterized queries only.
- Validate and sanitize any dynamic query components (sort fields, filters).

### Sensitive Data Exposure
- Do not log tokens, passwords, raw secrets, or raw payload dumps.
- Mask sensitive values in logs and responses.

### SSRF (connector configs)
- For any URL-based connector: restrict allowed domains/IP ranges.
- Block metadata IPs (169.254.169.254 etc.) and internal ranges unless explicitly allowed.

### AuthN/AuthZ
- Authenticate user identity (SSO/JWT/etc. per environment).
- Authorize by permissions + scope policies.

### Rate limiting
- Apply for write endpoints and connector run triggers.

### Security headers
- Frontend: standard security headers (CSP if possible), secure cookies if used.

## Secure coding rules
- Fail closed: deny if headers missing or permissions absent.
- Validate all inputs.
- Safe exception handling (no stack traces to client).
- Centralized correlationId for traceability.

## Implemented hardening controls (2026-06-03)
- API context filter rejects `/api/**` requests unless `X-Tenant-Id` and `X-User-Id` are valid UUID values; malformed `X-Correlation-Id` values are rejected to reduce header/log injection risk.
- Incident detail and status-write flows use tenant-scoped repository lookups (`incidentId + tenantId`) to prevent IDOR and cross-tenant access.
- User-provided incident note fields are size-bounded and reject unsupported control characters; polling window parameters are bounded to 1–168 hours.
- Connector list pagination is size-bounded and sort fields are allowlisted.
- BMC connector outbound paths must be safe relative paths; absolute URLs, traversal, CR/LF, query strings, fragments, and disabled TLS verification are rejected.
- SSRF validation requires HTTPS by default, rejects URL user-info/fragments, resolves all host addresses, and blocks localhost/private/link-local/metadata ranges unless explicitly configured otherwise for non-production.
- Neo4j vector-index configuration is validated against numeric and enum allowlists before formatting Cypher.
- Credentials and API keys are externalized through environment/property placeholders; packaged keystore material must not be committed or packaged.

## Implemented hardening controls (2026-06-11)
- Local HTTPS can reference a developer-supplied PFX keystore by file path, but the keystore password remains externalized through `SERVER_SSL_KEY_STORE_PASSWORD` and certificate files under `src/main/resources/certs/**` are excluded from Maven-packaged artifacts.

## Implemented hardening controls (2026-06-18)
- BMC, AppDynamics, and vROps connector tests keep `verifySsl=true` by default. Operators may explicitly set `verifySsl=false` per connector for governed dev/hybrid troubleshooting when Java truststore CA import is pending; the test still requires HTTPS and retains SSRF blocks for localhost, loopback, link-local, multicast, and metadata targets. The preferred remediation for PKIX failures remains importing the corporate CA chain into the JVM truststore and re-enabling verification.

## Implemented hardening controls (2026-06-21)
- Microsoft SCOM connector configuration validates WinRM management-server endpoints, rejects credentials/query/fragment and paths beyond `/wsman`, blocks localhost/metadata/link-local/multicast targets, encrypts username/password secrets, and passes SCOM live-test credentials only through a child PowerShell process environment. Live-test responses return sanitized step status and never return raw SCOM alert payloads, passwords, or PowerShell credential data.
- SCOM/WinRM expired certificate failures are reported as certificate lifecycle issues requiring renewal/rebind on the destination server. For dev/testing-domain connector hosts reaching the corporate SCOM endpoint over HTTPS/5986, the destination WinRM certificate must be non-expired, trusted by the connector host, and issued for the configured FQDN/SAN. Relaxed certificate-chain verification remains a governed troubleshooting control for trust-chain/CN/revocation issues and is not documented as a bypass for expired HTTPS listener certificates.
- SCOM/WinRM revocation-check failures are reported separately from unknown-CA and expired-certificate failures. The preferred control is CRL/OCSP reachability from the connector host; `verifySsl=false` remains an explicit temporary test-only troubleshooting control that passes `SkipRevocationCheck` to PowerShell while preserving HTTPS transport.
- EMCO Ping Monitor connector configuration validates SQL Server host/port and database names before JDBC URL construction, rejects JDBC/HTTP URLs and credential-bearing strings, blocks localhost/metadata/link-local/multicast targets, encrypts separate KFH/CCTV SQL credentials, uses parameterized bounded SQL probes with query timeouts, and returns only sanitized readiness steps without raw EMCO rows or SQL credential data.
- Connector secret encryption can now read the platform master key from either startup environment/property configuration or a protected deployment secret file (`KFH_AIOPS_SECRET_KEY_FILE` / `kfh.security.master-key-file`, with a local dev default under user-home `.kfh-aiops/secret-key.txt`). The key value and file content are never returned by Settings, never logged, and must remain outside the repository with service-account-only file permissions where possible.
- Connector live tests now fail closed with a secret-safe `SECRET_DECRYPTION_FAILED` result when saved credentials were encrypted with a different master key, rather than passing empty credentials to source-system testers. Credential rotation preserves old encrypted entries without decrypting them and encrypts only submitted replacement values with the current key.

## Implemented hardening controls (2026-06-29)
- Settings infrastructure Test Connection supports Redis, Kafka, and custom index storage through bounded, audited probes. Redis/Kafka tests reject URL syntax, credential-bearing endpoint strings, localhost, loopback, link-local, multicast, and metadata hosts before opening sockets/AdminClient probes; responses include only status, latency, checked endpoint, correlation ID, and sanitized messages.
- Settings custom index-storage tests require absolute non-traversal local/NFS paths before checking directory readability/writability. Cloud object-storage entries validate allowed pointer schemes only in this phase; HTTPS endpoints are checked against metadata/loopback/link-local targets and no SDK-backed cloud listing/read/write is performed yet.
- Settings metadata is persisted by tenant, country scope, environment, and key in `config.integration_settings`. Settings-managed provider secrets for AI, Neo4j, database/sharepoint/infrastructure rows are encrypted server-side and stripped from API responses; masked Test Connection requests decrypt saved secrets only inside the bounded server-side tester path.

## Residual architectural requirement
- Permission-based RBAC annotations exist on connector services. Production deployments must provide authenticated principals/authorities from the enterprise identity layer or trusted gateway before enabling protected endpoints. Header-only tenant/user context is not a substitute for authentication.

## Audit logging
Every write must log:
- tenantId, userId, action, entity, before/after summary, timestamp, correlationId

## Secrets management
- Use encrypted storage for integration secrets.
- Access secrets only in server-side code paths and keep them out of logs.
