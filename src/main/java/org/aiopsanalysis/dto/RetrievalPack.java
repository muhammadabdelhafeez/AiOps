package org.aiopsanalysis.dto;

import org.aiopsanalysis.domain.model.Classification;
import org.aiopsanalysis.domain.model.Severity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * RetrievalPack - Compact evidence pack for GPT-5.2-Pro reasoning.
 * Contains structured, aggregated evidence rather than raw alerts.
 * This is the key to keeping prompts small while enabling quality reasoning.
 */
public class RetrievalPack {

    private String runId;

    /**
     * Run timestamp.
     */
    private Instant runTimestamp;

    /**
     * Time window in days (typically 15).
     */
    private Integer windowDays;

    /**
     * Total alerts processed in this run.
     */
    private Integer totalAlertsProcessed;

    /**
     * Summary statistics.
     */
    private PackStats stats;

    /**
     * Alert groups to be reasoned (top N by priority).
     */
    private List<GroupEvidence> groups = new ArrayList<>();

    /**
     * Correlation clusters (groups that are related).
     */
    private List<CorrelationCluster> correlations = new ArrayList<>();

    public RetrievalPack() {}

    public RetrievalPack(String runId, Instant runTimestamp, Integer windowDays, Integer totalAlertsProcessed,
                         PackStats stats, List<GroupEvidence> groups, List<CorrelationCluster> correlations) {
        this.runId = runId;
        this.runTimestamp = runTimestamp;
        this.windowDays = windowDays;
        this.totalAlertsProcessed = totalAlertsProcessed;
        this.stats = stats;
        this.groups = groups != null ? groups : new ArrayList<>();
        this.correlations = correlations != null ? correlations : new ArrayList<>();
    }

    // Getters and setters
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public Instant getRunTimestamp() { return runTimestamp; }
    public void setRunTimestamp(Instant runTimestamp) { this.runTimestamp = runTimestamp; }
    public Integer getWindowDays() { return windowDays; }
    public void setWindowDays(Integer windowDays) { this.windowDays = windowDays; }
    public Integer getTotalAlertsProcessed() { return totalAlertsProcessed; }
    public void setTotalAlertsProcessed(Integer totalAlertsProcessed) { this.totalAlertsProcessed = totalAlertsProcessed; }
    public PackStats getStats() { return stats; }
    public void setStats(PackStats stats) { this.stats = stats; }
    public List<GroupEvidence> getGroups() { return groups; }
    public void setGroups(List<GroupEvidence> groups) { this.groups = groups; }
    public List<CorrelationCluster> getCorrelations() { return correlations; }
    public void setCorrelations(List<CorrelationCluster> correlations) { this.correlations = correlations; }

    public static RetrievalPackBuilder builder() { return new RetrievalPackBuilder(); }

    public static class RetrievalPackBuilder {
        private String runId;
        private Instant runTimestamp;
        private Integer windowDays;
        private Integer totalAlertsProcessed;
        private PackStats stats;
        private List<GroupEvidence> groups = new ArrayList<>();
        private List<CorrelationCluster> correlations = new ArrayList<>();

        public RetrievalPackBuilder runId(String runId) { this.runId = runId; return this; }
        public RetrievalPackBuilder runTimestamp(Instant runTimestamp) { this.runTimestamp = runTimestamp; return this; }
        public RetrievalPackBuilder windowDays(Integer windowDays) { this.windowDays = windowDays; return this; }
        public RetrievalPackBuilder totalAlertsProcessed(Integer totalAlertsProcessed) { this.totalAlertsProcessed = totalAlertsProcessed; return this; }
        public RetrievalPackBuilder stats(PackStats stats) { this.stats = stats; return this; }
        public RetrievalPackBuilder groups(List<GroupEvidence> groups) { this.groups = groups; return this; }
        public RetrievalPackBuilder correlations(List<CorrelationCluster> correlations) { this.correlations = correlations; return this; }
        public RetrievalPack build() { return new RetrievalPack(runId, runTimestamp, windowDays, totalAlertsProcessed, stats, groups, correlations); }
    }

