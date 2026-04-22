package org.aiopsanalysis.domain.postgres;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA Entity for incident.incident_evidence table.
 * Stores evidence artifacts linked to incidents - pointers to SharePoint or inline data.
 */
@Entity
@Table(name = "incident_evidence", schema = "incident",
    indexes = {
        @Index(name = "idx_incident_evidence_incident", columnList = "tenant_id, incident_id, created_at DESC")
    }
)
public class IncidentEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "evidence_id")
    private UUID evidenceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    /**
     * Type of evidence: CSV, RAW_SNIPPET, CANONICAL_PTR, SCREENSHOT, etc.
     */
    @Column(name = "type", nullable = false)
    private String type;

    /**
     * SharePoint URL for external evidence storage.
     */
    @Column(name = "sharepoint_url")
    private String sharepointUrl;

    /**
     * Inline payload for small snippets/metadata.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", insertable = false, updatable = false)
    private Incident incident;

    // Constructors
    public IncidentEvidence() {}

    public IncidentEvidence(UUID tenantId, UUID incidentId, String type) {
        this.tenantId = tenantId;
        this.incidentId = incidentId;
        this.type = type;
    }

    public IncidentEvidence(UUID tenantId, UUID incidentId, String type, String sharepointUrl) {
        this.tenantId = tenantId;
        this.incidentId = incidentId;
        this.type = type;
        this.sharepointUrl = sharepointUrl;
    }

    // Evidence type constants
    public static final String TYPE_CSV = "CSV";
    public static final String TYPE_RAW_SNIPPET = "RAW_SNIPPET";
    public static final String TYPE_CANONICAL_PTR = "CANONICAL_PTR";
    public static final String TYPE_SCREENSHOT = "SCREENSHOT";
    public static final String TYPE_LOG_EXCERPT = "LOG_EXCERPT";
    public static final String TYPE_METRIC_SNAPSHOT = "METRIC_SNAPSHOT";

    // Getters and Setters
    public UUID getEvidenceId() { return evidenceId; }
    public void setEvidenceId(UUID evidenceId) { this.evidenceId = evidenceId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSharepointUrl() { return sharepointUrl; }
    public void setSharepointUrl(String sharepointUrl) { this.sharepointUrl = sharepointUrl; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Incident getIncident() { return incident; }
    public void setIncident(Incident incident) { this.incident = incident; }

    // Builder pattern
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final IncidentEvidence evidence = new IncidentEvidence();

        public Builder tenantId(UUID v) { evidence.tenantId = v; return this; }
        public Builder incidentId(UUID v) { evidence.incidentId = v; return this; }
        public Builder type(String v) { evidence.type = v; return this; }
        public Builder sharepointUrl(String v) { evidence.sharepointUrl = v; return this; }
        public Builder payload(Map<String, Object> v) { evidence.payload = v; return this; }

        public IncidentEvidence build() {
            if (evidence.createdAt == null) evidence.createdAt = Instant.now();
            return evidence;
        }
    }
}
