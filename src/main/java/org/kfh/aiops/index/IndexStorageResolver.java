package org.kfh.aiops.index;

import java.nio.file.Path;
import org.kfh.aiops.platform.config.SettingsService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Component;

/**
 * Resolves the index storage root for a tenant/country/environment. Prefers the Settings-configured
 * INDEX_STORAGE connector path (Settings → Servers &amp; Index), falling back to
 * {@code kfh.index.storage.path}. Mirrors the Part-D Redis resolver so operators can point the index
 * at a validated path from the UI without a redeploy.
 */
@Component
public class IndexStorageResolver {

    private final SettingsService settingsService;
    private final IndexProperties properties;

    public IndexStorageResolver(SettingsService settingsService, IndexProperties properties) {
        this.settingsService = settingsService;
        this.properties = properties;
    }

    public Path resolveRoot(TenantContext ctx) {
        return settingsService.resolveIndexStorage(ctx)
                .filter(path -> !path.isBlank())
                .map(path -> Path.of(path.trim()))
                .orElseGet(() -> Path.of(properties.getStorage().getPath()));
    }
}
