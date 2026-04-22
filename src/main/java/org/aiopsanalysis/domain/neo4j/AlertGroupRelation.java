package org.aiopsanalysis.domain.neo4j;

import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

/**
 * Relationship properties for RELATED_TO edges between AlertGroup nodes.
 */
@RelationshipProperties
public class AlertGroupRelation {

    @RelationshipId
    private Long id;

    @TargetNode
    private AlertGroupNode targetGroup;

    @Property("score")
    private Double score;

    @Property("method")
    private String method;

    @Property("evidence")
    private String evidence;

    @Property("createdEpoch")
    private Long createdEpoch;

    @Property("runId")
    private String runId;

    public AlertGroupRelation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AlertGroupNode getTargetGroup() { return targetGroup; }
    public void setTargetGroup(AlertGroupNode targetGroup) { this.targetGroup = targetGroup; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getEvidence() { return evidence; }
    public void setEvidence(String evidence) { this.evidence = evidence; }

    public Long getCreatedEpoch() { return createdEpoch; }
    public void setCreatedEpoch(Long createdEpoch) { this.createdEpoch = createdEpoch; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public boolean isHighConfidence() { return score != null && score >= 0.8; }
    public boolean isDeterministic() { return "EXACT".equals(method) || "FAMILY".equals(method) || "COOCCUR".equals(method); }
}
