package org.kfh.aiops.index;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;
import org.kfh.aiops.index.model.IndexQuery;
import org.kfh.aiops.index.model.IndexSearchResult;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.index.model.TelemetryKind;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

/**
 * Searches the custom index (§10). Query flow: <b>time-partition prune → country/environment filter
 * → parallel filtered scan</b> across matching shards. Only shard directories whose date falls in
 * the requested window are opened, then their documents are filtered by the exact-match/text
 * predicates. Results are merged, sorted newest-first, and paginated.
 *
 * <p>Increment 2 replaces the in-shard scan with a postings-list lookup for high-cardinality fields.
 */
@Service
public class IndexSearchService {

    private final SegmentStore segmentStore;
    private final IndexProperties properties;

    public IndexSearchService(SegmentStore segmentStore, IndexProperties properties) {
        this.segmentStore = segmentStore;
        this.properties = properties;
    }

    public IndexSearchResult search(TenantContext ctx, IndexQuery query) {
        var started = System.nanoTime();
        var root = Path.of(properties.getStorage().getPath());
        var country = firstNonBlank(query.country(), ctx.countryCode());
        var environment = firstNonBlank(query.environment(), ctx.environment());
        var from = query.from() == null ? Instant.EPOCH : query.from();
        var to = query.to() == null ? Instant.now() : query.to();

        var shardDirs = candidateShards(root, country, environment, query.kindsOrAll(), from, to);
        var hits = scanShards(shardDirs, query, ctx, from, to);

        hits.sort(Comparator.comparing(TelemetryDocument::timestamp).reversed());
        var total = hits.size();
        var page = query.pageOrDefault();
        var size = query.sizeOrDefault();
        var offset = Math.min((long) page * size, total);
        var end = Math.min(offset + size, total);
        var pageHits = new ArrayList<>(hits.subList((int) offset, (int) end));
        return new IndexSearchResult(total, page, size, elapsedMs(started), pageHits);
    }

    /** Enumerate shard directories that survive time + country/env partition pruning. */
    private List<Path> candidateShards(Path root, String country, String environment,
            List<TelemetryKind> kinds, Instant from, Instant to) {
        var dirs = new ArrayList<Path>();
        var firstDate = LocalDate.ofInstant(from, ZoneOffset.UTC);
        var lastDate = LocalDate.ofInstant(to, ZoneOffset.UTC);
        for (var kind : kinds) {
            for (var date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
                for (var shard = 0; shard < Math.max(1, properties.getShardsPerDay()); shard++) {
                    var dir = root.resolve(new ShardKey(country, environment, kind, date, shard).relativePath());
                    if (Files.isDirectory(dir)) {
                        dirs.add(dir);
                    }
                }
            }
        }
        return dirs;
    }

    private List<TelemetryDocument> scanShards(List<Path> shardDirs, IndexQuery query,
            TenantContext ctx, Instant from, Instant to) {
        if (shardDirs.isEmpty()) {
            return new ArrayList<>();
        }
        var parallelism = Math.max(1, properties.getSearchParallelism());
        var pool = new ForkJoinPool(Math.min(parallelism, Math.max(1, shardDirs.size())));
        try {
            return pool.submit(() -> shardDirs.parallelStream()
                    .flatMap(dir -> segmentStore.readShard(dir).stream())
                    .filter(doc -> matches(doc, query, ctx, from, to))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new))).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Index search interrupted", ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            throw new IllegalStateException("Index search failed: "
                    + (ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage()), ex);
        } finally {
            pool.shutdown();
        }
    }

    private boolean matches(TelemetryDocument doc, IndexQuery query, TenantContext ctx, Instant from, Instant to) {
        if (doc.timestamp().isBefore(from) || doc.timestamp().isAfter(to)) {
            return false;
        }
        if (ctx.tenantId() != null && doc.tenantId() != null && !ctx.tenantId().equals(doc.tenantId())) {
            return false;
        }
        if (!eq(query.severity(), doc.severity())
                || !eq(query.sourceSystem(), doc.sourceSystem())
                || !eq(query.applicationId(), doc.applicationId())
                || !eq(query.serviceId(), doc.serviceId())
                || !eq(query.resourceId(), doc.resourceId())
                || !eq(query.traceId(), doc.traceId())
                || !eq(query.correlationId(), doc.correlationId())) {
            return false;
        }
        var text = query.text();
        if (text != null && !text.isBlank()) {
            var message = doc.message() == null ? "" : doc.message();
            return message.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
        }
        return true;
    }

    /** Exact-match keyword filter: blank filter passes; otherwise case-insensitive equality. */
    private static boolean eq(String filter, String value) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return value != null && filter.trim().equalsIgnoreCase(value.trim());
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a.trim() : b;
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }
}
