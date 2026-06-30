package org.kfh.aiops.plugin.implementations.vrops;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.kfh.aiops.platform.exception.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Validates VMware vROps / Aria Operations connector configuration before encrypted persistence. */
@Component
public class VropsConnectorConfigValidator {

    private static final String PLUGIN_TYPE = "VROPS";
    private static final String DEFAULT_AUTH_SOURCE = "KFH AD";
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private final Environment environment;

    public VropsConnectorConfigValidator() {
        this.environment = null;
    }

    @Autowired
    public VropsConnectorConfigValidator(Environment environment) {
        this.environment = environment;
    }

    public Map<String, Object> validateForCreate(Map<String, Object> fields, String environmentName) {
        if (Boolean.TRUE.equals(fields.get("installOnly"))) {
            return pendingDefaults();
        }
        var safe = validate(fields, Map.of(), environmentName, true);
        validateCreateSecrets(fields);
        safe.put("secretsMask", "configured");
        safe.put("configurationStatus", "CONFIGURED");
        return safe;
    }

    public Map<String, Object> validateForUpdate(Map<String, Object> fields, Map<String, Object> existing, String environmentName) {
        var safe = validate(fields, existing, environmentName, false);
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
        safe.put("typeLabel", "VMware vROps");
        safe.put("authMode", "Token");
        safe.put("enabled", true);
        safe.put("health", "PENDING");
        safe.put("lastTestStatus", "NOT_CONFIGURED");
        safe.put("configurationStatus", "PENDING");
        safe.put("secretsMask", "not_configured");
        safe.put("authSource", DEFAULT_AUTH_SOURCE);
        safe.put("hours", 1);
        safe.put("pageSize", 1000);
        safe.put("maxPages", 200);
        safe.put("maxWorkers", 12);
        safe.put("timeoutSeconds", 120);
        safe.put("intervalMin", 15);
        safe.put("verifySsl", true);
        safe.put("schedules", Map.of("intervalMin", 15));
        return safe;
    }

