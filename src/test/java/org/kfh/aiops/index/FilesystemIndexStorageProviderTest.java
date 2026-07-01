package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FilesystemIndexStorageProviderTest {

    private final FilesystemIndexStorageProvider provider = new FilesystemIndexStorageProvider();

    @Test
    void isFilesystemAndCoversPathBackedTypes() {
        assertThat(provider.isFilesystem()).isTrue();
        assertThat(provider.supportedTypes()).contains(
                IndexStorageType.LOCAL, IndexStorageType.NFS, IndexStorageType.SMB, IndexStorageType.PVC);
    }

    @Test
    void resolvesEndpointToPath() {
        assertThat(provider.resolveRoot("/opt/aiops-index")).isEqualTo(Path.of("/opt/aiops-index"));
    }

    @Test
    void rejectsBlankPath() {
        assertThatThrownBy(() -> provider.resolveRoot("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
