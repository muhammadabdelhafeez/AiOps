package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class S3IndexStorageProviderTest {

    private final S3IndexStorageProvider provider = new S3IndexStorageProvider();

    @Test
    void isNotFilesystem() {
        assertThat(provider.isFilesystem()).isFalse();
        assertThat(provider.supportedTypes()).containsExactly(IndexStorageType.S3);
    }

    @Test
    void resolveRootThrowsUntilWired() {
        assertThatThrownBy(() -> provider.resolveRoot("s3://bucket/index"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
