package org.kfh.aiops.platform.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Secret-safe request view for bounded infrastructure Settings connection tests.
 */
final class InfrastructureTestConfig {

    private final Map<String, Object> source;

    private InfrastructureTestConfig(Map<String, Object> source) {
        // Use a defensive LinkedHashMap (not Map.copyOf) because incoming JSON may include
        // null-valued keys such as "lastTest": null that would otherwise trigger NPE.
        this.source = source;
    }

    static InfrastructureTestConfig from(Map<String, Object> request) {
        var copy = new LinkedHashMap<String, Object>();
        if (request != null) {
            request.forEach(copy::put);
        }
        return new InfrastructureTestConfig(copy);
    }

    static InfrastructureTestConfig empty() {
        return new InfrastructureTestConfig(new LinkedHashMap<>());
    }

    boolean tlsEnabled() {
        var value = source.get("tlsEnabled");
        if (value instanceof Boolean bool) {
            return bool;
        }
        var text = value == null ? "" : String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text);
    }

    String type() {
        return firstNonBlank(source.get("type")).toUpperCase(Locale.ROOT);
    }

    String endpoint() {
        return firstNonBlank(source.get("endpoint"));
    }

    String username() {
        return firstNonBlank(source.get("username"));
    }

    String secret() {
        return firstNonBlank(source.get("secret"), source.get("password"));
    }

    String protocol() {
        return firstNonBlank(source.get("protocol"));
    }

    String provider() {
        return firstNonBlank(source.get("provider"));
    }

    String bucket() {
        return firstNonBlank(source.get("bucket"));
    }

    String clientId() {
        return firstNonBlank(source.get("clientId"));
    }

    String saslMechanism() {
        return firstNonBlank(source.get("saslMechanism"));
    }

    String truststorePath() {
        return firstNonBlank(source.get("truststorePath"));
    }

    int timeoutMs() {
        var timeoutSeconds = intValue(source.get("timeoutSeconds"), 5);
        var requested = intValue(source.get("timeoutMs"), timeoutSeconds * 1_000);
        return Math.max(3_000, Math.min(30_000, requested));
    }

    void requireEndpoint(String message) {
        if (endpoint().isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    int redisPort() {
        var port = intValue(source.get("port"), 0);
        return port > 0 ? port : 6379;
    }

    String safeEndpoint() {
        return type().equals("REDIS") ? endpoint() + ":" + redisPort() : endpoint();
    }

    private static int intValue(Object value, int fallback) {
        try {
            var text = value == null ? "" : String.valueOf(value).trim();
            return Integer.parseInt(text.isBlank() ? String.valueOf(fallback) : text);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String firstNonBlank(Object... values) {
        for (var value : values) {
            var text = value == null ? "" : String.valueOf(value).trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }
}

