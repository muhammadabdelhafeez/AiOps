# Services — Support & Cross-Cutting

> Catalog of **platform, security, observability, and operations** services that support the core domain.
> Core business services live in `docs/SERVICES_CORE.md`.

Update this file whenever a new support/cross-cutting class or endpoint is added/changed, then append a Task Log entry in the active `docs/PROGRESS-*.md`.

---

## 1. platform.security (`org.kfh.aiops.platform.security`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `SecurityConfig` | Spring Security filter chain, deny-by-default, CSRF rules. | 🟡 Skeleton (JWT/OIDC pending) |
| `TenantContextFilter` | Reads `X-Tenant-Id`/`X-User-Id`/`X-Correlation-Id`, rejects if missing/invalid. | 🟡 Implemented as `TenantContextResolver` in `platform.tenant` |
| `PermissionEvaluator` | Service-layer permission enforcement (RBAC). | 🟡 `TenantContext.requirePermission()` in place |
| `SsrfGuard` | Validates outbound URLs against allowlist (connector configs). | 🟢 Implemented |
| `SecretCipherService` | AES-GCM (or KMS) encryption for `config.connector_secrets`. | 🟢 Implemented |

> 2026-06-10: `SecurityConfig` currently permits `/api/v1/**` so the static frontend can call scaffold endpoints; service-layer `TenantContext`/RBAC remains enforced. Replace with enterprise JWT/OIDC before production.

## 2. platform.tenant (`org.kfh.aiops.platform.tenant`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `TenantContext` | Per-request tenant/user/country/correlationId holder. | 🟢 Implemented |
| `TenantContextResolver` | Spring `HandlerMethodArgumentResolver`. | 🟢 Implemented |
| `TenantWebMvcConfig` | Registers the resolver with Spring MVC. | 🟢 Implemented |

## 3. platform.country (`org.kfh.aiops.platform.country`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `CountryRegistry` | Allowed country codes (KW/BH/EG/…); enabled flags. | 🟢 Implemented |
| `CountryAccessGuard` | Enforces "Kuwait users must not see Bahrain/Egypt unless permitted". | 🟢 Implemented |

## 4. platform.audit (`org.kfh.aiops.platform.audit`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `AuditService` | Writes `identity.audit_log` with before/after JSON (secret-stripped). | 🟡 Port defined; `LoggingAuditService` placeholder |
| `LoggingAuditService` | Temporary structured-log audit until JPA adapter ships. | 🟢 Implemented |
| `JpaAuditService` | JPA-backed adapter persisting to `identity.audit_log`. | ⚪ Not implemented |
| `AuditAspect` | `@Auditable` annotation interceptor. | ⚪ Not implemented |
| `AuditController`, `AuditQueryService` | Frontend-aligned audit list/detail/export endpoints backed by the Phase 1 in-memory audit view. | 🟡 Phase 1 scaffold |

## 5. platform.config (`org.kfh.aiops.platform.config`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `AppProperties` | `@ConfigurationProperties` root for tenant/country/env settings. | 🟡 Split across `CountryRegistry`, `SsrfProperties` |
| `Resilience4jConfig` | Retry/CB/bulkhead defaults. | 🟡 Configured via `application.properties` defaults profile |
| `WebClientConfig` | Async client with timeouts + SSRF guard. | 🟢 Implemented (timeouts); SSRF wiring in next task |
| `ObjectMapperConfig` | JSR-310, snake_case, fail-on-unknown=false for inbound. | 🟢 Implemented |
| `SettingsController`, `SettingsService` | Frontend-aligned settings get/update/test endpoints; writes are audited. | 🟡 Phase 1 scaffold (in-memory) |

## 6. platform.exception (`org.kfh.aiops.platform.exception`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `GlobalExceptionHandler` | `@RestControllerAdvice` → `problem+json` responses; preserves framework status exceptions and maps missing resources to 404. | 🟢 Implemented |
| `MissingTenantContextException` | 400 on missing headers. | 🟢 Implemented |
| `ForbiddenAccessException` | 403 on RBAC/country denial. | 🟢 Implemented |
| `NotFoundException` | 404 with safe message. | 🟢 Implemented |
| `ValidationException` | 422 with field details (no PII). | 🟢 Implemented |
| `AiOpsException` | Base class with stable error code. | 🟢 Implemented |

## 7. platform.validation (`org.kfh.aiops.platform.validation`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `@CountryCodeValid` | Bean Validation constraint (KW/BH/EG…). | 🟢 Implemented |
| `@EnvironmentValid` | PROD/UAT/DEV. | 🟢 Implemented |
| `@SafeUrl` | SSRF allowlist check. | ⚪ Not implemented (use `SsrfGuard.check()` directly for now) |

