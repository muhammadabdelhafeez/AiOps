package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IndexStorageTypeTest {

    @Test
    void filesystemTypesAreFilesystem() {
        assertThat(IndexStorageType.LOCAL.isFilesystem()).isTrue();
        assertThat(IndexStorageType.NFS.isFilesystem()).isTrue();
        assertThat(IndexStorageType.SMB.isFilesystem()).isTrue();
        assertThat(IndexStorageType.PVC.isFilesystem()).isTrue();
    }

    @Test
    void objectStoreTypesAreNotFilesystem() {
        assertThat(IndexStorageType.S3.isFilesystem()).isFalse();
        assertThat(IndexStorageType.AZURE_BLOB.isFilesystem()).isFalse();
    }

    @Test
    void parsesValuesAndAliases() {
        assertThat(IndexStorageType.from("filesystem", null)).isEqualTo(IndexStorageType.LOCAL);
        assertThat(IndexStorageType.from("PVC", null)).isEqualTo(IndexStorageType.PVC);
        assertThat(IndexStorageType.from("azure", null)).isEqualTo(IndexStorageType.AZURE_BLOB);
        assertThat(IndexStorageType.from(null, "NFS")).isEqualTo(IndexStorageType.NFS);
        assertThat(IndexStorageType.from("bogus", null)).isEqualTo(IndexStorageType.LOCAL);
    }
}
