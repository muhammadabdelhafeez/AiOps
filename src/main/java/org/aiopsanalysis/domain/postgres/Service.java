package org.aiopsanalysis.domain.postgres;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity for cmdb.services table.
 * Represents a service in the CMDB - belongs to an Application and runs on Resources.
 * 
 * Service topology:
 * - Application -> Services -> Resources
 * - Services can depend on other Services (DEPENDS_ON relationship)
 */
@Entity
@Table(name = "services", schema = "cmdb",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "app_id", "name"}),
    indexes = {
        @Index(name = "idx_cmdb_services_app", columnList = "tenant_id, app_id")
    }
)
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "service_id")
    private UUID serviceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "app_id", nullable = false)
    private UUID appId;

    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Service type: API, DB, WEB, BATCH, QUEUE, CACHE, etc.
     */
    @Column(name = "type", nullable = false)
    private String type = "API";

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id", insertable = false, updatable = false)
    private Application application;

    @ManyToMany
    @JoinTable(
        name = "service_resources",
        schema = "cmdb",
        joinColumns = {
            @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id"),
            @JoinColumn(name = "service_id", referencedColumnName = "service_id")
        },
        inverseJoinColumns = {
            @JoinColumn(name = "resource_id", referencedColumnName = "resource_id")
        }
    )
    private List<Resource> resources = new ArrayList<>();

    // Service type constants
    public static final String TYPE_API = "API";
    public static final String TYPE_DB = "DB";
    public static final String TYPE_WEB = "WEB";
    public static final String TYPE_BATCH = "BATCH";
    public static final String TYPE_QUEUE = "QUEUE";
    public static final String TYPE_CACHE = "CACHE";
    public static final String TYPE_STORAGE = "STORAGE";
    public static final String TYPE_GATEWAY = "GATEWAY";

    // Constructors
    public Service() {}

    public Service(UUID tenantId, UUID appId, String name) {
        this.tenantId = tenantId;
        this.appId = appId;
        this.name = name;
    }

    public Service(UUID tenantId, UUID appId, String name, String type) {
        this.tenantId = tenantId;
        this.appId = appId;
        this.name = name;
        this.type = type;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Check if this is a database service.
     */
    public boolean isDatabase() {
        return TYPE_DB.equals(type);
    }

    /**
     * Check if this is an API service.
     */
    public boolean isApi() {
        return TYPE_API.equals(type);
    }

    // Getters and Setters
    public UUID getServiceId() { return serviceId; }
    public void setServiceId(UUID serviceId) { this.serviceId = serviceId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public UUID getAppId() { return appId; }
    public void setAppId(UUID appId) { this.appId = appId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public List<Resource> getResources() { return resources; }
    public void setResources(List<Resource> resources) { this.resources = resources; }

    // Builder pattern
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Service service = new Service();

        public Builder tenantId(UUID v) { service.tenantId = v; return this; }
        public Builder appId(UUID v) { service.appId = v; return this; }
        public Builder name(String v) { service.name = v; return this; }
        public Builder type(String v) { service.type = v; return this; }
        public Builder description(String v) { service.description = v; return this; }

        public Service build() {
            if (service.createdAt == null) service.createdAt = Instant.now();
            if (service.updatedAt == null) service.updatedAt = Instant.now();
            return service;
        }
    }
}
