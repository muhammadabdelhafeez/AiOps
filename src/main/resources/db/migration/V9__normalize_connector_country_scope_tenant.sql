-- =========================================================
-- KFH AIOps Command Center - Normalize Connector Country Scope Tenants
-- Version: 9.0
-- Description: Repair connector rows created by the Phase 1 scaffold when country
--              switching incorrectly changed tenant_id for BH/EG connector configs.
-- =========================================================

-- The KFH group command-center tenant remains the tenant boundary. Country is
-- represented by config->>'countryCode'. This repair is intentionally limited to
-- the known scaffold UUIDs used by the static country switcher and does not touch
-- arbitrary production tenant IDs.

INSERT INTO public.tenants(tenant_id, name)
VALUES ('00000000-0000-4000-8000-000000000001'::uuid, 'KFH Group')
ON CONFLICT (tenant_id) DO NOTHING;

WITH migrated_connectors AS (
    UPDATE config.connectors connector
    SET tenant_id = '00000000-0000-4000-8000-000000000001'::uuid,
        updated_at = now()
    WHERE connector.tenant_id IN (
            '00000000-0000-4000-8000-000000000002'::uuid,
            '00000000-0000-4000-8000-000000000003'::uuid
        )
      AND COALESCE(connector.config->>'countryCode', '') IN ('BH', 'EG')
      AND NOT EXISTS (
            SELECT 1
            FROM config.connectors existing
            WHERE existing.tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
              AND existing.name = connector.name
              AND existing.connector_id <> connector.connector_id
        )
    RETURNING connector.connector_id
)
UPDATE config.connector_secrets secret
SET tenant_id = '00000000-0000-4000-8000-000000000001'::uuid,
    updated_at = now()
WHERE secret.connector_id IN (SELECT connector_id FROM migrated_connectors);

WITH normalized_connectors AS (
    SELECT connector_id
    FROM config.connectors
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND COALESCE(config->>'countryCode', '') IN ('BH', 'EG')
)
UPDATE config.connector_runs run
SET tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
WHERE run.connector_id IN (SELECT connector_id FROM normalized_connectors)
  AND run.tenant_id IN (
        '00000000-0000-4000-8000-000000000002'::uuid,
        '00000000-0000-4000-8000-000000000003'::uuid
    );

WITH normalized_connectors AS (
    SELECT connector_id
    FROM config.connectors
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND COALESCE(config->>'countryCode', '') IN ('BH', 'EG')
)
UPDATE config.outbox_events event
SET tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
WHERE event.aggregate_id IN (SELECT connector_id FROM normalized_connectors)
  AND LOWER(event.aggregate_type) LIKE '%connector%'
  AND event.tenant_id IN (
        '00000000-0000-4000-8000-000000000002'::uuid,
        '00000000-0000-4000-8000-000000000003'::uuid
    );

