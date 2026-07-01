package org.kfh.aiops.index;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import org.kfh.aiops.index.model.TelemetryKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Deletes index shard date-directories older than the per-kind retention window (§10 "retention
 * rules per country and environment"). Archiving cold data to object storage is a follow-up (needs
 * the object-storage client). The scheduled trigger only fires when {@code @EnableScheduling} is on;
 * {@link #purgeExpired(Path, LocalDate)} can also be invoked directly (and is unit-tested).
 */
@Service
public class IndexRetentionService {

    private static final Logger log = LoggerFactory.getLogger(IndexRetentionService.class);

    private final IndexProperties properties;
    private final ArchiveStore archiveStore;

    public IndexRetentionService(IndexProperties properties, ArchiveStore archiveStore) {
        this.properties = properties;
        this.archiveStore = archiveStore;
    }

    @Scheduled(cron = "${kfh.index.retention.cron:0 30 2 * * *}")
    public void purgeExpiredScheduled() {
        var deleted = purgeExpired(Path.of(properties.getStorage().getPath()), LocalDate.now(ZoneOffset.UTC));
        if (deleted > 0) {
            log.info("Index retention purged {} expired shard date-directories", deleted);
        }
    }

    /**
     * Delete every {@code {country}/{env}/{kind}/{date}} directory whose date is older than the
     * kind's retention window relative to {@code asOf}. Returns the number of date-directories removed.
     */
    public int purgeExpired(Path root, LocalDate asOf) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        var deleted = 0;
        for (var countryDir : childDirs(root)) {
            for (var envDir : childDirs(countryDir)) {
                for (var kindDir : childDirs(envDir)) {
                    var kind = parseKind(kindDir.getFileName().toString());
                    if (kind == null) {
                        continue;
                    }
                    var cutoff = asOf.minusDays(retentionDays(kind));
                    for (var dateDir : childDirs(kindDir)) {
                        var date = parseDate(dateDir.getFileName().toString());
                        if (date != null && date.isBefore(cutoff)) {
                            if (properties.getArchive().isEnabled()) {
                                for (var shardDir : childDirs(dateDir)) {
                                    archiveStore.archiveShard(shardDir, root.relativize(shardDir));
                                }
                            }
                            deleteRecursively(dateDir);
                            deleted++;
                        }
                    }
                }
            }
        }
        return deleted;
    }

    private int retentionDays(TelemetryKind kind) {
        var r = properties.getRetentionDays();
        return switch (kind) {
            case ALERTS -> r.getAlerts();
            case LOGS -> r.getLogs();
            case METRICS -> r.getMetrics();
            case TRACES -> r.getTraces();
            case CHANGES -> r.getChanges();
        };
    }

    private static java.util.List<Path> childDirs(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory).toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to list index directory " + dir, ex);
        }
    }

    private static TelemetryKind parseKind(String name) {
        try {
            return TelemetryKind.from(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static LocalDate parseDate(String name) {
        try {
            return LocalDate.parse(name);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ex) {
                    throw new UncheckedIOException("Failed to delete " + path, ex);
                }
            });
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to walk " + dir, ex);
        }
    }
}
