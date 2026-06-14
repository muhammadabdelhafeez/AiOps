package org.kfh.aiops.commandcenter.reports;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.kfh.aiops.commandcenter.dto.PageResponse;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.commandcenter.support.UiQuerySupport;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.exception.NotFoundException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final CommandCenterReadModel readModel;
    private final CountryAccessGuard countryGuard;
    private final AuditService auditService;

    public ReportService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
        this.auditService = auditService;
    }

    public PageResponse<Map<String, Object>> list(TenantContext ctx, String country, String environment, int page, int size) {
        ctx.requirePermission("REPORT_READ");
        return PageResponse.of(readModel.reports(UiQuerySupport.country(ctx, countryGuard, country), UiQuerySupport.environment(ctx, environment)), page, size);
    }

    public Map<String, Object> get(TenantContext ctx, UUID id) {
        ctx.requirePermission("REPORT_READ");
        var report = readModel.findReport(id);
        if (report.isEmpty()) {
            throw new NotFoundException("Report not found");
        }
        countryGuard.requireAccess(ctx, String.valueOf(report.get("countryCode")));
        return report;
    }

    public List<Map<String, Object>> runs(TenantContext ctx) {
        ctx.requirePermission("REPORT_READ");
        return List.of(Map.of("runId", UUID.randomUUID().toString(), "status", "SUCCESS", "countryCode", ctx.countryCode()));
    }

    public List<Map<String, Object>> artifacts(TenantContext ctx, UUID runId) {
        ctx.requirePermission("REPORT_READ");
        return List.of(Map.of("artifactId", UUID.randomUUID().toString(), "runId", runId.toString(), "type", "EVIDENCE_PACK"));
    }

    public Map<String, Object> generate(TenantContext ctx, Map<String, Object> request) {
        ctx.requirePermission("REPORT_GENERATE");
        var runId = UUID.randomUUID();
        auditService.recordWrite(ctx, "REPORT_GENERATE_REQUESTED", "Report", runId.toString(), null, request);
        readModel.appendAudit(ctx, "REPORT_GENERATE_REQUESTED", "Report", runId.toString());
        return Map.of("runId", runId.toString(), "status", "QUEUED", "correlationId", ctx.correlationId());
    }
}