    // Nested classes
    public static class PackStats {
        private Integer newGroups;
        private Integer recurringGroups;
        private Integer possibleRecurringGroups;
        private Integer criticalGroups;
        private Integer highGroups;
        private Integer mediumGroups;
        private Integer lowGroups;
        private Integer totalActiveGroups;
        private Integer groupsWithSeveritySpike;
        private Integer groupsWithNewResources;

        public PackStats() {}
        public PackStats(Integer newGroups, Integer recurringGroups, Integer possibleRecurringGroups, Integer criticalGroups,
                         Integer highGroups, Integer mediumGroups, Integer lowGroups, Integer totalActiveGroups,
                         Integer groupsWithSeveritySpike, Integer groupsWithNewResources) {
            this.newGroups = newGroups; this.recurringGroups = recurringGroups; this.possibleRecurringGroups = possibleRecurringGroups;
            this.criticalGroups = criticalGroups; this.highGroups = highGroups; this.mediumGroups = mediumGroups;
            this.lowGroups = lowGroups; this.totalActiveGroups = totalActiveGroups;
            this.groupsWithSeveritySpike = groupsWithSeveritySpike; this.groupsWithNewResources = groupsWithNewResources;
        }
        public Integer getNewGroups() { return newGroups; }
        public void setNewGroups(Integer newGroups) { this.newGroups = newGroups; }
        public Integer getRecurringGroups() { return recurringGroups; }
        public void setRecurringGroups(Integer recurringGroups) { this.recurringGroups = recurringGroups; }
        public Integer getPossibleRecurringGroups() { return possibleRecurringGroups; }
        public void setPossibleRecurringGroups(Integer possibleRecurringGroups) { this.possibleRecurringGroups = possibleRecurringGroups; }
        public Integer getCriticalGroups() { return criticalGroups; }
        public void setCriticalGroups(Integer criticalGroups) { this.criticalGroups = criticalGroups; }
        public Integer getHighGroups() { return highGroups; }
        public void setHighGroups(Integer highGroups) { this.highGroups = highGroups; }
        public Integer getMediumGroups() { return mediumGroups; }
        public void setMediumGroups(Integer mediumGroups) { this.mediumGroups = mediumGroups; }
        public Integer getLowGroups() { return lowGroups; }
        public void setLowGroups(Integer lowGroups) { this.lowGroups = lowGroups; }
        public Integer getTotalActiveGroups() { return totalActiveGroups; }
        public void setTotalActiveGroups(Integer totalActiveGroups) { this.totalActiveGroups = totalActiveGroups; }
        public Integer getGroupsWithSeveritySpike() { return groupsWithSeveritySpike; }
        public void setGroupsWithSeveritySpike(Integer groupsWithSeveritySpike) { this.groupsWithSeveritySpike = groupsWithSeveritySpike; }
        public Integer getGroupsWithNewResources() { return groupsWithNewResources; }
        public void setGroupsWithNewResources(Integer groupsWithNewResources) { this.groupsWithNewResources = groupsWithNewResources; }
        public static PackStatsBuilder builder() { return new PackStatsBuilder(); }
        public static class PackStatsBuilder {
            private Integer newGroups, recurringGroups, possibleRecurringGroups, criticalGroups, highGroups, mediumGroups, lowGroups, totalActiveGroups, groupsWithSeveritySpike, groupsWithNewResources;
            public PackStatsBuilder newGroups(Integer v) { this.newGroups = v; return this; }
            public PackStatsBuilder recurringGroups(Integer v) { this.recurringGroups = v; return this; }
            public PackStatsBuilder possibleRecurringGroups(Integer v) { this.possibleRecurringGroups = v; return this; }
            public PackStatsBuilder criticalGroups(Integer v) { this.criticalGroups = v; return this; }
            public PackStatsBuilder highGroups(Integer v) { this.highGroups = v; return this; }
            public PackStatsBuilder mediumGroups(Integer v) { this.mediumGroups = v; return this; }
            public PackStatsBuilder lowGroups(Integer v) { this.lowGroups = v; return this; }
            public PackStatsBuilder totalActiveGroups(Integer v) { this.totalActiveGroups = v; return this; }
            public PackStatsBuilder groupsWithSeveritySpike(Integer v) { this.groupsWithSeveritySpike = v; return this; }
            public PackStatsBuilder groupsWithNewResources(Integer v) { this.groupsWithNewResources = v; return this; }
            public PackStats build() { return new PackStats(newGroups, recurringGroups, possibleRecurringGroups, criticalGroups, highGroups, mediumGroups, lowGroups, totalActiveGroups, groupsWithSeveritySpike, groupsWithNewResources); }
        }
    }

