package org.aiopsanalysis.domain.postgres;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA Entity for cmdb.resources table.
 * Represents infrastructure resources in the CMDB - servers, VMs, pods, databases, etc.
 * 
 * Resources are the targets of AlertOccurrences and are mapped to Services.
 * Alert -> Resource -> Service -> Application is the inventory mapping path.
 */
@Entity
@Table(name = "resources", schema = "cmdb",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "name", "kind"}),
    indexes = {
        @Index(name = "idx_cmdb_resources_name", columnList = "tenant_id, name")
    }
)
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /**
     * Resource name - typically hostname or unique identifier.
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Resource kind: SERVER, VM, POD, DB, SWITCH, STORAGE, CONTAINER, etc.
     */
    @Column(name = "kind", nullable = false)
    private String kind;

    /**
     * IP address of the resource (optional).
     */
    @Column(name = "ip", columnDefinition = "inet")
    private String ip;

    /**
     * Additional tags/metadata as JSON.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private Map<String, Object> tags;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // Resource kind constants
    public static final String KIND_SERVER = "SERVER";
    public static final String KIND_VM = "VM";
    public static final String KIND_POD = "POD";
    public static final String KIND_CONTAINER = "CONTAINER";
    public static final String KIND_DB = "DB";
    public static final String KIND_SWITCH = "SWITCH";
    public static final String KIND_STORAGE = "STORAGE";
    public static final String KIND_LOAD_BALANCER = "LOAD_BALANCER";
    public static final String KIND_FIREWALL = "FIREWALL";
    public static final String KIND_UNKNOWN = "UNKNOWN";

    // Constructors
    public Resource() {}

    public Resource(UUID tenantId, String name, String kind) {
        this.tenantId = tenantId;
        this.name = name;
        this.kind = kind;
    }

    public Resource(UUID tenantId, String name, String kind, String ip) {
        this.tenantId = tenantId;
        this.name = name;
        this.kind = kind;
        this.ip = ip;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Check if this is a compute resource (server/VM/pod).
     */
    public boolean isCompute() {
        return KIND_SERVER.equals(kind) || KIND_VM.equals(kind) || 
               KIND_POD.equals(kind) || KIND_CONTAINER.equals(kind);
    }

    /**
     * Check if this is a network resource.
     */
    public boolean isNetwork() {
        return KIND_SWITCH.equals(kind) || KIND_LOAD_BALANCER.equals(kind) || KIND_FIREWALL.equals(kind);
    }

    /**
     * Check if this is a storage/database resource.
     */
    public boolean isStorage() {
        return KIND_STORAGE.equals(kind) || KIND_DB.equals(kind);
    }

    /**
     * Get a tag value by key.
     */
    public Object getTag(String key) {
        return tags != null ? tags.get(key) : null;
    }

    // Getters and Setters
    public UUID getResourceId() { return resourceId; }
    public void setResourceId(UUID resourceId) { this.resourceId = resourceId; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }

    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }

    public Map<String, Object> getTags() { return tags; }
    public void setTags(Map<String, Object> tags) { this.tags = tags; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    // Builder pattern
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Resource resource = new Resource();

        public Builder tenantId(UUID v) { resource.tenantId = v; return this; }
        public Builder name(String v) { resource.name = v; return this; }
        public Builder kind(String v) { resource.kind = v; return this; }
        public Builder ip(String v) { resource.ip = v; return this; }
        public Builder tags(Map<String, Object> v) { resource.tags = v; return this; }

        public Resource build() {
            if (resource.createdAt == null) resource.createdAt = Instant.now();
            if (resource.updatedAt == null) resource.updatedAt = Instant.now();
            return resource;
        }
    }
}
