package org.kfh.aiops.plugin.implementations.bmc;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.kfh.aiops.platform.exception.ValidationException;
import org.kfh.aiops.plugin.service.ConnectorCatalogService;
import org.kfh.aiops.plugin.security.ConnectorEndpointGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.stereotype.Component;

@Component
public class BmcConnectorConfigValidator {

    private static final String DEFAULT_LOGIN_ENDPOINT = "/ims/api/v1/access_keys/login";
    private static final String DEFAULT_EVENTS_ENDPOINT = "/events-service/api/v1.0/events/msearch";
    private final ConnectorEndpointGuard endpointGuard;

    public BmcConnectorConfigValidator() {
        this(new StandardEnvironment());
    }

    @Autowired
    public BmcConnectorConfigValidator(Environment environment) {
        this(new ConnectorEndpointGuard(environment));
    }

    public BmcConnectorConfigValidator(ConnectorEndpointGuard endpointGuard) {
        this.endpointGuard = endpointGuard;
    }

    public Map<String, Object> validateForCreate(Map<String, Object> fields, String environment) {
        if (Boolean.TRUE.equals(fields.get("installOnly"))) {
            var safe = new LinkedHashMap<String, Object>();
            safe.put("pluginType", ConnectorCatalogService.BMC_PLUGIN_TYPE);
            safe.put("typeLabel", "BMC Helix");
            safe.put("authMode", "AccessKey");
            safe.put("enabled", true);
            safe.put("health", "PENDING");
            safe.put("lastTestStatus", "NOT_CONFIGURED");
            safe.put("configurationStatus", "PENDING");
            safe.put("secretsMask", "not_configured");
            safe.put("intervalMin", 15);
            safe.put("minutesBack", 60);
            safe.put("pageSize", 100);
            safe.put("maxEvents", 500);
            safe.put("timeoutSeconds", 120);
            safe.put("verifySsl", true);
            safe.put("loginEndpoint", DEFAULT_LOGIN_ENDPOINT);
            safe.put("eventsEndpoint", DEFAULT_EVENTS_ENDPOINT);
            safe.put("schedules", Map.of("intervalMin", 15));
            return safe;
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

    private Map<String, Object> validate(Map<String, Object> fields, Map<String, Object> existing,
            String environment, boolean create) {
        var merged = new LinkedHashMap<>(existing);
        merged.putAll(fields);

        var baseUrl = normalizeBaseUrl(required(merged, "baseUrl", fallback(merged, "endpointUrl"), create));
        var loginEndpoint = safeRelativePath(stringValue(merged.getOrDefault("loginEndpoint", DEFAULT_LOGIN_ENDPOINT)),
                "loginEndpoint");
        var eventsEndpoint = safeRelativePath(stringValue(merged.getOrDefault("eventsEndpoint", DEFAULT_EVENTS_ENDPOINT)),
                "eventsEndpoint");
        var minutesBack = boundedInt(merged.get("minutesBack"), 60, 1, 1440, "minutesBack");
        var pageSize = boundedInt(merged.get("pageSize"), 100, 1, 500, "pageSize");
        var maxEvents = boundedInt(merged.get("maxEvents"), 500, pageSize, 10_000, "maxEvents");
        var timeoutSeconds = boundedInt(merged.get("timeoutSeconds"), 120, 5, 300, "timeoutSeconds");
        var verifySsl = bool(merged.get("verifySsl"), true);
        var intervalMin = boundedInt(merged.get("intervalMin"), 15, 5, 1440, "intervalMin");
        var ownerTeam = boundedText(stringValue(merged.getOrDefault("ownerTeam", "Platform Ops")), "ownerTeam", 80);
        var notes = boundedText(stringValue(merged.getOrDefault("notes", "")), "notes", 500);

        var safe = new LinkedHashMap<String, Object>();
        safe.put("pluginType", ConnectorCatalogService.BMC_PLUGIN_TYPE);
        safe.put("typeLabel", "BMC Helix");
        safe.put("authMode", "AccessKey");
        safe.put("baseUrl", baseUrl);
        safe.put("endpointUrl", baseUrl);
        safe.put("loginEndpoint", loginEndpoint);
        safe.put("eventsEndpoint", eventsEndpoint);
        safe.put("minutesBack", minutesBack);
        safe.put("pageSize", pageSize);
        safe.put("maxEvents", maxEvents);
        safe.put("timeoutSeconds", timeoutSeconds);
        safe.put("verifySsl", verifySsl);
        safe.put("intervalMin", intervalMin);
        safe.put("ownerTeam", ownerTeam);
        safe.put("notes", notes);
        safe.put("health", merged.getOrDefault("health", "DISABLED"));
        safe.put("lastTestStatus", merged.getOrDefault("lastTestStatus", "NOT_TESTED"));
        safe.put("endpoints", List.of(Map.of("env", environment, "url", baseUrl, "port", 443)));
        safe.put("schedules", Map.of("intervalMin", intervalMin));
        safe.put("mappings", Map.of(
                "appField", "_source._service_name",
                "assetField", "_source.source_hostname",
                "severityMap", Map.of(
                        "CRITICAL", "critical",
                        "MAJOR", "high",
                        "MINOR", "medium",
                        "WARNING", "medium",
                        "INFO", "info")));
        return safe;
    }

    private static Object fallback(Map<String, Object> fields, String key) {
        return fields.get(key);
    }

    private static Object required(Map<String, Object> fields, String key, Object fallback, boolean create) {
        var value = fields.getOrDefault(key, fallback);
        if ((value == null || stringValue(value).isBlank()) && create) {
            throw new ValidationException("BMC connector requires " + key);
        }
        if (value == null || stringValue(value).isBlank()) {
            throw new ValidationException("BMC connector requires " + key + " before it can be updated");
        }
        return value;
    }

    private String normalizeBaseUrl(Object value) {
        var raw = stringValue(value);
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException ex) {
            throw new ValidationException("BMC baseUrl must be a valid HTTPS URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ValidationException("BMC baseUrl must use HTTPS");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ValidationException("BMC baseUrl must include a host");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new ValidationException("BMC baseUrl must not include credentials, query strings, or fragments");
        }
        if (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath())) {
            throw new ValidationException("BMC baseUrl must not include an API path; configure endpoints separately");
        }
        endpointGuard.validateLiteralHost("BMC", uri.getHost());
        var port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return "https://" + uri.getHost().toLowerCase(Locale.ROOT) + port;
    }


    private static String safeRelativePath(String raw, String fieldName) {
        var value = raw == null || raw.isBlank() ? ("loginEndpoint".equals(fieldName)
                ? DEFAULT_LOGIN_ENDPOINT : DEFAULT_EVENTS_ENDPOINT) : raw.trim();
        if (!value.startsWith("/") || value.contains("://") || value.contains("..") || value.contains("\\")
                || value.contains("?") || value.contains("#") || value.contains("\r") || value.contains("\n")
                || value.length() > 160) {
            throw new ValidationException("BMC " + fieldName + " must be a safe relative API path");
        }
        return value;
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
            throw new ValidationException("BMC connector requires access key and access secret key");
        }
        var secrets = (Map<Object, Object>) rawSecrets;
        if (secret(secrets, "accessKey", "access_key").isBlank()
                || secret(secrets, "accessSecretKey", "access_secret_key", "accessSecret").isBlank()) {
            throw new ValidationException("BMC connector requires access key and access secret key");
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateUpdateSecrets(Map<String, Object> fields) {
        if (!(fields.get("secretsPlain") instanceof Map<?, ?> rawSecrets)) {
            throw new ValidationException("BMC secretsPlain must be an object when supplied");
        }
        var secrets = (Map<Object, Object>) rawSecrets;
        if ((secrets.containsKey("accessKey") || secrets.containsKey("access_key"))
                && secret(secrets, "accessKey", "access_key").isBlank()) {
            throw new ValidationException("BMC access key cannot be blank when supplied");
        }
        if ((secrets.containsKey("accessSecretKey") || secrets.containsKey("access_secret_key")
                || secrets.containsKey("accessSecret"))
                && secret(secrets, "accessSecretKey", "access_secret_key", "accessSecret").isBlank()) {
            throw new ValidationException("BMC access secret key cannot be blank when supplied");
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
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null || stringValue(value).isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(stringValue(value));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}

