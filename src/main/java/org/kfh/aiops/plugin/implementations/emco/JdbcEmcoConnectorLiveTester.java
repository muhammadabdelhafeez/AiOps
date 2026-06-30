package org.kfh.aiops.plugin.implementations.emco;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

/** JDBC readiness tester for EMCO Ping Monitor SQL Server event stores. */
@Service
public class JdbcEmcoConnectorLiveTester implements EmcoConnectorLiveTester {

    private static final String KFH_PROBE_SQL = """
            SELECT TOP (1)
                h.[label], h.[host], h.[description] AS host_description,
                e.[title], e.[severity], e.[description] AS event_description,
                DATEADD(HOUR, 3, e.[event_time]) AS event_time_kuwait
            FROM [db_owner].[tb_host_events] AS e
            JOIN [db_owner].[tb_hosts] AS h ON e.[host_id] = h.[id]
            WHERE e.[event_time] >= DATEADD(MINUTE, -?, DATEADD(HOUR, -3, SYSDATETIME()))
              AND e.[title] IN ('Connection Quality', 'Host State')
              AND (
                  h.[description] LIKE 'Server Support%'
                  OR h.[description] LIKE 'Call Center%'
                  OR h.[description] LIKE 'Database%'
                  OR h.[description] LIKE 'DevOps - OpenShift%'
                  OR h.[description] LIKE 'Storage & Sun%'
                  OR h.[description] LIKE 'Telephony%'
              )
            ORDER BY e.[event_time] DESC
            """;

    private static final String CCTV_PROBE_SQL = """
            SELECT TOP (1)
                h.[label], h.[host], h.[description] AS host_description,
                e.[title], e.[severity], e.[description] AS event_description,
                DATEADD(HOUR, 3, e.[event_time]) AS event_time_kuwait
            FROM [dbo].[tb_host_events] AS e
            JOIN [dbo].[tb_hosts] AS h ON e.[host_id] = h.[id]
            WHERE e.[event_time] >= DATEADD(MINUTE, -?, DATEADD(HOUR, -3, SYSDATETIME()))
              AND e.[title] IN ('Connection Quality', 'Host State')
              AND h.[label] NOT LIKE 'CCTV%'
            ORDER BY e.[event_time] DESC
            """;

    private final CircuitBreaker circuitBreaker;
    private final SqlConnectionFactory connectionFactory;

    public JdbcEmcoConnectorLiveTester() {
        this(CircuitBreaker.ofDefaults("emcoConnectorLiveTest"), DriverManager::getConnection);
    }

