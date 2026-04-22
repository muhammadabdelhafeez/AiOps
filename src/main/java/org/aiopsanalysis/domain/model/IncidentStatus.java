package org.aiopsanalysis.domain.model;

/**
 * Incident lifecycle status - deterministic state machine.
 * 
 * States:
 * - OPEN: Active incident requiring attention
 * - ACKNOWLEDGED: Incident has been seen and is being worked on
 * - CLOSED: Incident is resolved (can be reopened within reopen_window)
 * - SUPPRESSED: Incident is muted during maintenance window
 */
public enum IncidentStatus {
    /**
     * Active incident requiring attention.
     * Initial state for new incidents.
     */
    OPEN("Open", "Active incident requiring attention", true),

    /**
     * Incident has been acknowledged and is being worked on.
     * Transitions from OPEN when an operator acknowledges.
     */
    ACKNOWLEDGED("Acknowledged", "Being worked on by operator", true),

    /**
     * Incident has been resolved and closed.
     * Can transition to OPEN (REOPENED) if same incident_key appears within reopen_window.
     */
    CLOSED("Closed", "Resolved incident", false),

    /**
     * Incident is suppressed during a maintenance window.
     * Transitions back to OPEN when maintenance ends if still active.
     */
    SUPPRESSED("Suppressed", "Muted during maintenance", false);

    private final String displayName;
    private final String description;
    private final boolean active;

    IncidentStatus(String displayName, String description, boolean active) {
        this.displayName = displayName;
        this.description = description;
        this.active = active;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this status represents an active/open incident.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Check if this incident can receive new alert occurrences.
     */
    public boolean canReceiveAlerts() {
        return this == OPEN || this == ACKNOWLEDGED;
    }

    /**
     * Check if this incident can be reopened.
     */
    public boolean canBeReopened() {
        return this == CLOSED;
    }

    /**
     * Check if status transition is valid.
     */
    public boolean canTransitionTo(IncidentStatus newStatus) {
        return switch (this) {
            case OPEN -> newStatus == ACKNOWLEDGED || newStatus == CLOSED || newStatus == SUPPRESSED;
            case ACKNOWLEDGED -> newStatus == OPEN || newStatus == CLOSED || newStatus == SUPPRESSED;
            case CLOSED -> newStatus == OPEN; // Reopen
            case SUPPRESSED -> newStatus == OPEN || newStatus == CLOSED;
        };
    }

    /**
     * Parse status from string (case-insensitive).
     */
    public static IncidentStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return OPEN;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "OPEN", "ACTIVE", "NEW" -> OPEN;
            case "ACKNOWLEDGED", "ACK", "WORKING" -> ACKNOWLEDGED;
            case "CLOSED", "RESOLVED", "DONE" -> CLOSED;
            case "SUPPRESSED", "MUTED", "MAINTENANCE" -> SUPPRESSED;
            default -> OPEN;
        };
    }
}
