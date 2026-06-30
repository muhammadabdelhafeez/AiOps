package org.kfh.aiops.plugin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.security.SecretCipherService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcConnectorPersistenceStore implements ConnectorPersistenceStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SecretCipherService secretCipherService;

    public JdbcConnectorPersistenceStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            SecretCipherService secretCipherService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.secretCipherService = secretCipherService;
    }

    @Override
    public List<Map<String, Object>> list(TenantContext ctx) {
        ensureTenant(ctx);
        var requestedCountry = normalize(ctx.countryCode());
        var requestedEnvironment = normalize(ctx.environment());
        if (CountryAccessGuard.ALL_COUNTRIES_SCOPE.equals(requestedCountry)) {
            return jdbcTemplate.query("""
                    SELECT connector_id, tenant_id, type, name, enabled, config, created_at, updated_at,
                           last_test_at, last_test_status, last_run_at, next_run_at
                    FROM config.connectors
                    WHERE tenant_id = ?
                      AND COALESCE(config->>'environment', 'PROD') = ?
                    ORDER BY updated_at DESC
                    """, (rs, rowNum) -> row(rs), ctx.tenantId(), requestedEnvironment);
        }
        return jdbcTemplate.query("""
                SELECT connector_id, tenant_id, type, name, enabled, config, created_at, updated_at,
                       last_test_at, last_test_status, last_run_at, next_run_at
                FROM config.connectors
                WHERE tenant_id = ?
                  AND COALESCE(config->>'countryCode', '') = ?
                  AND COALESCE(config->>'environment', 'PROD') = ?
                ORDER BY updated_at DESC
                """, (rs, rowNum) -> row(rs), ctx.tenantId(), requestedCountry, requestedEnvironment);
    }

    @Override
    public Optional<Map<String, Object>> find(UUID id) {
        var rows = jdbcTemplate.query("""
                SELECT connector_id, tenant_id, type, name, enabled, config, created_at, updated_at,
                       last_test_at, last_test_status, last_run_at, next_run_at
                FROM config.connectors
                WHERE connector_id = ?
                """, (rs, rowNum) -> row(rs), id);
        return rows.stream().findFirst();
    }

    @Override
    @Transactional
    public Map<String, Object> create(TenantContext ctx, String countryCode, String environment,
            String name, String pluginType, boolean enabled, Map<String, Object> fields) {
        ensureTenant(ctx);
        var config = connectorConfig(countryCode, environment, pluginType, enabled, fields);
        var id = UUID.randomUUID();
        var rows = jdbcTemplate.query("""
                INSERT INTO config.connectors(connector_id, tenant_id, type, name, enabled, config)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                RETURNING connector_id, tenant_id, type, name, enabled, config, created_at, updated_at,
                          last_test_at, last_test_status, last_run_at, next_run_at
                """, (rs, rowNum) -> row(rs), id, ctx.tenantId(), databaseType(pluginType), name, enabled, toJson(config));
        upsertSecretMetadata(ctx.tenantId(), id, fields);
        return rows.getFirst();
    }

    @Override
    @Transactional
    public Map<String, Object> update(UUID id, Map<String, Object> fields) {
        var existing = find(id).orElse(Map.of());
        if (existing.isEmpty()) {
            return Map.of();
        }
        var config = new LinkedHashMap<>(existing);
        config.remove("id");
        config.remove("connectorId");
        config.remove("tenantId");
        config.remove("createdAt");
        config.remove("updatedAt");
        config.remove("type");
        config.putAll(safeFields(fields));
        var enabled = fields.containsKey("enabled") ? Boolean.TRUE.equals(fields.get("enabled"))
                : Boolean.TRUE.equals(existing.get("enabled"));
        config.put("enabled", enabled);
        config.put("countryCode", normalize(String.valueOf(config.getOrDefault("countryCode", existing.get("countryCode")))));
        config.put("environment", normalize(String.valueOf(config.getOrDefault("environment", existing.get("environment")))));
        config.put("pluginType", normalizePluginType(config.getOrDefault("pluginType", existing.get("pluginType"))));
        var rows = jdbcTemplate.query("""
                UPDATE config.connectors
                SET name = COALESCE(?, name),
                    enabled = ?,
                    type = ?,
                    config = ?::jsonb,
                    last_test_status = COALESCE(?, last_test_status),
                    updated_at = now()
                WHERE connector_id = ?
                RETURNING connector_id, tenant_id, type, name, enabled, config, created_at, updated_at,
                          last_test_at, last_test_status, last_run_at, next_run_at
                """, (rs, rowNum) -> row(rs), stringOrNull(fields.get("name")), enabled,
                databaseType(config.get("pluginType")), toJson(config), stringOrNull(fields.get("lastTestStatus")), id);
        upsertSecretMetadata(UUID.fromString(String.valueOf(existing.get("tenantId"))), id, fields);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    @Override
    public Optional<Map<String, String>> secrets(UUID id) {
        var rows = jdbcTemplate.query("""
                SELECT secret_enc
                FROM config.connector_secrets
                WHERE connector_id = ?
                """, (rs, rowNum) -> readConfig(rs.getString("secret_enc")), id);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var payload = rows.getFirst();
        var encrypted = objectMap(payload.get("secrets"));
        if (encrypted.isEmpty()) {
            return Optional.empty();
        }
        var secrets = new LinkedHashMap<String, String>();
        encrypted.forEach((key, value) -> secrets.put(key, secretCipherService.decrypt(objectMap(value))));
        return Optional.of(secrets);
    }

    @Override
    public void recordTestResult(UUID id, boolean pass) {
        recordTestResult(id, pass, Map.of());
    }

    @Override
    public void recordTestResult(UUID id, boolean pass, Map<String, Object> result) {
        var health = pass ? "HEALTHY" : "DOWN";
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("health", health);
        snapshot.put("lastTestStatus", pass ? "PASS" : "FAIL");
        snapshot.put("lastHealthCheckedAt", Instant.now().toString());
        var message = stringOrNull(result == null ? null : result.get("message"));
        if (message != null) {
            snapshot.put("lastTestMessage", message.length() > 240 ? message.substring(0, 240) : message);
        }
        var errorCode = stringOrNull(result == null ? null : result.get("errorCode"));
        if (errorCode != null) {
            snapshot.put("lastTestErrorCode", errorCode);
        }
        if (Boolean.TRUE.equals(result == null ? null : result.get("credentialRecoveryRequired"))) {
            snapshot.put("credentialRecoveryRequired", true);
            snapshot.put("secretsMask", "needs_reentry");
            snapshot.put("credentialStatus", "needs_reentry");
            snapshot.put("configurationStatus", "PENDING");
        }
        jdbcTemplate.update("""
                UPDATE config.connectors
                SET last_test_at = now(),
                    last_test_status = ?,
                    config = config || ?::jsonb,
                    updated_at = now()
                WHERE connector_id = ?
                """, pass ? "PASS" : "FAIL", toJson(snapshot), id);
    }

    @Override
    public void delete(UUID id) {
        jdbcTemplate.update("DELETE FROM config.connectors WHERE connector_id = ?", id);
    }

    private void ensureTenant(TenantContext ctx) {
        jdbcTemplate.update("""
                INSERT INTO public.tenants(tenant_id, name)
                VALUES (?, ?)
                ON CONFLICT (tenant_id) DO NOTHING
                """, ctx.tenantId(), "Tenant " + ctx.tenantId());
    }

    private void upsertSecretMetadata(UUID tenantId, UUID id, Map<String, Object> fields) {
        if (!fields.containsKey("secretsMask") && !fields.containsKey("secretsPlain")) {
            return;
        }
        var payload = new LinkedHashMap<String, Object>();
        payload.put("status", String.valueOf(fields.getOrDefault("secretsMask", "configured")));
        payload.put("version", "1.0");
        payload.put("updatedAt", java.time.Instant.now().toString());
        var submittedSecrets = submittedSecrets(fields.get("secretsPlain"));
        if (submittedSecrets.isEmpty()) {
            upsertSecretMetadataOnly(tenantId, id, payload);
            return;
        }

        var encryptedSecrets = new LinkedHashMap<String, Object>();
        encryptedSecrets.putAll(existingEncryptedSecrets(id));
        submittedSecrets.forEach((key, value) -> encryptedSecrets.put(key, secretCipherService.encrypt(value)));
        payload.put("status", "configured");
        payload.put("secrets", encryptedSecrets);
        upsertSecretPayload(tenantId, id, payload);
    }

    private Map<String, Object> existingEncryptedSecrets(UUID id) {
        var rows = jdbcTemplate.query("""
                SELECT secret_enc
                FROM config.connector_secrets
                WHERE connector_id = ?
                """, (rs, rowNum) -> objectMap(readConfig(rs.getString("secret_enc")).get("secrets")), id);
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private void upsertSecretMetadataOnly(UUID tenantId, UUID id, Map<String, Object> payload) {
        jdbcTemplate.update("""
                INSERT INTO config.connector_secrets AS current_secrets(connector_id, tenant_id, secret_enc)
                VALUES (?, ?, ?::jsonb)
                ON CONFLICT (connector_id)
                DO UPDATE SET secret_enc = current_secrets.secret_enc || EXCLUDED.secret_enc, updated_at = now()
                """, id, tenantId, toJson(payload));
    }

    private void upsertSecretPayload(UUID tenantId, UUID id, Map<String, Object> payload) {
        jdbcTemplate.update("""
                INSERT INTO config.connector_secrets(connector_id, tenant_id, secret_enc)
                VALUES (?, ?, ?::jsonb)
                ON CONFLICT (connector_id)
                DO UPDATE SET secret_enc = EXCLUDED.secret_enc, updated_at = now()
                """, id, tenantId, toJson(payload));
    }

    private Map<String, Object> row(ResultSet rs) throws SQLException {
        var row = new LinkedHashMap<String, Object>();
        row.putAll(readConfig(rs.getString("config")));
        var pluginType = normalizePluginType(row.getOrDefault("pluginType", rs.getString("type")));
        var enabled = rs.getBoolean("enabled");
        row.put("id", rs.getString("connector_id"));
        row.put("connectorId", rs.getString("connector_id"));
        row.put("tenantId", rs.getString("tenant_id"));
        row.put("type", pluginType);
        row.put("pluginType", pluginType);
        row.put("name", rs.getString("name"));
        row.put("enabled", enabled);
        row.put("countryCode", normalize(String.valueOf(row.getOrDefault("countryCode", "KW"))));
        row.put("environment", normalize(String.valueOf(row.getOrDefault("environment", "PROD"))));
        row.put("createdAt", instant(rs.getTimestamp("created_at")));
        row.put("updatedAt", instant(rs.getTimestamp("updated_at")));
        putInstant(row, "lastTestAt", rs.getTimestamp("last_test_at"));
        putInstant(row, "lastRunAt", rs.getTimestamp("last_run_at"));
        putInstant(row, "nextRunAt", rs.getTimestamp("next_run_at"));
        var lastTestStatus = rs.getString("last_test_status");
        if (lastTestStatus != null) {
            row.put("lastTestStatus", lastTestStatus);
        }
        row.putIfAbsent("configurationStatus", row.containsKey("baseUrl") ? "CONFIGURED" : "PENDING");
        row.put("health", derivedHealth(enabled, row.get("health"), lastTestStatus));
        return row;
    }

    private static String derivedHealth(boolean enabled, Object configuredHealth, String lastTestStatus) {
        if (!enabled) {
            return "DISABLED";
        }
        var status = normalizeStatus(lastTestStatus);
        if ("FAIL".equals(status)) {
            return "DOWN";
        }
        if ("PASS".equals(status)) {
            return "HEALTHY";
        }
        var health = normalizeStatus(configuredHealth);
        if (health.isBlank()) {
            return "PENDING";
        }
        return health;
    }

    private static Map<String, Object> connectorConfig(String countryCode, String environment,
            String pluginType, boolean enabled, Map<String, Object> fields) {
        var config = safeFields(fields);
        config.put("countryCode", normalize(countryCode));
        config.put("environment", normalize(environment));
        config.put("pluginType", normalizePluginType(pluginType));
        config.put("enabled", enabled);
        return config;
    }

    private static Map<String, Object> safeFields(Map<String, Object> fields) {
        var safe = new LinkedHashMap<String, Object>();
        if (fields == null) {
            return safe;
        }
        fields.forEach((key, value) -> {
            if ("secretsMask".equals(key) || (!isSecretLike(key) && !"secretsPlain".equals(key) && !"tenantId".equals(key))) {
                safe.put(key, value);
            }
        });
        return safe;
    }

    private Map<String, Object> readConfig(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private static Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            var result = new LinkedHashMap<String, Object>();
            map.forEach((key, entry) -> result.put(String.valueOf(key), entry));
            return result;
        }
        return Map.of();
    }

    private static Map<String, String> submittedSecrets(Object value) {
        var raw = objectMap(value);
        if (raw.isEmpty()) {
            return Map.of();
        }
        var secrets = new LinkedHashMap<String, String>();
        raw.forEach((key, secretValue) -> {
            var secret = stringOrNull(secretValue);
            if (secret != null) {
                secrets.put(key, secret);
            }
        });
        return secrets;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Connector configuration cannot be serialized", ex);
        }
    }

    private static void putInstant(Map<String, Object> row, String key, Timestamp value) {
        if (value != null) {
            row.put(key, instant(value));
        }
    }

    private static String instant(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }

    private static String databaseType(Object pluginType) {
        return normalizePluginType(pluginType).toLowerCase(Locale.ROOT);
    }

    private static String normalizePluginType(Object pluginType) {
        var value = pluginType == null ? ConnectorCatalogService.BMC_PLUGIN_TYPE : String.valueOf(pluginType);
        return value.trim().isBlank() ? ConnectorCatalogService.BMC_PLUGIN_TYPE : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeStatus(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "PROD" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String stringOrNull(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    private static boolean isSecretLike(String key) {
        var normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("token") || normalized.contains("secret")
                || normalized.contains("apikey") || normalized.contains("api_key") || normalized.contains("credential");
    }
}

