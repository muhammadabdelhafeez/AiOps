---
applyTo: "**/backend/**,**/*.java,**/*.kt,**/src/main/**,**/src/test/**"
---

# Backend Instructions (Java 21 / Spring Boot 3)

## Architecture
- Controllers: thin, validate inputs, map DTOs, call services.
- Services: business logic, tenant scoping, RBAC enforcement, audit, outbox events.
- Repositories: tenant_id in every query; never fetch cross-tenant.
- DB: PostgreSQL is system-of-record; Neo4j is hot analytics only.

## Security (OWASP)
- Validate all inputs (Bean Validation), deny-by-default.
- Use Spring Security; enforce permission checks at service layer.
- Avoid IDOR: never trust client-provided IDs without tenant + scope validation.
- Protect against SSRF for any URL-based connector config.
- Implement rate limiting for write-heavy endpoints where applicable.
- Log safely: no secrets, no tokens, no raw payload dumps.

## Platform rules
- Required headers: X-Tenant-Id, X-User-Id (reject if missing).
- Audit every write (who/what/when/before-after/correlationId).
- Use outbox_events for async processing (AI generation, evidence CSV generation, scheduled runs).
- Degraded mode:
  - If Neo4j unavailable: still produce incidents but mark correlation as degraded.
  - If AI unavailable: queue AI tasks and mark "AI pending".

## API conventions
- Consistent pagination/filtering: page, size, sort, timeRange.
- Standard error format (problem+json recommended).
- Idempotency on run triggers where needed.
- Use OpenAPI annotations where possible.
