package org.kfh.aiops.ingestion.scom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Opt-in scheduled SCOM poll (the 20-minute causal-funnel cycle). Active only when
 * {@code kfh.ingestion.scom.enabled=true}. Failures are logged and swallowed so a transient WinRM/SCOM
 * outage never kills the schedule; the next tick retries.
 */
@Component
@ConditionalOnProperty(prefix = "kfh.ingestion.scom", name = "enabled", havingValue = "true")
public class ScomScheduledPoller {

    private static final Logger log = LoggerFactory.getLogger(ScomScheduledPoller.class);

    private final ScomCollector collector;

    public ScomScheduledPoller(ScomCollector collector) {
        this.collector = collector;
    }

    @Scheduled(fixedDelayString = "${kfh.ingestion.scom.poll-interval-ms:1200000}",
            initialDelayString = "${kfh.ingestion.scom.initial-delay-ms:90000}")
    public void poll() {
        try {
            var result = collector.collect();
            log.info("SCOM scheduled poll complete: {}", result);
        } catch (RuntimeException ex) {
            log.error("SCOM scheduled poll failed (will retry next tick): {}", ex.getMessage());
        }
    }
}
