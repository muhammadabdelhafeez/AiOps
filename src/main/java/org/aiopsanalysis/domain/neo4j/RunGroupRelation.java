package org.aiopsanalysis.domain.neo4j;

import org.aiopsanalysis.domain.model.Classification;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

/**
 * Relationship properties for HAS_GROUP edges between Run and AlertGroup.
 */
@RelationshipProperties
public class RunGroupRelation {

    @RelationshipId
    private Long id;

    @TargetNode
    private AlertGroupNode alertGroup;

    @Property("classification")
    private Classification classification;

    @Property("priority")
    private Double priority;

    @Property("scoreBreakdown")
    private String scoreBreakdown;

    @Property("reasonedByPro")
    private Boolean reasonedByPro;

    @Property("newOccurrencesCount")
    private Integer newOccurrencesCount;

    public RunGroupRelation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AlertGroupNode getAlertGroup() { return alertGroup; }
    public void setAlertGroup(AlertGroupNode alertGroup) { this.alertGroup = alertGroup; }

    public Classification getClassification() { return classification; }
    public void setClassification(Classification classification) { this.classification = classification; }

    public Double getPriority() { return priority; }
    public void setPriority(Double priority) { this.priority = priority; }

    public String getScoreBreakdown() { return scoreBreakdown; }
    public void setScoreBreakdown(String scoreBreakdown) { this.scoreBreakdown = scoreBreakdown; }

    public Boolean getReasonedByPro() { return reasonedByPro; }
    public void setReasonedByPro(Boolean reasonedByPro) { this.reasonedByPro = reasonedByPro; }

    public Integer getNewOccurrencesCount() { return newOccurrencesCount; }
    public void setNewOccurrencesCount(Integer newOccurrencesCount) { this.newOccurrencesCount = newOccurrencesCount; }

    public boolean isNew() { return classification == Classification.NEW; }

    public boolean isRecurringSure() {
        return classification == Classification.RECURRING_SURE || classification == Classification.RECURRING_LIKELY;
    }
}
