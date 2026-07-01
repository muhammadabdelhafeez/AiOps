package org.kfh.aiops.index;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Selects the {@link IndexStorageProvider} for a given {@link IndexStorageType}. All provider beans
 * are auto-discovered; unknown types default to the filesystem provider so the engine always has a
 * usable backend.
 */
@Component
public class IndexStorageProviderRegistry {

    private final Map<IndexStorageType, IndexStorageProvider> byType = new EnumMap<>(IndexStorageType.class);
    private final IndexStorageProvider filesystem;

    public IndexStorageProviderRegistry(List<IndexStorageProvider> providers) {
        IndexStorageProvider fs = null;
        for (var provider : providers) {
            for (var type : provider.supportedTypes()) {
                byType.put(type, provider);
            }
            if (provider.isFilesystem()) {
                fs = provider;
            }
        }
        this.filesystem = fs;
    }

    public IndexStorageProvider forType(IndexStorageType type) {
        var provider = byType.get(type);
        return provider != null ? provider : filesystem;
    }
}
