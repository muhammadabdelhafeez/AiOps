package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kfh.aiops.platform.config.SettingsService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexStorageResolverTest {

    @Mock
    private SettingsService settingsService;

    private final IndexStorageProviderRegistry registry = new IndexStorageProviderRegistry(List.of(
            new FilesystemIndexStorageProvider(), new S3IndexStorageProvider(), new AzureBlobIndexStorageProvider()));

    private TenantContext ctx() {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr-1", Set.of());
    }

    private IndexProperties props() {
        var props = new IndexProperties();
        props.getStorage().setPath("/default/aiops-index");
        return props;
    }

    @Test
    void usesConfiguredFilesystemPath() {
        var ctx = ctx();
        when(settingsService.resolveIndexStorage(ctx))
                .thenReturn(Optional.of(Map.of("provider", "LOCAL", "endpoint", "/data/custom-index")));

        var root = new IndexStorageResolver(settingsService, props(), registry).resolveRoot(ctx);

        assertThat(root).isEqualTo(Path.of("/data/custom-index"));
    }

    @Test
    void resolvesSmbUncPath() {
        var ctx = ctx();
        when(settingsService.resolveIndexStorage(ctx))
                .thenReturn(Optional.of(Map.of("provider", "SMB", "endpoint", "\\\\srv\\aiops-index")));

        var root = new IndexStorageResolver(settingsService, props(), registry).resolveRoot(ctx);

        assertThat(root).isEqualTo(Path.of("\\\\srv\\aiops-index"));
    }

    @Test
    void fallsBackToDefaultForObjectStorageUntilWired() {
        var ctx = ctx();
        when(settingsService.resolveIndexStorage(ctx))
                .thenReturn(Optional.of(Map.of("provider", "S3", "endpoint", "s3://bucket/idx")));

        var root = new IndexStorageResolver(settingsService, props(), registry).resolveRoot(ctx);

        assertThat(root).isEqualTo(Path.of("/default/aiops-index"));
    }

    @Test
    void fallsBackToPropertiesWhenUnset() {
        var ctx = ctx();
        when(settingsService.resolveIndexStorage(ctx)).thenReturn(Optional.empty());

        var root = new IndexStorageResolver(settingsService, props(), registry).resolveRoot(ctx);

        assertThat(root).isEqualTo(Path.of("/default/aiops-index"));
    }
}
