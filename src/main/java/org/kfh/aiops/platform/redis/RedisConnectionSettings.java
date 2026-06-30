package org.kfh.aiops.platform.redis;

/**
 * Typed, immutable view of a runtime Redis server resolved from Settings
 * (config.integration_settings → infrastructure.connections[type=REDIS]).
 *
 * <p>The platform uses Redis logical DB 0 only; tenant/country/environment isolation is by key
 * prefix (see .github/copilot-instructions.md §12 and docs/CAUSAL_PIPELINE.md §11), so this record
 * deliberately carries no database index. {@code password} is plaintext for server-side runtime use
 * only and must never be logged or returned by an API.
 */
public record RedisConnectionSettings(
        String host,
        int port,
        String username,
        String password,
        boolean tlsEnabled,
        boolean healthIndicatorEnabled,
        String name) {

    public RedisConnectionSettings {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Redis host is required");
        }
        host = host.trim();
        if (port <= 0 || port > 65535) {
            port = 6379;
        }
        username = username == null ? "" : username.trim();
        password = password == null ? "" : password;
        name = name == null ? "" : name.trim();
    }

    /** Pool cache key for a distinct server. Never includes the password value. */
    public String cacheKey() {
        return host + ":" + port + ":" + tlsEnabled + ":" + (username.isBlank() ? "-" : username);
    }
}
