package org.aiopsanalysis.domain.postgres;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity for cmdb.applications table.
 * Represents an application in the CMDB - the top-level entity for incident grouping.
 * 
 * Applications contain Services which run on Resources.
 * Incidents are created per Application based on correlated AlertGroups.
 */
@Entity
@Table(name = "applications", schema = "cmdb",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name"}),
    indexes = {
        @Index(name = "idx_cmdb_app_tenant", columnList = "tenant_id")
    }
)
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "app_id")
    private UUID appId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Business criticality tier: Tier1 (Critical), Tier2 (Important), Tier3 (Standard)
     */
    @Column(name = "criticality", nullable = false)
    private String criticality = "Tier2";

    /**
     * Owner group or team responsible for this application.
     */
    @Column(name = "owner_group")
    private String ownerGroup;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * System applications like UNMAPPED are flagged here.
     */
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Service> services = new ArrayList<>();

    // Criticality constants
    public static final String CRITICALITY_TIER1 = "Tier1";
    public static final String CRITICALITY_TIER2 = "Tier2";
    public static final String CRITICALITY_TIER3 = "Tier3";

    // Special application names
    public static final String UNMAPPED_APP_NAME = "UNMAPPED";

    // Constructors
    public Application() {}

    public Application(UUID tenantId, String name) {
        this.tenantId = tenantId;
        this.name = name;
    }

    public Application(UUID tenantId, String name, String criticality) {
        this.tenantId = tenantId;
        this.name = name;
        this.criticality = criticality;
    }

    /**
     * Create the special UNMAPPED application for a tenant.
     */
    public static Application createUnmappedApp(UUID tenantId) {
        Application app = new Application(tenantId, UNMAPPED_APP_NAME, CRITICALITY_TIER3);
        app.setIsSystem(true);
        app.setDescription("Unmapped/Unknown resources bucket");
        return app;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Check if this is the UNMAPPED pseudo-application.
     */
    public boolean isUnmapped() {
        return UNMAPPED_APP_NAME.equals(name) || Boolean.TRUE.equals(isSystem);
    }

    /**
     * Check if this is a Tier1 (Critical) application.
     */
    public boolean isCritical() {
        return CRITICALITY_TIER1.equals(criticality);
    }

    /**
     * Get severity weight based on criticality for incident prioritization.
     */
    public int getCriticalityWeight() {
        return switch (criticality) {
            case CRITICALITY_TIER1 -> 3;
            case CRITICALITY_TIER2 -> 2;
            case CRITICALITY_TIER3 -> 1;
            default -> 1;
        };
    }

    // Getters and Setters
    public UUID getAppId() { return appId; }
    public void setAppId(UUID appId) { this.appId = appId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCriticality() { return criticality; }
    public void setCriticality(String criticality) { this.criticality = criticality; }

    public String getOwnerGroup() { return ownerGroup; }
    public void setOwnerGroup(String ownerGroup) { this.ownerGroup = ownerGroup; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getIsSystem() { return isSystem; }
    public void setIsSystem(Boolean isSystem) { this.isSystem = isSystem; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<Service> getServices() { return services; }
    public void setServices(List<Service> services) { this.services = services; }

    // Builder pattern
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Application app = new Application();

        public Builder tenantId(UUID v) { app.tenantId = v; return this; }
        public Builder name(String v) { app.name = v; return this; }
        public Builder criticality(String v) { app.criticality = v; return this; }
        public Builder ownerGroup(String v) { app.ownerGroup = v; return this; }
        public Builder description(String v) { app.description = v; return this; }
        public Builder isSystem(Boolean v) { app.isSystem = v; return this; }

        public Application build() {
            if (app.createdAt == null) app.createdAt = Instant.now();
            if (app.updatedAt == null) app.updatedAt = Instant.now();
            return app;
        }
    }
}
