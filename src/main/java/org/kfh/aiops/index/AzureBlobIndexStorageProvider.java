package org.kfh.aiops.index;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Azure Blob Storage index storage. Registered for UI/resolver recognition but not yet functional —
 * see {@link S3IndexStorageProvider}. Wiring requires the Azure Storage Blob SDK + an object-storage
 * segment model (follow-up).
 */
@Component
public class AzureBlobIndexStorageProvider implements IndexStorageProvider {

    @Override
    public Set<IndexStorageType> supportedTypes() {
        return EnumSet.of(IndexStorageType.AZURE_BLOB);
    }

    @Override
    public boolean isFilesystem() {
        return false;
    }

    @Override
    public Path resolveRoot(String endpoint) {
        throw new UnsupportedOperationException(
                "Azure Blob index storage is not yet wired (needs the Azure Storage Blob client). Use a "
                        + "filesystem/NFS/SMB/PVC path for now.");
    }
}
