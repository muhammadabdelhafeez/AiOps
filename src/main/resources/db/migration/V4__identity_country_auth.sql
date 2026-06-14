-- Persist country-scoped login users and role permissions for User Management.
-- Additive migration: does not rewrite earlier identity schema history.

ALTER TABLE identity.users
    ADD COLUMN IF NOT EXISTS country_code TEXT NOT NULL DEFAULT 'KW',
    ADD COLUMN IF NOT EXISTS environment TEXT NOT NULL DEFAULT 'PROD',
    ADD COLUMN IF NOT EXISTS password_hash TEXT,
    ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_identity_users_tenant_country_env
ON identity.users(tenant_id, country_code, environment);

CREATE UNIQUE INDEX IF NOT EXISTS ux_identity_users_tenant_country_username
ON identity.users(tenant_id, country_code, lower(username));

ALTER TABLE identity.roles
    ADD COLUMN IF NOT EXISTS description TEXT;

CREATE TABLE IF NOT EXISTS identity.role_permissions (
    tenant_id UUID NOT NULL REFERENCES public.tenants(tenant_id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES identity.roles(role_id) ON DELETE CASCADE,
    permission TEXT NOT NULL,
    PRIMARY KEY (tenant_id, role_id, permission)
);

CREATE INDEX IF NOT EXISTS idx_identity_role_permissions_role
ON identity.role_permissions(role_id);

