package org.kfh.aiops.index;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import org.kfh.aiops.index.model.TelemetryKind;

/**
 * Identifies one shard directory: {@code {country}/{environment}/{kind}/{yyyy-MM-dd}/shard-NN}
 * (§10 shard path). Documents route to a shard by a stable hash of their id, so a document always
 * lands in the same shard and searches can fan out across all shards of a partition in parallel.
 */
public record ShardKey(String country, String environment, TelemetryKind kind, LocalDate date, int shard) {

    public ShardKey {
        country = norm(country);
        environment = norm(environment);
        if (kind == null) {
            throw new IllegalArgumentException("ShardKey.kind is required");
        }
        if (date == null) {
            throw new IllegalArgumentException("ShardKey.date is required");
        }
        if (shard < 0) {
            throw new IllegalArgumentException("ShardKey.shard must be >= 0");
        }
    }

    /** Relative shard directory beneath the index root. */
    public Path relativePath() {
        return Path.of(country, environment, kind.dir(), date.toString(), "shard-" + String.format(Locale.ROOT, "%02d", shard));
    }

    /** Stable shard number for a document id given the configured shards-per-day. */
    public static int shardFor(String documentId, int shardsPerDay) {
        var count = Math.max(1, shardsPerDay);
        return Math.floorMod(documentId.hashCode(), count);
    }

    private static String norm(String value) {
        return value == null || value.isBlank() ? "ALL" : value.trim().toUpperCase(Locale.ROOT);
    }
}
