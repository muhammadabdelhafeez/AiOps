package org.aiopsanalysis.domain.neo4j;

import org.aiopsanalysis.domain.model.Severity;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;

/**
 * Neo4j node representing an Application.
 */
@Node("App")
public class AppNode {

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

    @Property("owner")
    private String owner;

    @Property("businessCriticality")
    private Integer businessCriticality;

    @Property("tier")
    private Integer tier;

    @Property("lastUpdatedEpoch")
    private Long lastUpdatedEpoch;

    @Relationship(type = "HAS_SERVICE", direction = Relationship.Direction.OUTGOING)
    private List<ServiceNode> services = new ArrayList<>();

    public AppNode() {}

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

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public Integer getBusinessCriticality() { return businessCriticality; }
    public void setBusinessCriticality(Integer businessCriticality) { this.businessCriticality = businessCriticality; }

    public Integer getTier() { return tier; }
    public void setTier(Integer tier) { this.tier = tier; }

    public Long getLastUpdatedEpoch() { return lastUpdatedEpoch; }
    public void setLastUpdatedEpoch(Long lastUpdatedEpoch) { this.lastUpdatedEpoch = lastUpdatedEpoch; }

    public List<ServiceNode> getServices() { return services; }
    public void setServices(List<ServiceNode> services) { this.services = services; }

    public boolean isHealthy() {
        return healthSeverity == null || healthSeverity == Severity.INFO || healthSeverity == Severity.LOW;
    }
}
