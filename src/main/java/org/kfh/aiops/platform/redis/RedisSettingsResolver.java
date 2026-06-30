package org.kfh.aiops.platform.redis;

import java.util.Map;
import java.util.Optional;
import org.kfh.aiops.platform.config.SettingsService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Component;

/**
 * Resolves the active Redis server for a tenant/country/environment from Settings and maps it to a
 * typed {@link RedisConnectionSettings}. Secret decryption happens inside {@link SettingsService}
 * (which owns the master key); this resolver only adapts the decrypted result into a record.
 */
@Component
public class RedisSettingsResolver {

    private final SettingsService settingsService;

    public RedisSettingsResolver(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public Optional<RedisConnectionSettings> resolve(TenantContext ctx) {
        return settingsService.resolveRedisConnection(ctx).map(RedisSettingsResolver::toSettings);
    }

    private static RedisConnectionSettings toSettings(Map<String, Object> row) {
        return new RedisConnectionSettings(
                str(row.get("host")),
                intOrDefault(row.get("port"), 6379),
                str(row.get("username")),
                str(row.get("password")),
                bool(row.get("tlsEnabled")),
                bool(row.get("healthIndicatorEnabled")),
                str(row.get("name")));
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
    }

    private static int intOrDefault(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            var text = value == null ? "" : String.valueOf(value).trim();
            return text.isBlank() ? fallback : Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
