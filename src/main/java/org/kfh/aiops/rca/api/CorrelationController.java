package org.kfh.aiops.rca.api;

import java.time.Duration;
import java.time.Instant;
import org.kfh.aiops.rca.CorrelationResult;
import org.kfh.aiops.rca.CorrelationService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Correlation API (causal funnel Stages 4–6). Runs the correlation engine over the recent alert window
 * and returns the candidate incidents (root cause + impacted applications + evidence) plus unmapped CIs.
 * Tenant/country scoped and RBAC-gated ({@code ALERT_READ}) at the service layer.
 */
@RestController
@RequestMapping("/api/v1/correlation")
public class CorrelationController {

    private static final int DEFAULT_MINUTES = 120;
    private static final int MAX_MINUTES = 10_080; // 7 days

    private final CorrelationService correlationService;

    public CorrelationController(CorrelationService correlationService) {
        this.correlationService = correlationService;
    }

    /** Correlate the last {@code minutes} of alerts (default 120, capped at 7 days). */
    @GetMapping
    public CorrelationResult correlate(TenantContext ctx, @RequestParam(defaultValue = "120") int minutes) {
        var window = Math.min(Math.max(1, minutes), MAX_MINUTES);
        var to = Instant.now();
        var from = to.minus(Duration.ofMinutes(window));
        return correlationService.correlateWindow(ctx, from, to);
    }
}
