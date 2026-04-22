package org.aiopsanalysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.aiopsanalysis.domain.Connector.ConnectorType;

import java.util.Map;
import java.util.UUID;

/**
 * DTO for testing a connector connection.
 * Used for both existing connectors and new configurations.
 */
public class ConnectorTestRequest {

    @NotNull(message = "Connector type is required")
    private ConnectorType type;

    /**
     * Optional: existing connector ID to test.
     * If provided, will use stored configuration and secrets.
     */
    @JsonProperty("connectorId")
    private UUID connectorId;

    /**
     * Optional: configuration override for testing.
     * If connectorId is provided, this overrides stored config.
     */
    private Map<String, Object> config;

    /**
     * Optional: secrets override for testing.
     * If connectorId is provided, this overrides stored secrets.
     */
    private Map<String, String> secrets;

    /**
     * Test timeout in seconds (default: 30)
     */
    @JsonProperty("timeoutSeconds")
    private Integer timeoutSeconds = 30;

    // Constructors
    public ConnectorTestRequest() {}

    public ConnectorTestRequest(ConnectorType type, Map<String, Object> config, Map<String, String> secrets) {
        this.type = type;
        this.config = config;
        this.secrets = secrets;
    }

    // Getters and Setters
    public ConnectorType getType() { return type; }
    public void setType(ConnectorType type) { this.type = type; }

    public UUID getConnectorId() { return connectorId; }
    public void setConnectorId(UUID connectorId) { this.connectorId = connectorId; }

    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }

    public Map<String, String> getSecrets() { return secrets; }
    public void setSecrets(Map<String, String> secrets) { this.secrets = secrets; }

    public Integer getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Integer timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
