package org.kfh.aiops.index;

import java.nio.file.Path;
import java.util.Map;
import org.kfh.aiops.platform.config.SettingsService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Resolves the index storage root for a tenant/country/environment. Reads the Settings INDEX_STORAGE
 * connector (provider + endpoint), selects the matching {@link IndexStorageProvider} from the
 * registry, and returns a filesystem root the engine can use. Filesystem providers (LOCAL/NFS/SMB/PVC
 * — Linux, Windows, OpenShift) resolve directly; object-storage providers (S3/Azure) aren't wired
 * yet, so we log a warning and fall back to {@code kfh.index.storage.path} until they are.
 */
@Component
public class IndexStorageResolver {

    private static final Logger log = LoggerFactory.getLogger(IndexStorageResolver.class);

    private final SettingsService settingsService;
    private final IndexProperties properties;
    private final IndexStorageProviderRegistry providerRegistry;

    public IndexStorageResolver(SettingsService settingsService, IndexProperties properties,
            IndexStorageProviderRegistry providerRegistry) {
        this.settingsService = settingsService;
        this.properties = properties;
        this.providerRegistry = providerRegistry;
    }

    public Path resolveRoot(TenantContext ctx) {
        var configured = settingsService.resolveIndexStorage(ctx);
        if (configured.isEmpty()) {
            return defaultRoot();
        }
        var cfg = configured.get();
        var type = IndexStorageType.from(str(cfg.get("provider")), properties.getStorage().getProvider());
        var provider = providerRegistry.forType(type);
        if (!provider.isFilesystem()) {
            log.warn("Index storage provider {} is not wired yet; using filesystem default {}",
                    type, properties.getStorage().getPath());
            return defaultRoot();
        }
        var endpoint = str(cfg.get("endpoint"));
        return provider.resolveRoot(endpoint.isBlank() ? properties.getStorage().getPath() : endpoint);
    }

    private Path defaultRoot() {
        return Path.of(properties.getStorage().getPath());
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
