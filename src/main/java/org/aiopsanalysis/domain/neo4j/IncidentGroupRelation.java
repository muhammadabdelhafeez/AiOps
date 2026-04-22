package org.aiopsanalysis.domain.neo4j;

import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

/**
 * Neo4j relationship properties for HAS_GROUP relationship.
 * Links IncidentRef to AlertGroup nodes with confidence score.
 * 
 * This relationship indicates which AlertGroups are part of an incident
 * and the confidence level of that association.
 */
@RelationshipProperties
public class IncidentGroupRelation {

    @RelationshipId
    private Long id;

    @TargetNode
    private AlertGroupNode alertGroup;

    /**
     * Confidence score for this group being part of the incident (0.0-1.0).
     */
    @Property("confidence")
    private Double confidence;

    /**
     * Whether this group is a primary driver of the incident.
     */
    @Property("isPrimary")
    private Boolean isPrimary;

    /**
     * Timestamp when this relationship was created/updated.
     */
    @Property("createdAt")
    private Long createdAt;

    // Constructors
    public IncidentGroupRelation() {}

    public IncidentGroupRelation(AlertGroupNode alertGroup) {
        this.alertGroup = alertGroup;
        this.createdAt = System.currentTimeMillis();
    }

    public IncidentGroupRelation(AlertGroupNode alertGroup, Double confidence) {
        this.alertGroup = alertGroup;
        this.confidence = confidence;
        this.createdAt = System.currentTimeMillis();
    }

    public IncidentGroupRelation(AlertGroupNode alertGroup, Double confidence, Boolean isPrimary) {
        this.alertGroup = alertGroup;
        this.confidence = confidence;
        this.isPrimary = isPrimary;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * Check if this is a high-confidence association.
     */
    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.8;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AlertGroupNode getAlertGroup() { return alertGroup; }
    public void setAlertGroup(AlertGroupNode alertGroup) { this.alertGroup = alertGroup; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public Boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(Boolean isPrimary) { this.isPrimary = isPrimary; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
