package org.kfh.aiops.commandcenter.schedules;

import java.util.List;
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
public class ScheduleService {

    private final CommandCenterReadModel readModel;
    private final CountryAccessGuard countryGuard;
    private final AuditService auditService;

    public ScheduleService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
        this.auditService = auditService;
    }

    public PageResponse<Map<String, Object>> list(TenantContext ctx, int page, int size) {
        ctx.requirePermission("SCHEDULE_READ");
        return PageResponse.of(readModel.schedules(ctx.countryCode(), ctx.environment()), page, size);
    }

    public Map<String, Object> get(TenantContext ctx, UUID id) {
        ctx.requirePermission("SCHEDULE_READ");
        return require(ctx, id);
    }

    public Map<String, Object> create(TenantContext ctx, UiWriteRequest request) {
        ctx.requirePermission("SCHEDULE_WRITE");
        var created = readModel.createSchedule(ctx, UiQuerySupport.name(request, "New schedule"), UiQuerySupport.fields(request));
        audit(ctx, "SCHEDULE_CREATED", created.get("id"));
        return created;
    }

    public Map<String, Object> update(TenantContext ctx, UUID id, UiWriteRequest request) {
        ctx.requirePermission("SCHEDULE_WRITE");
        require(ctx, id);
        var updated = readModel.updateSchedule(id, UiQuerySupport.fields(request));
        audit(ctx, "SCHEDULE_UPDATED", id);
        return updated;
    }

    public void delete(TenantContext ctx, UUID id) {
        ctx.requirePermission("SCHEDULE_WRITE");
        require(ctx, id);
        readModel.deleteSchedule(id);
        audit(ctx, "SCHEDULE_DELETED", id);
    }

    public Map<String, Object> toggle(TenantContext ctx, UUID id, boolean enabled) {
        ctx.requirePermission("SCHEDULE_WRITE");
        require(ctx, id);
        var updated = readModel.updateSchedule(id, Map.of("enabled", enabled));
        audit(ctx, "SCHEDULE_TOGGLED", id);
        return updated;
    }

    public Map<String, Object> run(TenantContext ctx, UUID id) {
        ctx.requirePermission("SCHEDULE_RUN");
        require(ctx, id);
        audit(ctx, "SCHEDULE_RUN_REQUESTED", id);
        return Map.of("connectorRunId", UUID.randomUUID().toString(), "status", "QUEUED", "correlationId", ctx.correlationId());
    }

    public List<Map<String, Object>> runs(TenantContext ctx, UUID id) {
        require(ctx, id);
        return List.of(Map.of("runId", UUID.randomUUID().toString(), "status", "SUCCESS", "scheduleId", id.toString()));
    }

    private Map<String, Object> require(TenantContext ctx, UUID id) {
        var row = readModel.findSchedule(id);
        if (row.isEmpty()) {
            throw new NotFoundException("Schedule not found");
        }
        countryGuard.requireAccess(ctx, String.valueOf(row.get("countryCode")));
        return row;
    }

    private void audit(TenantContext ctx, String action, Object id) {
        auditService.recordWrite(ctx, action, "Schedule", String.valueOf(id), null, Map.of("id", String.valueOf(id)));
        readModel.appendAudit(ctx, action, "Schedule", String.valueOf(id));
    }
}

