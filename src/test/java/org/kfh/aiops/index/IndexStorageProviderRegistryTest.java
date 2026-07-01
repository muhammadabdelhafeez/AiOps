package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class IndexStorageProviderRegistryTest {

    private final IndexStorageProviderRegistry registry = new IndexStorageProviderRegistry(List.of(
            new FilesystemIndexStorageProvider(), new S3IndexStorageProvider(), new AzureBlobIndexStorageProvider()));

    @Test
    void routesFilesystemTypesToFilesystemProvider() {
        assertThat(registry.forType(IndexStorageType.LOCAL)).isInstanceOf(FilesystemIndexStorageProvider.class);
        assertThat(registry.forType(IndexStorageType.NFS).isFilesystem()).isTrue();
        assertThat(registry.forType(IndexStorageType.SMB).isFilesystem()).isTrue();
        assertThat(registry.forType(IndexStorageType.PVC).isFilesystem()).isTrue();
    }

    @Test
    void routesCloudTypesToObjectStoreProviders() {
        assertThat(registry.forType(IndexStorageType.S3)).isInstanceOf(S3IndexStorageProvider.class);
        assertThat(registry.forType(IndexStorageType.AZURE_BLOB)).isInstanceOf(AzureBlobIndexStorageProvider.class);
    }
}
