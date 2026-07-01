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

class SegmentStoreTest {

    private final SegmentStore store = new SegmentStore(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void appendThenReadRoundTrips(@TempDir Path tmp) {
        var shard = tmp.resolve("shard-00");
        store.append(shard, List.of(doc("a"), doc("b")));

        var read = store.readShard(shard);

        assertThat(read).extracting(TelemetryDocument::id).containsExactly("a", "b");
        assertThat(read.get(0).attributes()).containsEntry("errorCount", 5);
    }

    @Test
    void appendIsAdditive(@TempDir Path tmp) {
        var shard = tmp.resolve("shard-00");
        store.append(shard, List.of(doc("a")));
        store.append(shard, List.of(doc("b")));
        assertThat(store.readShard(shard)).hasSize(2);
    }

    @Test
    void readMissingShardReturnsEmpty(@TempDir Path tmp) {
        assertThat(store.readShard(tmp.resolve("nope"))).isEmpty();
    }

    private static TelemetryDocument doc(String id) {
        return new TelemetryDocument(id, Instant.parse("2026-06-07T10:00:00Z"), UUID.randomUUID(),
                "KW", "PROD", TelemetryKind.ALERTS, "SCOM", "MOBILE", "TRANSFER", "SRV-1", "SERVER",
                "CRITICAL", "T1", "C1", "TX1", "cpu high", "obj://raw/1", Map.of("errorCount", 5));
    }
}
