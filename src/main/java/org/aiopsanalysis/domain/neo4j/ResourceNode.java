package org.aiopsanalysis.domain.neo4j;

import org.aiopsanalysis.domain.model.ResourceType;
import org.aiopsanalysis.domain.model.Severity;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Neo4j node representing an infrastructure Resource.
 */
@Node("Resource")
public class ResourceNode {

    @Id
    private String id;

    @Property("name")
    private String name;

    @Property("type")
    private ResourceType type;

    @Property("healthSeverity")
    private Severity healthSeverity;

    @Property("healthScore")
    private Integer healthScore;

    @Property("tags")
    private Map<String, String> tags = new HashMap<>();

    @Property("owner")
    private String owner;

    @Property("environment")
    private String environment;

    @Property("location")
    private String location;

    @Property("businessCriticality")
    private Integer businessCriticality;

    @Property("lastUpdatedEpoch")
    private Long lastUpdatedEpoch;

    @Relationship(type = "RUNS", direction = Relationship.Direction.OUTGOING)
    private List<AppNode> runsApps = new ArrayList<>();

    @Relationship(type = "HOSTS", direction = Relationship.Direction.OUTGOING)
    private List<ResourceNode> hostsResources = new ArrayList<>();

    @Relationship(type = "CONNECTS_TO", direction = Relationship.Direction.OUTGOING)
    private List<ResourceNode> connectsTo = new ArrayList<>();

    @Relationship(type = "DEPENDS_ON", direction = Relationship.Direction.OUTGOING)
    private List<ResourceNode> dependsOn = new ArrayList<>();

    @Relationship(type = "STORES_ON", direction = Relationship.Direction.OUTGOING)
    private List<ResourceNode> storesOn = new ArrayList<>();

    public ResourceNode() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ResourceType getType() { return type; }
    public void setType(ResourceType type) { this.type = type; }

    public Severity getHealthSeverity() { return healthSeverity; }
    public void setHealthSeverity(Severity healthSeverity) { this.healthSeverity = healthSeverity; }

    public Integer getHealthScore() { return healthScore; }
    public void setHealthScore(Integer healthScore) { this.healthScore = healthScore; }

    public Map<String, String> getTags() { return tags; }
    public void setTags(Map<String, String> tags) { this.tags = tags; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public Integer getBusinessCriticality() { return businessCriticality; }
    public void setBusinessCriticality(Integer businessCriticality) { this.businessCriticality = businessCriticality; }

    public Long getLastUpdatedEpoch() { return lastUpdatedEpoch; }
    public void setLastUpdatedEpoch(Long lastUpdatedEpoch) { this.lastUpdatedEpoch = lastUpdatedEpoch; }

    public List<AppNode> getRunsApps() { return runsApps; }
    public void setRunsApps(List<AppNode> runsApps) { this.runsApps = runsApps; }

    public List<ResourceNode> getHostsResources() { return hostsResources; }
    public void setHostsResources(List<ResourceNode> hostsResources) { this.hostsResources = hostsResources; }

    public List<ResourceNode> getConnectsTo() { return connectsTo; }
    public void setConnectsTo(List<ResourceNode> connectsTo) { this.connectsTo = connectsTo; }

    public List<ResourceNode> getDependsOn() { return dependsOn; }
    public void setDependsOn(List<ResourceNode> dependsOn) { this.dependsOn = dependsOn; }

    public List<ResourceNode> getStoresOn() { return storesOn; }
    public void setStoresOn(List<ResourceNode> storesOn) { this.storesOn = storesOn; }

    public boolean isHealthy() {
        return healthSeverity == null || healthSeverity == Severity.INFO || healthSeverity == Severity.LOW;
    }

    public boolean isCritical() { return healthSeverity == Severity.CRITICAL; }

    public String getTag(String key) { return tags != null ? tags.get(key) : null; }
}
