package org.aiopsanalysis.service.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.aiopsanalysis.config.SsrfValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * BMC Helix ITSM Connector Plugin.
 *
 * Supports:
 * - BMC Helix ITSM / BMC Remedy
 * - REST API integration
 * - Authentication: Basic, API Key, OAuth2
 */
@Component
public class BMCHelixConnectorPlugin implements ConnectorPlugin {

    private static final Logger log = LoggerFactory.getLogger(BMCHelixConnectorPlugin.class);

    private final WebClient.Builder webClientBuilder;
    private final SsrfValidator ssrfValidator;
    private final ObjectMapper objectMapper;

    public BMCHelixConnectorPlugin(WebClient.Builder webClientBuilder, SsrfValidator ssrfValidator, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.ssrfValidator = ssrfValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getType() {
        return "BMC";
    }

    @Override
    public String getDisplayName() {
        return "BMC Helix ITSM";
    }

    @Override
    public String getDescription() {
        return "Connect to BMC Helix ITSM / BMC Remedy for incident and event data ingestion";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public ConfigSchema getConfigSchema() {
        List<FieldDefinition> configFields = List.of(
                new FieldDefinition("endpointBaseUrl", "Base URL", "text", true, null,
                        "https://bmc-helix.example.com", "BMC Helix server base URL", null, null, null),
                new FieldDefinition("method", "Collection Method", "select", true, "API",
                        null, "How to collect data from BMC",
                        List.of(new SelectOption("API", "REST API"), new SelectOption("EXPORT", "Export Files")),
                        null, null),
                new FieldDefinition("timeoutMs", "Timeout (ms)", "number", false, 30000,
                        "30000", "Request timeout in milliseconds", null, 5000, 300000),
                new FieldDefinition("verifyTls", "Verify TLS", "checkbox", false, true,
                        null, "Verify server TLS certificate", null, null, null),
                new FieldDefinition("env", "Environment", "select", true, "Prod",
                        null, "Target environment",
                        List.of(new SelectOption("Prod", "Production"), new SelectOption("DR", "Disaster Recovery"), new SelectOption("Both", "Both")),
                        null, null),
                new FieldDefinition("domain", "Domain", "select", true, "Infra",
                        null, "Alert domain classification",
                        List.of(
                                new SelectOption("Infra", "Infrastructure"),
                                new SelectOption("Core", "Core Banking"),
                                new SelectOption("Payments", "Payments"),
                                new SelectOption("Security", "Security"),
                                new SelectOption("Data", "Data & Analytics"),
                                new SelectOption("Channels", "Digital Channels")
                        ), null, null),
                new FieldDefinition("windowMinutes", "Collection Window (min)", "number", false, 60,
                        "60", "Default time window for alert collection", null, 5, 1440),
                new FieldDefinition("maxRecords", "Max Records", "number", false, 50000,
                        "50000", "Maximum records to collect per run", null, 100, 100000),
                new FieldDefinition("testPath", "Health Check Path", "text", false, "/api/v1/health",
                        "/api/v1/health", "API endpoint for connection testing", null, null, null),
                new FieldDefinition("alertsPath", "Alerts API Path", "text", false, "/api/arsys/v1/entry/HPD:IncidentInterface",
                        "/api/arsys/v1/entry/HPD:IncidentInterface", "API endpoint for alerts", null, null, null),
                new FieldDefinition("notes", "Notes", "textarea", false, null,
                        "Add notes about this connector...", "Internal notes", null, null, null)
        );

        List<FieldDefinition> secretFields = List.of(
                new FieldDefinition("authType", "Authentication Type", "select", true, "BASIC",
                        null, "Authentication method",
                        List.of(
                                new SelectOption("BASIC", "Basic Auth"),
                                new SelectOption("APIKEY", "API Key"),
                                new SelectOption("OAUTH2", "OAuth 2.0")
                        ), null, null),
                new FieldDefinition("username", "Username", "text", false, null,
                        "service-account", "Username for Basic auth", null, null, null),
                new FieldDefinition("password", "Password", "password", false, null,
                        "********", "Password for Basic auth", null, null, null),
                new FieldDefinition("apiKey", "API Key", "password", false, null,
                        "********", "API key for API Key auth", null, null, null),
                new FieldDefinition("clientId", "Client ID", "text", false, null,
                        null, "Client ID for OAuth2", null, null, null),
                new FieldDefinition("clientSecret", "Client Secret", "password", false, null,
                        "********", "Client secret for OAuth2", null, null, null),
                new FieldDefinition("tokenUrl", "Token URL", "text", false, null,
                        "https://bmc-helix.example.com/oauth/token", "OAuth2 token endpoint", null, null, null)
        );

        return new ConfigSchema(configFields, secretFields);
    }

    @Override
    public List<String> validateConfig(JsonNode config) {
        List<String> errors = new ArrayList<>();

        // Required fields
        if (!hasField(config, "endpointBaseUrl")) {
            errors.add("Base URL is required");
        } else {
            String url = config.get("endpointBaseUrl").asText();
            SsrfValidator.ValidationResult ssrfResult = ssrfValidator.validate(url);
            if (!ssrfResult.valid()) {
                errors.add("Invalid URL: " + ssrfResult.error());
            }
        }

        if (!hasField(config, "method")) {
            errors.add("Collection method is required");
        }

        if (!hasField(config, "env")) {
            errors.add("Environment is required");
        }

        if (!hasField(config, "domain")) {
            errors.add("Domain is required");
        }

        return errors;
    }

    @Override
    public TestResult testConnection(JsonNode config, Map<String, String> secrets) {
        long startTime = System.currentTimeMillis();

        try {
            String baseUrl = config.get("endpointBaseUrl").asText();
            String testPath = config.has("testPath") ? config.get("testPath").asText() : "/api/v1/health";
            int timeoutMs = config.has("timeoutMs") ? config.get("timeoutMs").asInt() : 30000;
            String authType = secrets.getOrDefault("authType", "BASIC");

            // SSRF validation
            SsrfValidator.ValidationResult ssrfResult = ssrfValidator.validate(baseUrl);
            if (!ssrfResult.valid()) {
                return TestResult.failure("SSRF blocked: " + ssrfResult.error());
            }

            // Build WebClient
            WebClient webClient = webClientBuilder
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            // Build request with auth
            WebClient.RequestHeadersSpec<?> request = webClient.get()
                    .uri(testPath)
                    .headers(headers -> addAuthHeaders(headers, authType, secrets));

            // Execute with timeout
            String response = request
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .onErrorResume(WebClientResponseException.class, e -> {
                        log.warn("BMC test failed with status {}: {}", e.getStatusCode(), e.getMessage());
                        return Mono.error(e);
                    })
                    .block();

            long latency = System.currentTimeMillis() - startTime;
            log.info("BMC connection test successful, latency: {}ms", latency);
            return TestResult.success(200, latency, "Connection successful");

        } catch (WebClientResponseException e) {
            long latency = System.currentTimeMillis() - startTime;
            log.warn("BMC connection test failed: {} {}", e.getStatusCode(), e.getMessage());
            return TestResult.failure(e.getStatusCode().value(), latency,
                    "HTTP " + e.getStatusCode().value() + ": " + e.getStatusText(), null);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("BMC connection test error", e);
            return TestResult.failure(0, latency, "Connection error: " + e.getMessage(), null);
        }
    }

    @Override
    public CollectResult collect(JsonNode config, Map<String, String> secrets, int windowMinutes) {
        long startTime = System.currentTimeMillis();

        try {
            String baseUrl = config.get("endpointBaseUrl").asText();
            String alertsPath = config.has("alertsPath") ? config.get("alertsPath").asText() : "/api/arsys/v1/entry/HPD:IncidentInterface";
            int timeoutMs = config.has("timeoutMs") ? config.get("timeoutMs").asInt() : 60000;
            int maxRecords = config.has("maxRecords") ? config.get("maxRecords").asInt() : 50000;
            String authType = secrets.getOrDefault("authType", "BASIC");

            // SSRF validation
            SsrfValidator.ValidationResult ssrfResult = ssrfValidator.validate(baseUrl);
            if (!ssrfResult.valid()) {
                return CollectResult.failure("SSRF blocked: " + ssrfResult.error(), 1, 0);
            }

            // Build WebClient
            WebClient webClient = webClientBuilder
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();

            // Build request - query for incidents modified in window
            // Note: Actual BMC API query syntax varies by version
            String query = String.format("?q='Last Modified Date' > \"%s\"&limit=%d",
                    java.time.Instant.now().minusSeconds(windowMinutes * 60L).toString(),
                    maxRecords);

            WebClient.RequestHeadersSpec<?> request = webClient.get()
                    .uri(alertsPath + query)
                    .headers(headers -> addAuthHeaders(headers, authType, secrets));

            // Execute with timeout
            String response = request
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .block();

            long duration = System.currentTimeMillis() - startTime;

            // Parse response and count records
            JsonNode responseJson = objectMapper.readTree(response);
            int recordCount = 0;
            if (responseJson.has("entries")) {
                recordCount = responseJson.get("entries").size();
            }

            log.info("BMC collect completed: {} records in {}ms", recordCount, duration);
            return CollectResult.success(recordCount, recordCount, 0, duration, null);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("BMC collect error", e);
            return CollectResult.failure("Collection error: " + e.getMessage(), 1, duration);
        }
    }

    private void addAuthHeaders(HttpHeaders headers, String authType, Map<String, String> secrets) {
        switch (authType.toUpperCase()) {
            case "BASIC":
                String username = secrets.get("username");
                String password = secrets.get("password");
                if (username != null && password != null) {
                    String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
                    headers.set("Authorization", "Basic " + auth);
                }
                break;
            case "APIKEY":
                String apiKey = secrets.get("apiKey");
                if (apiKey != null) {
                    headers.set("Authorization", "AR-JWT " + apiKey);
                }
                break;
            case "BEARER":
                String token = secrets.get("token");
                if (token != null) {
                    headers.set("Authorization", "Bearer " + token);
                }
                break;
            // OAuth2 would require token exchange first
        }
    }

    private boolean hasField(JsonNode node, String fieldName) {
        return node != null && node.has(fieldName) && !node.get(fieldName).isNull()
                && !node.get(fieldName).asText().isEmpty();
    }
}
