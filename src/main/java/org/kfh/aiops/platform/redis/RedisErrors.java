package org.kfh.aiops.platform.redis;

/** Secret-safe rendering of Redis client exceptions for logs and operator-facing messages. */
public final class RedisErrors {

    private RedisErrors() {
    }

    public static String safe(Throwable ex) {
        var raw = ex == null || ex.getMessage() == null
                ? (ex == null ? "unknown error" : ex.getClass().getSimpleName())
                : ex.getMessage();
        var scrubbed = raw
                .replaceAll("(?i)(password|secret|auth|token|credential)\\s*[:=]\\s*\\S+", "$1=masked")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return scrubbed.length() <= 200 ? scrubbed : scrubbed.substring(0, 197) + "...";
    }
}
