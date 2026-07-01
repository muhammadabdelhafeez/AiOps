package org.kfh.aiops.ingestion.bmc;

import org.kfh.aiops.ingestion.IngestionResult;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual BMC collection trigger. {@code POST /api/v1/ingestion/bmc/collect-now} pulls the current BMC
 * event window and runs it through the ingestion pipeline under the caller's tenant/country scope.
 * RBAC-gated ({@code ALERT_INGEST}) before any outbound BMC call is made.
 */
@RestController
@RequestMapping("/api/v1/ingestion/bmc")
public class BmcCollectionController {

    private final BmcCollector collector;

    public BmcCollectionController(BmcCollector collector) {
        this.collector = collector;
    }

    @PostMapping("/collect-now")
    public IngestionResult collectNow(TenantContext ctx) {
        ctx.requirePermission("ALERT_INGEST");
        return collector.collectNow(ctx);
    }
}
