-- Allow EMCO Ping Monitor SQL Server connector rows while preserving the existing type allowlist.
ALTER TABLE config.connectors
DROP CONSTRAINT IF EXISTS chk_connector_type;

ALTER TABLE config.connectors
ADD CONSTRAINT chk_connector_type CHECK (type IN (
    'bmc',
    'appdynamics',
    'vrops',
    'solarwinds',
    'scom',
    'emco',
    'nagios',
    'zabbix',
    'prometheus'
));

