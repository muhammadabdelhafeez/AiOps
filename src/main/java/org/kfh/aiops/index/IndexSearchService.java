package org.kfh.aiops.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import org.kfh.aiops.index.model.IndexQuery;
import org.kfh.aiops.index.model.IndexSearchResult;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.index.model.TelemetryKind;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

/**
 * Searches the custom index (§10). Query flow: <b>time-partition prune → country/environment filter
 * → parallel postings-backed scan</b> across matching shards. Only shard directories whose date
 * falls in the requested window are opened; each shard's cached {@link ShardIndex} resolves
 * exact-match filters via posting-list intersection, then applies time/tenant/text. Results merge,
 * sort newest-first, and paginate.
 */
@Service
public class IndexSearchService {

    private final ShardIndexCache shardIndexCache;
    private final IndexProperties properties;
    private final IndexStorageResolver storageResolver;
    private final CountryAccessGuard countryAccessGuard;

    public IndexSearchService(ShardIndexCache shardIndexCache, IndexProperties properties,
            IndexStorageResolver storageResolver, CountryAccessGuard countryAccessGuard) {
        this.shardIndexCache = shardIndexCache;
        this.properties = properties;
        this.storageResolver = storageResolver;
        this.countryAccessGuard = countryAccessGuard;
    }

    public IndexSearchResult search(TenantContext ctx, IndexQuery query) {
        var started = System.nanoTime();
        var root = storageResolver.resolveRoot(ctx);
        var country = firstNonBlank(query.country(), ctx.countryCode());
        // Country isolation: a caller may only search a country they are scoped to (or hold
        // COUNTRY_GLOBAL_VIEW). Enforced before any shard directory is opened.
        countryAccessGuard.requireAccess(ctx, country);
        var environment = firstNonBlank(query.environment(), ctx.environment());
        var from = query.from() == null ? Instant.EPOCH : query.from();
        var to = query.to() == null ? Instant.now() : query.to();

        var shardDirs = candidateShards(root, country, environment, query.kindsOrAll(), from, to);
        var hits = scanShards(shardDirs, query, ctx, from, to);

        hits.sort(Comparator.comparing(TelemetryDocument::timestamp).reversed());
        var total = hits.size();
        var page = query.pageOrDefault();
        var size = query.sizeOrDefault();
        var offset = (int) Math.min((long) page * size, total);
        var end = Math.min(offset + size, total);
        var pageHits = new ArrayList<>(hits.subList(offset, end));
        return new IndexSearchResult(total, page, size, elapsedMs(started), pageHits);
    }

    /** Enumerate shard directories that survive time + country/env partition pruning. */
    private List<Path> candidateShards(Path root, String country, String environment,
            List<TelemetryKind> kinds, Instant from, Instant to) {
        var dirs = new ArrayList<Path>();
        var firstDate = LocalDate.ofInstant(from, ZoneOffset.UTC);
        var lastDate = LocalDate.ofInstant(to, ZoneOffset.UTC);
        var shards = Math.max(1, properties.getShardsPerDay());
        for (var scopedCountry : expandCountries(root, country)) {
            for (var kind : kinds) {
                for (var date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
                    for (var shard = 0; shard < shards; shard++) {
                        var dir = root.resolve(new ShardKey(scopedCountry, environment, kind, date, shard).relativePath());
                        if (Files.isDirectory(dir)) {
                            dirs.add(dir);
                        }
                    }
                }
            }
        }
        return dirs;
    }

    /**
     * A concrete country searches only its own partition; the global {@code ALL} scope (a global-admin
     * session) fans out across every country partition present under the index root — otherwise alerts
     * ingested under e.g. {@code KW} would be invisible to an {@code ALL}-scoped caller.
     */
    private List<String> expandCountries(Path root, String country) {
        if (country != null && !country.isBlank()
                && !CountryAccessGuard.ALL_COUNTRIES_SCOPE.equalsIgnoreCase(country.trim())) {
            return List.of(country.trim());
        }
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isDirectory)
                    .map(dir -> dir.getFileName().toString())
                    .filter(name -> !CountryAccessGuard.ALL_COUNTRIES_SCOPE.equalsIgnoreCase(name))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            return List.of();
        }
    }

    private List<TelemetryDocument> scanShards(List<Path> shardDirs, IndexQuery query,
            TenantContext ctx, Instant from, Instant to) {
        if (shardDirs.isEmpty()) {
            return new ArrayList<>();
        }
        var parallelism = Math.max(1, Math.min(properties.getSearchParallelism(), shardDirs.size()));
        var pool = new ForkJoinPool(parallelism);
        try {
            return pool.submit(() -> shardDirs.parallelStream()
                    .flatMap(dir -> shardIndexCache.get(dir).search(query, from, to, ctx.tenantId()).stream())
                    .collect(Collectors.toCollection(ArrayList::new))).get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Index search interrupted", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Index search failed: "
                    + (ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage()), ex);
        } finally {
            pool.shutdown();
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a.trim() : b;
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0, Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
    }
}
