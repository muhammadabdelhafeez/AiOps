package org.kfh.aiops.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.kfh.aiops.index.IndexWriterService;
import org.kfh.aiops.index.model.TelemetryDocument;
import org.kfh.aiops.normalization.fingerprint.FingerprintDedupService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Causal funnel Stages 0→3: turns a batch of raw connector events into deduplicated, indexed
 * telemetry. For each raw event: normalize ({@link TelemetryNormalizer}) → short-window fingerprint
 * dedup ({@link FingerprintDedupService}, Stage&nbsp;2) → collect the fresh documents → index them in
 * one batch ({@link IndexWriterService}, Stage&nbsp;3).
 *
 * <p>Resilience contract: a single unmappable event is counted as {@code failed} and skipped — it
 * never aborts the batch. Redis being unavailable fails open (event treated as new), so alerts are
 * never silently dropped because a dependency is down.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final FingerprintDedupService dedupService;
    private final IndexWriterService indexWriterService;

    public IngestionService(FingerprintDedupService dedupService, IndexWriterService indexWriterService) {
        this.dedupService = dedupService;
        this.indexWriterService = indexWriterService;
    }

    /**
     * Ingest one batch of raw events from a single source. Requires {@code ALERT_INGEST} — the
     * pipeline runs under a system/collector principal, not an end user.
     */
    public IngestionResult ingest(TenantContext ctx, TelemetryNormalizer normalizer, List<Map<String, Object>> rawEvents) {
        ctx.requirePermission("ALERT_INGEST");
        if (rawEvents == null || rawEvents.isEmpty()) {
            return IngestionResult.empty();
        }

        var received = rawEvents.size();
        var failed = 0;
        var duplicates = 0;
        List<TelemetryDocument> fresh = new ArrayList<>();

        for (var raw : rawEvents) {
            TelemetryDocument document;
            try {
                document = normalizer.normalize(ctx, raw);
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Dropping unmappable {} event; correlationId={}, reason={}",
                        normalizer.sourceSystem(), ctx.correlationId(), ex.toString());
                continue;
            }
            if (dedupService.isFirstOccurrence(ctx, document.sourceSystem(), document.resourceId(), dedupCode(document))) {
                fresh.add(document);
            } else {
                duplicates++;
            }
        }

        var indexed = indexWriterService.index(ctx, fresh);
        var result = new IngestionResult(received, received - failed, duplicates, indexed, failed);
        log.info("Ingested {} batch: received={}, normalized={}, duplicates={}, indexed={}, failed={}, correlationId={}",
                normalizer.sourceSystem(), result.received(), result.normalized(), result.duplicatesDropped(),
                result.indexed(), result.failed(), ctx.correlationId());
        return result;
    }

    /** Stage-2 fingerprint signal: the normalizer-supplied {@code errorCode}, else severity. */
    private static String dedupCode(TelemetryDocument document) {
        var code = document.attributes().get("errorCode");
        if (code != null && !String.valueOf(code).isBlank()) {
            return String.valueOf(code);
        }
        return document.severity() != null ? document.severity() : "NONE";
    }
}
