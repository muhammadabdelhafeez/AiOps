package org.kfh.aiops.index;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Filesystem-backed index storage: {@link IndexStorageType#LOCAL} (Linux/Windows local disk),
 * {@link IndexStorageType#NFS} (mount), {@link IndexStorageType#SMB} (Windows UNC / Samba share),
 * and {@link IndexStorageType#PVC} (OpenShift PersistentVolumeClaim mount). All resolve to a
 * {@code java.nio.file.Path}, so the engine's segment store works unchanged on any of them — the
 * only difference is the endpoint string (e.g. {@code /opt/aiops-index}, {@code \\host\share},
 * {@code /var/aiops-index} on a PVC).
 */
@Component
public class FilesystemIndexStorageProvider implements IndexStorageProvider {

    @Override
    public Set<IndexStorageType> supportedTypes() {
        return EnumSet.of(IndexStorageType.LOCAL, IndexStorageType.NFS, IndexStorageType.SMB, IndexStorageType.PVC);
    }

    @Override
    public boolean isFilesystem() {
        return true;
    }

    @Override
    public Path resolveRoot(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Filesystem index storage path is required");
        }
        return Path.of(endpoint.trim());
    }
}
