package org.kfh.aiops.index;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.springframework.stereotype.Component;

/**
 * Filesystem/NFS {@link ArchiveStore}: gzip-copies a shard's segment to
 * {@code {archive.path}/{country}/{env}/{kind}/{date}/shard-NN/segment.jsonl.gz}. This is the
 * on-prem "object storage" target; a cloud (S3/Azure Blob) implementation would swap in here.
 */
@Component
public class FilesystemArchiveStore implements ArchiveStore {

    private final IndexProperties properties;

    public FilesystemArchiveStore(IndexProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<String> archiveShard(Path shardDir, Path relativePath) {
        var segment = shardDir.resolve(SegmentStore.SEGMENT_FILE);
        if (!Files.isRegularFile(segment)) {
            return Optional.empty();
        }
        var target = Path.of(properties.getArchive().getPath())
                .resolve(relativePath)
                .resolve(SegmentStore.SEGMENT_FILE + ".gz");
        try {
            Files.createDirectories(target.getParent());
            try (var in = Files.newInputStream(segment);
                 var out = new GZIPOutputStream(Files.newOutputStream(target,
                         StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                in.transferTo(out);
            }
            return Optional.of(target.toUri().toString());
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to archive shard " + shardDir + " to " + target, ex);
        }
    }
}
