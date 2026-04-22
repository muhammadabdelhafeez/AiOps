-- =========================================================
-- KFH AIOps Command Center - Connector Runs Migration
-- Version: 2.0
-- Description: Add connector run history and logs tables
-- =========================================================

-- =========================================================
-- OPS SCHEMA - Connector Runs
-- =========================================================
CREATE TABLE IF NOT EXISTS ops.connector_runs (
    connector_run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    connector_id     UUID NOT NULL REFERENCES config.connectors(connector_id) ON DELETE CASCADE,
    started_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at         TIMESTAMPTZ,
    status           TEXT NOT NULL DEFAULT 'QUEUED',  -- QUEUED, RUNNING, SUCCESS, FAILED
    run_type         TEXT NOT NULL DEFAULT 'COLLECT', -- COLLECT, TEST
    summary          TEXT,
    metrics          JSONB  -- {pulled, normalized, errors, latencyMs, artifactUrl, httpStatus, message}
);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_connector_runs_tenant_connector_started
ON ops.connector_runs(tenant_id, connector_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_connector_runs_tenant_started
ON ops.connector_runs(tenant_id, started_at DESC);

CREATE INDEX IF NOT EXISTS idx_connector_runs_status
ON ops.connector_runs(tenant_id, status) WHERE status IN ('QUEUED', 'RUNNING');

-- =========================================================
-- OPS SCHEMA - Connector Run Logs
-- =========================================================
CREATE TABLE IF NOT EXISTS ops.connector_run_logs (
    id               BIGSERIAL PRIMARY KEY,
    tenant_id        UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    connector_run_id UUID NOT NULL REFERENCES ops.connector_runs(connector_run_id) ON DELETE CASCADE,
    at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    level            TEXT NOT NULL DEFAULT 'INFO',  -- DEBUG, INFO, WARN, ERROR
    message          TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_connector_run_logs_run
ON ops.connector_run_logs(tenant_id, connector_run_id, at);

-- =========================================================
-- Comments for documentation
-- =========================================================
COMMENT ON TABLE ops.connector_runs IS 'History of connector test and collect runs';
COMMENT ON COLUMN ops.connector_runs.status IS 'QUEUED (pending worker pickup), RUNNING, SUCCESS, FAILED';
COMMENT ON COLUMN ops.connector_runs.run_type IS 'TEST (connectivity test) or COLLECT (data ingestion)';
COMMENT ON COLUMN ops.connector_runs.metrics IS 'JSON metrics: {pulled, normalized, errors, latencyMs, artifactUrl, httpStatus, message}';

COMMENT ON TABLE ops.connector_run_logs IS 'Detailed logs for each connector run';
COMMENT ON COLUMN ops.connector_run_logs.level IS 'Log level: DEBUG, INFO, WARN, ERROR';
