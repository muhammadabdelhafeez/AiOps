package org.kfh.aiops.plugin.implementations.appdynamics;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.kfh.aiops.platform.exception.ValidationException;
import org.kfh.aiops.plugin.security.ConnectorEndpointGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Component;

/** Validates AppDynamics connector configuration before encrypted persistence. */
@Component
public class AppDynamicsConnectorConfigValidator {

    private static final String PLUGIN_TYPE = "APPDYNAMICS";
    private final ConnectorEndpointGuard endpointGuard;

    public AppDynamicsConnectorConfigValidator() {
        this(new StandardEnvironment());
    }

    @Autowired
    public AppDynamicsConnectorConfigValidator(Environment environment) {
        this(new ConnectorEndpointGuard(environment));
    }

    public AppDynamicsConnectorConfigValidator(ConnectorEndpointGuard endpointGuard) {
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
        safe.put("typeLabel", "AppDynamics");
        safe.put("authMode", "BasicAuth");
        safe.put("enabled", true);
        safe.put("health", "PENDING");
        safe.put("lastTestStatus", "NOT_CONFIGURED");
        safe.put("configurationStatus", "PENDING");
        safe.put("secretsMask", "not_configured");
        safe.put("durationMinutes", 60);
        safe.put("timeoutSeconds", 120);
        safe.put("verifySsl", true);
        safe.put("maxWorkers", 15);
        safe.put("intervalMin", 15);
        safe.put("fetchErrors", true);
        safe.put("fetchViolations", true);
        safe.put("fetchSlowTransactions", true);
        safe.put("schedules", Map.of("intervalMin", 15));
        return safe;
    }

    private Map<String, Object> validate(Map<String, Object> fields, Map<String, Object> existing,
            String environment, boolean create) {
        var merged = new LinkedHashMap<>(existing);
        merged.putAll(fields);

        var controllerUrl = normalizeControllerUrl(required(merged, "controllerUrl", fallback(merged, "baseUrl"), create));
        var durationMinutes = boundedInt(merged.get("durationMinutes"), 60, 1, 1440, "durationMinutes");
        var timeoutSeconds = boundedInt(merged.get("timeoutSeconds"), 120, 5, 300, "timeoutSeconds");
        var verifySsl = bool(merged.get("verifySsl"), true);
        var maxWorkers = boundedInt(merged.get("maxWorkers"), 15, 1, 64, "maxWorkers");
        var intervalMin = boundedInt(merged.get("intervalMin"), 15, 5, 1440, "intervalMin");
        var ownerTeam = boundedText(stringValue(merged.getOrDefault("ownerTeam", "App Support")), "ownerTeam", 80);
        var notes = boundedText(stringValue(merged.getOrDefault("notes", "")), "notes", 500);
        var fetchErrors = bool(merged.get("fetchErrors"), true);
        var fetchViolations = bool(merged.get("fetchViolations"), true);
        var fetchSlowTransactions = bool(merged.get("fetchSlowTransactions"), true);
        if (!fetchErrors && !fetchViolations && !fetchSlowTransactions) {
            throw new ValidationException("AppDynamics connector must enable at least one fetch option");
        }

        var safe = new LinkedHashMap<String, Object>();
        safe.put("pluginType", PLUGIN_TYPE);
        safe.put("typeLabel", "AppDynamics");
        safe.put("authMode", "BasicAuth");
        safe.put("controllerUrl", controllerUrl);
        safe.put("baseUrl", controllerUrl);
        safe.put("endpointUrl", controllerUrl);
        safe.put("durationMinutes", durationMinutes);
        safe.put("timeoutSeconds", timeoutSeconds);
        safe.put("verifySsl", verifySsl);
        safe.put("maxWorkers", maxWorkers);
        safe.put("intervalMin", intervalMin);
        safe.put("fetchErrors", fetchErrors);
        safe.put("fetchViolations", fetchViolations);
        safe.put("fetchSlowTransactions", fetchSlowTransactions);
        safe.put("ownerTeam", ownerTeam);
        safe.put("notes", notes);
        safe.put("health", merged.getOrDefault("health", "DISABLED"));
        safe.put("lastTestStatus", merged.getOrDefault("lastTestStatus", "NOT_TESTED"));
        safe.put("endpoints", List.of(Map.of("env", environment, "url", controllerUrl, "port", 443)));
        safe.put("schedules", Map.of("intervalMin", intervalMin));
        safe.put("mappings", Map.of(
                "appField", "applicationName",
                "assetField", "nodeName",
                "severityMap", Map.of(
                        "ERROR", "critical",
                        "VERY_SLOW", "high",
                        "SLOW", "medium",
                        "STALL", "high",
                        "CRITICAL", "critical",
                        "WARNING", "medium")));
        return safe;
    }

