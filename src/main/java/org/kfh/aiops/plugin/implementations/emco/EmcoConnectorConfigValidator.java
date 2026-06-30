package org.kfh.aiops.plugin.implementations.emco;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.kfh.aiops.platform.exception.ValidationException;
import org.kfh.aiops.plugin.security.ConnectorEndpointGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Component;

/** Validates EMCO Ping Monitor SQL Server connector configuration before encrypted persistence. */
@Component
public class EmcoConnectorConfigValidator {

    private static final String PLUGIN_TYPE = "EMCO";
    private static final String DEFAULT_SQL_SERVER = "dcvsamdb01";
    private static final Pattern DATABASE_NAME = Pattern.compile("^[A-Za-z0-9_.$#-]{1,128}$");
    private final ConnectorEndpointGuard endpointGuard;

    public EmcoConnectorConfigValidator() {
        this(new StandardEnvironment());
    }

    @Autowired
    public EmcoConnectorConfigValidator(Environment environment) {
        this(new ConnectorEndpointGuard(environment));
    }

    public EmcoConnectorConfigValidator(ConnectorEndpointGuard endpointGuard) {
        this.endpointGuard = endpointGuard;
    }

    public Map<String, Object> validateForCreate(Map<String, Object> fields, String environment) {
        if (Boolean.TRUE.equals(fields.get("installOnly"))) {
            return pendingDefaults();
        }
        var safe = validate(fields, Map.of(), environment, true);
        validateCreateSecrets(fields);
        safe.put("secretsMask", "configured");
        safe.put("configurationStatus", "CONFIGURED");
        return safe;
    }

    public Map<String, Object> validateForUpdate(Map<String, Object> fields, Map<String, Object> existing, String environment) {
        var safe = validate(fields, existing, environment, false);
        var pendingSecrets = "not_configured".equals(existing.get("secretsMask"))
                || "PENDING".equals(existing.get("configurationStatus"));
        if (pendingSecrets) {
            validateCreateSecrets(fields);
            safe.put("secretsMask", "configured");
        } else if (fields.containsKey("secretsPlain")) {
            validateUpdateSecrets(fields);
            safe.put("secretsMask", "configured");
        } else if (existing.containsKey("secretsMask")) {
            safe.put("secretsMask", existing.get("secretsMask"));
        }
        safe.put("configurationStatus", "CONFIGURED");
        return safe;
    }

    private static Map<String, Object> pendingDefaults() {
        var safe = new LinkedHashMap<String, Object>();
        safe.put("pluginType", PLUGIN_TYPE);
        safe.put("typeLabel", "EMCO Ping Monitor");
        safe.put("authMode", "SqlServerCredentials");
        safe.put("enabled", true);
        safe.put("health", "PENDING");
        safe.put("lastTestStatus", "NOT_CONFIGURED");
        safe.put("configurationStatus", "PENDING");
        safe.put("secretsMask", "not_configured");
        safe.put("sqlServer", "DCVSAMDB01");
        safe.put("host", "DCVSAMDB01");
        safe.put("sqlPort", 11433);
        safe.put("kfhDatabase", "EMCO_KFH_PROD");
        safe.put("cctvDatabase", "EMCO_CCTV_PROD");
        safe.put("minutesBack", 60);
        safe.put("connectionTimeoutSeconds", 30);
        safe.put("queryTimeoutSeconds", 120);
        safe.put("encrypt", true);
        safe.put("trustServerCertificate", false);
        safe.put("intervalMin", 15);
        safe.put("schedules", Map.of("intervalMin", 15));
        return safe;
    }

