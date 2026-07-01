package org.kfh.aiops.index;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Caches a {@link ShardIndex} per shard directory, keyed by the segment byte size. Because segments
 * are append-only, a size change is a reliable "the shard grew" signal that invalidates the cached
 * parsed documents + postings and triggers a rebuild.
 */
@Component
public class ShardIndexCache {

    private final SegmentStore segmentStore;
    private final Map<Path, ShardIndex> cache = new ConcurrentHashMap<>();

    public ShardIndexCache(SegmentStore segmentStore) {
        this.segmentStore = segmentStore;
    }

    ShardIndex get(Path shardDir) {
        var size = segmentStore.segmentSize(shardDir);
        var existing = cache.get(shardDir);
        if (existing != null && existing.segmentSize() == size) {
            return existing;
        }
        var rebuilt = ShardIndex.build(segmentStore.readShard(shardDir), size);
        cache.put(shardDir, rebuilt);
        return rebuilt;
    }

    /** Number of shards currently cached (test/diagnostics). */
    public int cachedShardCount() {
        return cache.size();
    }
}
