package org.kfh.aiops.platform.redis;

import java.time.Duration;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Component;

/**
 * Tenant/country/environment-scoped Redis reachability probe for the Dashboard "System Health" tile
 * and operator tooling. Returns a secret-safe status and never throws.
 *
 * <p>This is intentionally NOT a global {@code /actuator/health} HealthIndicator: runtime Redis is
 * resolved per (tenant, country, environment) from Settings, which the global actuator contract
 * cannot express. The static Spring Boot Redis indicator ({@code management.health.redis.enabled})
 * remains available for the optionally-configured {@code spring.data.redis.*} server.
 */
@Component
public class RedisHealthProbe {

    private final RedisSettingsResolver resolver;
    private final RedisConnectionProvider provider;

    public RedisHealthProbe(RedisSettingsResolver resolver, RedisConnectionProvider provider) {
        this.resolver = resolver;
        this.provider = provider;
    }

    public RedisHealth check(TenantContext ctx) {
        var settings = resolver.resolve(ctx);
        if (settings.isEmpty()) {
            return new RedisHealth("NOT_CONFIGURED", 0, "No Redis server is configured for this country/environment.");
        }
        if (!settings.get().healthIndicatorEnabled()) {
            return new RedisHealth("DISABLED", 0, "Redis health check is disabled for this connector.");
        }
        var started = System.nanoTime();
        try {
            var pong = provider.execute(settings.get(), commands -> commands.ping());
            var latency = elapsedMs(started);
            return "PONG".equalsIgnoreCase(pong)
                    ? new RedisHealth("UP", latency, "Redis PING succeeded.")
                    : new RedisHealth("DOWN", latency, "Redis PING returned an unexpected reply.");
        } catch (RuntimeException ex) {
            return new RedisHealth("DOWN", elapsedMs(started), "Redis unreachable: " + RedisErrors.safe(ex));
        }
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }

    public record RedisHealth(String status, long latencyMs, String message) {
    }
}