    private Map<String, Object> validate(Map<String, Object> fields, Map<String, Object> existing,
            String environment, boolean create) {
        var merged = new LinkedHashMap<>(existing);
        merged.putAll(fields);
        var endpoint = normalizeEndpoint(required(merged, "sqlServer", fallback(merged, "host"), create),
                boundedInt(merged.get("sqlPort"), 11433, 1, 65535, "sqlPort"));
        var kfhDatabase = databaseName(required(merged, "kfhDatabase", null, create), "kfhDatabase");
        var cctvDatabase = databaseName(required(merged, "cctvDatabase", null, create), "cctvDatabase");
        var minutesBack = boundedInt(merged.get("minutesBack"), 60, 1, 10080, "minutesBack");
        var connectionTimeoutSeconds = boundedInt(merged.get("connectionTimeoutSeconds"), 30, 5, 300,
                "connectionTimeoutSeconds");
        var queryTimeoutSeconds = boundedInt(merged.get("queryTimeoutSeconds"), 120, 5, 600,
                "queryTimeoutSeconds");
        var intervalMin = boundedInt(merged.get("intervalMin"), 15, 5, 1440, "intervalMin");
        var ownerTeam = boundedText(stringValue(merged.getOrDefault("ownerTeam", "Network Ops")), "ownerTeam", 80);
        var notes = boundedText(stringValue(merged.getOrDefault("notes", "")), "notes", 500);
        var encrypt = bool(merged.get("encrypt"), true);
        var trustServerCertificate = bool(merged.get("trustServerCertificate"), false);

        var safe = new LinkedHashMap<String, Object>();
        safe.put("pluginType", PLUGIN_TYPE);
        safe.put("typeLabel", "EMCO Ping Monitor");
        safe.put("authMode", "SqlServerCredentials");
        safe.put("sqlServer", endpoint.host());
        safe.put("host", endpoint.host());
        safe.put("sqlPort", endpoint.port());
        safe.put("kfhDatabase", kfhDatabase);
        safe.put("cctvDatabase", cctvDatabase);
        safe.put("baseUrl", endpoint.safeBaseUrl());
        safe.put("endpointUrl", endpoint.safeBaseUrl());
        safe.put("minutesBack", minutesBack);
        safe.put("connectionTimeoutSeconds", connectionTimeoutSeconds);
        safe.put("timeoutSeconds", queryTimeoutSeconds);
        safe.put("queryTimeoutSeconds", queryTimeoutSeconds);
        safe.put("encrypt", encrypt);
        safe.put("trustServerCertificate", trustServerCertificate);
        safe.put("intervalMin", intervalMin);
        safe.put("ownerTeam", ownerTeam);
        safe.put("notes", notes);
        safe.put("health", merged.getOrDefault("health", "DISABLED"));
        safe.put("lastTestStatus", merged.getOrDefault("lastTestStatus", "NOT_TESTED"));
        safe.put("endpoints", List.of(
                endpointDescriptor(environment, endpoint, kfhDatabase, encrypt, trustServerCertificate, "KFH"),
                endpointDescriptor(environment, endpoint, cctvDatabase, encrypt, trustServerCertificate, "CCTV")));
        safe.put("schedules", Map.of("intervalMin", intervalMin));
        safe.put("mappings", Map.of(
                "appField", "host_description",
                "assetField", "host",
                "severityMap", Map.of("DOWN", "critical", "HIGH", "high", "WARNING", "medium", "UP", "info")));
        return safe;
    }

    private static Map<String, Object> endpointDescriptor(String environment, SqlEndpoint endpoint, String database,
            boolean encrypt, boolean trustServerCertificate, String domain) {
        return Map.of(
                "env", environment,
                "domain", domain,
                "url", endpoint.safeJdbcUrl(database, encrypt, trustServerCertificate),
                "port", endpoint.port());
    }

    private SqlEndpoint normalizeEndpoint(Object value, int configuredPort) {
        var raw = stringValue(value);
        if (raw.isBlank()) {
            raw = DEFAULT_SQL_SERVER;
        }
        if (raw.contains("://") || raw.toLowerCase(Locale.ROOT).startsWith("jdbc:")) {
            throw new ValidationException("EMCO sqlServer must be a SQL Server hostname or IP address, not a URL");
        }
        if (raw.contains("/") || raw.contains("\\") || raw.contains("?") || raw.contains("#") || raw.contains("@")) {
            throw new ValidationException("EMCO sqlServer must not include paths, credentials, query strings, or fragments");
        }
        var host = raw;
        var port = configuredPort;
        if (raw.indexOf(':') == raw.lastIndexOf(':') && raw.contains(":")) {
            var parts = raw.split(":", 2);
            host = parts[0];
            port = boundedInt(parts[1], configuredPort, 1, 65535, "sqlPort");
        }
        var normalizedHost = ConnectorEndpointGuard.normalizeHost(host);
        if (normalizedHost.isBlank()) {
            throw new ValidationException("EMCO sqlServer must include a hostname or IP address");
        }
        endpointGuard.validateLiteralHost("EMCO", normalizedHost);
        return new SqlEndpoint(normalizedHost, port);
    }