    private static Object fallback(Map<String, Object> fields, String key) {
        return fields.get(key);
    }

    private static Object required(Map<String, Object> fields, String key, Object fallback, boolean create) {
        var value = fields.getOrDefault(key, fallback);
        if ((value == null || stringValue(value).isBlank()) && create) {
            throw new ValidationException("AppDynamics connector requires " + key);
        }
        if (value == null || stringValue(value).isBlank()) {
            throw new ValidationException("AppDynamics connector requires " + key + " before it can be updated");
        }
        return value;
    }

    private String normalizeControllerUrl(Object value) {
        var raw = stringValue(value);
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException ex) {
            throw new ValidationException("AppDynamics controllerUrl must be a valid HTTPS URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ValidationException("AppDynamics controllerUrl must use HTTPS");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ValidationException("AppDynamics controllerUrl must include a host");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new ValidationException("AppDynamics controllerUrl must not include credentials, query strings, or fragments");
        }
        var path = uri.getPath() == null || uri.getPath().isBlank() || "/".equals(uri.getPath())
                ? "/controller" : uri.getPath();
        if (!"/controller".equals(path)) {
            throw new ValidationException("AppDynamics controllerUrl may include only the /controller path; API paths are added by the connector");
        }
        endpointGuard.validateLiteralHost("AppDynamics", uri.getHost());
        var port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return "https://" + uri.getHost().toLowerCase(Locale.ROOT) + port + "/controller";
    }


    private static int boundedInt(Object value, int defaultValue, int min, int max, String fieldName) {
        var raw = value == null || stringValue(value).isBlank() ? String.valueOf(defaultValue) : stringValue(value).trim();
        try {
            var parsed = Integer.parseInt(raw);
            if (parsed < min || parsed > max) {
                throw new ValidationException(fieldName + " must be between " + min + " and " + max);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new ValidationException(fieldName + " must be a valid integer");
        }
    }

    private static String boundedText(String value, String fieldName, int maxLength) {
        var normalized = value == null ? "" : value.trim();
        if (normalized.length() > maxLength) {
            throw new ValidationException(fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static void validateCreateSecrets(Map<String, Object> fields) {
        if (!(fields.get("secretsPlain") instanceof Map<?, ?> rawSecrets)) {
            throw new ValidationException("AppDynamics connector requires username and password");
        }
        var secrets = (Map<Object, Object>) rawSecrets;
        if (secret(secrets, "username", "user", "appdynamicsUsername").isBlank()
                || secret(secrets, "password", "appdynamicsPassword").isBlank()) {
            throw new ValidationException("AppDynamics connector requires username and password");
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateUpdateSecrets(Map<String, Object> fields) {
        if (!(fields.get("secretsPlain") instanceof Map<?, ?> rawSecrets)) {
            throw new ValidationException("AppDynamics secretsPlain must be an object when supplied");
        }
        var secrets = (Map<Object, Object>) rawSecrets;
        if ((secrets.containsKey("username") || secrets.containsKey("user") || secrets.containsKey("appdynamicsUsername"))
                && secret(secrets, "username", "user", "appdynamicsUsername").isBlank()) {
            throw new ValidationException("AppDynamics username cannot be blank when supplied");
        }
        if ((secrets.containsKey("password") || secrets.containsKey("appdynamicsPassword"))
                && secret(secrets, "password", "appdynamicsPassword").isBlank()) {
            throw new ValidationException("AppDynamics password cannot be blank when supplied");
        }
    }

    private static String secret(Map<Object, Object> secrets, String... keys) {
        for (String key : keys) {
            var value = secrets.get(key);
            if (value != null) {
                return stringValue(value).trim();
            }
        }
        return "";
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value == null || stringValue(value).isBlank()) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(stringValue(value));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

