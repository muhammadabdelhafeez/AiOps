package org.aiopsanalysis.domain.neo4j;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j node representing a pipeline Run.
 */
@Node("Run")
public class RunNode {

    @Id
    private String runId;

    @Property("tsEpoch")
    private Long tsEpoch;

    @Property("endTsEpoch")
    private Long endTsEpoch;

    @Property("status")
    private String status;

    @Property("alertsProcessed")
    private Integer alertsProcessed;

    @Property("newGroupsCreated")
    private Integer newGroupsCreated;

    @Property("recurringGroupsUpdated")
    private Integer recurringGroupsUpdated;

    @Property("groupsReasonedByPro")
    private Integer groupsReasonedByPro;

    @Property("sharePointPath")
    private String sharePointPath;

    @Property("errorMessage")
    private String errorMessage;

    @Property("triggerType")
    private String triggerType;

    @Relationship(type = "HAS_GROUP", direction = Relationship.Direction.OUTGOING)
    private List<RunGroupRelation> groups = new ArrayList<>();

    public RunNode() {}

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public Long getTsEpoch() { return tsEpoch; }
    public void setTsEpoch(Long tsEpoch) { this.tsEpoch = tsEpoch; }

    public Long getEndTsEpoch() { return endTsEpoch; }
    public void setEndTsEpoch(Long endTsEpoch) { this.endTsEpoch = endTsEpoch; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getAlertsProcessed() { return alertsProcessed; }
    public void setAlertsProcessed(Integer alertsProcessed) { this.alertsProcessed = alertsProcessed; }

    public Integer getNewGroupsCreated() { return newGroupsCreated; }
    public void setNewGroupsCreated(Integer newGroupsCreated) { this.newGroupsCreated = newGroupsCreated; }

    public Integer getRecurringGroupsUpdated() { return recurringGroupsUpdated; }
    public void setRecurringGroupsUpdated(Integer recurringGroupsUpdated) { this.recurringGroupsUpdated = recurringGroupsUpdated; }

    public Integer getGroupsReasonedByPro() { return groupsReasonedByPro; }
    public void setGroupsReasonedByPro(Integer groupsReasonedByPro) { this.groupsReasonedByPro = groupsReasonedByPro; }

    public String getSharePointPath() { return sharePointPath; }
    public void setSharePointPath(String sharePointPath) { this.sharePointPath = sharePointPath; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getTriggerType() { return triggerType; }
    public void setTriggerType(String triggerType) { this.triggerType = triggerType; }

    public List<RunGroupRelation> getGroups() { return groups; }
    public void setGroups(List<RunGroupRelation> groups) { this.groups = groups; }

    public boolean isCompleted() { return "COMPLETED".equals(status); }
    public boolean isFailed() { return "FAILED".equals(status); }

    public Long getDurationMs() {
        return (tsEpoch != null && endTsEpoch != null) ? endTsEpoch - tsEpoch : null;
    }
}
