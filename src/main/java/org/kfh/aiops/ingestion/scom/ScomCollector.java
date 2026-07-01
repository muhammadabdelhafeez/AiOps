package org.kfh.aiops.ingestion.scom;

import java.util.Set;
import java.util.UUID;
import org.kfh.aiops.ingestion.IngestionResult;
import org.kfh.aiops.ingestion.IngestionService;
import org.kfh.aiops.ingestion.ScomNormalizer;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pulls SCOM alerts (WinRM/PowerShell) and feeds them through the shared ingestion pipeline
 * (normalize → dedup → index). {@link #collectNow(TenantContext)} runs under the caller's scope (manual
 * endpoint); {@link #collect()} builds a configured system scope for the scheduled poll.
 */
@Service
public class ScomCollector {

    private static final Logger log = LoggerFactory.getLogger(ScomCollector.class);

    private final ScomProperties properties;
    private final ScomWinRmClient client;
    private final ScomNormalizer normalizer;
    private final IngestionService ingestionService;

    public ScomCollector(ScomProperties properties, ScomWinRmClient client, ScomNormalizer normalizer,
                         IngestionService ingestionService) {
        this.properties = properties;
        this.client = client;
        this.normalizer = normalizer;
        this.ingestionService = ingestionService;
    }

    /** Collect using the caller's tenant/country scope. */
    public IngestionResult collectNow(TenantContext ctx) {
        requireConfigured();
        var raw = client.fetchRawEvents(properties.getHoursBack());
        var result = ingestionService.ingest(ctx, normalizer, raw);
        log.info("SCOM collect (country={}, env={}): {}", ctx.countryCode(), ctx.environment(), result);
        return result;
    }

    /** Collect using the configured system scope (scheduled poll). */
    public IngestionResult collect() {
        return collectNow(systemContext());
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "SCOM ingestion is not configured — set kfh.ingestion.scom.management-server, username and password.");
        }
    }

    private TenantContext systemContext() {
        return new TenantContext(
                UUID.fromString(properties.getTenantId()),
                UUID.fromString(properties.getTenantId()),
                properties.getCountryCode(),
                properties.getEnvironment(),
                "scom-scheduled-poll",
                Set.of("ALERT_INGEST"));
    }
}
