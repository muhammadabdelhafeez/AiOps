package org.kfh.aiops.index.model;

import java.util.Locale;

/**
 * The five searchable telemetry partitions (§10 index naming: logs/alerts/traces/metrics/changes).
 * Each kind is time-partitioned into its own shard path so queries can prune by kind + date.
 */
public enum TelemetryKind {
    LOGS,
    ALERTS,
    TRACES,
    METRICS,
    CHANGES;

    /** Lower-case directory segment used in the shard path. */
    public String dir() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static TelemetryKind from(String value) {
        return TelemetryKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