## 8. platform.observability (`org.kfh.aiops.platform.observability`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `CorrelationIdFilter` | Generate/propagate `X-Correlation-Id` and MDC. | 🟢 Implemented |
| `HttpActionLoggingFilter` | Secret-safe web action logging: one Java console log line per HTTP request with method, path, status, duration, tenant/user/country/environment, and correlation ID; excludes bodies, query strings, tokens, and passwords. | 🟢 Implemented |
| `StructuredLoggingAppender` | JSON logs with required fields (§20). | 🟡 SLF4J MDC pattern in `application.properties` |
| `MetricsConfig` | Micrometer + Prometheus exposition. | 🟡 Actuator + Prometheus enabled in properties |
| `HealthIndicators` | DB/Redis/Neo4j/object-storage probes. | ⚪ Not implemented |

## 9. platform.outbox (`org.kfh.aiops.platform.outbox`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `OutboxPublisher` | Port for transactional outbox writes (secret-stripped payload). | 🟢 Port defined |
| `OutboxEventEntity` | Row in `ops.outbox_events`. | ⚪ Not implemented |
| `JdbcOutboxPublisher` | Adapter writing to `ops.outbox_events`. | ⚪ Not implemented |
| `OutboxRelay` | Scheduled drain → notification/AI workers. | ⚪ Not implemented |

## 10. notification (`org.kfh.aiops.notification`)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `EmailNotifier` | SMTP + templated email. | ⚪ Not implemented |
| `TeamsNotifier` | Microsoft Teams webhook (signed). | ⚪ Not implemented |
| `SmsNotifier` | SMS gateway adapter. | ⚪ Not implemented |
| `WebhookNotifier` | Outbound HTTPS with signature. | ⚪ Not implemented |
| `NotificationRouter` | Pick channel per severity/country/journey. | ⚪ Not implemented |

## 11. ops (scheduling & jobs)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `JobScheduler` | Quartz/Spring Scheduling for retention/archive/plugin runs. | ⚪ Not implemented |
| `ConnectorRunRecorder` | Persist `ops.connector_runs` + `connector_run_logs`. | ⚪ Not implemented |
| `RetentionJob` | Drops/archives old shards per country/env policy. | ⚪ Not implemented |
| `ArchiveJob` | Moves cold telemetry to object storage. | ⚪ Not implemented |

## 12. identity (admin APIs)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `UserController`, `RoleController`, `IdentityAdminService` | Frontend-aligned user/role CRUD, user role list, and user permission lookup endpoints; user list/detail operations are country-guarded and login-user writes require `IdentityJdbcRepository` so created users persist in PostgreSQL and can sign in. | 🟡 Phase 1 scaffold with DB-required login-user writes |
| `IdentityBootstrapProperties`, `IdentityBootstrapInitializer` | Datasource-backed startup bootstrap for the configured tenant, default RBAC roles, and optional configured admin create/reactivate/password-reset when `KFH_BOOTSTRAP_ADMIN_PASSWORD` is supplied. | 🟢 Implemented |
| `BootstrapInMemoryAuthenticator` | In-memory bootstrap admin sign-in fallback that accepts the configured bootstrap username/password/country/environment regardless of database state. Disabled by clearing `kfh.identity.bootstrap.password`. | 🟢 Implemented |
| `IdentityAuthService`, `IdentityJdbcRepository` | Datasource-backed sign-in with BCrypt verification and secret-safe diagnostic counters for rejected sign-ins. | 🟢 Implemented |
| `AuditQueryService` | Query audit events with RBAC; JPA-backed `identity.audit_log` adapter pending. | 🟡 Phase 1 scaffold (in-memory) |

**Endpoints:** `/api/v1/users`, `/api/v1/roles`, `/api/v1/audit` implemented as Phase 1 scaffold endpoints.

## 13. Application entry points (`org.kfh.aiops` root)

| Class | Responsibility | Status |
|-------|----------------|--------|
| `AiOpsApplication` | Main Spring Boot application entry point in the root package, using default scanning for `org.kfh.aiops.*`. | 🟢 Implemented |
| `ServletInitializer` | WAR deployment initializer for managed servlet containers. | 🟢 Implemented |

---

## Cross-Cutting Conventions

- All services use **constructor injection**.
- All write paths emit an `AuditService.recordWrite(...)` call.
- All async work goes through the **outbox** (no in-transaction HTTP).
- All external HTTP uses `WebClientConfig` + `SsrfGuard`.
- All Redis keys follow `health:`, `dedup:`, `lock:`, `rate-limit:`, `dashboard:`, `rca:evidence:`, `ai:summary:` patterns (see `CODE_TEMPLATES.md`).
- All secrets are read through `SecretCipherService` and **never** returned by APIs.

---

## Status Legend
🟢 Implemented · 🟡 In progress · 🔴 Blocked · ⚪ Not started

