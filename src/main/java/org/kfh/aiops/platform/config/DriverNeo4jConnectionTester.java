package org.kfh.aiops.platform.config;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.springframework.stereotype.Component;

/**
 * Neo4j Java Driver based live connection tester for Settings database metadata.
 */
@Component
public class DriverNeo4jConnectionTester implements Neo4jConnectionTester {

    private static final String MASKED_SECRET = "••••••••••••";

    @Override
    public Map<String, Object> test(TenantContext ctx, String section, Map<String, Object> request) {
        var started = System.nanoTime();
        var config = Neo4jTestConfig.from(request);
        try {
            config.validate();
            executeProbe(config);
            return result(ctx, section, config, "Pass", latencyMs(started),
                    "Neo4j topology graph connection passed.");
        } catch (RuntimeException ex) {
            return result(ctx, section, config, "Fail", latencyMs(started),
                    "Neo4j connection failed: " + safeMessage(ex));
        }
    }

    private static void executeProbe(Neo4jTestConfig config) {
        var driverConfig = Config.builder()
                .withConnectionTimeout(config.timeoutSeconds(), TimeUnit.SECONDS)
                .withMaxConnectionPoolSize(1)
                .build();
        try (var driver = GraphDatabase.driver(config.uri(), AuthTokens.basic(config.username(), config.password()), driverConfig);
             var session = driver.session(SessionConfig.builder().withDatabase(config.database()).build())) {
            session.run("RETURN 1 AS ok").consume();
        }
    }

    private static Map<String, Object> result(TenantContext ctx, String section, Neo4jTestConfig config, String status,
            long latencyMs, String message) {
        var result = new LinkedHashMap<String, Object>();
        result.put("section", section);
        result.put("status", status);
        result.put("pass", "Pass".equals(status));
        result.put("latencyMs", latencyMs);
        result.put("message", message);
        result.put("checkedEndpoint", config.safeEndpoint());
        result.put("database", config.database());
        result.put("testedAt", Instant.now().toString());
        result.put("correlationId", ctx.correlationId());
        return result;
    }

    private static long latencyMs(long started) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    private static String safeMessage(RuntimeException ex) {
        var message = String.valueOf(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage())
                .replaceAll("(?i)(password|credential|secret|token|authorization|username)\\s*[:=]\\s*[^,;\\s]+", "$1=masked")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        return message.length() <= 220 ? message : message.substring(0, 217) + "...";
    }

    private record Neo4jTestConfig(String uri, String username, String password, String database, int timeoutSeconds) {

        static Neo4jTestConfig from(Map<String, Object> request) {
            var uri = firstNonBlank(request.get("boltUrl"), request.get("uri"), endpointWithPort(request));
            return new Neo4jTestConfig(
                    uri,
                    firstNonBlank(request.get("user"), request.get("username")),
                    firstNonBlank(request.get("password"), request.get("secret")),
                    firstNonBlank(request.get("database"), "neo4j"),
                    boundedTimeout(request.get("timeoutSeconds")));
        }

        void validate() {
            if (uri.isBlank()) {
                throw new IllegalArgumentException("Neo4j Bolt URI is required.");
            }
            var parsed = URI.create(uri);
            var scheme = String.valueOf(parsed.getScheme()).toLowerCase();
            if (!scheme.equals("bolt") && !scheme.equals("neo4j") && !scheme.equals("bolt+s")
                    && !scheme.equals("neo4j+s") && !scheme.equals("bolt+ssc") && !scheme.equals("neo4j+ssc")) {
                throw new IllegalArgumentException("Neo4j URI must use bolt or neo4j scheme.");
            }
            if (parsed.getUserInfo() != null || parsed.getFragment() != null) {
                throw new IllegalArgumentException("Neo4j URI must not include credentials or fragments.");
            }
            if (username.isBlank()) {
                throw new IllegalArgumentException("Neo4j username is required.");
            }
            if (password.isBlank() || MASKED_SECRET.equals(password)) {
                throw new IllegalArgumentException("Neo4j password is required for Test Connection.");
            }
        }

        String safeEndpoint() {
            return uri;
        }

        private static String endpointWithPort(Map<String, Object> request) {
            var endpoint = firstNonBlank(request.get("endpoint"));
            var port = firstNonBlank(request.get("port"));
            if (endpoint.isBlank()) {
                return "";
            }
            if (endpoint.contains("://")) {
                return endpoint;
            }
            return "bolt://" + endpoint + (port.isBlank() ? ":7687" : ":" + port);
        }

        private static int boundedTimeout(Object value) {
            try {
                var timeout = Integer.parseInt(firstNonBlank(value, "5"));
                return Math.max(3, Math.min(30, timeout));
            } catch (NumberFormatException ex) {
                return 5;
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
}