    public static class GroupEvidence {
        private String groupId, alertTitle, source, classificationReason, previousSummary, deltaDescription;
        private Classification classification;
        private Severity severityMax;
        private Double priorityScore;
        private Instant firstSeen, lastSeen;
        private Integer count15d, countCurrentRun;
        private List<ImpactedResource> impactedResources = new ArrayList<>();
        private List<RelatedGroup> relatedGroups = new ArrayList<>();
        private List<OccurrenceSample> recentOccurrences = new ArrayList<>();

        public GroupEvidence() {}
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        public String getAlertTitle() { return alertTitle; }
        public void setAlertTitle(String alertTitle) { this.alertTitle = alertTitle; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public Classification getClassification() { return classification; }
        public void setClassification(Classification classification) { this.classification = classification; }
        public String getClassificationReason() { return classificationReason; }
        public void setClassificationReason(String classificationReason) { this.classificationReason = classificationReason; }
        public Severity getSeverityMax() { return severityMax; }
        public void setSeverityMax(Severity severityMax) { this.severityMax = severityMax; }
        public Double getPriorityScore() { return priorityScore; }
        public void setPriorityScore(Double priorityScore) { this.priorityScore = priorityScore; }
        public Instant getFirstSeen() { return firstSeen; }
        public void setFirstSeen(Instant firstSeen) { this.firstSeen = firstSeen; }
        public Instant getLastSeen() { return lastSeen; }
        public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
        public Integer getCount15d() { return count15d; }
        public void setCount15d(Integer count15d) { this.count15d = count15d; }
        public Integer getCountCurrentRun() { return countCurrentRun; }
        public void setCountCurrentRun(Integer countCurrentRun) { this.countCurrentRun = countCurrentRun; }
        public List<ImpactedResource> getImpactedResources() { return impactedResources; }
        public void setImpactedResources(List<ImpactedResource> impactedResources) { this.impactedResources = impactedResources; }
        public List<RelatedGroup> getRelatedGroups() { return relatedGroups; }
        public void setRelatedGroups(List<RelatedGroup> relatedGroups) { this.relatedGroups = relatedGroups; }
        public List<OccurrenceSample> getRecentOccurrences() { return recentOccurrences; }
        public void setRecentOccurrences(List<OccurrenceSample> recentOccurrences) { this.recentOccurrences = recentOccurrences; }
        public String getPreviousSummary() { return previousSummary; }
        public void setPreviousSummary(String previousSummary) { this.previousSummary = previousSummary; }
        public String getDeltaDescription() { return deltaDescription; }
        public void setDeltaDescription(String deltaDescription) { this.deltaDescription = deltaDescription; }
        public static GroupEvidenceBuilder builder() { return new GroupEvidenceBuilder(); }
        public static class GroupEvidenceBuilder {
            private String groupId, alertTitle, source, classificationReason, previousSummary, deltaDescription;
            private Classification classification;
            private Severity severityMax;
            private Double priorityScore;
            private Instant firstSeen, lastSeen;
            private Integer count15d, countCurrentRun;
            private List<ImpactedResource> impactedResources = new ArrayList<>();
            private List<RelatedGroup> relatedGroups = new ArrayList<>();
            private List<OccurrenceSample> recentOccurrences = new ArrayList<>();
            public GroupEvidenceBuilder groupId(String v) { this.groupId = v; return this; }
            public GroupEvidenceBuilder alertTitle(String v) { this.alertTitle = v; return this; }
            public GroupEvidenceBuilder source(String v) { this.source = v; return this; }
            public GroupEvidenceBuilder classification(Classification v) { this.classification = v; return this; }
            public GroupEvidenceBuilder classificationReason(String v) { this.classificationReason = v; return this; }
            public GroupEvidenceBuilder severityMax(Severity v) { this.severityMax = v; return this; }
            public GroupEvidenceBuilder priorityScore(Double v) { this.priorityScore = v; return this; }
            public GroupEvidenceBuilder firstSeen(Instant v) { this.firstSeen = v; return this; }
            public GroupEvidenceBuilder lastSeen(Instant v) { this.lastSeen = v; return this; }
            public GroupEvidenceBuilder count15d(Integer v) { this.count15d = v; return this; }
            public GroupEvidenceBuilder countCurrentRun(Integer v) { this.countCurrentRun = v; return this; }
            public GroupEvidenceBuilder impactedResources(List<ImpactedResource> v) { this.impactedResources = v; return this; }
            public GroupEvidenceBuilder relatedGroups(List<RelatedGroup> v) { this.relatedGroups = v; return this; }
            public GroupEvidenceBuilder recentOccurrences(List<OccurrenceSample> v) { this.recentOccurrences = v; return this; }
            public GroupEvidenceBuilder previousSummary(String v) { this.previousSummary = v; return this; }
            public GroupEvidenceBuilder deltaDescription(String v) { this.deltaDescription = v; return this; }
            public GroupEvidence build() { GroupEvidence g = new GroupEvidence(); g.groupId = groupId; g.alertTitle = alertTitle; g.source = source; g.classification = classification; g.classificationReason = classificationReason; g.severityMax = severityMax; g.priorityScore = priorityScore; g.firstSeen = firstSeen; g.lastSeen = lastSeen; g.count15d = count15d; g.countCurrentRun = countCurrentRun; g.impactedResources = impactedResources; g.relatedGroups = relatedGroups; g.recentOccurrences = recentOccurrences; g.previousSummary = previousSummary; g.deltaDescription = deltaDescription; return g; }
        }
    }

