package org.aiopsanalysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.aiopsanalysis.domain.Connector.ConnectorType;

import java.util.Map;

/**
 * DTO for creating a new connector with validation.
 * Enforces OWASP input validation and BMC-specific configuration rules.
 */
public class ConnectorCreateRequest {

    @NotNull(message = "Connector type is required")
    private ConnectorType type;

    @NotBlank(message = "Connector name is required")
    @Size(min = 1, max = 100, message = "Connector name must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_\\-\\s]+$", message = "Connector name contains invalid characters")
    private String name;

    @NotNull(message = "Configuration is required")
    private Map<String, Object> config;

    /**
     * Secrets map - will be encrypted before storage.
     * For BMC: password, apiKey, clientSecret, etc.
     */
    private Map<String, String> secrets;

    @JsonProperty("enabled")
    private Boolean enabled = true;

    // Constructors
    public ConnectorCreateRequest() {}

    public ConnectorCreateRequest(ConnectorType type, String name, Map<String, Object> config) {
        this.type = type;
        this.name = name;
        this.config = config;
    }

    // Getters and Setters
    public ConnectorType getType() { return type; }
    public void setType(ConnectorType type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }

    public Map<String, String> getSecrets() { return secrets; }
    public void setSecrets(Map<String, String> secrets) { this.secrets = secrets; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    /**
     * BMC-specific validation for configuration and secrets.
     */
    public void validateBMCConfiguration() {
        if (type == ConnectorType.BMC) {
            if (config == null) {
                throw new IllegalArgumentException("BMC configuration is required");
            }

            String baseUrl = (String) config.get("baseUrl");
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("BMC baseUrl is required");
            }

            // SSRF Protection: Validate URL format and restrict domains
            if (!isValidBMCUrl(baseUrl)) {
                throw new IllegalArgumentException("Invalid BMC URL format or blocked domain");
            }

            String username = (String) config.get("username");
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("BMC username is required");
            }

            // Validate secrets contain required fields
            if (secrets == null || secrets.isEmpty()) {
                throw new IllegalArgumentException("BMC secrets are required");
            }

            if (!secrets.containsKey("password") || secrets.get("password").trim().isEmpty()) {
                throw new IllegalArgumentException("BMC password is required");
            }
        }
    }

    /**
     * SSRF Protection: Validate BMC URLs against allowed patterns.
     */
    private boolean isValidBMCUrl(String url) {
        try {
            java.net.URL parsedUrl = new java.net.URL(url);

            // Must be HTTPS
            if (!"https".equals(parsedUrl.getProtocol())) {
                return false;
            }

            String host = parsedUrl.getHost().toLowerCase();

            // Block localhost, internal IPs, metadata endpoints
            if (host.equals("localhost") || host.equals("127.0.0.1") ||
                host.startsWith("192.168.") || host.startsWith("10.") ||
                host.startsWith("172.") || host.equals("169.254.169.254")) {
                return false;
            }

            // Allow only corporate BMC domains (customize as needed)
            return host.endsWith(".kfh.com") || host.endsWith(".remedyitsm.com") ||
                   host.matches("^bmc-prod-[a-z0-9\\-]+\\.kfh\\.com$");

        } catch (Exception e) {
            return false;
        }
    }
}
