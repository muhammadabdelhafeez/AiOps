package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.index.model.TelemetryKind;

class ShardKeyTest {

    @Test
    void buildsCountryKindDateShardPath() {
        // Environment is intentionally excluded from the shard path (no meaning in this deployment).
        var key = new ShardKey("kw", "prod", TelemetryKind.ALERTS, LocalDate.of(2026, 6, 7), 3);
        assertThat(key.relativePath())
                .isEqualTo(Path.of("KW", "alerts", "2026-06-07", "shard-03"));
    }

    @Test
    void shardForIsStableAndInRange() {
        var a = ShardKey.shardFor("alert-123", 4);
        var b = ShardKey.shardFor("alert-123", 4);
        assertThat(a).isEqualTo(b).isBetween(0, 3);
    }

    @Test
    void shardForNeverNegative() {
        // hashCode can be negative; floorMod must keep it in range.
        assertThat(ShardKey.shardFor("￿￿", 4)).isBetween(0, 3);
    }
}