    public static class ImpactedResource {
        private String resourceId, resourceName, resourceType;
        private Integer occurrenceCount;
        public ImpactedResource() {}
        public ImpactedResource(String resourceId, String resourceName, String resourceType, Integer occurrenceCount) { this.resourceId = resourceId; this.resourceName = resourceName; this.resourceType = resourceType; this.occurrenceCount = occurrenceCount; }
        public String getResourceId() { return resourceId; }
        public void setResourceId(String resourceId) { this.resourceId = resourceId; }
        public String getResourceName() { return resourceName; }
        public void setResourceName(String resourceName) { this.resourceName = resourceName; }
        public String getResourceType() { return resourceType; }
        public void setResourceType(String resourceType) { this.resourceType = resourceType; }
        public Integer getOccurrenceCount() { return occurrenceCount; }
        public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }
        public static ImpactedResourceBuilder builder() { return new ImpactedResourceBuilder(); }
        public static class ImpactedResourceBuilder {
            private String resourceId, resourceName, resourceType;
            private Integer occurrenceCount;
            public ImpactedResourceBuilder resourceId(String v) { this.resourceId = v; return this; }
            public ImpactedResourceBuilder resourceName(String v) { this.resourceName = v; return this; }
            public ImpactedResourceBuilder resourceType(String v) { this.resourceType = v; return this; }
            public ImpactedResourceBuilder occurrenceCount(Integer v) { this.occurrenceCount = v; return this; }
            public ImpactedResource build() { return new ImpactedResource(resourceId, resourceName, resourceType, occurrenceCount); }
        }
    }

    public static class RelatedGroup {
        private String groupId, alertTitle, relationMethod;
        private Double relationScore;
        public RelatedGroup() {}
        public RelatedGroup(String groupId, String alertTitle, String relationMethod, Double relationScore) { this.groupId = groupId; this.alertTitle = alertTitle; this.relationMethod = relationMethod; this.relationScore = relationScore; }
        public String getGroupId() { return groupId; }
        public void setGroupId(String groupId) { this.groupId = groupId; }
        public String getAlertTitle() { return alertTitle; }
        public void setAlertTitle(String alertTitle) { this.alertTitle = alertTitle; }
        public String getRelationMethod() { return relationMethod; }
        public void setRelationMethod(String relationMethod) { this.relationMethod = relationMethod; }
        public Double getRelationScore() { return relationScore; }
        public void setRelationScore(Double relationScore) { this.relationScore = relationScore; }
        public static RelatedGroupBuilder builder() { return new RelatedGroupBuilder(); }
        public static class RelatedGroupBuilder {
            private String groupId, alertTitle, relationMethod;
            private Double relationScore;
            public RelatedGroupBuilder groupId(String v) { this.groupId = v; return this; }
            public RelatedGroupBuilder alertTitle(String v) { this.alertTitle = v; return this; }
            public RelatedGroupBuilder relationMethod(String v) { this.relationMethod = v; return this; }
            public RelatedGroupBuilder relationScore(Double v) { this.relationScore = v; return this; }
            public RelatedGroup build() { return new RelatedGroup(groupId, alertTitle, relationMethod, relationScore); }
        }
    }

