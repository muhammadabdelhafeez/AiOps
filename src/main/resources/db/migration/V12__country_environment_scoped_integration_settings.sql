-- Scope Settings metadata permanently by tenant, country, environment, and setting key.
-- Existing tenant-wide metadata is preserved as ALL-country / ALL-environment fallback.

ALTER TABLE config.integration_settings
    ADD COLUMN IF NOT EXISTS country_code TEXT NOT NULL DEFAULT 'ALL';

ALTER TABLE config.integration_settings
    ADD COLUMN IF NOT EXISTS environment TEXT NOT NULL DEFAULT 'ALL';

UPDATE config.integration_settings
SET country_code = 'ALL'
WHERE country_code IS NULL OR btrim(country_code) = '';

UPDATE config.integration_settings
SET environment = 'ALL'
WHERE environment IS NULL OR btrim(environment) = '';

ALTER TABLE config.integration_settings
    DROP CONSTRAINT IF EXISTS integration_settings_tenant_id_key_key;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'integration_settings_tenant_country_env_key_key'
    ) THEN
        ALTER TABLE config.integration_settings
            ADD CONSTRAINT integration_settings_tenant_country_env_key_key
            UNIQUE (tenant_id, country_code, environment, key);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_integration_settings_tenant_scope
    ON config.integration_settings (tenant_id, country_code, environment, key);

