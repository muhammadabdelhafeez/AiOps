package org.kfh.aiops.index;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import org.kfh.aiops.index.model.TelemetryKind;

/**
 * Identifies one shard directory: {@code {country}/{kind}/{yyyy-MM-dd}/shard-NN} (§10 shard path).
 * Environment is intentionally NOT part of the path — it has no meaning in this deployment, so all
 * telemetry for a country/kind/date lands in one partition regardless of environment. Documents route
 * to a shard by a stable hash of their id, so a document always lands in the same shard and searches
 * fan out across all shards of a partition in parallel. The {@code environment} field is retained for
 * call-site compatibility but ignored by {@link #relativePath()}.
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

    /** Relative shard directory beneath the index root ({@code {country}/{kind}/{date}/shard-NN}). */
    public Path relativePath() {
        return Path.of(country, kind.dir(), date.toString(), "shard-" + String.format(Locale.ROOT, "%02d", shard));
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
