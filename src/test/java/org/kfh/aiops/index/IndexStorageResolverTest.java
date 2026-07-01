package org.kfh.aiops.index;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
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

    private TenantContext ctx() {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), "KW", "PROD", "corr-1", Set.of());
    }

    @Test
    void usesSettingsPathWhenConfigured() {
        var ctx = ctx();
        var props = new IndexProperties();
        props.getStorage().setPath("/default/aiops-index");
        when(settingsService.resolveIndexStorage(ctx)).thenReturn(Optional.of("/data/custom-index"));

        var root = new IndexStorageResolver(settingsService, props).resolveRoot(ctx);

        assertThat(root).isEqualTo(Path.of("/data/custom-index"));
    }

    @Test
    void fallsBackToPropertiesWhenUnset() {
        var ctx = ctx();
        var props = new IndexProperties();
        props.getStorage().setPath("/default/aiops-index");
        when(settingsService.resolveIndexStorage(ctx)).thenReturn(Optional.empty());

        var root = new IndexStorageResolver(settingsService, props).resolveRoot(ctx);

        assertThat(root).isEqualTo(Path.of("/default/aiops-index"));
    }
}
