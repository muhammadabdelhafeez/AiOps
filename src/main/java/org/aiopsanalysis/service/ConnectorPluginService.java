package org.aiopsanalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.aiopsanalysis.config.SsrfValidator;
import org.aiopsanalysis.service.connector.ConnectorPlugin;
import org.aiopsanalysis.service.connector.ConnectorPluginRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing connector plugins and their validations.
 * Provides plugin-specific configuration validation and test operations.
 */
@Service
public class ConnectorPluginService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorPluginService.class);

    private final ConnectorPluginRegistry pluginRegistry;
    private final SsrfValidator ssrfValidator;

    public ConnectorPluginService(ConnectorPluginRegistry pluginRegistry, SsrfValidator ssrfValidator) {
        this.pluginRegistry = pluginRegistry;
        this.ssrfValidator = ssrfValidator;
    }

    /**
     * Validate configuration for a specific connector type.
     *
     * @param type Connector type (e.g., "BMC", "SCOM")
     * @param config Non-secret configuration
     * @param secrets Secret credentials (will be validated but not logged)
     * @throws IllegalArgumentException if validation fails
     */
    public void validateConfiguration(String type, Map<String, Object> config, Map<String, String> secrets) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Connector type is required");
        }

        Optional<ConnectorPlugin> plugin = pluginRegistry.getPlugin(type);
        if (plugin.isEmpty()) {
            throw new IllegalArgumentException("Unknown connector type: " + type);
        }

        // Convert config map to JsonNode for plugin validation
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        JsonNode configNode = mapper.valueToTree(config);

        // Validate using plugin
        List<String> errors = plugin.get().validateConfig(configNode);

        // Additional SSRF validation for URL fields
        if (config.containsKey("baseUrl")) {
            SsrfValidator.ValidationResult ssrfResult = ssrfValidator.validate(config.get("baseUrl").toString());
            if (!ssrfResult.valid()) {
                errors.add("URL validation failed: " + ssrfResult.error());
            }
        }

        if (config.containsKey("endpointBaseUrl")) {
            SsrfValidator.ValidationResult ssrfResult = ssrfValidator.validate(config.get("endpointBaseUrl").toString());
            if (!ssrfResult.valid()) {
                errors.add("Endpoint URL validation failed: " + ssrfResult.error());
            }
        }

        // Validate secrets
        List<String> secretErrors = validateSecrets(type, secrets);
        errors.addAll(secretErrors);

        if (!errors.isEmpty()) {
            log.warn("Configuration validation failed for type {}: {}", type, errors);
            throw new IllegalArgumentException("Configuration validation failed: " + String.join(", ", errors));
        }

        log.debug("Configuration validated successfully for connector type: {}", type);
    }

    /**
     * Validate secrets for a connector type.
     */
    private List<String> validateSecrets(String type, Map<String, String> secrets) {
        List<String> errors = new ArrayList<>();

        if (secrets == null || secrets.isEmpty()) {
            // Some connectors might not require secrets
            return errors;
        }

        // Validate auth type
        String authType = secrets.get("authType");
        if (authType != null) {
            switch (authType.toUpperCase()) {
                case "BASIC":
                    if (!secrets.containsKey("username") || secrets.get("username").isBlank()) {
                        errors.add("Username is required for Basic authentication");
                    }
                    if (!secrets.containsKey("password") || secrets.get("password").isBlank()) {
                        errors.add("Password is required for Basic authentication");
                    }
                    break;
                case "APIKEY":
                    if (!secrets.containsKey("apiKey") || secrets.get("apiKey").isBlank()) {
                        errors.add("API key is required for API Key authentication");
                    }
                    break;
                case "OAUTH2":
                    if (!secrets.containsKey("clientId") || secrets.get("clientId").isBlank()) {
                        errors.add("Client ID is required for OAuth2 authentication");
                    }
                    if (!secrets.containsKey("clientSecret") || secrets.get("clientSecret").isBlank()) {
                        errors.add("Client secret is required for OAuth2 authentication");
                    }
                    break;
                case "BEARER":
                    if (!secrets.containsKey("token") || secrets.get("token").isBlank()) {
                        errors.add("Token is required for Bearer authentication");
                    }
                    break;
                default:
                    errors.add("Unknown authentication type: " + authType);
            }
        }

        return errors;
    }

    /**
     * Get all available connector plugins.
     */
    public List<ConnectorPluginRegistry.PluginMetadata> getAvailablePlugins() {
        return pluginRegistry.getPluginMetadata();
    }

    /**
     * Get plugin by type.
     */
    public Optional<ConnectorPlugin> getPlugin(String type) {
        return pluginRegistry.getPlugin(type);
    }

    /**
     * Check if a connector type is supported.
     */
    public boolean isTypeSupported(String type) {
        return pluginRegistry.isSupported(type);
    }
}
