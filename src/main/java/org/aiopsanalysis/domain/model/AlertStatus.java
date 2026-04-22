package org.aiopsanalysis.domain.model;

/**
 * Status of an alert group in its lifecycle.
 */
public enum AlertStatus {
    ACTIVE("Active", true),
    INVESTIGATING("Investigating", true),
    MITIGATED("Mitigated", true),
    MONITORING("Monitoring", true),
    RESOLVED("Resolved", false),
    CLOSED("Closed", false),
    SUPPRESSED("Suppressed", false);

    private final String displayName;
    private final boolean isOpen;

    AlertStatus(String displayName, boolean isOpen) {
        this.displayName = displayName;
        this.isOpen = isOpen;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this status represents an open/active incident.
     */
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Check if this status represents a closed incident.
     */
    public boolean isClosed() {
        return !isOpen;
    }
}
