package org.kfh.aiops.plugin.implementations.scom;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.kfh.aiops.platform.exception.ValidationException;
import org.kfh.aiops.plugin.security.ConnectorEndpointGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Component;

/** Validates Microsoft SCOM WinRM connector configuration before encrypted persistence. */
@Component
public class ScomConnectorConfigValidator {

    private static final String PLUGIN_TYPE = "SCOM";
    private static final Set<String> AUTH_METHODS = Set.of("KERBEROS", "NEGOTIATE", "DEFAULT", "CREDSSP");
    private final ConnectorEndpointGuard endpointGuard;

    public ScomConnectorConfigValidator() {
        this(new StandardEnvironment());
    }

    @Autowired
    public ScomConnectorConfigValidator(Environment environment) {
        this(new ConnectorEndpointGuard(environment));
    }

    public ScomConnectorConfigValidator(ConnectorEndpointGuard endpointGuard) {
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
        safe.put("typeLabel", "Microsoft SCOM");
        safe.put("authMode", "WinRM");
        safe.put("enabled", true);
        safe.put("health", "PENDING");
        safe.put("lastTestStatus", "NOT_CONFIGURED");
        safe.put("configurationStatus", "PENDING");
        safe.put("secretsMask", "not_configured");
        safe.put("managementServer", "dcvscoap12.corp.kfh.kw");
        safe.put("domain", "corp.kfh.kw");
        safe.put("winrmPort", 5986);
        safe.put("useHttps", true);
        safe.put("verifySsl", true);
        safe.put("authMethod", "Kerberos");
        safe.put("hoursBack", 1);
        safe.put("connectionTimeoutSeconds", 60);
        safe.put("intervalMin", 15);
        safe.put("schedules", Map.of("intervalMin", 15));
        return safe;
    }

    private Map<String, Object> validate(Map<String, Object> fields, Map<String, Object> existing,
            String environment, boolean create) {
        var merged = new LinkedHashMap<>(existing);
        merged.putAll(fields);
        var useHttps = bool(merged.get("useHttps"), true);
        var defaultPort = useHttps ? 5986 : 5985;
        var configuredPort = boundedInt(merged.get("winrmPort"), defaultPort, 1, 65535, "winrmPort");
        var endpoint = normalizeEndpoint(required(merged, "managementServer", fallback(merged, "host"), create),
                configuredPort, useHttps);
        var domain = boundedText(required(merged, "domain", null, create), "domain", 120);
        var authMethod = normalizeAuthMethod(merged.getOrDefault("authMethod", "Kerberos"));
        var hoursBack = boundedInt(merged.getOrDefault("hoursBack", merged.get("hours")), 1, 1, 168, "hoursBack");
        var timeoutSeconds = boundedInt(merged.getOrDefault("connectionTimeoutSeconds", merged.get("timeoutSeconds")),
                60, 5, 300, "connectionTimeoutSeconds");
        var intervalMin = boundedInt(merged.get("intervalMin"), 15, 5, 1440, "intervalMin");
        var ownerTeam = boundedText(stringValue(merged.getOrDefault("ownerTeam", "Infrastructure Ops")), "ownerTeam", 80);
        var notes = boundedText(stringValue(merged.getOrDefault("notes", "")), "notes", 500);
        var verifySsl = bool(merged.get("verifySsl"), true);

        var safe = new LinkedHashMap<String, Object>();
        safe.put("pluginType", PLUGIN_TYPE);
        safe.put("typeLabel", "Microsoft SCOM");
        safe.put("authMode", "WinRM");
        safe.put("managementServer", endpoint.host());
        safe.put("host", endpoint.host());
        safe.put("domain", domain);
        safe.put("winrmPort", endpoint.port());
        safe.put("useHttps", endpoint.useHttps());
        safe.put("verifySsl", verifySsl);
        safe.put("authMethod", authMethod);
        safe.put("hoursBack", hoursBack);
        safe.put("hours", hoursBack);
        safe.put("connectionTimeoutSeconds", timeoutSeconds);
        safe.put("timeoutSeconds", timeoutSeconds);
        safe.put("baseUrl", endpoint.wsmanUrl());
        safe.put("endpointUrl", endpoint.wsmanUrl());
        safe.put("intervalMin", intervalMin);
        safe.put("ownerTeam", ownerTeam);
        safe.put("notes", notes);
        safe.put("health", merged.getOrDefault("health", "DISABLED"));
        safe.put("lastTestStatus", merged.getOrDefault("lastTestStatus", "NOT_TESTED"));
        safe.put("endpoints", List.of(Map.of("env", environment, "url", endpoint.wsmanUrl(), "port", endpoint.port())));
        safe.put("schedules", Map.of("intervalMin", intervalMin));
        safe.put("mappings", Map.of(
                "appField", "MonitoringObjectPath",
                "assetField", "NetbiosComputerName",
                "severityMap", Map.of("ERROR", "critical", "WARNING", "medium", "INFORMATION", "info")));
        return safe;
    }

