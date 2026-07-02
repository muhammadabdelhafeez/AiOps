package org.kfh.aiops.topology;

/**
 * A concrete configuration item (CI) bound to a component — the thing an alert is actually about.
 * {@code ciKey} is matched (case-insensitively) against an alert's {@code resourceId}.
 */
public record Asset(String ciKey, String name, String type, String componentId) {

    /** Normalized match key for resolving alert resourceIds. */
    public String matchKey() {
        return ciKey == null ? "" : ciKey.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