    private static String databaseName(Object value, String field) {
        var database = stringValue(value);
        if (database.isBlank()) {
            throw new ValidationException("EMCO connector requires " + field);
        }
        if (!DATABASE_NAME.matcher(database).matches()) {
            throw new ValidationException("EMCO " + field + " must be 1-128 characters and contain only letters, digits, underscore, dash, dot, #, or $");
        }
        return database;
    }

    private static Object fallback(Map<String, Object> fields, String key) {
        return fields.get(key);
    }

    private static Object required(Map<String, Object> fields, String key, Object fallback, boolean create) {
        var value = fields.getOrDefault(key, fallback);
        if (value == null || stringValue(value).isBlank()) {
            throw new ValidationException("EMCO connector requires " + key + (create ? "" : " before it can be updated"));
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static void validateCreateSecrets(Map<String, Object> fields) {
        if (!(fields.get("secretsPlain") instanceof Map<?, ?> rawSecrets)) {
            throw new ValidationException("EMCO connector requires KFH and CCTV SQL Server usernames and passwords");
        }
        var secrets = (Map<Object, Object>) rawSecrets;
        if (secret(secrets, "kfhUsername", "kfhUser", "kfh_username").isBlank()
                || secret(secrets, "kfhPassword", "kfh_password").isBlank()
                || secret(secrets, "cctvUsername", "cctvUser", "cctv_username").isBlank()
                || secret(secrets, "cctvPassword", "cctv_password").isBlank()) {
            throw new ValidationException("EMCO connector requires KFH and CCTV SQL Server usernames and passwords");
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateUpdateSecrets(Map<String, Object> fields) {
        if (!(fields.get("secretsPlain") instanceof Map<?, ?> rawSecrets)) {
            throw new ValidationException("EMCO secretsPlain must be an object when supplied");
        }
        var secrets = (Map<Object, Object>) rawSecrets;
        var hasKfhUsername = !secret(secrets, "kfhUsername", "kfhUser", "kfh_username").isBlank();
        var hasKfhPassword = !secret(secrets, "kfhPassword", "kfh_password").isBlank();
        var hasCctvUsername = !secret(secrets, "cctvUsername", "cctvUser", "cctv_username").isBlank();
        var hasCctvPassword = !secret(secrets, "cctvPassword", "cctv_password").isBlank();
        if (hasKfhUsername != hasKfhPassword) {
            throw new ValidationException("EMCO KFH username and password must be rotated together");
        }
        if (hasCctvUsername != hasCctvPassword) {
            throw new ValidationException("EMCO CCTV username and password must be rotated together");
        }
    }

    private static String secret(Map<Object, Object> secrets, String... keys) {
        for (var key : keys) {
            var value = secrets.get(key);
            if (value != null && !stringValue(value).isBlank()) {
                return stringValue(value);
            }
        }
        return "";
    }

    private static int boundedInt(Object value, int fallback, int min, int max, String field) {
        try {
            var parsed = value == null || stringValue(value).isBlank() ? fallback : Integer.parseInt(stringValue(value));
            if (parsed < min || parsed > max) {
                throw new ValidationException("EMCO " + field + " must be between " + min + " and " + max);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new ValidationException("EMCO " + field + " must be a valid integer");
        }
    }

    private static String boundedText(Object value, String field, int max) {
        var text = stringValue(value);
        if (text.length() > max) {
            throw new ValidationException("EMCO " + field + " must not exceed " + max + " characters");
        }
        return text;
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value == null || stringValue(value).isBlank() ? fallback : Boolean.parseBoolean(stringValue(value));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record SqlEndpoint(String host, int port) {
        String safeBaseUrl() {
            return "jdbc:sqlserver://" + host + ":" + port;
        }

        String safeJdbcUrl(String database, boolean encrypt, boolean trustServerCertificate) {
            return safeBaseUrl() + ";databaseName=" + database + ";encrypt=" + encrypt
                    + ";trustServerCertificate=" + trustServerCertificate;
        }
    }
}

