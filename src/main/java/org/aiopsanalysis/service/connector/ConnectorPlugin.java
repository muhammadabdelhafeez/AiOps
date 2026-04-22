package org.aiopsanalysis.service.connector;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * Interface for connector plugins.
 * Each connector type (BMC, SCOM, SolarWinds, etc.) implements this interface.
 */
public interface ConnectorPlugin {

    /**
     * Get the connector type identifier.
     */
    String getType();

    /**
     * Get display name for UI.
     */
    String getDisplayName();

    /**
     * Get plugin description.
     */
    String getDescription();

    /**
     * Get plugin version.
     */
    String getVersion();

    /**
     * Get the configuration schema for this connector type.
     * Used by UI to render configuration forms.
     */
    ConfigSchema getConfigSchema();

    /**
     * Validate configuration.
     *
     * @param config The configuration to validate
     * @return List of validation errors (empty if valid)
     */
    List<String> validateConfig(JsonNode config);

    /**
     * Test connection to the data source.
     *
     * @param config Non-secret configuration
     * @param secrets Decrypted secrets
     * @return Test result
     */
    TestResult testConnection(JsonNode config, Map<String, String> secrets);

    /**
     * Collect alerts from the data source.
     *
     * @param config Non-secret configuration
     * @param secrets Decrypted secrets
     * @param windowMinutes Time window to collect
     * @return Collection result
     */
    CollectResult collect(JsonNode config, Map<String, String> secrets, int windowMinutes);

    /**
     * Configuration schema for UI form generation.
     */
    record ConfigSchema(
            List<FieldDefinition> configFields,
            List<FieldDefinition> secretFields
    ) {}

    /**
     * Field definition for configuration forms.
     */
    record FieldDefinition(
            String name,
            String label,
            String type,        // text, password, number, select, checkbox, textarea
            boolean required,
            Object defaultValue,
            String placeholder,
            String helpText,
            List<SelectOption> options,  // For select type
            Integer min,                  // For number type
            Integer max                   // For number type
    ) {}

    /**
     * Option for select fields.
     */
    record SelectOption(String value, String label) {}

    /**
     * Test connection result.
     */
    record TestResult(
            boolean success,
            int httpStatus,
            long latencyMs,
            String message,
            JsonNode details
    ) {
        public static TestResult success(int httpStatus, long latencyMs, String message) {
            return new TestResult(true, httpStatus, latencyMs, message, null);
        }

        public static TestResult failure(int httpStatus, long latencyMs, String message, JsonNode details) {
            return new TestResult(false, httpStatus, latencyMs, message, details);
        }

        public static TestResult failure(String message) {
            return new TestResult(false, 0, 0, message, null);
        }
    }

    /**
     * Collection result.
     */
    record CollectResult(
            boolean success,
            int alertsPulled,
            int alertsNormalized,
            int errors,
            long durationMs,
            String message,
            JsonNode rawData,
            String artifactUrl
    ) {
        public static CollectResult success(int pulled, int normalized, int errors, long durationMs, String artifactUrl) {
            return new CollectResult(true, pulled, normalized, errors, durationMs,
                    "Collected " + pulled + " alerts", null, artifactUrl);
        }

        public static CollectResult failure(String message, int errors, long durationMs) {
            return new CollectResult(false, 0, 0, errors, durationMs, message, null, null);
        }
    }
}
