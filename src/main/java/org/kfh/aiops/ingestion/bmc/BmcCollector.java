package org.kfh.aiops.ingestion.bmc;

import java.util.Set;
import java.util.UUID;
import org.kfh.aiops.ingestion.BmcNormalizer;
import org.kfh.aiops.ingestion.IngestionResult;
import org.kfh.aiops.ingestion.IngestionService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pulls BMC Helix events and feeds them through the shared ingestion pipeline
 * (normalize → dedup → index). {@link #collectNow(TenantContext)} runs under the caller's scope (manual
 * endpoint); {@link #collect()} builds a configured system scope for the scheduled poll.
 */
@Service
public class BmcCollector {

    private static final Logger log = LoggerFactory.getLogger(BmcCollector.class);

    private final BmcProperties properties;
    private final BmcHelixClient client;
    private final BmcNormalizer normalizer;
    private final IngestionService ingestionService;

    public BmcCollector(BmcProperties properties, BmcHelixClient client, BmcNormalizer normalizer,
                        IngestionService ingestionService) {
        this.properties = properties;
        this.client = client;
        this.normalizer = normalizer;
        this.ingestionService = ingestionService;
    }

    /** Collect using the caller's tenant/country scope. */
    public IngestionResult collectNow(TenantContext ctx) {
        requireConfigured();
        long t0 = System.nanoTime();
        log.info("[COLLECT] BMC start: country={} env={} window={}m correlationId={}",
                ctx.countryCode(), ctx.environment(), properties.getMinutesBack(), ctx.correlationId());
        var raw = client.fetchRawEvents(properties.getMinutesBack(), properties.getMaxEvents());
        var result = ingestionService.ingest(ctx, normalizer, raw);
        log.info("[COLLECT] BMC complete: country={} env={} fetched={} indexed={} duplicates={} failed={} took={}ms correlationId={}",
                ctx.countryCode(), ctx.environment(), raw.size(), result.indexed(), result.duplicatesDropped(),
                result.failed(), (System.nanoTime() - t0) / 1_000_000, ctx.correlationId());
        return result;
    }

    /** Collect using the configured system scope (scheduled poll). */
    public IngestionResult collect() {
        return collectNow(systemContext());
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "BMC ingestion is not configured — set kfh.ingestion.bmc.base-url, access-key and access-secret-key.");
        }
    }

    private TenantContext systemContext() {
        return new TenantContext(
                UUID.fromString(properties.getTenantId()),
                UUID.fromString(properties.getTenantId()),
                properties.getCountryCode(),
                properties.getEnvironment(),
                "bmc-scheduled-poll",
                Set.of("ALERT_INGEST"));
    }
}
