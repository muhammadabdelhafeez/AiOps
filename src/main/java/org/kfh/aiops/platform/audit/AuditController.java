package org.kfh.aiops.platform.audit;

import java.util.UUID;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    public Object list(TenantContext ctx, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditQueryService.list(ctx, page, size);
    }

    @GetMapping("/{id}")
    public Object get(TenantContext ctx, @PathVariable UUID id) {
        return auditQueryService.get(ctx, id);
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(TenantContext ctx) {
        ctx.requirePermission("AUDIT_READ");
        return ResponseEntity.ok("audit export placeholder for correlation " + ctx.correlationId());
    }
}