    private ScomEndpoint normalizeEndpoint(Object value, int configuredPort, boolean configuredHttps) {
        var raw = stringValue(value);
        URI uri;
        try {
            uri = raw.contains("://") ? new URI(raw) : new URI((configuredHttps ? "https://" : "http://") + raw);
        } catch (URISyntaxException ex) {
            throw new ValidationException("SCOM managementServer must be a valid host, IP, or WinRM URL");
        }
        if (!List.of("http", "https").contains(stringValue(uri.getScheme()).toLowerCase(Locale.ROOT))) {
            throw new ValidationException("SCOM managementServer must use http or https WinRM");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new ValidationException("SCOM managementServer must not include credentials, query strings, or fragments");
        }
        var path = uri.getPath() == null ? "" : uri.getPath();
        if (!path.isBlank() && !"/".equals(path) && !"/wsman".equalsIgnoreCase(path)) {
            throw new ValidationException("SCOM managementServer may include only the /wsman path");
        }
        var host = ConnectorEndpointGuard.normalizeHost(uri.getHost());
        if (host.isBlank()) {
            throw new ValidationException("SCOM managementServer must include a hostname or IP address");
        }
        endpointGuard.validateLiteralHost("SCOM", host);
        var port = uri.getPort() == -1 ? configuredPort : uri.getPort();
        var https = "https".equalsIgnoreCase(uri.getScheme());
        return new ScomEndpoint(host, port, https, (https ? "https://" : "http://") + host + ":" + port + "/wsman");
    }

    private static Object fallback(Map<String, Object> fields, String key) {
        return fields.get(key);
    }

    private static Object required(Map<String, Object> fields, String key, Object fallback, boolean create) {
        var value = fields.getOrDefault(key, fallback);
        if (value == null || stringValue(value).isBlank()) {
            throw new ValidationException("SCOM connector requires " + key + (create ? "" : " before it can be updated"));
        }
        return value;
    }

    private static String normalizeAuthMethod(Object value) {
        var raw = stringValue(value).isBlank() ? "Kerberos" : stringValue(value);
        var normalized = raw.toUpperCase(Locale.ROOT);
        if (!AUTH_METHODS.contains(normalized)) {
            throw new ValidationException("SCOM authMethod must be Kerberos, Negotiate, Default, or CredSSP");
        }
        return normalized.charAt(0) + normalized.substring(1).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static void validateCreateSecrets(Map<String, Object> fields) {
        if (!(fields.get("secretsPlain") instanceof Map<?, ?> rawSecrets)) {
            throw new ValidationException("SCOM connector requires username and password");
        }
        var secrets = (Map<Object, Object>) rawSecrets;
        if (secret(secrets, "username", "user", "scomUsername").isBlank()
                || secret(secrets, "password", "scomPassword").isBlank()) {
            throw new ValidationException("SCOM connector requires username and password");
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateUpdateSecrets(Map<String, Object> fields) {
        if (!(fields.get("secretsPlain") instanceof Map<?, ?> rawSecrets)) {
            throw new ValidationException("SCOM secretsPlain must be an object when supplied");
        }
        var secrets = (Map<Object, Object>) rawSecrets;
        var hasUsername = !secret(secrets, "username", "user", "scomUsername").isBlank();
        var hasPassword = !secret(secrets, "password", "scomPassword").isBlank();
        if (hasUsername != hasPassword) {
            throw new ValidationException("SCOM username and password must be rotated together");
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
                throw new ValidationException("SCOM " + field + " must be between " + min + " and " + max);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new ValidationException("SCOM " + field + " must be a valid integer");
        }
    }

    private static String boundedText(Object value, String field, int max) {
        var text = stringValue(value);
        if (text.length() > max) {
            throw new ValidationException("SCOM " + field + " must not exceed " + max + " characters");
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

    private record ScomEndpoint(String host, int port, boolean useHttps, String wsmanUrl) { }
}
