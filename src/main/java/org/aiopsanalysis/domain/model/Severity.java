package org.aiopsanalysis.domain.model;

/**
 * Alert severity levels for AIOps incidents.
 * Used for prioritization and scoring.
 */
public enum Severity {
    CRITICAL(4, "Critical"),
    HIGH(3, "High"),
    MEDIUM(2, "Medium"),
    LOW(1, "Low"),
    INFO(0, "Informational");

    private final int weight;
    private final String displayName;

    Severity(int weight, String displayName) {
        this.weight = weight;
        this.displayName = displayName;
    }

    public int getWeight() {
        return weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parse severity from string (case-insensitive).
     */
    public static Severity fromString(String value) {
        if (value == null || value.isBlank()) {
            return INFO;
        }
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "CRITICAL", "CRIT", "1" -> CRITICAL;
            case "HIGH", "2" -> HIGH;
            case "MEDIUM", "MED", "MODERATE", "3" -> MEDIUM;
            case "LOW", "4" -> LOW;
            default -> INFO;
        };
    }
}
