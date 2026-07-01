package org.kfh.aiops.index;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Amazon S3 / S3-compatible (e.g. MinIO) index storage. Registered so the UI can offer it and the
 * resolver recognises it, but not yet functional — the segment store is filesystem-based and S3 has
 * no append semantics. Wiring this requires the S3 SDK + an object-storage segment model (follow-up).
 */
@Component
public class S3IndexStorageProvider implements IndexStorageProvider {

    @Override
    public Set<IndexStorageType> supportedTypes() {
        return EnumSet.of(IndexStorageType.S3);
    }

    @Override
    public boolean isFilesystem() {
        return false;
    }

    @Override
    public Path resolveRoot(String endpoint) {
        throw new UnsupportedOperationException(
                "S3 index storage is not yet wired (needs the object-storage client). Use a filesystem/NFS/SMB/PVC "
                        + "path for now, or add the S3 SDK to enable this provider.");
    }
}
