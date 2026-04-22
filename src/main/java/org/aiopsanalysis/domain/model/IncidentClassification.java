package org.aiopsanalysis.domain.model;

/**
 * Incident classification label - deterministic, not AI-based.
 * 
 * Classification is determined by incident_key + last_closed_at:
 * - NEW: No prior incident_key seen in reopen_window
 * - ONGOING: Same incident_key exists and status is OPEN/ACKNOWLEDGED
 * - REOPENED: Was CLOSED, now active again within reopen_window
 * - NEW_KNOWN_PATTERN: New incident created, but AlertGroups match old patterns beyond reopen_window
 * 
 * This classification is purely database-driven - GPT never decides new/old/reopen.
 */
public enum IncidentClassification {
    /**
     * Brand new incident - no prior incident_key seen in reopen_window.
     * AlertGroups involved are either new or seen beyond reopen_window.
     */
    NEW("New", "New incident with no recent history", "🆕"),

    /**
     * Ongoing incident - already open from previous runs.
     * Same incident_key exists and status is OPEN or ACKNOWLEDGED.
     */
    ONGOING("Ongoing", "Existing open incident updated", "🔄"),

    /**
     * Reopened incident - was closed, now active again within reopen_window.
     * Same incident_key appears again and (now - last_closed_at) <= reopen_window.
     */
    REOPENED("Reopened", "Previously closed, now active again", "🔁"),

    /**
     * New incident but with known pattern - AlertGroups existed historically.
     * New incident created because beyond reopen_window, but linked to old patterns.
     */
    NEW_KNOWN_PATTERN("New (Known Pattern)", "New incident with recurring alert patterns", "📋");

    private final String displayName;
    private final String description;
    private final String emoji;

    IncidentClassification(String displayName, String description, String emoji) {
        this.displayName = displayName;
        this.description = description;
        this.emoji = emoji;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getEmoji() {
        return emoji;
    }

    /**
     * Get UI-friendly label with emoji.
     */
    public String getLabel() {
        return emoji + " " + displayName;
    }

    /**
     * Check if this is a truly new incident (not a continuation).
     */
    public boolean isNew() {
        return this == NEW || this == NEW_KNOWN_PATTERN;
    }

    /**
     * Check if this is a continuation of existing incident.
     */
    public boolean isContinuation() {
        return this == ONGOING || this == REOPENED;
    }

    /**
     * Check if this classification indicates recurring patterns.
     */
    public boolean hasKnownPatterns() {
        return this == REOPENED || this == NEW_KNOWN_PATTERN || this == ONGOING;
    }

    /**
     * Get priority for sorting (lower = more important for operators).
     * REOPENED > NEW > NEW_KNOWN_PATTERN > ONGOING
     */
    public int getSortPriority() {
        return switch (this) {
            case REOPENED -> 1;        // Highest priority - needs immediate attention
            case NEW -> 2;             // New issues to investigate
            case NEW_KNOWN_PATTERN -> 3; // Known patterns, likely has runbook
            case ONGOING -> 4;         // Already being tracked
        };
    }

    /**
     * Parse classification from string (case-insensitive).
     */
    public static IncidentClassification fromString(String value) {
        if (value == null || value.isBlank()) {
            return NEW;
        }
        String normalized = value.trim().toUpperCase().replace(" ", "_").replace("-", "_");
        return switch (normalized) {
            case "NEW" -> NEW;
            case "ONGOING", "EXISTING", "OPEN" -> ONGOING;
            case "REOPENED", "REOPEN", "REACTIVATED" -> REOPENED;
            case "NEW_KNOWN_PATTERN", "KNOWN_PATTERN", "RECURRING", "NEW_RECURRING_PATTERN" -> NEW_KNOWN_PATTERN;
            default -> NEW;
        };
    }

    /**
     * Determine classification based on incident state.
     * This is the core deterministic logic - no AI involved.
     * 
     * @param existingIncident Whether an incident with same key exists
     * @param existingStatus Status of existing incident (if any)
     * @param withinReopenWindow Whether we're within reopen window of last closure
     * @param hasHistoricalPatterns Whether AlertGroups have historical matches
     * @return Appropriate classification
     */
    public static IncidentClassification determine(
            boolean existingIncident,
            IncidentStatus existingStatus,
            boolean withinReopenWindow,
            boolean hasHistoricalPatterns) {
        
        if (existingIncident) {
            if (existingStatus != null && existingStatus.isActive()) {
                // Same incident_key exists and is OPEN/ACKNOWLEDGED
                return ONGOING;
            }
            if (existingStatus == IncidentStatus.CLOSED && withinReopenWindow) {
                // Was CLOSED, now active again within reopen window
                return REOPENED;
            }
        }
        
        // No existing active incident or beyond reopen window
        if (hasHistoricalPatterns) {
            return NEW_KNOWN_PATTERN;
        }
        
        return NEW;
    }
}