    public static class OccurrenceSample {
        private Instant timestamp;
        private String message, resourceName;
        private Severity severity;
        public OccurrenceSample() {}
        public OccurrenceSample(Instant timestamp, String message, String resourceName, Severity severity) { this.timestamp = timestamp; this.message = message; this.resourceName = resourceName; this.severity = severity; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getResourceName() { return resourceName; }
        public void setResourceName(String resourceName) { this.resourceName = resourceName; }
        public Severity getSeverity() { return severity; }
        public void setSeverity(Severity severity) { this.severity = severity; }
        public static OccurrenceSampleBuilder builder() { return new OccurrenceSampleBuilder(); }
        public static class OccurrenceSampleBuilder {
            private Instant timestamp;
            private String message, resourceName;
            private Severity severity;
            public OccurrenceSampleBuilder timestamp(Instant v) { this.timestamp = v; return this; }
            public OccurrenceSampleBuilder message(String v) { this.message = v; return this; }
            public OccurrenceSampleBuilder resourceName(String v) { this.resourceName = v; return this; }
            public OccurrenceSampleBuilder severity(Severity v) { this.severity = v; return this; }
            public OccurrenceSample build() { return new OccurrenceSample(timestamp, message, resourceName, severity); }
        }
    }

    public static class CorrelationCluster {
        private String clusterId, description, correlationMethod, suggestedRootCause;
        private List<String> groupIds = new ArrayList<>();
        private Double confidence;
        public CorrelationCluster() {}
        public CorrelationCluster(String clusterId, String description, List<String> groupIds, String correlationMethod, Double confidence, String suggestedRootCause) {
            this.clusterId = clusterId; this.description = description; this.groupIds = groupIds != null ? groupIds : new ArrayList<>(); this.correlationMethod = correlationMethod; this.confidence = confidence; this.suggestedRootCause = suggestedRootCause;
        }
        public String getClusterId() { return clusterId; }
        public void setClusterId(String clusterId) { this.clusterId = clusterId; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<String> getGroupIds() { return groupIds; }
        public void setGroupIds(List<String> groupIds) { this.groupIds = groupIds; }
        public String getCorrelationMethod() { return correlationMethod; }
        public void setCorrelationMethod(String correlationMethod) { this.correlationMethod = correlationMethod; }
        public Double getConfidence() { return confidence; }
        public void setConfidence(Double confidence) { this.confidence = confidence; }
        public String getSuggestedRootCause() { return suggestedRootCause; }
        public void setSuggestedRootCause(String suggestedRootCause) { this.suggestedRootCause = suggestedRootCause; }
        public static CorrelationClusterBuilder builder() { return new CorrelationClusterBuilder(); }
        public static class CorrelationClusterBuilder {
            private String clusterId, description, correlationMethod, suggestedRootCause;
            private List<String> groupIds = new ArrayList<>();
            private Double confidence;
            public CorrelationClusterBuilder clusterId(String v) { this.clusterId = v; return this; }
            public CorrelationClusterBuilder description(String v) { this.description = v; return this; }
            public CorrelationClusterBuilder groupIds(List<String> v) { this.groupIds = v; return this; }
            public CorrelationClusterBuilder correlationMethod(String v) { this.correlationMethod = v; return this; }
            public CorrelationClusterBuilder confidence(Double v) { this.confidence = v; return this; }
            public CorrelationClusterBuilder suggestedRootCause(String v) { this.suggestedRootCause = v; return this; }
            public CorrelationCluster build() { return new CorrelationCluster(clusterId, description, groupIds, correlationMethod, confidence, suggestedRootCause); }
        }
    }
}