    private Map<String, Object> validate(Map<String, Object> fields, Map<String, Object> existing,
            String environmentName, boolean create) {
        var merged = new LinkedHashMap<>(existing);
        merged.putAll(fields);
        var endpoint = normalizeEndpoint(required(merged, "host", fallback(merged, "baseUrl"), create));
        var authSource = boundedText(stringValue(merged.getOrDefault("authSource", DEFAULT_AUTH_SOURCE)), "authSource", 80);
        var hours = boundedInt(merged.get("hours"), 1, 1, 168, "hours");
        var pageSize = boundedInt(merged.get("pageSize"), 1000, 1, 5000, "pageSize");
        var maxPages = boundedInt(merged.get("maxPages"), 200, 1, 1000, "maxPages");
        var maxWorkers = boundedInt(merged.get("maxWorkers"), 12, 1, 64, "maxWorkers");
        var timeoutSeconds = boundedInt(merged.get("timeoutSeconds"), 120, 5, 300, "timeoutSeconds");
        var intervalMin = boundedInt(merged.get("intervalMin"), 15, 5, 1440, "intervalMin");
        var ownerTeam = boundedText(stringValue(merged.getOrDefault("ownerTeam", "Infrastructure Ops")), "ownerTeam", 80);
        var notes = boundedText(stringValue(merged.getOrDefault("notes", "")), "notes", 500);
        var verifySsl = bool(merged.get("verifySsl"), true);

        var safe = new LinkedHashMap<String, Object>();
        safe.put("pluginType", PLUGIN_TYPE);
        safe.put("typeLabel", "VMware vROps");
        safe.put("authMode", "Token");
        safe.put("host", endpoint.host());
        safe.put("baseUrl", endpoint.baseUrl());
        safe.put("endpointUrl", endpoint.baseUrl());
        safe.put("authSource", authSource);
        safe.put("hours", hours);
        safe.put("pageSize", pageSize);
        safe.put("maxPages", maxPages);
        safe.put("maxWorkers", maxWorkers);
        safe.put("timeoutSeconds", timeoutSeconds);
        safe.put("intervalMin", intervalMin);
        safe.put("verifySsl", verifySsl);
        safe.put("ownerTeam", ownerTeam);
        safe.put("notes", notes);
        safe.put("health", merged.getOrDefault("health", "DISABLED"));
        safe.put("lastTestStatus", merged.getOrDefault("lastTestStatus", "NOT_TESTED"));
        safe.put("endpoints", List.of(Map.of("env", environmentName, "url", endpoint.baseUrl(), "port", endpoint.port())));
        safe.put("schedules", Map.of("intervalMin", intervalMin));
        safe.put("mappings", Map.of(
                "appField", "resourceKey.name",
                "assetField", "resourceId",
                "severityMap", Map.of(
                        "CRITICAL", "critical",
                        "IMMEDIATE", "critical",
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
            throw new ValidationException("vROps connector requires " + key);
        }
        if (value == null || stringValue(value).isBlank()) {
            throw new ValidationException("vROps connector requires " + key + " before it can be updated");
        }
        return value;
    }

    private VropsEndpoint normalizeEndpoint(Object value) {
        var raw = stringValue(value);
        URI uri;
        try {
            uri = raw.startsWith("https://") ? new URI(raw) : new URI("https://" + raw);
        } catch (URISyntaxException ex) {
            throw new ValidationException("vROps host must be a valid host, IP, or HTTPS suite-api URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new ValidationException("vROps connector must use HTTPS");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new ValidationException("vROps host must not include user-info, query strings, or fragments");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new ValidationException("vROps host must include a hostname or IP address");
        }
        var path = uri.getPath() == null || uri.getPath().isBlank() ? "" : uri.getPath();
        if (!path.isBlank() && !"/suite-api/api".equals(path)) {
            throw new ValidationException("vROps URL may include only the /suite-api/api path; API paths are added by the connector");
        }
        var host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        rejectUnsafeHost(host);
        var port = uri.getPort() == -1 ? 443 : uri.getPort();
        var hostWithPort = port == 443 ? host : host + ":" + port;
        return new VropsEndpoint(hostWithPort, "https://" + hostWithPort + "/suite-api/api", port);
    }

    private void rejectUnsafeHost(String host) {
        if (host.equals("localhost") || host.endsWith(".localhost") || host.equals("169.254.169.254")) {
            throw new ValidationException("vROps host is not allowed for SSRF protection");
        }
        if (isUnsafeLiteralHost(host)) {
            throw new ValidationException("vROps host targets a loopback, link-local, metadata, or multicast address");
        }
    }

    private static boolean isUnsafeLiteralHost(String host) {
        if (!IPV4.matcher(host).matches()) {
            return false;
        }
        try {
            var address = InetAddress.getByName(host);
            return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isMulticastAddress();
        } catch (Exception ex) {
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateCreateSecrets(Map<String, Object> fields) {
        if (!(fields.get("secretsPlain") instanceof Map<?, ?> rawSecrets)) {
            throw new ValidationException("vROps connector requires username and password");
        }
        var secrets = (Map<Object, Object>) rawSecrets;
        if (secret(secrets, "username", "user", "vropsUsername").isBlank()
                || secret(secrets, "password", "vropsPassword").isBlank()) {
            throw new ValidationException("vROps connector requires username and password");
        }
    }

    @SuppressWarnings("unchecked")
    private static void validateUpdateSecrets(Map<String, Object> fields) {
        if (!(fields.get("secretsPlain") instanceof Map<?, ?> rawSecrets)) {
            return;
        }
        var secrets = (Map<Object, Object>) rawSecrets;
        var hasUsername = !secret(secrets, "username", "user", "vropsUsername").isBlank();
        var hasPassword = !secret(secrets, "password", "vropsPassword").isBlank();
        if (hasUsername != hasPassword) {
            throw new ValidationException("vROps username and password must be rotated together");
        }
    }

    private static String secret(Map<Object, Object> secrets, String... names) {
        for (var name : names) {
            var value = secrets.get(name);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private static int boundedInt(Object value, int fallback, int min, int max, String field) {
        try {
            var parsed = value == null || String.valueOf(value).isBlank() ? fallback : Integer.parseInt(String.valueOf(value));
            if (parsed < min || parsed > max) {
                throw new ValidationException("vROps " + field + " must be between " + min + " and " + max);
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new ValidationException("vROps " + field + " must be a number");
        }
    }

    private static String boundedText(String value, String field, int max) {
        var trimmed = value == null ? "" : value.trim();
        if (trimmed.length() > max) {
            throw new ValidationException("vROps " + field + " is too long");
        }
        return trimmed;
    }

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record VropsEndpoint(String host, String baseUrl, int port) { }
}

