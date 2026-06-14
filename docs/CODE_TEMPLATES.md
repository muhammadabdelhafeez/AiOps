# Code Templates & Naming Conventions — KFH Causal AIOps Platform

> Every generated class, file, and identifier must follow this document.
> See `.github/copilot-instructions.md` §6 (packages), §22 (code style), and §29–§31 (per-language rules).

---

## 1. Naming Conventions

### Java
| Element | Convention | Example |
|---|---|---|
| Package | lowercase, dot-separated, under `org.kfh.aiops` | `org.kfh.aiops.incident.lifecycle` |
| Class | PascalCase | `IncidentLifecycleService` |
| Interface | PascalCase, no `I` prefix | `AiOpsConnectorPlugin` |
| Record (DTO) | PascalCase, suffix `Dto`/`Request`/`Response` | `IncidentSummaryDto`, `CreateIncidentRequest` |
| Enum | PascalCase singular | `IncidentStatus` |
| Method | camelCase, verb-first | `openIncident`, `findByTenantAndCountry` |
| Constant | UPPER_SNAKE_CASE | `MAX_RETRY_ATTEMPTS` |
| Test class | `{ClassUnderTest}Test` | `IncidentLifecycleServiceTest` |
| Test method | `should{ExpectedBehavior}When{Condition}` | `shouldRejectApiRequestWithoutTenantHeader` |
| Controller | `{Resource}Controller`, REST under `/api/v1/...` | `IncidentController` |
| Service | `{Domain}Service` | `RcaCausalService` |
| Repository | `{Entity}Repository` | `IncidentRepository` |
| Entity | `{Domain}Entity` (JPA) or `{Domain}Node` (Neo4j) | `IncidentEntity`, `ApplicationNode` |
| Mapper | `{Source}To{Target}Mapper` | `CanonicalEventToIncidentMapper` |
| Plugin | `{Source}ConnectorPlugin` | `SCOMConnectorPlugin` |

### Database (PostgreSQL)
- Tables/columns: `snake_case`, plural tables (`incidents`, `connector_runs`).
- Schemas: `identity`, `config`, `cmdb`, `incident`, `ops` (see `DATABASE_SCHEMA.md`).
- Flyway: `V{n}__<short_description>.sql`.
- Indexes: `ix_{table}_{col1}_{col2}`. Uniques: `ux_{table}_{cols}`. FKs: `fk_{table}_{ref_table}`.

### REST API
- Versioned base: `/api/v1/`.
- Plural resource names: `/api/v1/incidents`, `/api/v1/connectors`.
- Sub-resources: `/api/v1/incidents/{id}/evidence`.
- Action verbs (avoid when possible): `/api/v1/incidents/{id}:reopen` (colon form).
- Query params: `page`, `size`, `sort`, `country`, `environment`, `severity`, `from`, `to`.

### React / TypeScript
- Components: `PascalCase.tsx` (`IncidentTable.tsx`).
- Hooks: `useCamelCase.ts` (`useIncidents.ts`).
- Files for non-component utilities: `kebab-case.ts` (`api-client.ts`).
- Pages: `pages/{area}/{Page}.tsx` (`pages/incidents/IncidentsPage.tsx`).
- CSS modules / Tailwind class groups: utility-first, no inline styles.

### Cypher / Neo4j
- Node labels: `PascalCase` singular (`Application`, `BusinessJourney`).
- Relationship types: `SCREAMING_SNAKE_CASE` (`DEPENDS_ON`, `HAS_JOURNEY`).
- Property names: `camelCase` (`countryCode`, `criticality`).

### Redis keys
- Colon-separated, country + environment scoped:
  - `health:{COUNTRY}:{ENV}:{type}:{id}`
  - `dedup:{COUNTRY}:{source}:{resource}:{ruleCode}`
  - `lock:connector:{plugin}:{COUNTRY}`
  - `rate-limit:tenant:{COUNTRY}:{path}`
  - `dashboard:{COUNTRY}:{ENV}:overview`
  - `rca:evidence:{incidentNumber}`
  - `ai:summary:known-issue:{hash}`

