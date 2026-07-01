package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.index.model.TelemetryKind;

class ShardIndexCacheTest {

    private final SegmentStore store = new SegmentStore(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void cachesUntilSegmentGrowsThenRebuilds(@TempDir Path tmp) {
        var cache = new ShardIndexCache(store);
        var shard = tmp.resolve("shard-00");
        store.append(shard, List.of(doc("a")));

        var first = cache.get(shard);
        var second = cache.get(shard);
        assertThat(second).isSameAs(first);          // cache hit — no re-parse

        store.append(shard, List.of(doc("b")));       // segment size changes
        var third = cache.get(shard);
        assertThat(third).isNotSameAs(first);          // invalidated + rebuilt
        assertThat(cache.cachedShardCount()).isEqualTo(1);
    }

    private static TelemetryDocument doc(String id) {
        return new TelemetryDocument(id, Instant.parse("2026-06-07T10:00:00Z"), UUID.randomUUID(), "KW", "PROD",
                TelemetryKind.ALERTS, "SCOM", "MOBILE", "TRANSFER", "SRV-1", "SERVER", "CRITICAL", "T1", "C1", "TX1",
                "msg", "obj://raw/" + id, Map.of());
    }
}
