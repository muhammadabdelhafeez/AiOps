package org.aiopsanalysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.aiopsanalysis.domain.Connector.ConnectorStatus;
import org.aiopsanalysis.domain.Connector.ConnectorType;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for connector responses - masks sensitive information.
 * Never includes secrets or encrypted data.
 */
public class ConnectorResponse {

    private UUID connectorId;
    private ConnectorType type;
    private String name;
    private Boolean enabled;

    /**
     * Configuration with secrets masked.
     * BMC: {baseUrl, username, timeout} but NOT password
     */
    private Map<String, Object> config;

    private String pluginVersion;
    private LocalDateTime lastTestAt;
    private ConnectorStatus lastTestStatus;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Statistics
    @JsonProperty("hasSecrets")
    private Boolean hasSecrets;

    @JsonProperty("lastTestMessage")
    private String lastTestMessage;

    // Constructors
    public ConnectorResponse() {}

    public ConnectorResponse(UUID connectorId, ConnectorType type, String name) {
        this.connectorId = connectorId;
        this.type = type;
        this.name = name;
    }

    // Getters and Setters
    public UUID getConnectorId() { return connectorId; }
    public void setConnectorId(UUID connectorId) { this.connectorId = connectorId; }

    public ConnectorType getType() { return type; }
    public void setType(ConnectorType type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Map<String, Object> getConfig() { return config; }
    public void setConfig(Map<String, Object> config) { this.config = config; }

    public String getPluginVersion() { return pluginVersion; }
    public void setPluginVersion(String pluginVersion) { this.pluginVersion = pluginVersion; }

    public LocalDateTime getLastTestAt() { return lastTestAt; }
    public void setLastTestAt(LocalDateTime lastTestAt) { this.lastTestAt = lastTestAt; }

    public ConnectorStatus getLastTestStatus() { return lastTestStatus; }
    public void setLastTestStatus(ConnectorStatus lastTestStatus) { this.lastTestStatus = lastTestStatus; }

    public LocalDateTime getLastRunAt() { return lastRunAt; }
    public void setLastRunAt(LocalDateTime lastRunAt) { this.lastRunAt = lastRunAt; }

    public LocalDateTime getNextRunAt() { return nextRunAt; }
    public void setNextRunAt(LocalDateTime nextRunAt) { this.nextRunAt = nextRunAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Boolean getHasSecrets() { return hasSecrets; }
    public void setHasSecrets(Boolean hasSecrets) { this.hasSecrets = hasSecrets; }

    public String getLastTestMessage() { return lastTestMessage; }
    public void setLastTestMessage(String lastTestMessage) { this.lastTestMessage = lastTestMessage; }
}
