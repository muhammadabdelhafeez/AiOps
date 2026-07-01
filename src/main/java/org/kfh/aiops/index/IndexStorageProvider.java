package org.kfh.aiops.index;

import java.nio.file.Path;
import java.util.Set;

/**
 * Pluggable index storage backend (the "plugin"). One implementation per storage family; the
 * {@link IndexStorageProviderRegistry} selects by {@link IndexStorageType}. Filesystem providers
 * return a {@code Path} the engine reads/writes directly; object-storage providers are not
 * filesystem-backed and throw from {@link #resolveRoot(String)} until their client is wired.
 */
public interface IndexStorageProvider {

    Set<IndexStorageType> supportedTypes();

    /** True if this provider exposes a {@code java.nio.file.Path} the segment store can use directly. */
    boolean isFilesystem();

    /**
     * Resolve the filesystem root for {@code endpoint}. Throws {@link UnsupportedOperationException}
     * for object-storage providers (no filesystem path).
     */
    Path resolveRoot(String endpoint);
}
