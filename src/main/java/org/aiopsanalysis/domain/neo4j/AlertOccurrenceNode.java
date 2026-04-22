package org.aiopsanalysis.domain.neo4j;

import org.aiopsanalysis.domain.model.Severity;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

/**
 * Neo4j node representing an individual AlertOccurrence (raw alert event).
 */
@Node("AlertOccurrence")
public class AlertOccurrenceNode {

    @Id
    private String id;

    @Property("tsEpoch")
    private Long tsEpoch;

    @Property("source")
    private String source;

    @Property("severity")
    private Severity severity;

    @Property("messageClean")
    private String messageClean;

    @Property("rawRef")
    private String rawRef;

    @Property("runId")
    private String runId;

    @Property("resourceId")
    private String resourceId;

    @Property("appId")
    private String appId;

    @Property("serviceId")
    private String serviceId;

    @Relationship(type = "INSTANCE_OF", direction = Relationship.Direction.OUTGOING)
    private AlertGroupNode alertGroup;

    @Relationship(type = "ON_RESOURCE", direction = Relationship.Direction.OUTGOING)
    private ResourceNode resource;

    public AlertOccurrenceNode() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getTsEpoch() { return tsEpoch; }
    public void setTsEpoch(Long tsEpoch) { this.tsEpoch = tsEpoch; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public String getMessageClean() { return messageClean; }
    public void setMessageClean(String messageClean) { this.messageClean = messageClean; }

    public String getRawRef() { return rawRef; }
    public void setRawRef(String rawRef) { this.rawRef = rawRef; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public AlertGroupNode getAlertGroup() { return alertGroup; }
    public void setAlertGroup(AlertGroupNode alertGroup) { this.alertGroup = alertGroup; }

    public ResourceNode getResource() { return resource; }
    public void setResource(ResourceNode resource) { this.resource = resource; }
}
