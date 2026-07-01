package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.index.model.TelemetryKind;

class IndexWriterServiceTest {

    private final SegmentStore store = new SegmentStore(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test
    void routesDocumentToCountryEnvKindDateShard(@TempDir Path root) {
        var props = props(root, 4);
        var writer = new IndexWriterService(store, props);
        var doc = new TelemetryDocument("alert-1", Instant.parse("2026-06-07T10:00:00Z"), UUID.randomUUID(),
                "KW", "PROD", TelemetryKind.ALERTS, "SCOM", "MOBILE", "TRANSFER", "SRV-1", "SERVER",
                "CRITICAL", "T1", "C1", "TX1", "cpu high", "obj://raw/1", Map.of());

        var written = writer.index(List.of(doc));

        var shard = ShardKey.shardFor("alert-1", 4);
        var expected = root.resolve(new ShardKey("KW", "PROD", TelemetryKind.ALERTS,
                java.time.LocalDate.of(2026, 6, 7), shard).relativePath());
        assertThat(written).isEqualTo(1);
        assertThat(Files.isDirectory(expected)).isTrue();
        assertThat(store.readShard(expected)).extracting(TelemetryDocument::id).containsExactly("alert-1");
    }

    @Test
    void emptyBatchWritesNothing(@TempDir Path root) {
        var writer = new IndexWriterService(store, props(root, 4));
        assertThat(writer.index(List.of())).isZero();
    }

    private static IndexProperties props(Path root, int shards) {
        var props = new IndexProperties();
        props.getStorage().setPath(root.toString());
        props.setShardsPerDay(shards);
        return props;
    }
}
