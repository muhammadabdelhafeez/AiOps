package org.aiopsanalysis.domain.neo4j;

import org.aiopsanalysis.domain.model.IncidentClassification;
import org.aiopsanalysis.domain.model.IncidentStatus;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j node representing an IncidentRef - a mirror of PostgreSQL incident for graph traversal.
 * 
 * This is NOT the system-of-record (PostgreSQL is). This node exists for:
 * - Fast graph traversal when querying incident topology
 * - Linking incidents to AlertGroups in the graph
 * - Generating incident subgraphs for UI visualization
 * 
 * The incident data in PostgreSQL is the authoritative source.
 */
@Node("IncidentRef")
public class IncidentRefNode {

    @Id
    private String id;

    @Property("tenantId")
    private String tenantId;

    @Property("incidentId")
    private String incidentId;

    @Property("appId")
    private String appId;

    @Property("status")
    private IncidentStatus status;

    @Property("classificationLabel")
    private IncidentClassification classificationLabel;

    @Property("title")
    private String title;

    @Property("updatedAt")
    private Long updatedAt;

    /**
     * Relationship to AlertGroups that are part of this incident.
     */
    @Relationship(type = "HAS_GROUP", direction = Relationship.Direction.OUTGOING)
    private List<IncidentGroupRelation> alertGroups = new ArrayList<>();

    /**
     * Relationship to Resources impacted by this incident (optional shortcut).
     */
    @Relationship(type = "IMPACTS_RESOURCE", direction = Relationship.Direction.OUTGOING)
    private List<ResourceNode> impactedResources = new ArrayList<>();

    // Constructors
    public IncidentRefNode() {}

    public IncidentRefNode(String tenantId, String incidentId, String appId) {
        this.id = tenantId + ":" + incidentId;
        this.tenantId = tenantId;
        this.incidentId = incidentId;
        this.appId = appId;
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Generate composite ID from tenantId and incidentId.
     */
    public static String generateId(String tenantId, String incidentId) {
        return tenantId + ":" + incidentId;
    }

    /**
     * Update the timestamp.
     */
    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Check if this incident is active.
     */
    public boolean isActive() {
        return status != null && status.isActive();
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getIncidentId() { return incidentId; }
    public void setIncidentId(String incidentId) { this.incidentId = incidentId; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public IncidentStatus getStatus() { return status; }
    public void setStatus(IncidentStatus status) { this.status = status; }

    public IncidentClassification getClassificationLabel() { return classificationLabel; }
    public void setClassificationLabel(IncidentClassification classificationLabel) { this.classificationLabel = classificationLabel; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Long updatedAt) { this.updatedAt = updatedAt; }

    public List<IncidentGroupRelation> getAlertGroups() { return alertGroups; }
    public void setAlertGroups(List<IncidentGroupRelation> alertGroups) { this.alertGroups = alertGroups; }

    public List<ResourceNode> getImpactedResources() { return impactedResources; }
    public void setImpactedResources(List<ResourceNode> impactedResources) { this.impactedResources = impactedResources; }

    // Builder pattern
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final IncidentRefNode node = new IncidentRefNode();

        public Builder tenantId(String v) { node.tenantId = v; return this; }
        public Builder incidentId(String v) { node.incidentId = v; return this; }
        public Builder appId(String v) { node.appId = v; return this; }
        public Builder status(IncidentStatus v) { node.status = v; return this; }
        public Builder classificationLabel(IncidentClassification v) { node.classificationLabel = v; return this; }
        public Builder title(String v) { node.title = v; return this; }

        public IncidentRefNode build() {
            node.id = IncidentRefNode.generateId(node.tenantId, node.incidentId);
            if (node.updatedAt == null) node.updatedAt = System.currentTimeMillis();
            return node;
        }
    }
}
