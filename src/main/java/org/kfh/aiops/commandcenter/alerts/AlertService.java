package org.kfh.aiops.commandcenter.alerts;

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
public class AlertService {

    private final CommandCenterReadModel readModel;
    private final CountryAccessGuard countryGuard;
    private final AuditService auditService;

    public AlertService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
        this.auditService = auditService;
    }

    public PageResponse<Map<String, Object>> list(TenantContext ctx, String country, String environment, int page, int size) {
        ctx.requirePermission("ALERT_READ");
        return PageResponse.of(readModel.alerts(UiQuerySupport.country(ctx, countryGuard, country), UiQuerySupport.environment(ctx, environment)), page, size);
    }

    public Map<String, Object> get(TenantContext ctx, UUID id) {
        ctx.requirePermission("ALERT_READ");
        var alert = readModel.findAlert(id);
        if (alert.isEmpty()) {
            throw new NotFoundException("Alert not found");
        }
        countryGuard.requireAccess(ctx, String.valueOf(alert.get("countryCode")));
        return alert;
    }

    public Map<String, Object> acknowledge(TenantContext ctx, List<UUID> ids) {
        ctx.requirePermission("ALERT_ACKNOWLEDGE");
        ids.forEach(id -> readModel.update(readModel.alertMap(), id, Map.of("status", "ACKNOWLEDGED")));
        auditService.recordWrite(ctx, "ALERT_ACKNOWLEDGED", "Alert", ids.toString(), null, Map.of("ids", ids));
        readModel.appendAudit(ctx, "ALERT_ACKNOWLEDGED", "Alert", ids.toString());
        return Map.of("acknowledged", ids.size(), "correlationId", ctx.correlationId());
    }

    public List<Map<String, Object>> activity(TenantContext ctx) {
        ctx.requirePermission("ALERT_READ");
        return readModel.audit(ctx).stream().filter(row -> String.valueOf(row.get("action")).contains("ALERT")).toList();
    }
}

