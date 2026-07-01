package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class FilesystemArchiveStoreTest {

    @Test
    void gzipsSegmentToArchivePathAndRoundTrips(@TempDir Path index, @TempDir Path archiveRoot) throws IOException {
        var props = new IndexProperties();
        props.getArchive().setPath(archiveRoot.toString());
        var store = new FilesystemArchiveStore(props);

        var shard = index.resolve(Path.of("KW", "PROD", "alerts", "2026-05-01", "shard-00"));
        Files.createDirectories(shard);
        var content = "{\"id\":\"a\"}\n{\"id\":\"b\"}\n";
        Files.writeString(shard.resolve("segment.jsonl"), content);

        var ref = store.archiveShard(shard, index.relativize(shard));

        assertThat(ref).isPresent();
        var gz = archiveRoot.resolve(Path.of("KW", "PROD", "alerts", "2026-05-01", "shard-00", "segment.jsonl.gz"));
        assertThat(Files.exists(gz)).isTrue();
        try (var in = new GZIPInputStream(Files.newInputStream(gz))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo(content);
        }
    }

    @Test
    void emptyWhenShardHasNoSegment(@TempDir Path index, @TempDir Path archiveRoot) {
        var props = new IndexProperties();
        props.getArchive().setPath(archiveRoot.toString());
        assertThat(new FilesystemArchiveStore(props).archiveShard(index.resolve("nope"), Path.of("x"))).isEmpty();
    }
}
