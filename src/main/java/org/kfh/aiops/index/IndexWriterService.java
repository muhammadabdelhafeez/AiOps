package org.kfh.aiops.index;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.springframework.stereotype.Service;

/**
 * Writes normalized telemetry into the sharded, append-only index (causal funnel Stage 3). Documents
 * are routed to {@code {country}/{env}/{kind}/{date}/shard-NN} by a stable hash of their id and
 * appended in per-shard batches. Called by the (future) ingestion/normalization pipeline.
 */
@Service
public class IndexWriterService {

    private final SegmentStore segmentStore;
    private final IndexProperties properties;

    public IndexWriterService(SegmentStore segmentStore, IndexProperties properties) {
        this.segmentStore = segmentStore;
        this.properties = properties;
    }

    /**
     * Index a batch of documents. Returns the number written.
     */
    public int index(List<TelemetryDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        var root = Path.of(properties.getStorage().getPath());
        var shardsPerDay = properties.getShardsPerDay();

        // Group by target shard directory so each shard is appended once per batch.
        Map<Path, List<TelemetryDocument>> byShard = new LinkedHashMap<>();
        for (var doc : documents) {
            var date = LocalDate.ofInstant(doc.timestamp(), ZoneOffset.UTC);
            var shard = ShardKey.shardFor(doc.id(), shardsPerDay);
            var key = new ShardKey(doc.countryCode(), doc.environment(), doc.kind(), date, shard);
            byShard.computeIfAbsent(root.resolve(key.relativePath()), k -> new ArrayList<>()).add(doc);
        }
        byShard.forEach(segmentStore::append);
        return documents.size();
    }
}
