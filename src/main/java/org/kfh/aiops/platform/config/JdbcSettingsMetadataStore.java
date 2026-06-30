package org.kfh.aiops.platform.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Comparator;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-backed implementation of {@link SettingsMetadataStore} that persists Settings
 * metadata into {@code config.integration_settings} (created by {@code V12__country_environment_scoped_integration_settings.sql}).
 *
 * <p>Intentionally NOT guarded by {@code @ConditionalOnBean(JdbcTemplate.class)}: that
 * annotation is evaluated during component scan, before Spring Boot's
 * {@code JdbcTemplateAutoConfiguration} registers the {@link JdbcTemplate} bean, which
 * caused this {@code @Repository} to be silently skipped and made
 * {@code SettingsService.persistMetadataSettings} fail with HTTP 503
 * {@code SETTINGS_PERSISTENCE_UNAVAILABLE}. PostgreSQL is the system of record for this
 * platform, so {@link JdbcTemplate} is always present; if it ever is not, the application
 * must fail fast at startup rather than silently lose Settings writes.
 */
@Repository
public class JdbcSettingsMetadataStore implements SettingsMetadataStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final String ALL_SCOPE = "ALL";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSettingsMetadataStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> load(TenantContext ctx) {
        ensureTenant(ctx);
        // Load every row for this tenant (the dataset is intentionally tiny - one row
        // per (country_code, environment, key) tuple per tenant). Scope filtering and
        // ordering are applied in Java below so the SQL stays simple and bind-parameter
        // counts cannot drift out of sync with the placeholders (an earlier 7-vs-5
        // mismatch caused load() to throw and SettingsService to silently fall back to
        // an empty Map, which made the UI render blank even though rows existed in
        // config.integration_settings).
        var rows = jdbcTemplate.query("""
                SELECT key, value, country_code, environment
                FROM config.integration_settings
                WHERE tenant_id = ?
                """, (rs, rowNum) -> new ScopedSetting(
                        rs.getString("key"),
                        readValue(rs.getString("value")),
                        rs.getString("country_code"),
                        rs.getString("environment")),
                ctx.tenantId());
        var requestedCountry = country(ctx);
        var requestedEnvironment = environment(ctx);
        var settings = new LinkedHashMap<String, Object>();
        rows.stream()
                // Keep only rows that match the requested scope or fall back to ALL.
                .filter(row -> matchesScope(row.countryCode(), requestedCountry))
                .filter(row -> matchesScope(row.environment(), requestedEnvironment))
                // Apply least-specific first so more-specific overrides win on merge:
                // (ALL, ALL) -> (ALL, env) -> (country, ALL) -> (country, env).
                .sorted(Comparator
                        .comparingInt((ScopedSetting row) -> scopeRank(row.countryCode(), requestedCountry))
                        .thenComparingInt(row -> scopeRank(row.environment(), requestedEnvironment)))
                .forEach(row -> mergeSetting(settings, row.key(), row.value()));
        return settings;
    }

    private static boolean matchesScope(String actual, String requested) {
        var normalized = scope(actual);
        return ALL_SCOPE.equals(normalized) || normalized.equalsIgnoreCase(requested);
    }

    @Override
    @Transactional
    public void save(TenantContext ctx, Map<String, Object> settings) {
        ensureTenant(ctx);
        settings.forEach((key, value) -> {
            var scopes = countryScopesFor(key, value, ctx);
            jdbcTemplate.update("""
                    DELETE FROM config.integration_settings
                    WHERE tenant_id = ?
                      AND key = ?
                      AND environment = ?
                      AND country_code NOT IN (%s)
                    """.formatted(placeholders(scopes.size())), deleteArgs(ctx, key, scopes));
            scopes.forEach(scope -> jdbcTemplate.update("""
                    INSERT INTO config.integration_settings(tenant_id, country_code, environment, key, value)
                    VALUES (?, ?, ?, ?, ?::jsonb)
                    ON CONFLICT (tenant_id, country_code, environment, key)
                    DO UPDATE SET value = EXCLUDED.value, updated_at = now()
                    """, ctx.tenantId(), scope, environment(ctx), key, toJson(value)));
        });
    }

    private void ensureTenant(TenantContext ctx) {
        // Use DO NOTHING without a conflict target so this is robust against both the
        // tenant_id PRIMARY KEY conflict (the common case) and any UNIQUE (name)
        // conflict that could arise if another row already owns the generated name.
        jdbcTemplate.update("""
                INSERT INTO public.tenants(tenant_id, name)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """, ctx.tenantId(), "Tenant " + ctx.tenantId());
    }

    private Map<String, Object> readValue(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Settings metadata cannot be serialized", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static void mergeSetting(Map<String, Object> settings, String key, Map<String, Object> value) {
        var existing = settings.get(key);
        if (existing instanceof Map<?, ?> existingMap) {
            settings.put(key, mergeMaps(new LinkedHashMap<>((Map<String, Object>) existingMap), value));
        } else {
            settings.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeMaps(Map<String, Object> target, Map<String, Object> source) {
        source.forEach((key, value) -> {
            var existing = target.get(key);
            if (existing instanceof Map<?, ?> existingMap && value instanceof Map<?, ?> valueMap) {
                target.put(key, mergeMaps(new LinkedHashMap<>((Map<String, Object>) existingMap), (Map<String, Object>) valueMap));
            } else if (existing instanceof Iterable<?> existingItems && value instanceof Iterable<?> newItems) {
                target.put(key, mergeLists(existingItems, newItems));
            } else {
                target.put(key, value);
            }
        });
        return target;
    }

    private static List<Object> mergeLists(Iterable<?> ignoredExistingItems, Iterable<?> newItems) {
        var replacement = new ArrayList<>();
        newItems.forEach(replacement::add);
        return replacement;
    }

    private static String country(TenantContext ctx) {
        return scope(ctx.countryCode());
    }

    private static String environment(TenantContext ctx) {
        return scope(ctx.environment());
    }

    private static String scope(String value) {
        var normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return normalized.isBlank() || "GLOBAL".equals(normalized) || "DEFAULT".equals(normalized) ? ALL_SCOPE : normalized;
    }

    private record ScopedSetting(String key, Map<String, Object> value, String countryCode, String environment) { }

    private static int scopeRank(String actual, String requested) {
        return requested.equalsIgnoreCase(scope(actual)) ? 1 : 0;
    }

    private static Object[] deleteArgs(TenantContext ctx, String key, Set<String> scopes) {
        var args = new ArrayList<>();
        args.add(ctx.tenantId());
        args.add(key);
        args.add(environment(ctx));
        args.addAll(scopes);
        return args.toArray();
    }

    private static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    private static Set<String> countryScopesFor(String key, Object value, TenantContext ctx) {
        if (!(value instanceof Map<?, ?> section)) {
            return Set.of(country(ctx));
        }
        if ("azureOpenAI".equals(key)) {
            return scopesFromIntegrations(section.get("integrations"), ctx);
        }
        if ("databases".equals(key) || "sharepoint".equals(key) || "infrastructure".equals(key)) {
            return scopesFromConnections(section.get("connections"), ctx);
        }
        if ("neo4j".equals(key)) {
            // Neo4j is a single-object section that now exposes its own countryCode /
            // countryCodes field in Settings -> Databases -> Edit Neo4j Topology Graph.
            // Persist under the chosen scope so the row is durable for that country (or
            // ALL when the operator picks All Countries), mirroring how azureOpenAI
            // integrations and databases.connections scope rows by their own payload.
            return scopesFromItem(section, ctx);
        }
        if ("teams".equals(key) || "system".equals(key)) {
            return Set.of(country(ctx));
        }
        return Set.of(country(ctx));
    }

    private static Set<String> scopesFromIntegrations(Object integrations, TenantContext ctx) {
        var scopes = new LinkedHashSet<String>();
        if (integrations instanceof Iterable<?> iterable) {
            iterable.forEach(item -> scopes.addAll(scopesFromItem(item, ctx)));
        }
        return scopes.isEmpty() ? Set.of(country(ctx)) : scopes;
    }

    private static Set<String> scopesFromConnections(Object connections, TenantContext ctx) {
        var scopes = new LinkedHashSet<String>();
        if (connections instanceof Iterable<?> iterable) {
            iterable.forEach(item -> scopes.addAll(scopesFromItem(item, ctx)));
        }
        return scopes.isEmpty() ? Set.of(country(ctx)) : scopes;
    }

    private static Set<String> scopesFromItem(Object item, TenantContext ctx) {
        if (!(item instanceof Map<?, ?> map)) {
            return Set.of(country(ctx));
        }
        var scopes = normalizeScopes(map.containsKey("countryCodes") ? map.get("countryCodes") : map.get("countryCode"));
        return scopes.isEmpty() ? Set.of(country(ctx)) : scopes;
    }

    private static Set<String> normalizeScopes(Object value) {
        var scopes = new LinkedHashSet<String>();
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> addScope(scopes, item));
        } else {
            addScope(scopes, value);
        }
        if (scopes.isEmpty() || scopes.contains(ALL_SCOPE)) {
            return Set.of(ALL_SCOPE);
        }
        return scopes;
    }

    private static void addScope(Set<String> scopes, Object value) {
        var raw = value == null ? "" : String.valueOf(value);
        for (var token : raw.split(",")) {
            var normalized = scope(token);
            if (!normalized.isBlank()) {
                scopes.add(normalized);
            }
        }
    }
}
