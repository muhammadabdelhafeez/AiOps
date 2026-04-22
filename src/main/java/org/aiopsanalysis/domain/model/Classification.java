package org.aiopsanalysis.domain.model;

/**
 * Classification for alert groups - deterministic new vs recurring.
 * 
 * Per enterprise architecture:
 * - RECURRING_SURE: Exact fingerprint match (deterministic)
 * - RECURRING_LIKELY: Family fingerprint match (deterministic)
 * - POSSIBLE_RECURRING: Embedding similarity match only (AI-assisted)
 * - NEW: No match found in 15-day window
 */
public enum Classification {
    /**
     * Exact fingerprint match found in 15-day window.
     * Deterministic - no AI guessing.
     */
    RECURRING_SURE("Recurring (Sure)", "Exact fingerprint match"),

    /**
     * Family fingerprint match found in 15-day window.
     * Deterministic - same alert pattern/family.
     */
    RECURRING_LIKELY("Recurring (Likely)", "Family pattern match"),

    /**
     * Only embedding similarity match found.
     * AI-assisted - should not be used for critical decisions.
     */
    POSSIBLE_RECURRING("Possible Recurring", "Semantic similarity only"),

    /**
     * No match found in the 15-day window.
     * This is a genuinely new alert pattern.
     */
    NEW("New", "No prior occurrence");

    private final String displayName;
    private final String description;

    Classification(String displayName, String description) {
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
     * Check if this classification is deterministic (not AI guessing).
     */
    public boolean isDeterministic() {
        return this == RECURRING_SURE || this == RECURRING_LIKELY || this == NEW;
    }

    /**
     * Check if this is a recurring classification.
     */
    public boolean isRecurring() {
        return this == RECURRING_SURE || this == RECURRING_LIKELY || this == POSSIBLE_RECURRING;
    }
}
