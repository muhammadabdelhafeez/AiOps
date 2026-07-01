package org.kfh.aiops.ingestion.scom;

import org.kfh.aiops.ingestion.IngestionResult;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual SCOM collection trigger. {@code POST /api/v1/ingestion/scom/collect-now} pulls the current
 * SCOM alert window and runs it through the ingestion pipeline under the caller's tenant/country scope.
 * RBAC-gated ({@code ALERT_INGEST}) before any WinRM/PowerShell session is spawned.
 */
@RestController
@RequestMapping("/api/v1/ingestion/scom")
public class ScomCollectionController {

    private final ScomCollector collector;

    public ScomCollectionController(ScomCollector collector) {
        this.collector = collector;
    }

    @PostMapping("/collect-now")
    public IngestionResult collectNow(TenantContext ctx) {
        ctx.requirePermission("ALERT_INGEST");
        return collector.collectNow(ctx);
    }
}
