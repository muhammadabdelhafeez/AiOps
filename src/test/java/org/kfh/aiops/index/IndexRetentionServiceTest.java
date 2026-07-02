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
        props.getStorage().setPath(root.toString()); // alerts retention = 30 days; archive disabled
        var service = new IndexRetentionService(props, new FilesystemArchiveStore(props));

        var alerts = root.resolve(Path.of("KW", "alerts"));
        Files.createDirectories(alerts.resolve("2026-05-01").resolve("shard-00"));
        Files.createDirectories(alerts.resolve("2026-06-28").resolve("shard-00"));

        var deleted = service.purgeExpired(root, LocalDate.of(2026, 7, 1)); // cutoff = 2026-06-01

        assertThat(deleted).isEqualTo(1);
        assertThat(Files.exists(alerts.resolve("2026-05-01"))).isFalse();
        assertThat(Files.exists(alerts.resolve("2026-06-28"))).isTrue();
    }

    @Test
    void archivesShardBeforeDeleteWhenEnabled(@TempDir Path root, @TempDir Path archiveRoot) throws IOException {
        var props = new IndexProperties();
        props.getStorage().setPath(root.toString());
        props.getArchive().setEnabled(true);
        props.getArchive().setPath(archiveRoot.toString());
        var service = new IndexRetentionService(props, new FilesystemArchiveStore(props));

        var expiredShard = root.resolve(Path.of("KW", "alerts", "2026-05-01", "shard-00"));
        Files.createDirectories(expiredShard);
        Files.writeString(expiredShard.resolve("segment.jsonl"), "{\"id\":\"a\"}\n");

        var deleted = service.purgeExpired(root, LocalDate.of(2026, 7, 1));

        assertThat(deleted).isEqualTo(1);
        assertThat(Files.exists(root.resolve(Path.of("KW", "alerts", "2026-05-01")))).isFalse();
        assertThat(Files.exists(archiveRoot.resolve(
                Path.of("KW", "alerts", "2026-05-01", "shard-00", "segment.jsonl.gz")))).isTrue();
    }

    @Test
    void missingRootReturnsZero(@TempDir Path root) {
        var props = new IndexProperties();
        var service = new IndexRetentionService(props, new FilesystemArchiveStore(props));
        assertThat(service.purgeExpired(root.resolve("does-not-exist"), LocalDate.of(2026, 7, 1))).isZero();
    }
}
