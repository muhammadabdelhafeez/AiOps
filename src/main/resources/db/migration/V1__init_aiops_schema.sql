-- =========================================================
-- KFH AIOps Command Center - PostgreSQL DDL (multi-schema)
-- Version: 1.0
-- Description: Complete database schema for microservices architecture
-- =========================================================

-- Recommended extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================================
-- Base (public) schema - Tenants
-- =========================================================
CREATE TABLE IF NOT EXISTS public.tenants (
    tenant_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (name)
);

-- Helper function for updated_at triggers
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- =========================================================
-- Create all schemas
-- =========================================================
CREATE SCHEMA IF NOT EXISTS identity;
CREATE SCHEMA IF NOT EXISTS config;
CREATE SCHEMA IF NOT EXISTS cmdb;
CREATE SCHEMA IF NOT EXISTS ops;
CREATE SCHEMA IF NOT EXISTS incident;
CREATE SCHEMA IF NOT EXISTS report;
CREATE SCHEMA IF NOT EXISTS notify;

-- =========================================================
-- IDENTITY SCHEMA (Users/RBAC/Audit)
-- =========================================================
CREATE TABLE IF NOT EXISTS identity.users (
    user_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    username      TEXT NOT NULL,
    display_name  TEXT,
    email         TEXT,
    auth_provider TEXT NOT NULL DEFAULT 'local',
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, username)
);

