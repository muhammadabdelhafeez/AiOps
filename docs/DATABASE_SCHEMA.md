# Database Schema — KFH Causal AIOps Platform (PostgreSQL)

> **Source of truth for the relational system of record.**
> Neo4j (topology), Redis (hot cache), the Custom Index Engine (telemetry search), and Object Storage (raw archives) are documented separately in `docs/ARCHITECTURE.md`.
>
> All schema changes **must** ship as a new Flyway migration `src/main/resources/db/migration/V{n}__description.sql` and be reflected here in the same PR.

---

## Conventions

- Database: **PostgreSQL 15+**
- Migration tool: **Flyway** (versioned, never modify applied migrations)
- Naming: `snake_case`, plural table names, singular column names
- Primary keys: `UUID` (`uuid_generate_v4()` or app-generated)
- Every business table must have:
  - `tenant_id UUID NOT NULL` (multi-tenant isolation)
  - `country_code CHAR(2) NOT NULL` (KW, BH, EG, …)
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
  - `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
  - `created_by UUID`, `updated_by UUID` (FK → `identity.users.id` when available)
  - `version BIGINT NOT NULL DEFAULT 0` (optimistic locking)
- Indexes:
  - `(tenant_id, <time column>)` on every event/incident/alert table
  - Fingerprint columns
  - Foreign keys used in filters
- Constraints: prefer **FK + UNIQUE + CHECK** over application-only validation.
- No raw log payloads in PostgreSQL — store the pointer (`raw_ref`) and put the payload in object storage.

---

## Schema Map

```
identity.*       users, roles, permissions, audit_log
config.*         connectors, connector_secrets, schedules
cmdb.*           applications, services, resources, business_journeys, ownership
incident.*       incidents, incident_status_history, incident_evidence,
                 incident_groups, rca_results
