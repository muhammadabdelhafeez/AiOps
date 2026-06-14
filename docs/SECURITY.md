# Security (OWASP) — KFH AIOps Command Center

## Baseline security requirements
- TLS everywhere
- Strict multi-tenancy: tenant_id in every query and record
- RBAC permission checks for every protected action
- Audit log for every write
- Secrets encrypted at rest; never logged
- Input validation on all APIs (DTO validation)

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

## Residual architectural requirement
- Permission-based RBAC annotations exist on connector services. Production deployments must provide authenticated principals/authorities from the enterprise identity layer or trusted gateway before enabling protected endpoints. Header-only tenant/user context is not a substitute for authentication.

## Audit logging
Every write must log:
- tenantId, userId, action, entity, before/after summary, timestamp, correlationId

## Secrets management
- Use encrypted storage for integration secrets.
- Access secrets only in server-side code paths and keep them out of logs.
