-- Ensure identity.audit_log supports durable Audit Activity rows on databases that
-- were created before the Phase 1 audit activity adapter was added.

CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE IF NOT EXISTS identity.audit_log (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    at TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_user_id UUID,
    action TEXT NOT NULL DEFAULT 'UNKNOWN',
    entity_type TEXT NOT NULL DEFAULT 'Application',
    entity_id TEXT NOT NULL DEFAULT '',
    details JSONB NOT NULL DEFAULT '{}'::jsonb
);

ALTER TABLE identity.audit_log
    ADD COLUMN IF NOT EXISTS audit_id UUID DEFAULT gen_random_uuid(),
    ADD COLUMN IF NOT EXISTS at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS actor_user_id UUID,
    ADD COLUMN IF NOT EXISTS action TEXT NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS entity_type TEXT NOT NULL DEFAULT 'Application',
    ADD COLUMN IF NOT EXISTS entity_id TEXT NOT NULL DEFAULT '';

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'identity'
          AND table_name = 'audit_log'
          AND column_name = 'details'
    ) THEN
        ALTER TABLE identity.audit_log ADD COLUMN details JSONB NOT NULL DEFAULT '{}'::jsonb;
    ELSIF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'identity'
          AND table_name = 'audit_log'
          AND column_name = 'details'
          AND udt_name <> 'jsonb'
    ) THEN
        ALTER TABLE identity.audit_log RENAME COLUMN details TO details_legacy;
        ALTER TABLE identity.audit_log ADD COLUMN details JSONB NOT NULL DEFAULT '{}'::jsonb;
    END IF;
END $$;

UPDATE identity.audit_log
SET audit_id = gen_random_uuid()
WHERE audit_id IS NULL;

ALTER TABLE identity.audit_log
    ALTER COLUMN audit_id SET NOT NULL,
    ALTER COLUMN audit_id SET DEFAULT gen_random_uuid(),
    ALTER COLUMN details SET DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_audit_tenant_at
ON identity.audit_log(tenant_id, at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_tenant_country_env
ON identity.audit_log(tenant_id, (details->>'countryCode'), (details->>'environment'), at DESC);