ops.*            connector_runs, connector_run_logs, jobs, outbox_events
```

---

## identity schema

### identity.users
| Column | Type | Notes |
|--------|------|-------|
| user_id | UUID PK | |
| tenant_id | UUID NOT NULL | |
| username | TEXT NOT NULL | UNIQUE per tenant, country, and environment in `V5__identity_user_scope_uniqueness.sql` |
| display_name | TEXT | |
| email | TEXT | |
| auth_provider | TEXT | defaults to `local` |
| is_active | BOOLEAN NOT NULL | `false` disables sign-in |
| country_code | TEXT NOT NULL | User belongs to one scope: physical country (`KW`, `BH`, `EG`) or virtual all-country identity scope (`ALL`) for global users |
| environment | TEXT NOT NULL | `PROD`, `UAT`, `DEV` |
| password_hash | TEXT | BCrypt hash for local identity users; never returned by APIs |
| last_login_at | TIMESTAMPTZ | |
| created_at, updated_at | TIMESTAMPTZ | |

Indexes: `(tenant_id, country_code, environment)`, `(tenant_id, country_code, environment, lower(username)) UNIQUE`.

### identity.roles
| id UUID PK | tenant_id UUID | name VARCHAR(80) | description TEXT |

Predefined Phase 1 local roles: `GLOBAL_ADMIN`, `COUNTRY_ADMIN`, `NOC_OPERATOR`, `VIEWER`. The UI shows simplified labels (`Admin`, `Operator`, `Viewer`) and the backend maps them to canonical stored role names based on country scope.

### identity.role_permissions
| tenant_id UUID FK | role_id UUID FK | permission TEXT | PRIMARY KEY (tenant_id, role_id, permission) |

### identity.user_roles
| tenant_id UUID FK | user_id UUID FK | role_id UUID FK | PRIMARY KEY (tenant_id, user_id, role_id) |

### identity.audit_log
| Column | Type | Notes |
|--------|------|-------|
| audit_id | UUID PK | |
| tenant_id | UUID NOT NULL | |
| at | TIMESTAMPTZ NOT NULL | |
| actor_user_id | UUID | nullable; details also store the caller userId as text because bootstrap/system users may not have FK rows |
| action | TEXT NOT NULL | e.g. `CONNECTOR_UPDATED` |
| entity_type | TEXT NOT NULL | |
| entity_id | TEXT NOT NULL | |
| details | JSONB | secret-stripped metadata including `userId`, `countryCode`, `environment`, `correlationId`, `result`, `severity`, `message`, `beforeState`, and `afterState` |

Index: `(tenant_id, at DESC)`. Audit Activity APIs filter country/environment from `details` and never store passwords, tokens, API keys, connector secrets, or raw request bodies.

---

## config schema

### config.connectors
| id UUID PK | tenant_id | country_code | plugin_type VARCHAR(60) | name VARCHAR(150) | environment VARCHAR(10) | enabled BOOLEAN | base_url TEXT | last_test_at TIMESTAMPTZ | last_test_status VARCHAR(20) | metadata JSONB | created_at, updated_at, version |

UNIQUE: `(tenant_id, name)`.

### config.connector_secrets
| id UUID PK | connector_id UUID FK | secret_key VARCHAR(80) | secret_ciphertext BYTEA NOT NULL | kms_key_id VARCHAR(120) | created_at | rotated_at |

Never SELECT this table from controllers.

### config.schedules
| id UUID PK | tenant_id | connector_id FK | cron_expression VARCHAR(60) | timezone VARCHAR(60) | enabled BOOLEAN | last_run_at | next_run_at |

---

## cmdb schema

### cmdb.applications
| id UUID PK | tenant_id | country_code | code VARCHAR(60) UNIQUE per tenant | name VARCHAR(255) | criticality VARCHAR(20) | business_domain VARCHAR(80) | owner_team_id UUID | metadata JSONB |

### cmdb.services
| id UUID PK | tenant_id | application_id FK | code VARCHAR(80) | name VARCHAR(255) | service_type VARCHAR(40) | criticality VARCHAR(20) |

### cmdb.resources
| id UUID PK | tenant_id | country_code | resource_external_id VARCHAR(150) | resource_type VARCHAR(40) | resource_role VARCHAR(40) | hostname VARCHAR(255) | ip_address INET | application_id FK NULL | service_id FK NULL | metadata JSONB |

UNIQUE: `(tenant_id, resource_external_id)`.

### cmdb.business_journeys
| id UUID PK | tenant_id | country_code | code VARCHAR(80) | name VARCHAR(255) | business_domain VARCHAR(80) | criticality VARCHAR(20) |

### cmdb.journey_applications
| journey_id FK | application_id FK | sequence_order INT | PRIMARY KEY (journey_id, application_id) |

### cmdb.ownership
| id UUID PK | tenant_id | entity_type VARCHAR(40) | entity_id UUID | team_id UUID FK | role VARCHAR(40) |

---

## incident schema

### incident.incidents
| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| tenant_id | UUID NOT NULL | |
| country_code | CHAR(2) NOT NULL | |
| environment | VARCHAR(10) NOT NULL | PROD/UAT/DEV |
| incident_number | VARCHAR(40) NOT NULL | e.g. `INC-20260607-001` |
| title | VARCHAR(500) NOT NULL | |
| severity | VARCHAR(20) NOT NULL | CRITICAL/HIGH/MEDIUM/LOW/INFO |
| status | VARCHAR(20) NOT NULL | NEW/OPEN/ACKNOWLEDGED/MITIGATED/MONITORING/CLOSED/REOPENED/SUPPRESSED |
| business_journey_id | UUID FK | nullable |
| primary_application_id | UUID FK | |
| root_cause_resource_id | UUID FK | nullable until RCA done |
| confidence | NUMERIC(5,2) | 0–100 |
| first_event_at | TIMESTAMPTZ NOT NULL | |
| last_event_at | TIMESTAMPTZ NOT NULL | |
| closed_at | TIMESTAMPTZ | |
| operator_locked | BOOLEAN NOT NULL DEFAULT FALSE | blocks auto-close |
| correlation_id | VARCHAR(64) | |
| created_at, updated_at, created_by, updated_by, version | | |

Indexes: `(tenant_id, country_code, status, last_event_at DESC)`, `(incident_number) UNIQUE`.

### incident.incident_status_history
| id UUID PK | incident_id FK | from_status | to_status | changed_by UUID | reason TEXT | correlation_id | changed_at TIMESTAMPTZ |

### incident.incident_evidence
| id UUID PK | incident_id FK | evidence_type VARCHAR(40) | summary TEXT | source_system VARCHAR(60) | raw_ref TEXT | observed_at TIMESTAMPTZ |

`raw_ref` is the object-storage pointer; **never** store full payloads here.

### incident.incident_groups
| id UUID PK | tenant_id | fingerprint VARCHAR(128) NOT NULL | causal_path_hash VARCHAR(128) | first_seen_at | last_seen_at | recurrence_count INT |

UNIQUE: `(tenant_id, fingerprint)`.

### incident.incident_group_members
| group_id FK | incident_id FK | PRIMARY KEY (group_id, incident_id) |

### incident.rca_results
| id UUID PK | incident_id FK UNIQUE | root_cause_entity_id VARCHAR(150) | root_cause_entity_type VARCHAR(40) | impacted_journey VARCHAR(255) | summary TEXT | symptom_entity_ids TEXT[] | evidence JSONB | confidence NUMERIC(5,2) | recommended_action TEXT | ai_narrative TEXT | model_used VARCHAR(60) | generated_at TIMESTAMPTZ |

---

## ops schema

### ops.connector_runs
| id UUID PK | tenant_id | connector_id FK | started_at | ended_at | status VARCHAR(20) | events_collected BIGINT | error_code VARCHAR(80) | error_message TEXT | correlation_id |

Indexes: `(connector_id, started_at DESC)`.

### ops.connector_run_logs
| id UUID PK | run_id FK | level VARCHAR(10) | message TEXT | logged_at TIMESTAMPTZ |

### ops.jobs
| id UUID PK | tenant_id | job_type VARCHAR(60) | status | payload JSONB | scheduled_at | started_at | ended_at | attempts INT | last_error TEXT |

### ops.outbox_events
| Column | Type | Notes |
|--------|------|-------|
| id | UUID PK | |
| tenant_id | UUID NOT NULL | |
| aggregate_type | VARCHAR(60) NOT NULL | e.g. `Incident` |
| aggregate_id | UUID NOT NULL | |
| event_type | VARCHAR(80) NOT NULL | e.g. `INCIDENT_OPENED` |
| payload | JSONB NOT NULL | secret-stripped |
| status | VARCHAR(20) NOT NULL | PENDING/PUBLISHED/FAILED |
| correlation_id | VARCHAR(64) | |
| created_at TIMESTAMPTZ NOT NULL | published_at TIMESTAMPTZ | attempts INT NOT NULL DEFAULT 0 |

Indexes: `(status, created_at)`, `(tenant_id, created_at DESC)`.

---

## Migration Workflow

1. Pick the next free number: look at `src/main/resources/db/migration/` for the highest `V{n}__*.sql`.
2. Create `V{n+1}__<short_description>.sql` (snake_case, no spaces).
3. Write **idempotent**, **additive** DDL where possible (`IF NOT EXISTS`, new nullable columns, then backfill, then NOT NULL in a later migration).
4. Add/adjust indexes in the same migration.
5. Update this document (`docs/DATABASE_SCHEMA.md`) to reflect the new structure.
6. Add a Task Log entry to the active `docs/PROGRESS-*.md` referencing the migration filename.

---

## What NOT to put in PostgreSQL

- Raw telemetry rows (logs, traces, metric samples) — use the **Custom Index Engine**.
- Plaintext secrets — use `config.connector_secrets` (encrypted) or an external KMS.
- Topology graph — use **Neo4j**.
- Hot health state / dashboard cache — use **Redis**.