    JdbcEmcoConnectorLiveTester(CircuitBreaker circuitBreaker, SqlConnectionFactory connectionFactory) {
        this.circuitBreaker = circuitBreaker;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public Map<String, Object> test(TenantContext ctx, Map<String, Object> connector, Map<String, String> secrets) {
        var started = System.nanoTime();
        var steps = new ArrayList<Map<String, Object>>();
        var connectorId = text(connector, "id", text(connector, "connectorId", ""));
        var checkedEndpoint = checkedEndpoint(connector);
        try {
            var config = EmcoConfig.from(connector, secrets);
            checkedEndpoint = config.checkedEndpoint();
            validateConfig(config);
            steps.add(step("Configuration", "pass",
                    "Required SQL Server endpoint, KFH/CCTV database names, and encrypted credentials are present."));
            var kfhHasRows = probeDomain(config.kfh());
            steps.add(step("KFH SQL Server probe", "pass", probeMessage("KFH", kfhHasRows)));
            var cctvHasRows = probeDomain(config.cctv());
            steps.add(step("CCTV SQL Server probe", "pass", probeMessage("CCTV", cctvHasRows)));
            return result(ctx, connectorId, true, latencyMs(started),
                    "EMCO connector is reachable and ready to read KFH/CCTV Ping Monitor events.",
                    checkedEndpoint, steps, config.encrypt(), config.trustServerCertificate());
        } catch (RuntimeException ex) {
            var failure = safeMessage(ex);
            if (steps.isEmpty()) {
                steps.add(step("Configuration", "fail", failure));
            } else {
                steps.add(step("EMCO SQL Server communication", "fail", failure));
            }
            return result(ctx, connectorId, false, latencyMs(started),
                    "EMCO connector test failed: " + failure, checkedEndpoint, steps,
                    booleanValue(connector.get("encrypt"), true), booleanValue(connector.get("trustServerCertificate"), false));
        }
    }

    private boolean probeDomain(EmcoDomainConfig domain) {
        return circuitBreaker.executeSupplier(() -> {
            try (Connection connection = connectionFactory.open(domain.jdbcUrl(), domain.connectionProperties());
                    PreparedStatement statement = connection.prepareStatement(domain.probeSql())) {
                statement.setQueryTimeout(domain.queryTimeoutSeconds());
                statement.setInt(1, domain.minutesBack());
                try (ResultSet resultSet = statement.executeQuery()) {
                    return resultSet.next();
                }
            } catch (SQLException ex) {
                throw new IllegalStateException(domain.domain() + " EMCO SQL probe failed: " + ex.getMessage(), ex);
            }
        });
    }

    private static void validateConfig(EmcoConfig config) {
        if (config.host().isBlank()) {
            throw new IllegalArgumentException("EMCO SQL Server host is required. Save connector configuration first.");
        }
        if (config.kfh().database().isBlank() || config.cctv().database().isBlank()) {
            throw new IllegalArgumentException("EMCO KFH and CCTV database names are required.");
        }
        if (config.kfh().username().isBlank() || config.kfh().password().isBlank()
                || config.cctv().username().isBlank() || config.cctv().password().isBlank()) {
            throw new IllegalArgumentException("EMCO KFH and CCTV SQL Server credentials are required. Save credentials first.");
        }
    }

    private static String probeMessage(String domain, boolean hasRows) {
        return hasRows
                ? domain + " query compiled and returned at least one recent host state/connection-quality event."
                : domain + " query compiled successfully; no recent matching events were returned for the configured window.";
    }

    private static Map<String, Object> result(TenantContext ctx, String connectorId, boolean pass, long latencyMs,
            String message, String checkedEndpoint, List<Map<String, Object>> steps, boolean encrypt,
            boolean trustServerCertificate) {
        var result = new LinkedHashMap<String, Object>();
        result.put("connectorRunId", java.util.UUID.randomUUID().toString());
        result.put("connectorId", connectorId);
        result.put("pass", pass);
        result.put("readyToCollect", pass);
        result.put("status", pass ? "Pass" : "Fail");
        result.put("latencyMs", latencyMs);
        result.put("message", message);
        result.put("checkedEndpoint", checkedEndpoint);
        result.put("testedAt", Instant.now().toString());
        result.put("correlationId", ctx.correlationId());
        result.put("encrypt", encrypt);
        result.put("trustServerCertificate", trustServerCertificate);
        result.put("steps", steps);
        return result;
    }

    private static Map<String, Object> step(String name, String status, String message) {
        return Map.of("name", name, "status", status, "message", message);
    }

    private static long latencyMs(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String checkedEndpoint(Map<String, Object> connector) {
        var host = text(connector, "sqlServer", text(connector, "host", ""));
        if (host.isBlank()) {
            return text(connector, "baseUrl", text(connector, "endpointUrl", ""));
        }
        var port = integer(connector, "sqlPort", 11433, 1, 65535);
        return "jdbc:sqlserver://" + host + ":" + port;
    }

    private static String safeMessage(RuntimeException ex) {
        var message = Objects.toString(ex.getMessage(), ex.getClass().getSimpleName())
                .replaceAll("(?i)(password|authorization|token|secret|credential|username|user)\\s*[:=]\\s*[^,;\\s]+", "$1=masked")
                .replaceAll("(?i)(for user '?)[^'\\s]+('?)", "$1masked$2")
                .replaceAll("[\\r\\n\\t]+", " ")
                .trim();
        if (message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 300 ? message : message.substring(0, 297) + "...";
    }

    private static String text(Map<String, ?> values, String key, String fallback) {
        var value = values == null ? null : values.get(key);
        return value == null ? fallback : String.valueOf(value).trim();
    }

    private static int integer(Map<String, Object> values, String key, int fallback, int min, int max) {
        try {
            var parsed = Integer.parseInt(text(values, key, String.valueOf(fallback)));
            return Math.max(min, Math.min(max, parsed));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean booleanValue(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null || String.valueOf(value).isBlank() ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    @FunctionalInterface
    interface SqlConnectionFactory {
        Connection open(String jdbcUrl, Properties properties) throws SQLException;
    }

    private record EmcoConfig(String host, int port, int minutesBack, int connectionTimeoutSeconds,
            int queryTimeoutSeconds, boolean encrypt, boolean trustServerCertificate, EmcoDomainConfig kfh,
            EmcoDomainConfig cctv) {

        static EmcoConfig from(Map<String, Object> connector, Map<String, String> secrets) {
            var host = text(connector, "sqlServer", text(connector, "host", ""));
            var port = integer(connector, "sqlPort", 11433, 1, 65535);
            var minutesBack = integer(connector, "minutesBack", 60, 1, 10080);
            var connectionTimeoutSeconds = integer(connector, "connectionTimeoutSeconds", 30, 5, 300);
            var queryTimeoutSeconds = integer(connector, "queryTimeoutSeconds", 120, 5, 600);
            var encrypt = booleanValue(connector.get("encrypt"), true);
            var trustServerCertificate = booleanValue(connector.get("trustServerCertificate"), false);
            var kfh = domain("KFH", host, port, text(connector, "kfhDatabase", ""), minutesBack,
                    connectionTimeoutSeconds, queryTimeoutSeconds, encrypt, trustServerCertificate,
                    text(secrets, "kfhUsername", text(secrets, "kfhUser", text(secrets, "kfh_username", ""))),
                    text(secrets, "kfhPassword", text(secrets, "kfh_password", "")), KFH_PROBE_SQL);
            var cctv = domain("CCTV", host, port, text(connector, "cctvDatabase", ""), minutesBack,
                    connectionTimeoutSeconds, queryTimeoutSeconds, encrypt, trustServerCertificate,
                    text(secrets, "cctvUsername", text(secrets, "cctvUser", text(secrets, "cctv_username", ""))),
                    text(secrets, "cctvPassword", text(secrets, "cctv_password", "")), CCTV_PROBE_SQL);
            return new EmcoConfig(host, port, minutesBack, connectionTimeoutSeconds, queryTimeoutSeconds, encrypt,
                    trustServerCertificate, kfh, cctv);
        }

        String checkedEndpoint() {
            return "jdbc:sqlserver://" + host + ":" + port
                    + ";databaseName=" + kfh.database() + "," + cctv.database()
                    + ";encrypt=" + encrypt + ";trustServerCertificate=" + trustServerCertificate;
        }

        private static EmcoDomainConfig domain(String domain, String host, int port, String database, int minutesBack,
                int connectionTimeoutSeconds, int queryTimeoutSeconds, boolean encrypt, boolean trustServerCertificate,
                String username, String password, String probeSql) {
            var jdbcUrl = "jdbc:sqlserver://" + host + ":" + port
                    + ";databaseName=" + database
                    + ";encrypt=" + encrypt
                    + ";trustServerCertificate=" + trustServerCertificate
                    + ";loginTimeout=" + connectionTimeoutSeconds;
            return new EmcoDomainConfig(domain, database, jdbcUrl, minutesBack, queryTimeoutSeconds,
                    username, password, probeSql);
        }
    }

    private record EmcoDomainConfig(String domain, String database, String jdbcUrl, int minutesBack,
            int queryTimeoutSeconds, String username, String password, String probeSql) {

        Properties connectionProperties() {
            var properties = new Properties();
            properties.setProperty("user", username);
            properties.setProperty("password", password);
            return properties;
        }
    }
}

