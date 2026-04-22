package org.aiopsanalysis.domain.neo4j;

import org.aiopsanalysis.domain.model.Severity;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

/**
 * Neo4j node representing a Service (microservice or component).
 */
@Node("Service")
public class ServiceNode {

    @Id
    private String id;

    @Property("name")
    private String name;

    @Property("description")
    private String description;

    @Property("healthSeverity")
    private Severity healthSeverity;

    @Property("healthScore")
    private Integer healthScore;

    @Property("serviceType")
    private String serviceType;

    @Property("endpoint")
    private String endpoint;

    @Property("port")
    private Integer port;

    @Property("lastUpdatedEpoch")
    private Long lastUpdatedEpoch;

    public ServiceNode() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Severity getHealthSeverity() { return healthSeverity; }
    public void setHealthSeverity(Severity healthSeverity) { this.healthSeverity = healthSeverity; }

    public Integer getHealthScore() { return healthScore; }
    public void setHealthScore(Integer healthScore) { this.healthScore = healthScore; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String serviceType) { this.serviceType = serviceType; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public Long getLastUpdatedEpoch() { return lastUpdatedEpoch; }
    public void setLastUpdatedEpoch(Long lastUpdatedEpoch) { this.lastUpdatedEpoch = lastUpdatedEpoch; }

    public boolean isHealthy() {
        return healthSeverity == null || healthSeverity == Severity.INFO || healthSeverity == Severity.LOW;
    }
}
