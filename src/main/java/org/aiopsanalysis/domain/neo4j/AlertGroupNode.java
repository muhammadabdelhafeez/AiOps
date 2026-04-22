package org.aiopsanalysis.domain.neo4j;

import org.aiopsanalysis.domain.model.AlertStatus;
import org.aiopsanalysis.domain.model.Classification;
import org.aiopsanalysis.domain.model.Severity;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j node representing an AlertGroup - a recurring "incident pattern" bucket.
 */
@Node("AlertGroup")
public class AlertGroupNode {

    @Id
    private String id;

    @Property("fingerprintExact")
    private String fingerprintExact;

    @Property("fingerprintFamily")
    private String fingerprintFamily;

    @Property("alertTitle")
    private String alertTitle;

    @Property("source")
    private String source;

    @Property("severityMax")
    private Severity severityMax;

    @Property("status")
    private AlertStatus status;

    @Property("classification")
    private Classification classification;

    @Property("firstSeenEpoch")
    private Long firstSeenEpoch;

    @Property("lastSeenEpoch")
    private Long lastSeenEpoch;

    @Property("count15d")
    private Integer count15d;

    @Property("countTotal")
    private Integer countTotal;

    @Property("priorityScore")
    private Double priorityScore;

    @Property("embedding")
    private float[] embedding;

    @Property("embeddingModel")
    private String embeddingModel;

    @Property("embeddingDim")
    private Integer embeddingDim;

    @Property("signatureText")
    private String signatureText;

    @Property("lastRunId")
    private String lastRunId;

    @Property("lastSummary")
    private String lastSummary;

    @Property("lastSummaryEpoch")
    private Long lastSummaryEpoch;

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    private List<AlertGroupRelation> relatedGroups = new ArrayList<>();

    public AlertGroupNode() {}

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFingerprintExact() { return fingerprintExact; }
    public void setFingerprintExact(String fingerprintExact) { this.fingerprintExact = fingerprintExact; }

    public String getFingerprintFamily() { return fingerprintFamily; }
    public void setFingerprintFamily(String fingerprintFamily) { this.fingerprintFamily = fingerprintFamily; }

    public String getAlertTitle() { return alertTitle; }
    public void setAlertTitle(String alertTitle) { this.alertTitle = alertTitle; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Severity getSeverityMax() { return severityMax; }
    public void setSeverityMax(Severity severityMax) { this.severityMax = severityMax; }

    public AlertStatus getStatus() { return status; }
    public void setStatus(AlertStatus status) { this.status = status; }

    public Classification getClassification() { return classification; }
    public void setClassification(Classification classification) { this.classification = classification; }

    public Long getFirstSeenEpoch() { return firstSeenEpoch; }
    public void setFirstSeenEpoch(Long firstSeenEpoch) { this.firstSeenEpoch = firstSeenEpoch; }

    public Long getLastSeenEpoch() { return lastSeenEpoch; }
    public void setLastSeenEpoch(Long lastSeenEpoch) { this.lastSeenEpoch = lastSeenEpoch; }

    public Integer getCount15d() { return count15d; }
    public void setCount15d(Integer count15d) { this.count15d = count15d; }

    public Integer getCountTotal() { return countTotal; }
    public void setCountTotal(Integer countTotal) { this.countTotal = countTotal; }

    public Double getPriorityScore() { return priorityScore; }
    public void setPriorityScore(Double priorityScore) { this.priorityScore = priorityScore; }

    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }

    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }

    public Integer getEmbeddingDim() { return embeddingDim; }
    public void setEmbeddingDim(Integer embeddingDim) { this.embeddingDim = embeddingDim; }

    public String getSignatureText() { return signatureText; }
    public void setSignatureText(String signatureText) { this.signatureText = signatureText; }

    public String getLastRunId() { return lastRunId; }
    public void setLastRunId(String lastRunId) { this.lastRunId = lastRunId; }

    public String getLastSummary() { return lastSummary; }
    public void setLastSummary(String lastSummary) { this.lastSummary = lastSummary; }

    public Long getLastSummaryEpoch() { return lastSummaryEpoch; }
    public void setLastSummaryEpoch(Long lastSummaryEpoch) { this.lastSummaryEpoch = lastSummaryEpoch; }

    public List<AlertGroupRelation> getRelatedGroups() { return relatedGroups; }
    public void setRelatedGroups(List<AlertGroupRelation> relatedGroups) { this.relatedGroups = relatedGroups; }

    public boolean hasEmbedding() {
        return embedding != null && embedding.length > 0;
    }

    public boolean needsReprocessing(long currentCount, Severity currentMaxSeverity) {
        if (lastSummary == null || lastSummary.isBlank()) return true;
        if (count15d != null && currentCount > count15d * 1.2) return true;
        if (severityMax != null && currentMaxSeverity != null 
            && currentMaxSeverity.getWeight() > severityMax.getWeight()) return true;
        return false;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final AlertGroupNode node = new AlertGroupNode();
        public Builder id(String id) { node.id = id; return this; }
        public Builder fingerprintExact(String v) { node.fingerprintExact = v; return this; }
        public Builder fingerprintFamily(String v) { node.fingerprintFamily = v; return this; }
        public Builder alertTitle(String v) { node.alertTitle = v; return this; }
        public Builder source(String v) { node.source = v; return this; }
        public Builder severityMax(Severity v) { node.severityMax = v; return this; }
        public Builder status(AlertStatus v) { node.status = v; return this; }
        public Builder classification(Classification v) { node.classification = v; return this; }
        public Builder firstSeenEpoch(Long v) { node.firstSeenEpoch = v; return this; }
        public Builder lastSeenEpoch(Long v) { node.lastSeenEpoch = v; return this; }
        public Builder count15d(Integer v) { node.count15d = v; return this; }
        public Builder countTotal(Integer v) { node.countTotal = v; return this; }
        public Builder priorityScore(Double v) { node.priorityScore = v; return this; }
        public Builder embedding(float[] v) { node.embedding = v; return this; }
        public Builder embeddingModel(String v) { node.embeddingModel = v; return this; }
        public Builder embeddingDim(Integer v) { node.embeddingDim = v; return this; }
        public Builder signatureText(String v) { node.signatureText = v; return this; }
        public Builder lastRunId(String v) { node.lastRunId = v; return this; }
        public Builder lastSummary(String v) { node.lastSummary = v; return this; }
        public Builder lastSummaryEpoch(Long v) { node.lastSummaryEpoch = v; return this; }
        public AlertGroupNode build() { return node; }
    }
}
