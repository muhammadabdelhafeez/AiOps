package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class IndexRetentionServiceTest {

    @Test
    void deletesExpiredDateDirectoriesKeepsRecent(@TempDir Path root) throws IOException {
        var props = new IndexProperties();
        props.getStorage().setPath(root.toString()); // alerts retention = 30 days (default)
        var service = new IndexRetentionService(props);

        var alerts = root.resolve(Path.of("KW", "PROD", "alerts"));
        var expired = alerts.resolve("2026-05-01").resolve("shard-00");
        var recent = alerts.resolve("2026-06-28").resolve("shard-00");
        Files.createDirectories(expired);
        Files.createDirectories(recent);
        Files.writeString(expired.resolve("segment.jsonl"), "x");
        Files.writeString(recent.resolve("segment.jsonl"), "y");

        var deleted = service.purgeExpired(root, LocalDate.of(2026, 7, 1)); // cutoff = 2026-06-01

        assertThat(deleted).isEqualTo(1);
        assertThat(Files.exists(alerts.resolve("2026-05-01"))).isFalse();
        assertThat(Files.exists(alerts.resolve("2026-06-28"))).isTrue();
    }

    @Test
    void missingRootReturnsZero(@TempDir Path root) {
        var service = new IndexRetentionService(new IndexProperties());
        assertThat(service.purgeExpired(root.resolve("does-not-exist"), LocalDate.of(2026, 7, 1))).isZero();
    }
}