---

## 2. Java Templates

### 2.1 Controller (thin, validates, maps DTOs)

```java
package org.kfh.aiops.incident.api;

import jakarta.validation.Valid;
import org.kfh.aiops.incident.dto.*;
import org.kfh.aiops.incident.service.IncidentService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public Page<IncidentSummaryDto> list(
            TenantContext ctx,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String environment,
            @RequestParam(required = false) String severity,
            Pageable pageable) {
        return incidentService.search(ctx, country, environment, severity, pageable);
    }

    @GetMapping("/{id}")
    public IncidentDetailDto get(TenantContext ctx, @PathVariable UUID id) {
        return incidentService.getById(ctx, id);
    }

    @PostMapping
    public ResponseEntity<IncidentSummaryDto> create(
            TenantContext ctx,
            @Valid @RequestBody CreateIncidentRequest request) {
        var created = incidentService.create(ctx, request);
        return ResponseEntity.status(201).body(created);
    }
}
```

### 2.2 Service (business logic, tenant scoping, audit, outbox)

```java
package org.kfh.aiops.incident.service;

import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.kfh.aiops.incident.repository.IncidentRepository;
import org.kfh.aiops.incident.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final AuditService auditService;
    private final IncidentMapper mapper;
    private final OutboxPublisher outbox;

    public IncidentService(IncidentRepository incidentRepository,
                           AuditService auditService,
                           IncidentMapper mapper,
                           OutboxPublisher outbox) {
        this.incidentRepository = incidentRepository;
        this.auditService = auditService;
        this.mapper = mapper;
        this.outbox = outbox;
    }

    @Transactional
    public IncidentSummaryDto create(TenantContext ctx, CreateIncidentRequest request) {
        ctx.requirePermission("INCIDENT_CREATE");
        var entity = mapper.toEntity(ctx, request);
        incidentRepository.save(entity);

        auditService.recordWrite(ctx, "INCIDENT_CREATED", "Incident",
                entity.getId().toString(), null, mapper.toAuditView(entity));

        outbox.publish(ctx, "INCIDENT_OPENED", entity.getId(), mapper.toEventPayload(entity));
        return mapper.toSummary(entity);
    }
}
```

### 2.3 Repository (tenant + country always in the query)

```java
package org.kfh.aiops.incident.repository;

import org.kfh.aiops.incident.model.IncidentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<IncidentEntity, UUID> {

    @Query("""
        select i from IncidentEntity i
        where i.tenantId = :tenantId
          and (:country is null or i.countryCode = :country)
          and (:env is null or i.environment = :env)
          and (:severity is null or i.severity = :severity)
        """)
    Page<IncidentEntity> search(@Param("tenantId") UUID tenantId,
                                @Param("country") String country,
                                @Param("env") String env,
                                @Param("severity") String severity,
                                Pageable pageable);

    Optional<IncidentEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
```

### 2.4 DTO (record, validated)

```java
package org.kfh.aiops.incident.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateIncidentRequest(
        @NotBlank @Size(max = 500) String title,
        @NotBlank @Pattern(regexp = "KW|BH|EG") String countryCode,
        @NotBlank @Pattern(regexp = "PROD|UAT|DEV") String environment,
        @NotBlank @Pattern(regexp = "CRITICAL|HIGH|MEDIUM|LOW|INFO") String severity,
        @NotNull Instant firstEventAt,
        String correlationId
) { }
```

### 2.5 Entity (auditable, optimistic locking)

```java
package org.kfh.aiops.incident.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incidents", schema = "incident",
       indexes = {
         @Index(name = "ix_incidents_tenant_status_time",
                columnList = "tenant_id, status, last_event_at")
       })
public class IncidentEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(nullable = false, length = 10)
    private String environment;

    @Column(name = "incident_number", nullable = false, unique = true, length = 40)
    private String incidentNumber;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "first_event_at", nullable = false)
    private Instant firstEventAt;

    @Column(name = "last_event_at", nullable = false)
    private Instant lastEventAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    // getters/setters omitted
}
```

