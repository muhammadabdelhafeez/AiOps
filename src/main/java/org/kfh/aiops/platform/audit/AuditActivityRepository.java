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
        var actor = resolveActor(ctx.userId());
        var details = details(ctx, action, entityType, entityId, beforeState, afterState, actor);
        jdbcTemplate.update("""
                INSERT INTO identity.audit_log(audit_id, tenant_id, actor_user_id, action, entity_type, entity_id, details)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                """, UUID.randomUUID(), ctx.tenantId(), ctx.userId(), safe(action), safe(entityType), safe(entityId), toJson(details));
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
            String entityId, Object beforeState, Object afterState, ActorIdentity actor) {
        var safeBeforeState = sanitize(beforeState);
        var safeAfterState = sanitize(afterState);
        var details = new LinkedHashMap<String, Object>();
        details.put("tenantId", ctx.tenantId().toString());
        details.put("userId", ctx.userId().toString());
        details.put("actorUserId", ctx.userId().toString());
        details.put("actorUsername", actor.username());
        details.put("actorName", actor.displayName());
        details.put("actorSource", actor.source()); // "user" or "system"
        details.put("countryCode", normalize(ctx.countryCode()));
        details.put("environment", normalize(ctx.environment()));
        details.put("correlationId", ctx.correlationId());
        details.put("category", safe(entityType));
        details.put("result", "Success");
        details.put("severity", "Info");
        details.put("message", safe(action) + " on " + safe(entityType) + " " + safe(entityId));
        details.put("beforeState", safeBeforeState);
        details.put("afterState", safeAfterState);
        // Derive Fail/Warn defaults from action naming so connector and other
        // lifecycle events render correctly on the audit page even when the
        // caller did not explicitly pass result/severity in afterState.
        var upperAction = safe(action).toUpperCase(Locale.ROOT);
        if (upperAction.endsWith("_FAILED") || upperAction.endsWith("_FAILURE") || upperAction.endsWith("_REJECTED")) {
            details.put("result", "Fail");
            details.put("severity", "Warn");
        }
        mergeDisplayFields(details, safeAfterState);
        return details;
    }

    /**
     * Resolve a friendly actor identity from identity.users. Audit must succeed
     * whether or not the actor row exists (e.g. bootstrap default UUID,
     * scheduled jobs, or after a user was deleted), so this is fail-soft and
     * returns a "System" label for any unresolved or null actor UUID.
     */
    private ActorIdentity resolveActor(UUID actorUserId) {
        if (actorUserId == null) {
            return ActorIdentity.system();
        }
        try {
            var rows = jdbcTemplate.query(
                    "SELECT username, display_name FROM identity.users WHERE user_id = ?",
                    (rs, rowNum) -> Map.of(
                            "username", nonBlank(rs.getString("username"), ""),
                            "displayName", nonBlank(rs.getString("display_name"), "")),
                    actorUserId);
            if (rows.isEmpty()) {
                return ActorIdentity.system();
            }
            var row = rows.get(0);
            var username = nonBlank(String.valueOf(row.getOrDefault("username", "")), "");
            var displayName = nonBlank(String.valueOf(row.getOrDefault("displayName", "")), username);
            return new ActorIdentity(username.isBlank() ? "System" : username,
                    displayName.isBlank() ? (username.isBlank() ? "System" : username) : displayName,
                    "user");
        } catch (Exception ex) {
            return ActorIdentity.system();
        }
    }

    private record ActorIdentity(String username, String displayName, String source) {
        static ActorIdentity system() {
            return new ActorIdentity("System", "System", "system");
        }
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
        enrichLoginDisplay(row, rs.getString("action"), rs.getString("entity_type"), rs.getString("entity_id"));
        row.put("details", details);
        row.putIfAbsent("countryCode", "");
        row.putIfAbsent("environment", "PROD");
        row.putIfAbsent("result", "Success");
        row.putIfAbsent("severity", "Info");
        row.putIfAbsent("category", rs.getString("entity_type"));
        row.putIfAbsent("message", rs.getString("action") + " on " + rs.getString("entity_type") + " " + rs.getString("entity_id"));
        return row;
    }

    private static void mergeDisplayFields(Map<String, Object> details, Object state) {
        var display = asMap(state);
        if (display.isEmpty()) {
            return;
        }
        putTextIfPresent(details, display, "actorName");
        putTextIfPresent(details, display, "actorUsername");
        putTextIfPresent(details, display, "actorUserId");
        putTextIfPresent(details, display, "targetName");
        putTextIfPresent(details, display, "targetType");
        putTextIfPresent(details, display, "targetId");
        putTextIfPresent(details, display, "result");
        putTextIfPresent(details, display, "severity");
        putTextIfPresent(details, display, "message");
    }

    private static void enrichLoginDisplay(Map<String, Object> row, String action, String entityType, String entityId) {
        if (!safe(action).startsWith("LOGIN_")) {
            return;
        }
        var afterState = asMap(row.get("afterState"));
        var nestedDetails = asMap(afterState.get("details"));
        var actorName = firstText(row, "actorName", "actorUsername");
        if (actorName.isBlank()) {
            actorName = firstText(afterState, "actorName", "displayName", "username", "actorUsername");
        }
        if (actorName.isBlank()) {
            actorName = firstText(nestedDetails, "displayName", "username");
        }
        if (!actorName.isBlank()) {
            row.put("actorName", actorName);
        }
        row.putIfAbsent("actorUsername", firstText(afterState, "username", "actorUsername"));
        row.put("targetName", nonBlank(firstText(row, "targetName"), "Authentication"));
        row.put("targetId", nonBlank(firstText(row, "targetId"), "AUTHENTICATION"));
        row.putIfAbsent("targetType", nonBlank(entityType, "Security"));
        if ("LOGIN_FAILED".equals(safe(action))) {
            row.put("result", "Fail");
            row.put("severity", "Warn");
        } else if ("LOGIN_SUCCEEDED".equals(safe(action))) {
            row.put("result", "Success");
            row.putIfAbsent("severity", "Info");
        }
        if (shouldReplaceLoginMessage(row, action, entityType, entityId)) {
            row.put("message", loginMessage(action, actorName));
        }
    }

    private static boolean shouldReplaceLoginMessage(Map<String, Object> row, String action, String entityType, String entityId) {
        var message = firstText(row, "message");
        return message.isBlank() || message.equals(safe(action) + " on " + safe(entityType) + " " + safe(entityId));
    }

    private static String loginMessage(String action, String actorName) {
        var suffix = actorName == null || actorName.isBlank() ? "" : " for " + actorName;
        return "LOGIN_FAILED".equals(safe(action)) ? "Login failed" + suffix : "Login succeeded" + suffix;
    }

    private static void putTextIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        var value = firstText(source, key);
        if (!value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String firstText(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        for (var key : keys) {
            var value = source.get(key);
            if (value != null) {
                var text = String.valueOf(value).trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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

