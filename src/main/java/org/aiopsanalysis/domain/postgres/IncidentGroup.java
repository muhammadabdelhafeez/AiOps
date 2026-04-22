package org.aiopsanalysis.domain.postgres;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA Entity for incident.incident_groups table.
 * Links PostgreSQL incidents to Neo4j AlertGroups.
 * 
 * This is the bridge between the relational incident lifecycle
 * and the graph-based alert correlation.
 */
@Entity
@Table(name = "incident_groups", schema = "incident")
@IdClass(IncidentGroup.IncidentGroupId.class)
public class IncidentGroup {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Id
    @Column(name = "neo4j_group_id", nullable = false)
    private String neo4jGroupId;

    /**
     * Confidence score for this group's association with the incident.
     */
    @Column(name = "confidence", precision = 3, scale = 2)
    private BigDecimal confidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", insertable = false, updatable = false)
    private Incident incident;

    // Constructors
    public IncidentGroup() {}

    public IncidentGroup(UUID tenantId, UUID incidentId, String neo4jGroupId) {
        this.tenantId = tenantId;
        this.incidentId = incidentId;
        this.neo4jGroupId = neo4jGroupId;
    }

    public IncidentGroup(UUID tenantId, UUID incidentId, String neo4jGroupId, BigDecimal confidence) {
        this.tenantId = tenantId;
        this.incidentId = incidentId;
        this.neo4jGroupId = neo4jGroupId;
        this.confidence = confidence;
    }

    // Getters and Setters
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getIncidentId() { return incidentId; }
    public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }

    public String getNeo4jGroupId() { return neo4jGroupId; }
    public void setNeo4jGroupId(String neo4jGroupId) { this.neo4jGroupId = neo4jGroupId; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public Incident getIncident() { return incident; }
    public void setIncident(Incident incident) { this.incident = incident; }

    // Composite Key class
    public static class IncidentGroupId implements Serializable {
        private UUID tenantId;
        private UUID incidentId;
        private String neo4jGroupId;

        public IncidentGroupId() {}

        public IncidentGroupId(UUID tenantId, UUID incidentId, String neo4jGroupId) {
            this.tenantId = tenantId;
            this.incidentId = incidentId;
            this.neo4jGroupId = neo4jGroupId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IncidentGroupId that = (IncidentGroupId) o;
            return Objects.equals(tenantId, that.tenantId) &&
                   Objects.equals(incidentId, that.incidentId) &&
                   Objects.equals(neo4jGroupId, that.neo4jGroupId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, incidentId, neo4jGroupId);
        }

        // Getters and Setters for composite key
        public UUID getTenantId() { return tenantId; }
        public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

        public UUID getIncidentId() { return incidentId; }
        public void setIncidentId(UUID incidentId) { this.incidentId = incidentId; }

        public String getNeo4jGroupId() { return neo4jGroupId; }
        public void setNeo4jGroupId(String neo4jGroupId) { this.neo4jGroupId = neo4jGroupId; }
    }
}