### 2.6 Plugin (connector contract)

```java
package org.kfh.aiops.plugin.implementations.scom;

import org.kfh.aiops.normalization.model.CanonicalTelemetryEvent;
import org.kfh.aiops.plugin.api.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SCOMConnectorPlugin implements AiOpsConnectorPlugin {

    @Override public String pluginType() { return "SCOM"; }

    @Override public PluginMetadata metadata() {
        return PluginMetadata.builder()
                .displayName("Microsoft SCOM")
                .version("1.0.0")
                .supportedEventTypes(List.of("ALERT", "METRIC"))
                .build();
    }

    @Override public PluginTestResult testConnection(PluginContext context) {
        // validate base URL (SSRF allowlist), credentials present (encrypted), reachable
        return PluginTestResult.success();
    }

    @Override public List<CanonicalTelemetryEvent> collect(PluginContext context, CollectionWindow window) {
        // pagination + retry + circuit breaker; map source DTO → CanonicalTelemetryEvent
        return List.of();
    }

    @Override public PluginHealth health() { return PluginHealth.healthy(); }
}
```

### 2.7 Outbox event (no direct external calls in transactions)

```java
package org.kfh.aiops.platform.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository repo;

    public OutboxPublisher(OutboxEventRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void publish(TenantContext ctx, String eventType, UUID aggregateId, JsonNode payload) {
        var event = new OutboxEventEntity();
        event.setTenantId(ctx.tenantId());
        event.setAggregateType("Incident");
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);   // must be secret-stripped
        event.setStatus("PENDING");
        event.setCorrelationId(ctx.correlationId());
        repo.save(event);
    }
}
```

### 2.8 Unit test (AAA pattern, descriptive names)

```java
class IncidentLifecycleServiceTest {

    @Test
    void shouldNotCloseIncidentWhenRootCauseStillUnhealthy() {
        // arrange
        var incident = TestFixtures.openIncident();
        var health = HealthState.unhealthy(incident.getRootCauseResourceId());
        var sut = new IncidentLifecycleService(/* mocks */);

        // act
        var result = sut.evaluateClosure(incident, health, /* quiet window */);

        // assert
        assertThat(result.shouldClose()).isFalse();
        assertThat(result.reason()).contains("ROOT_CAUSE_UNHEALTHY");
    }
}
```

---

## 3. Flyway Migration Template

```sql
-- V{n}__add_incident_status_history.sql
-- Purpose: Track every incident status transition for audit + lifecycle replay.
-- Owner:   incident module
-- Country/tenant: ALL

CREATE TABLE IF NOT EXISTS incident.incident_status_history (
    id              UUID PRIMARY KEY,
    incident_id     UUID NOT NULL REFERENCES incident.incidents(id),
    from_status     VARCHAR(20),
    to_status       VARCHAR(20) NOT NULL,
    changed_by      UUID,
    reason          TEXT,
    correlation_id  VARCHAR(64),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_incident_status_history_incident_time
    ON incident.incident_status_history (incident_id, changed_at DESC);
```

Rules:
- Never modify an applied migration; ship `V{n+1}` instead.
- Prefer additive changes; do data backfill in a separate migration.
- Update `docs/DATABASE_SCHEMA.md` in the same PR.

---

## 4. React / TypeScript Templates

### 4.1 API Client wrapper

```ts
// src/api/client.ts
import axios from "axios";

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? "/api/v1",
  timeout: 15_000,
});

api.interceptors.request.use((config) => {
  config.headers["X-Tenant-Id"] = sessionStore.getTenantId();
  config.headers["X-User-Id"] = sessionStore.getUserId();
  config.headers["X-Correlation-Id"] = crypto.randomUUID();
  return config;
});

api.interceptors.response.use(
  (r) => r,
  (err) => {
    // typed error mapping; do not retry writes
    return Promise.reject(toApiError(err));
  }
);
```

