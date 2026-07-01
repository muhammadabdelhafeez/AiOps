package org.kfh.aiops.ingestion.bmc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Opt-in scheduled BMC poll (the 20-minute causal-funnel cycle). Active only when
 * {@code kfh.ingestion.bmc.enabled=true}. Failures are logged and swallowed so a transient BMC outage
 * never kills the schedule; the next tick retries.
 */
@Component
@ConditionalOnProperty(prefix = "kfh.ingestion.bmc", name = "enabled", havingValue = "true")
public class BmcScheduledPoller {

    private static final Logger log = LoggerFactory.getLogger(BmcScheduledPoller.class);

    private final BmcCollector collector;

    public BmcScheduledPoller(BmcCollector collector) {
        this.collector = collector;
    }

    @Scheduled(fixedDelayString = "${kfh.ingestion.bmc.poll-interval-ms:1200000}",
            initialDelayString = "${kfh.ingestion.bmc.initial-delay-ms:60000}")
    public void poll() {
        try {
            var result = collector.collect();
            log.info("BMC scheduled poll complete: {}", result);
        } catch (RuntimeException ex) {
            log.error("BMC scheduled poll failed (will retry next tick): {}", ex.getMessage());
        }
    }
}