CREATE TRIGGER trg_identity_users_updated_at
BEFORE UPDATE ON identity.users
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS identity.roles (
    role_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

CREATE TABLE IF NOT EXISTS identity.user_roles (
    tenant_id UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES identity.users(user_id) ON DELETE CASCADE,
    role_id   UUID NOT NULL REFERENCES identity.roles(role_id) ON DELETE CASCADE,
    PRIMARY KEY (tenant_id, user_id, role_id)
);

CREATE TABLE IF NOT EXISTS identity.audit_log (
    audit_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_user_id UUID REFERENCES identity.users(user_id),
    action        TEXT NOT NULL,
    entity_type   TEXT NOT NULL,
    entity_id     TEXT NOT NULL,
    details       JSONB
);

CREATE INDEX IF NOT EXISTS idx_audit_tenant_at
ON identity.audit_log(tenant_id, at DESC);

-- =========================================================
-- CONFIG SCHEMA (Connectors/Schedules/Integrations)
-- =========================================================
CREATE TABLE IF NOT EXISTS config.connectors (
    connector_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    type         TEXT NOT NULL,
    name         TEXT NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT true,
    config       JSONB NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

CREATE TRIGGER trg_config_connectors_updated_at
BEFORE UPDATE ON config.connectors
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS config.connector_secrets (
    connector_id UUID PRIMARY KEY REFERENCES config.connectors(connector_id) ON DELETE CASCADE,
    tenant_id    UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    secret_enc   JSONB NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS config.schedules (
    schedule_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    cron        TEXT NOT NULL,
    timezone    TEXT NOT NULL DEFAULT 'Europe/London',
    enabled     BOOLEAN NOT NULL DEFAULT true,
    scope       JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

CREATE TRIGGER trg_config_schedules_updated_at
BEFORE UPDATE ON config.schedules
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS config.integration_settings (
    setting_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    key        TEXT NOT NULL,
    value      JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, key)
);

-- =========================================================
-- CMDB SCHEMA (Applications/Services/Resources/Dependencies)
-- =========================================================
CREATE TABLE IF NOT EXISTS cmdb.applications (
    app_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    criticality  TEXT NOT NULL DEFAULT 'Tier2',
    owner_group  TEXT,
    description  TEXT,
    is_system    BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

CREATE TRIGGER trg_cmdb_applications_updated_at
BEFORE UPDATE ON cmdb.applications
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS cmdb.services (
    service_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    app_id      UUID NOT NULL REFERENCES cmdb.applications(app_id) ON DELETE CASCADE,
    name        TEXT NOT NULL,
    type        TEXT NOT NULL DEFAULT 'API',
    description TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, app_id, name)
);

CREATE TRIGGER trg_cmdb_services_updated_at
BEFORE UPDATE ON cmdb.services
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS cmdb.resources (
    resource_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    name         TEXT NOT NULL,
    kind         TEXT NOT NULL,
    ip           INET,
    tags         JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name, kind)
);

CREATE TRIGGER trg_cmdb_resources_updated_at
BEFORE UPDATE ON cmdb.resources
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS cmdb.service_resources (
    tenant_id   UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    service_id  UUID NOT NULL REFERENCES cmdb.services(service_id) ON DELETE CASCADE,
    resource_id UUID NOT NULL REFERENCES cmdb.resources(resource_id) ON DELETE CASCADE,
    PRIMARY KEY (tenant_id, service_id, resource_id)
);

CREATE TABLE IF NOT EXISTS cmdb.service_dependencies (
    tenant_id             UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    service_id            UUID NOT NULL REFERENCES cmdb.services(service_id) ON DELETE CASCADE,
    depends_on_service_id UUID NOT NULL REFERENCES cmdb.services(service_id) ON DELETE CASCADE,
    dep_type              TEXT,
    PRIMARY KEY (tenant_id, service_id, depends_on_service_id)
);

CREATE INDEX IF NOT EXISTS idx_cmdb_services_app
ON cmdb.services(tenant_id, app_id);

CREATE INDEX IF NOT EXISTS idx_cmdb_resources_name
ON cmdb.resources(tenant_id, name);

-- =========================================================
-- OPS SCHEMA (Runs + SharePoint Artifacts + Outbox)
-- =========================================================
CREATE TABLE IF NOT EXISTS ops.runs (
    run_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id  UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at   TIMESTAMPTZ,
    status     TEXT NOT NULL DEFAULT 'RUNNING',
    stats      JSONB
);

CREATE INDEX IF NOT EXISTS idx_runs_tenant_started
ON ops.runs(tenant_id, started_at DESC);

CREATE TABLE IF NOT EXISTS ops.run_artifacts (
    artifact_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    run_id         UUID NOT NULL REFERENCES ops.runs(run_id) ON DELETE CASCADE,
    type           TEXT NOT NULL,
    sharepoint_url TEXT NOT NULL,
    checksum       TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_run_artifacts_run
ON ops.run_artifacts(tenant_id, run_id);

CREATE TABLE IF NOT EXISTS ops.outbox_events (
    event_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_type     TEXT NOT NULL,
    aggregate_type TEXT NOT NULL,
    aggregate_id   TEXT NOT NULL,
    payload        JSONB NOT NULL,
    published_at   TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
ON ops.outbox_events(tenant_id, created_at)
WHERE published_at IS NULL;

-- =========================================================
-- INCIDENT SCHEMA (Lifecycle + links to Neo4j groupIds)
-- =========================================================
CREATE TABLE IF NOT EXISTS incident.incidents (
    incident_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    app_id                UUID NOT NULL REFERENCES cmdb.applications(app_id),
    incident_key          TEXT NOT NULL,
    title                 TEXT NOT NULL,
    status                TEXT NOT NULL DEFAULT 'OPEN',
    severity              TEXT NOT NULL DEFAULT 'MEDIUM',
    classification_label  TEXT NOT NULL DEFAULT 'NEW',
    first_seen            TIMESTAMPTZ NOT NULL,
    last_seen             TIMESTAMPTZ NOT NULL,
    last_closed_at        TIMESTAMPTZ,
    reopen_count          INT NOT NULL DEFAULT 0,
    assigned_to           UUID REFERENCES identity.users(user_id),
    pro_summary           TEXT,
    confidence            NUMERIC(3,2),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, incident_key)
);

CREATE INDEX IF NOT EXISTS idx_incidents_app_status
ON incident.incidents(tenant_id, app_id, status);

CREATE INDEX IF NOT EXISTS idx_incidents_last_seen
ON incident.incidents(tenant_id, last_seen DESC);

CREATE INDEX IF NOT EXISTS idx_incidents_incident_key
ON incident.incidents(tenant_id, incident_key);

CREATE TRIGGER trg_incidents_updated_at
BEFORE UPDATE ON incident.incidents
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS incident.incident_groups (
    tenant_id      UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    incident_id    UUID NOT NULL REFERENCES incident.incidents(incident_id) ON DELETE CASCADE,
    neo4j_group_id TEXT NOT NULL,
    confidence     NUMERIC(3,2),
    PRIMARY KEY (tenant_id, incident_id, neo4j_group_id)
);

CREATE TABLE IF NOT EXISTS incident.incident_evidence (
    evidence_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    incident_id     UUID NOT NULL REFERENCES incident.incidents(incident_id) ON DELETE CASCADE,
    type            TEXT NOT NULL,
    sharepoint_url  TEXT,
    payload         JSONB,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_incident_evidence_incident
ON incident.incident_evidence(tenant_id, incident_id, created_at DESC);

CREATE TABLE IF NOT EXISTS incident.incident_status_history (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    incident_id   UUID NOT NULL REFERENCES incident.incidents(incident_id) ON DELETE CASCADE,
    from_status   TEXT,
    to_status     TEXT NOT NULL,
    at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_user_id UUID REFERENCES identity.users(user_id),
    notes         TEXT
);

CREATE INDEX IF NOT EXISTS idx_incident_history
ON incident.incident_status_history(tenant_id, incident_id, at DESC);

-- =========================================================
-- REPORT SCHEMA (Hourly report archive)
-- =========================================================
CREATE TABLE IF NOT EXISTS report.hourly_reports (
    report_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    run_id      UUID NOT NULL REFERENCES ops.runs(run_id) ON DELETE CASCADE,
    headline    TEXT,
    summary     TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, run_id)
);

CREATE TABLE IF NOT EXISTS report.hourly_report_items (
    tenant_id  UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    report_id  UUID NOT NULL REFERENCES report.hourly_reports(report_id) ON DELETE CASCADE,
    item_type  TEXT NOT NULL,
    item_id    TEXT NOT NULL,
    rank       INT NOT NULL,
    details    JSONB,
    PRIMARY KEY (tenant_id, report_id, item_type, item_id)
);

CREATE INDEX IF NOT EXISTS idx_report_items_rank
ON report.hourly_report_items(tenant_id, report_id, rank);

-- =========================================================
-- NOTIFY SCHEMA (Teams posting log)
-- =========================================================
CREATE TABLE IF NOT EXISTS notify.teams_messages (
    msg_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    run_id      UUID REFERENCES ops.runs(run_id) ON DELETE SET NULL,
    report_id   UUID REFERENCES report.hourly_reports(report_id) ON DELETE SET NULL,
    channel_key TEXT NOT NULL,
    status      TEXT NOT NULL DEFAULT 'PENDING',
    attempts    INT NOT NULL DEFAULT 0,
    response    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_teams_messages_status
ON notify.teams_messages(tenant_id, status, created_at DESC);

-- =========================================================
-- Seed data: Default UNMAPPED application per tenant
-- This should be run per tenant after tenant creation
-- =========================================================
-- INSERT INTO cmdb.applications(tenant_id, name, criticality, is_system, description)
-- VALUES (:tenantId, 'UNMAPPED', 'Tier3', true, 'Unmapped/Unknown resources bucket')
-- ON CONFLICT DO NOTHING;

-- =========================================================
-- Comments for documentation
-- =========================================================
COMMENT ON SCHEMA identity IS 'User management, RBAC, and audit logging';
COMMENT ON SCHEMA config IS 'Connector configurations, schedules, and integration settings';
COMMENT ON SCHEMA cmdb IS 'Configuration Management Database - Applications, Services, Resources';
COMMENT ON SCHEMA ops IS 'Operational data - Runs, Artifacts, Event Outbox';
COMMENT ON SCHEMA incident IS 'Incident lifecycle management';
COMMENT ON SCHEMA report IS 'Hourly reports and reporting items';
COMMENT ON SCHEMA notify IS 'Notification delivery tracking (Teams, etc.)';

COMMENT ON TABLE incident.incidents IS 'Main incident table with lifecycle state machine';
COMMENT ON COLUMN incident.incidents.incident_key IS 'Stable hash: hash(tenantId + appId + sorted(primary_group_ids))';
COMMENT ON COLUMN incident.incidents.status IS 'OPEN, ACKNOWLEDGED, CLOSED, SUPPRESSED';
COMMENT ON COLUMN incident.incidents.classification_label IS 'NEW, ONGOING, REOPENED, NEW_KNOWN_PATTERN';
COMMENT ON COLUMN incident.incidents.reopen_count IS 'Number of times incident was reopened after closure';
