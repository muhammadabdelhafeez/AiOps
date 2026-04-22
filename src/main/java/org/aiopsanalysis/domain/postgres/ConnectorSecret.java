package org.aiopsanalysis.domain.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for config.connector_secrets table.
 * Stores encrypted secrets for connectors (API keys, passwords, tokens, etc.).
 *
 * SECURITY: Secrets are encrypted at rest using AES-256-GCM.
 * Never log or return plaintext secrets to clients.
 */
@Entity
@Table(name = "connector_secrets", schema = "config")
public class ConnectorSecret {

    @Id
    @Column(name = "connector_id")
    private UUID connectorId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Encrypted secrets stored as JSONB.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "secret_enc", nullable = false, columnDefinition = "jsonb")
    private JsonNode secretEnc;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // Default constructor
    public ConnectorSecret() {}

    // All-args constructor
    public ConnectorSecret(UUID connectorId, UUID tenantId, JsonNode secretEnc, Instant updatedAt) {
        this.connectorId = connectorId;
        this.tenantId = tenantId;
        this.secretEnc = secretEnc;
        this.updatedAt = updatedAt;
    }

    // Getters
    public UUID getConnectorId() { return connectorId; }
    public UUID getTenantId() { return tenantId; }
    public JsonNode getSecretEnc() { return secretEnc; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters
    public void setConnectorId(UUID connectorId) { this.connectorId = connectorId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setSecretEnc(JsonNode secretEnc) { this.secretEnc = secretEnc; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void updateSecrets(JsonNode newSecretEnc) {
        this.secretEnc = newSecretEnc;
        this.updatedAt = Instant.now();
    }

    // Builder
    public static ConnectorSecretBuilder builder() {
        return new ConnectorSecretBuilder();
    }

    public static class ConnectorSecretBuilder {
        private UUID connectorId;
        private UUID tenantId;
        private JsonNode secretEnc;
        private Instant updatedAt = Instant.now();

        public ConnectorSecretBuilder connectorId(UUID connectorId) { this.connectorId = connectorId; return this; }
        public ConnectorSecretBuilder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public ConnectorSecretBuilder secretEnc(JsonNode secretEnc) { this.secretEnc = secretEnc; return this; }
        public ConnectorSecretBuilder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public ConnectorSecret build() {
            return new ConnectorSecret(connectorId, tenantId, secretEnc, updatedAt);
        }
    }
}
