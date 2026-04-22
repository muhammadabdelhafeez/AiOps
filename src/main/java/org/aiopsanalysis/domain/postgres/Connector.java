package org.aiopsanalysis.domain.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for config.connectors table.
 * Stores connector configuration (non-secret) for data sources.
 */
@Entity
@Table(name = "connectors", schema = "config")
public class Connector {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "connector_id")
    private UUID connectorId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    /**
     * Non-secret configuration stored as JSONB.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private JsonNode config;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // Default constructor
    public Connector() {}

    // All-args constructor
    public Connector(UUID connectorId, UUID tenantId, String type, String name,
                     Boolean enabled, JsonNode config, Instant createdAt, Instant updatedAt) {
        this.connectorId = connectorId;
        this.tenantId = tenantId;
        this.type = type;
        this.name = name;
        this.enabled = enabled;
        this.config = config;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public UUID getConnectorId() { return connectorId; }
    public UUID getTenantId() { return tenantId; }
    public String getType() { return type; }
    public String getName() { return name; }
    public Boolean getEnabled() { return enabled; }
    public JsonNode getConfig() { return config; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters
    public void setConnectorId(UUID connectorId) { this.connectorId = connectorId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setType(String type) { this.type = type; }
    public void setName(String name) { this.name = name; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public void setConfig(JsonNode config) { this.config = config; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void enable() {
        this.enabled = true;
        this.updatedAt = Instant.now();
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    // Builder pattern
    public static ConnectorBuilder builder() {
        return new ConnectorBuilder();
    }

    public static class ConnectorBuilder {
        private UUID connectorId;
        private UUID tenantId;
        private String type;
        private String name;
        private Boolean enabled = true;
        private JsonNode config;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();

        public ConnectorBuilder connectorId(UUID connectorId) { this.connectorId = connectorId; return this; }
        public ConnectorBuilder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public ConnectorBuilder type(String type) { this.type = type; return this; }
        public ConnectorBuilder name(String name) { this.name = name; return this; }
        public ConnectorBuilder enabled(Boolean enabled) { this.enabled = enabled; return this; }
        public ConnectorBuilder config(JsonNode config) { this.config = config; return this; }
        public ConnectorBuilder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public ConnectorBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Connector build() {
            return new Connector(connectorId, tenantId, type, name, enabled, config, createdAt, updatedAt);
        }
    }
}
