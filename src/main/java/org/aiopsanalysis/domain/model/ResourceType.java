package org.aiopsanalysis.domain.model;

/**
 * Types of resources in the infrastructure topology.
 * Used for Neo4j node classification and topology visualization.
 */
public enum ResourceType {
    // Compute
    HOST("Host", "Physical or virtual host"),
    VM("Virtual Machine", "Virtual machine instance"),
    CONTAINER("Container", "Container instance"),
    POD("Pod", "Kubernetes pod"),
    
    // Network
    SWITCH("Switch", "Network switch"),
    ROUTER("Router", "Network router"),
    LOAD_BALANCER("Load Balancer", "Load balancer"),
    FIREWALL("Firewall", "Firewall device"),
    
    // Storage
    DATASTORE("Datastore", "Storage datastore"),
    STORAGE_ARRAY("Storage Array", "Storage array"),
    NAS("NAS", "Network attached storage"),
    
    // Database
    DATABASE("Database", "Database instance"),
    DATABASE_CLUSTER("Database Cluster", "Database cluster"),
    
    // Cache
    CACHE("Cache", "Cache instance (Redis, Memcached)"),
    
    // Application
    APPLICATION("Application", "Application"),
    SERVICE("Service", "Microservice or service"),
    API_GATEWAY("API Gateway", "API Gateway"),
    
    // Infrastructure
    CLUSTER("Cluster", "Compute cluster"),
    DATACENTER("Datacenter", "Datacenter"),
    REGION("Region", "Cloud region"),
    
    // Other
    UNKNOWN("Unknown", "Unknown resource type");

    private final String displayName;
    private final String description;

    ResourceType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse resource type from string (case-insensitive, handles common aliases).
     */
    public static ResourceType fromString(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        try {
            return ResourceType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // Handle common aliases
            return switch (normalized) {
                case "SERVER", "PHYSICAL_HOST" -> HOST;
                case "VIRTUAL_MACHINE", "VIRTUALMACHINE" -> VM;
                case "DB", "DATABASE_SERVER" -> DATABASE;
                case "LB" -> LOAD_BALANCER;
                case "FW" -> FIREWALL;
                case "APP" -> APPLICATION;
                case "SVC" -> SERVICE;
                default -> UNKNOWN;
            };
        }
    }
}
