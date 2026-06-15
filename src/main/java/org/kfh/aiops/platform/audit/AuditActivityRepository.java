package org.kfh.aiops.platform.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AuditActivityRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditActivityRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordWrite(TenantContext ctx, String action, String entityType,
            String entityId, Object beforeState, Object afterState) {
        ensureTenant(ctx.tenantId());
        var details = details(ctx, action, entityType, entityId, beforeState, afterState);
        jdbcTemplate.update("""
                INSERT INTO identity.audit_log(audit_id, tenant_id, actor_user_id, action, entity_type, entity_id, details)
                VALUES (?, ?, NULL, ?, ?, ?, ?::jsonb)
                """, UUID.randomUUID(), ctx.tenantId(), safe(action), safe(entityType), safe(entityId), toJson(details));
    }

    public List<Map<String, Object>> list(TenantContext ctx) {
        var args = new ArrayList<Object>();
        args.add(ctx.tenantId());
        args.add(normalize(ctx.environment()));
        var countryFilter = "";
        if (!CountryAccessGuard.ALL_COUNTRIES_SCOPE.equals(normalize(ctx.countryCode()))) {
            countryFilter = " AND COALESCE(details->>'countryCode', '') = ?";
            args.add(normalize(ctx.countryCode()));
        }
        return jdbcTemplate.query("""
                SELECT audit_id, tenant_id, at, action, entity_type, entity_id, details
                FROM identity.audit_log
                WHERE tenant_id = ?
                  AND COALESCE(details->>'environment', 'PROD') = ?
                """ + countryFilter + """
                ORDER BY at DESC
                """, (rs, rowNum) -> row(rs), args.toArray());
    }

    public Optional<Map<String, Object>> find(TenantContext ctx, UUID id) {
        var rows = jdbcTemplate.query("""
                SELECT audit_id, tenant_id, at, action, entity_type, entity_id, details
                FROM identity.audit_log
                WHERE tenant_id = ? AND audit_id = ?
                """, (rs, rowNum) -> row(rs), ctx.tenantId(), id);
        return rows.stream()
                .filter(row -> countryAllowed(ctx, row))
                .filter(row -> normalize(ctx.environment()).equals(row.get("environment")))
                .findFirst();
    }

    private void ensureTenant(UUID tenantId) {
        jdbcTemplate.update("""
                INSERT INTO public.tenants(tenant_id, name)
                VALUES (?, ?)
                ON CONFLICT (tenant_id) DO NOTHING
                """, tenantId, "Tenant " + tenantId);
    }

    private Map<String, Object> details(TenantContext ctx, String action, String entityType,
            String entityId, Object beforeState, Object afterState) {
        var details = new LinkedHashMap<String, Object>();
        details.put("tenantId", ctx.tenantId().toString());
        details.put("userId", ctx.userId().toString());
        details.put("countryCode", normalize(ctx.countryCode()));
        details.put("environment", normalize(ctx.environment()));
        details.put("correlationId", ctx.correlationId());
        details.put("category", safe(entityType));
        details.put("result", "Success");
        details.put("severity", "Info");
        details.put("message", safe(action) + " on " + safe(entityType) + " " + safe(entityId));
        details.put("beforeState", sanitize(beforeState));
        details.put("afterState", sanitize(afterState));
        return details;
    }

    private Map<String, Object> row(ResultSet rs) throws SQLException {
        var row = new LinkedHashMap<String, Object>();
        var details = readDetails(rs.getString("details"));
        var at = instant(rs.getTimestamp("at"));
        row.put("id", rs.getString("audit_id"));
        row.put("auditId", rs.getString("audit_id"));
        row.put("tenantId", rs.getString("tenant_id"));
        row.put("createdAt", at);
        row.put("updatedAt", at);
        row.put("timestamp", at);
        row.put("action", rs.getString("action"));
        row.put("entityType", rs.getString("entity_type"));
        row.put("entityId", rs.getString("entity_id"));
        row.putAll(details);
        row.put("details", details);
        row.putIfAbsent("countryCode", "");
        row.putIfAbsent("environment", "PROD");
        row.putIfAbsent("result", "Success");
        row.putIfAbsent("severity", "Info");
        row.putIfAbsent("category", rs.getString("entity_type"));
        row.putIfAbsent("message", rs.getString("action") + " on " + rs.getString("entity_type") + " " + rs.getString("entity_id"));
        return row;
    }

    private Map<String, Object> readDetails(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to serialize audit details", ex);
        }
    }

    private static boolean countryAllowed(TenantContext ctx, Map<String, Object> row) {
        var requested = normalize(ctx.countryCode());
        return CountryAccessGuard.ALL_COUNTRIES_SCOPE.equals(requested) || requested.equals(row.get("countryCode"));
    }

    private static Object sanitize(Object value) {
        if (value instanceof Map<?, ?> map) {
            var safe = new LinkedHashMap<String, Object>();
            map.forEach((key, nestedValue) -> {
                var keyText = String.valueOf(key);
                if (!isSecretLike(keyText)) {
                    safe.put(keyText, sanitize(nestedValue));
                }
            });
            return safe;
        }
        if (value instanceof Iterable<?> iterable) {
            var safe = new ArrayList<>();
            iterable.forEach(item -> safe.add(sanitize(item)));
            return safe;
        }
        return value;
    }

    private static boolean isSecretLike(String key) {
        var normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("token") || normalized.contains("secret")
                || normalized.contains("apikey") || normalized.contains("api_key") || normalized.contains("credential");
    }

    private static String instant(Timestamp timestamp) {
        return timestamp == null ? Instant.now().truncatedTo(ChronoUnit.SECONDS).toString() : timestamp.toInstant().toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? "PROD" : value.trim().toUpperCase(Locale.ROOT);
    }
}

