package org.kfh.aiops.platform.redis;

/**
 * Builds colon-delimited, country/environment-scoped Redis keys per the conventions in
 * docs/DATA_MODEL.md and .github/copilot-instructions.md §12. Key-prefix isolation (not logical DB
 * selection) keeps tenants/countries/environments separate on Redis DB 0.
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** {@code dedup:{country}:{env}:{source}:{ci}:{code}} — short alert dedup window (TTL 2–10 min). */
    public static String dedup(String country, String environment, String source, String resourceId, String errorCode) {
        return String.join(":", "dedup", seg(country), seg(environment), seg(source), seg(resourceId), seg(errorCode));
    }

    /** {@code health:{country}:{env}:{kind}:{id}} — hot health state (TTL 5–15 min). */
    public static String health(String country, String environment, String kind, String id) {
        return String.join(":", "health", seg(country), seg(environment), seg(kind), seg(id));
    }

    private static String seg(String value) {
        var trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Redis key segment must not be blank");
        }
        for (var i = 0; i < trimmed.length(); i++) {
            var c = trimmed.charAt(i);
            if (c == ':' || c < 0x20) {
                throw new IllegalArgumentException("Redis key segment must not contain ':' or control characters");
            }
        }
        return trimmed;
    }
}
