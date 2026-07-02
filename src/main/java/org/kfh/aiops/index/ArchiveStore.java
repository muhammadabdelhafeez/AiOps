package org.kfh.aiops.index;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Moves a cold shard's segment to the archive tier (§10 "store raw compressed payload in object
 * storage"). The filesystem/NFS implementation ships now; S3 / Azure Blob implementations are
 * drop-in replacements behind this interface once those SDKs are added.
 */
public interface ArchiveStore {

    /**
     * Archive the shard's segment under {@code relativePath} ({@code country/kind/date/shard-NN}).
     * Returns the archive reference (path/URI), or empty if the shard has no segment.
     */
    Optional<String> archiveShard(Path shardDir, Path relativePath);
}
