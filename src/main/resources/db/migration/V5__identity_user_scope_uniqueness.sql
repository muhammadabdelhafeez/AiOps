-- Align local-login uniqueness with the sign-in scope: tenant + country + environment + username.
-- Previous constraints were too broad and could block legitimate environment-scoped users.

ALTER TABLE identity.users
    DROP CONSTRAINT IF EXISTS users_tenant_id_username_key;

DROP INDEX IF EXISTS identity.ux_identity_users_tenant_country_username;

CREATE UNIQUE INDEX IF NOT EXISTS ux_identity_users_tenant_country_env_username
ON identity.users(tenant_id, country_code, environment, lower(username));

