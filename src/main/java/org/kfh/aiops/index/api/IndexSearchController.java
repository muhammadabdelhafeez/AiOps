package org.kfh.aiops.index.api;

import org.kfh.aiops.index.IndexSearchService;
import org.kfh.aiops.index.model.IndexQuery;
import org.kfh.aiops.index.model.IndexSearchResult;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Telemetry search API over the custom index engine (§10). Tenant/country scoped and RBAC-gated at
 * the service layer. Returns a paged, filtered, newest-first result set — callers build a compact
 * EvidencePack from it; full result sets are never handed to AI.
 */
@RestController
@RequestMapping("/api/v1/logs")
public class IndexSearchController {

    private final IndexSearchService indexSearchService;

    public IndexSearchController(IndexSearchService indexSearchService) {
        this.indexSearchService = indexSearchService;
    }

    @PostMapping("/search")
    public IndexSearchResult search(TenantContext ctx, @RequestBody IndexQuery query) {
        ctx.requirePermission("ALERT_READ");
        return indexSearchService.search(ctx, query);
    }
}
