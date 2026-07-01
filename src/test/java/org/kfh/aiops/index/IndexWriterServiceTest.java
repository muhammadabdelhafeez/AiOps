package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.index.model.TelemetryKind;
import org.kfh.aiops.platform.config.SettingsService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexWriterServiceTest {

    private final SegmentStore store = new SegmentStore(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Mock
    private SettingsService settingsService;

    @Test
    void routesDocumentToCountryEnvKindDateShard(@TempDir Path root) {
        var props = props(root);
        when(settingsService.resolveIndexStorage(any())).thenReturn(Optional.empty());
        var writer = new IndexWriterService(store, props, new IndexStorageResolver(settingsService, props));

        var written = writer.index(ctx(), List.of(doc("alert-1")));

        var shard = ShardKey.shardFor("alert-1", 4);
        var expected = root.resolve(new ShardKey("KW", "PROD", TelemetryKind.ALERTS,
                LocalDate.of(2026, 6, 7), shard).relativePath());
        assertThat(written).isEqualTo(1);
        assertThat(Files.isDirectory(expected)).isTrue();
        assertThat(store.readShard(expected)).extracting(TelemetryDocument::id).containsExactly("alert-1");
    }

    @Test
    void emptyBatchWritesNothing(@TempDir Path root) {
        var props = props(root);
        var writer = new IndexWriterService(store, props, new IndexStorageResolver(settingsService, props));
        assertThat(writer.index(ctx(), List.of())).isZero();
    }

    private static IndexProperties props(Path root) {
        var props = new IndexProperties();
        props.getStorage().setPath(root.toString());
        props.setShardsPerDay(4);
        return props;
    }

    private static TenantContext ctx() {
        return new TenantContext(UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.randomUUID(), "KW", "PROD", "corr-1", Set.of("ALERT_READ"));
    }

    private static TelemetryDocument doc(String id) {
        return new TelemetryDocument(id, Instant.parse("2026-06-07T10:00:00Z"),
                UUID.fromString("00000000-0000-4000-8000-000000000001"), "KW", "PROD", TelemetryKind.ALERTS,
                "SCOM", "MOBILE", "TRANSFER", "SRV-1", "SERVER", "CRITICAL", "T1", "C1", "TX1",
                "cpu high", "obj://raw/1", Map.of());
    }
}