### 4.2 Page component skeleton

```tsx
// src/pages/incidents/IncidentsPage.tsx
import { useIncidents } from "@/hooks/useIncidents";
import { IncidentTable } from "@/components/incidents/IncidentTable";
import { FilterBar } from "@/components/common/FilterBar";

export default function IncidentsPage() {
  const { data, isLoading, error, filters, setFilters } = useIncidents();

  if (error) return <ErrorBanner error={error} />;

  return (
    <section className="p-6 space-y-4">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-kfh-navy">Incidents</h1>
        <FilterBar value={filters} onChange={setFilters} />
      </header>
      {isLoading ? <TableSkeleton rows={10} /> : <IncidentTable data={data} />}
    </section>
  );
}
```

### 4.3 Hook

```ts
// src/hooks/useIncidents.ts
import { useQuery } from "@tanstack/react-query";
import { api } from "@/api/client";

export function useIncidents(filters: IncidentFilters) {
  return useQuery({
    queryKey: ["incidents", filters],
    queryFn: async () => (await api.get("/incidents", { params: filters })).data,
    staleTime: 30_000,
  });
}
```

---

## 5. Cypher Template (topology upsert)

```cypher
// Upsert an application owned by a team in a country
MERGE (c:Country {code: $countryCode})
MERGE (app:Application {tenantId: $tenantId, code: $appCode})
  ON CREATE SET app.name = $appName, app.criticality = $criticality, app.createdAt = datetime()
  ON MATCH  SET app.name = $appName, app.criticality = $criticality, app.updatedAt = datetime()
MERGE (team:Team {tenantId: $tenantId, code: $teamCode})
MERGE (team)-[:OWNS]->(app)
MERGE (c)-[:HAS_APPLICATION]->(app);
```

---

## 6. Logging Template

Every log entry must include the structured fields from `.github/copilot-instructions.md` §20:

```java
log.atInfo()
   .addKeyValue("tenantId", ctx.tenantId())
   .addKeyValue("countryCode", ctx.countryCode())
   .addKeyValue("userId", ctx.userId())
   .addKeyValue("correlationId", ctx.correlationId())
   .addKeyValue("incidentId", incident.getId())
   .addKeyValue("durationMs", durationMs)
   .addKeyValue("status", "SUCCESS")
   .log("Incident opened");
```

Never log: passwords, API keys, tokens, PII, secret connector config, raw payloads.

---

## 7. Error Response Template (problem+json)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingTenantContextException.class)
    public ResponseEntity<ProblemDetail> handleMissingTenant(MissingTenantContextException ex,
                                                             HttpServletRequest req) {
        var pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setType(URI.create("https://aiops.kfh/errors/missing-context"));
        pd.setTitle("Missing or invalid context");
        pd.setProperty("code", "MISSING_OR_INVALID_CONTEXT");
        pd.setProperty("correlationId", req.getHeader("X-Correlation-Id"));
        pd.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(400).body(pd);
    }
}
```

---

## 8. Definition-of-Done Boilerplate Checklist

Copy this into every PR description:

```
- [ ] Tenant + country context enforced
- [ ] RBAC checked at service layer
- [ ] Inputs validated (Bean Validation / Zod)
- [ ] Audit log written for write actions
- [ ] No secrets / PII / tokens logged or returned
- [ ] SSRF-safe for any URL-based config
- [ ] Pagination + filtering supported on list endpoints
- [ ] Correlation ID propagated end-to-end
- [ ] Tests for core logic added
- [ ] docs/PROGRESS-*.md entry appended
- [ ] docs/API_CONTRACTS.md updated (if API changed)
- [ ] docs/DATABASE_SCHEMA.md updated (if schema changed)
- [ ] docs/SERVICES_CORE.md / SERVICES_SUPPORT.md updated (if classes/endpoints added)
```

