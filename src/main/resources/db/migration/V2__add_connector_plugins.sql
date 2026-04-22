-- =========================================================
-- KFH AIOps Command Center - Connector Plugin System
-- Version: 2.0
-- Description: Add connector plugin support and BMC connector
-- =========================================================

-- Add connector plugin types and BMC-specific fields
ALTER TABLE config.connectors
ADD COLUMN IF NOT EXISTS plugin_version TEXT DEFAULT '1.0',
ADD COLUMN IF NOT EXISTS last_test_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS last_test_status TEXT,
ADD COLUMN IF NOT EXISTS last_run_at TIMESTAMPTZ,
ADD COLUMN IF NOT EXISTS next_run_at TIMESTAMPTZ;

-- Add indexes for tenant-scoped connector queries and scheduled runs
CREATE INDEX IF NOT EXISTS idx_connectors_tenant_type ON config.connectors(tenant_id, type);
CREATE INDEX IF NOT EXISTS idx_connectors_tenant_enabled ON config.connectors(tenant_id, enabled);
CREATE INDEX IF NOT EXISTS idx_connectors_next_run ON config.connectors(tenant_id, next_run_at) WHERE enabled = true;

-- Create connector run history table
CREATE TABLE IF NOT EXISTS config.connector_runs (
    run_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connector_id UUID NOT NULL REFERENCES config.connectors(connector_id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    run_type TEXT NOT NULL CHECK (run_type IN ('manual', 'scheduled', 'test')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    status TEXT NOT NULL DEFAULT 'running' CHECK (status IN ('running', 'success', 'error', 'timeout')),
    alerts_collected INTEGER DEFAULT 0,
    error_message TEXT,
    run_metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Indexes for connector run queries
CREATE INDEX IF NOT EXISTS idx_connector_runs_tenant ON config.connector_runs(tenant_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_connector_runs_connector ON config.connector_runs(connector_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_connector_runs_status ON config.connector_runs(tenant_id, status, started_at DESC);

-- Update connector secrets table to support plugin-specific encryption
ALTER TABLE config.connector_secrets
ADD COLUMN IF NOT EXISTS secret_version TEXT DEFAULT '1.0',
ADD COLUMN IF NOT EXISTS encryption_key_id TEXT;

-- Create outbox events table for connector operations
CREATE TABLE IF NOT EXISTS config.outbox_events (
    event_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    aggregate_type TEXT NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    event_data JSONB NOT NULL,
    correlation_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT
);

-- Indexes for outbox pattern processing
CREATE INDEX IF NOT EXISTS idx_outbox_unprocessed ON config.outbox_events(tenant_id, created_at) WHERE processed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_outbox_correlation ON config.outbox_events(correlation_id);

-- Add trigger to update connector updated_at on secrets change
CREATE OR REPLACE FUNCTION config.update_connector_on_secret_change()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    UPDATE config.connectors
    SET updated_at = now()
    WHERE connector_id = NEW.connector_id AND tenant_id = NEW.tenant_id;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_connector_secrets_update_parent
    AFTER INSERT OR UPDATE ON config.connector_secrets
    FOR EACH ROW EXECUTE FUNCTION config.update_connector_on_secret_change();

-- Add connector plugin configuration constraints
ALTER TABLE config.connectors
ADD CONSTRAINT chk_connector_type CHECK (type IN ('bmc', 'solarwinds', 'scom', 'nagios', 'zabbix', 'prometheus'));

-- Add tenant-scoped unique constraint for connector names
-- (already exists but ensuring it's properly named)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_connectors_tenant_name'
    ) THEN
        ALTER TABLE config.connectors
        DROP CONSTRAINT IF EXISTS config_connectors_tenant_id_name_key;

        ALTER TABLE config.connectors
        ADD CONSTRAINT uq_connectors_tenant_name UNIQUE (tenant_id, name);
    END IF;
END $$;
