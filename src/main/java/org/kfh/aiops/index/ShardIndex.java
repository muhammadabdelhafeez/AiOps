package org.kfh.aiops.index;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.kfh.aiops.index.model.IndexQuery;
import org.kfh.aiops.index.model.TelemetryDocument;

/**
 * In-memory parsed documents + inverted postings for a single shard. Built once per shard segment
 * and cached (see {@link ShardIndexCache}) so repeated searches skip JSON re-parsing and resolve
 * exact-match keyword filters by posting-list intersection instead of scanning every document.
 */
final class ShardIndex {

    private final List<TelemetryDocument> docs;
    /** field -&gt; lower-cased value -&gt; document indices. */
    private final Map<String, Map<String, List<Integer>>> postings;
    private final long segmentSize;

    private ShardIndex(List<TelemetryDocument> docs, Map<String, Map<String, List<Integer>>> postings, long segmentSize) {
        this.docs = docs;
        this.postings = postings;
        this.segmentSize = segmentSize;
    }

    static ShardIndex build(List<TelemetryDocument> docs, long segmentSize) {
        Map<String, Map<String, List<Integer>>> postings = new HashMap<>();
        for (var i = 0; i < docs.size(); i++) {
            var d = docs.get(i);
            add(postings, "severity", d.severity(), i);
            add(postings, "sourceSystem", d.sourceSystem(), i);
            add(postings, "applicationId", d.applicationId(), i);
            add(postings, "serviceId", d.serviceId(), i);
            add(postings, "resourceId", d.resourceId(), i);
            add(postings, "traceId", d.traceId(), i);
            add(postings, "correlationId", d.correlationId(), i);
        }
        return new ShardIndex(docs, postings, segmentSize);
    }

    long segmentSize() {
        return segmentSize;
    }

    /** Documents in this shard matching the query (unsorted); time/tenant/text applied after postings. */
    List<TelemetryDocument> search(IndexQuery query, Instant from, Instant to, UUID tenantId) {
        Set<Integer> candidates = null;
        candidates = narrow(candidates, "severity", query.severity());
        candidates = narrow(candidates, "sourceSystem", query.sourceSystem());
        candidates = narrow(candidates, "applicationId", query.applicationId());
        candidates = narrow(candidates, "serviceId", query.serviceId());
        candidates = narrow(candidates, "resourceId", query.resourceId());
        candidates = narrow(candidates, "traceId", query.traceId());
        candidates = narrow(candidates, "correlationId", query.correlationId());

        var indices = candidates == null
                ? IntStream.range(0, docs.size()).boxed().toList()
                : List.copyOf(candidates);

        var text = query.text();
        var out = new ArrayList<TelemetryDocument>();
        for (var i : indices) {
            var d = docs.get(i);
            if (d.timestamp().isBefore(from) || d.timestamp().isAfter(to)) {
                continue;
            }
            if (tenantId != null && d.tenantId() != null && !tenantId.equals(d.tenantId())) {
                continue;
            }
            if (text != null && !text.isBlank()) {
                var message = d.message() == null ? "" : d.message();
                if (!message.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                    continue;
                }
            }
            out.add(d);
        }
        return out;
    }

    private Set<Integer> narrow(Set<Integer> current, String field, String filter) {
        if (filter == null || filter.isBlank()) {
            return current;
        }
        var matches = postings.getOrDefault(field, Map.of())
                .getOrDefault(filter.trim().toLowerCase(Locale.ROOT), List.of());
        if (current == null) {
            return new HashSet<>(matches);
        }
        current.retainAll(new HashSet<>(matches));
        return current;
    }

    private static void add(Map<String, Map<String, List<Integer>>> postings, String field, String value, int index) {
        if (value == null || value.isBlank()) {
            return;
        }
        postings.computeIfAbsent(field, k -> new HashMap<>())
                .computeIfAbsent(value.trim().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                .add(index);
    }
}
