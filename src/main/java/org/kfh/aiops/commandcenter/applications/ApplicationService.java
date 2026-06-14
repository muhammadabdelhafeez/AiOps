package org.kfh.aiops.commandcenter.applications;

import java.util.Map;
import java.util.UUID;
import org.kfh.aiops.commandcenter.dto.PageResponse;
import org.kfh.aiops.commandcenter.dto.UiWriteRequest;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.commandcenter.support.UiQuerySupport;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.exception.NotFoundException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class ApplicationService {

    private final CommandCenterReadModel readModel;
    private final CountryAccessGuard countryGuard;
    private final AuditService auditService;

    public ApplicationService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
        this.auditService = auditService;
    }

    public PageResponse<Map<String, Object>> list(TenantContext ctx, String country, String environment, int page, int size) {
        ctx.requirePermission("APPLICATION_READ");
        return PageResponse.of(readModel.applications(UiQuerySupport.country(ctx, countryGuard, country), UiQuerySupport.environment(ctx, environment)), page, size);
    }

    public Map<String, Object> get(TenantContext ctx, UUID id) {
        ctx.requirePermission("APPLICATION_READ");
        return require(ctx, id);
    }

    public Map<String, Object> create(TenantContext ctx, UiWriteRequest request) {
        ctx.requirePermission("APPLICATION_WRITE");
        var created = readModel.createApplication(ctx, UiQuerySupport.name(request, "New application"), UiQuerySupport.fields(request));
        audit(ctx, "APPLICATION_CREATED", created.get("id"));
        return created;
    }

    public Map<String, Object> update(TenantContext ctx, UUID id, UiWriteRequest request) {
        ctx.requirePermission("APPLICATION_WRITE");
        require(ctx, id);
        var updated = readModel.updateApplication(id, UiQuerySupport.fields(request));
        audit(ctx, "APPLICATION_UPDATED", id);
        return updated;
    }

    public void delete(TenantContext ctx, UUID id) {
        ctx.requirePermission("APPLICATION_WRITE");
        require(ctx, id);
        readModel.deleteApplication(id);
        audit(ctx, "APPLICATION_DELETED", id);
    }

    public Object inventory(TenantContext ctx, UUID id) {
        require(ctx, id);
        var appId = id.toString();
        return readModel.inventory(ctx.countryCode(), ctx.environment()).stream()
                .filter(row -> appId.equals(String.valueOf(row.get("applicationId"))))
                .toList();
    }

    public Object incidents(TenantContext ctx, UUID id) {
        require(ctx, id);
        var appId = id.toString();
        return readModel.incidents(ctx.countryCode(), ctx.environment()).stream()
                .filter(row -> appId.equals(String.valueOf(row.get("applicationId"))))
                .toList();
    }

    public Object health(TenantContext ctx, UUID id) {
        var app = require(ctx, id);
        return Map.of("applicationId", id.toString(), "health", app.getOrDefault("health", "UNKNOWN"),
                "degradedDependencies", inventory(ctx, id));
    }

    private Map<String, Object> require(TenantContext ctx, UUID id) {
        var app = readModel.findApplication(id);
        if (app.isEmpty()) {
            throw new NotFoundException("Application not found");
        }
        countryGuard.requireAccess(ctx, String.valueOf(app.get("countryCode")));
        return app;
    }

    private void audit(TenantContext ctx, String action, Object id) {
        auditService.recordWrite(ctx, action, "Application", String.valueOf(id), null, Map.of("id", String.valueOf(id)));
        readModel.appendAudit(ctx, action, "Application", String.valueOf(id));
    }
}

