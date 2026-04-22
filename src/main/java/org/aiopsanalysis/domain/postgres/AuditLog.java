package org.aiopsanalysis.domain.postgres;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA Entity for identity.audit_log table.
 * Records all write operations for security and compliance.
 */
@Entity
@Table(name = "audit_log", schema = "identity")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "audit_id")
    private UUID auditId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "at", nullable = false)
    private Instant at = Instant.now();

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private JsonNode details;

    // Default constructor
    public AuditLog() {}

    // All-args constructor
    public AuditLog(UUID auditId, UUID tenantId, Instant at, UUID actorUserId,
                    String action, String entityType, String entityId, JsonNode details) {
        this.auditId = auditId;
        this.tenantId = tenantId;
        this.at = at;
        this.actorUserId = actorUserId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
    }

    // Getters
    public UUID getAuditId() { return auditId; }
    public UUID getTenantId() { return tenantId; }
    public Instant getAt() { return at; }
    public UUID getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public JsonNode getDetails() { return details; }

    // Setters
    public void setAuditId(UUID auditId) { this.auditId = auditId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setAt(Instant at) { this.at = at; }
    public void setActorUserId(UUID actorUserId) { this.actorUserId = actorUserId; }
    public void setAction(String action) { this.action = action; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public void setDetails(JsonNode details) { this.details = details; }

    /**
     * Factory method for creating audit log for connector actions.
     */
    public static AuditLog forConnector(UUID tenantId, UUID userId, String action, UUID connectorId, JsonNode details) {
        return AuditLog.builder()
                .tenantId(tenantId)
                .actorUserId(userId)
                .action(action)
                .entityType("CONNECTOR")
                .entityId(connectorId.toString())
                .details(details)
                .at(Instant.now())
                .build();
    }

    /**
     * Factory method for creating audit log for secret reveal.
     */
    public static AuditLog forSecretReveal(UUID tenantId, UUID userId, UUID connectorId, String field, String reason) {
        return AuditLog.builder()
                .tenantId(tenantId)
                .actorUserId(userId)
                .action("SECRET_REVEALED")
                .entityType("CONNECTOR")
                .entityId(connectorId.toString())
                .at(Instant.now())
                .build();
    }

    // Builder
    public static AuditLogBuilder builder() {
        return new AuditLogBuilder();
    }

    public static class AuditLogBuilder {
        private UUID auditId;
        private UUID tenantId;
        private Instant at = Instant.now();
        private UUID actorUserId;
        private String action;
        private String entityType;
        private String entityId;
        private JsonNode details;

        public AuditLogBuilder auditId(UUID auditId) { this.auditId = auditId; return this; }
        public AuditLogBuilder tenantId(UUID tenantId) { this.tenantId = tenantId; return this; }
        public AuditLogBuilder at(Instant at) { this.at = at; return this; }
        public AuditLogBuilder actorUserId(UUID actorUserId) { this.actorUserId = actorUserId; return this; }
        public AuditLogBuilder action(String action) { this.action = action; return this; }
        public AuditLogBuilder entityType(String entityType) { this.entityType = entityType; return this; }
        public AuditLogBuilder entityId(String entityId) { this.entityId = entityId; return this; }
        public AuditLogBuilder details(JsonNode details) { this.details = details; return this; }

        public AuditLog build() {
            return new AuditLog(auditId, tenantId, at, actorUserId, action, entityType, entityId, details);
        }
    }
}
