package org.kfh.aiops.incident.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.kfh.aiops.commandcenter.dto.PageResponse;
import org.kfh.aiops.commandcenter.dto.UiWriteRequest;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.commandcenter.support.UiQuerySupport;
import org.kfh.aiops.incident.model.IncidentStatus;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.exception.NotFoundException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {

    private final CommandCenterReadModel readModel;
    private final CountryAccessGuard countryGuard;
    private final AuditService auditService;

    public IncidentService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
        this.auditService = auditService;
    }

    public PageResponse<Map<String, Object>> list(TenantContext ctx, String country, String environment, int page, int size) {
        ctx.requirePermission("INCIDENT_READ");
        var rows = readModel.incidents(UiQuerySupport.country(ctx, countryGuard, country), UiQuerySupport.environment(ctx, environment));
        return PageResponse.of(rows, page, size);
    }

    public Map<String, Object> get(TenantContext ctx, UUID id) {
        ctx.requirePermission("INCIDENT_READ");
        return requireIncident(ctx, id);
    }

    public Map<String, Object> create(TenantContext ctx, UiWriteRequest request) {
        ctx.requirePermission("INCIDENT_CREATE");
        var fields = UiQuerySupport.fields(request);
        fields.putIfAbsent("status", IncidentStatus.NEW.name());
        fields.putIfAbsent("firstEventAt", Instant.now().toString());
        fields.putIfAbsent("lastEventAt", Instant.now().toString());
        var created = readModel.createIncident(ctx, UiQuerySupport.name(request, "New incident"), fields);
        audit(ctx, "INCIDENT_CREATED", created.get("id"));
        return created;
    }

    public Map<String, Object> update(TenantContext ctx, UUID id, UiWriteRequest request) {
        ctx.requirePermission("INCIDENT_UPDATE");
        requireIncident(ctx, id);
        var updated = readModel.updateIncident(id, UiQuerySupport.fields(request));
        audit(ctx, "INCIDENT_UPDATED", id);
        return updated;
    }

    public Map<String, Object> updateStatus(TenantContext ctx, UUID id, String status) {
        ctx.requirePermission("INCIDENT_UPDATE");
        IncidentStatus.valueOf(status.toUpperCase());
        requireIncident(ctx, id);
        var updated = readModel.updateIncident(id, Map.of("status", status.toUpperCase(), "lastStatusChangedAt", Instant.now().toString()));
        audit(ctx, "INCIDENT_STATUS_UPDATED", id);
        return updated;
    }

    public List<Map<String, Object>> evidence(TenantContext ctx, UUID id) {
        ctx.requirePermission("INCIDENT_READ");
        var incident = requireIncident(ctx, id);
        return List.of(
                Map.of("id", id + ":e1", "type", "METRIC", "summary", "Storage latency started before downstream failures", "observedAt", incident.get("createdAt")),
                Map.of("id", id + ":e2", "type", "TRACE", "summary", "Transfer service DB waits align with Oracle latency", "observedAt", incident.get("updatedAt")));
    }

    public List<Map<String, Object>> related(TenantContext ctx, UUID id) {
        ctx.requirePermission("INCIDENT_READ");
        var incident = requireIncident(ctx, id);
        var appId = String.valueOf(incident.get("applicationId"));
        return readModel.incidents(ctx.countryCode(), ctx.environment()).stream()
                .filter(row -> appId.equals(String.valueOf(row.get("applicationId"))))
                .filter(row -> !id.toString().equals(String.valueOf(row.get("id"))))
                .toList();
    }

    public List<Map<String, Object>> timeline(TenantContext ctx, UUID id) {
        ctx.requirePermission("INCIDENT_READ");
        var incident = requireIncident(ctx, id);
        return List.of(
                Map.of("timestamp", incident.get("createdAt"), "eventType", "ROOT_CAUSE_SIGNAL", "summary", "Upstream dependency degradation detected"),
                Map.of("timestamp", incident.get("updatedAt"), "eventType", "INCIDENT_STATE", "summary", "Incident is " + incident.get("status")));
    }

    private Map<String, Object> requireIncident(TenantContext ctx, UUID id) {
        var incident = readModel.findIncident(id);
        if (incident.isEmpty()) {
            throw new NotFoundException("Incident not found");
        }
        countryGuard.requireAccess(ctx, String.valueOf(incident.get("countryCode")));
        return incident;
    }

    private void audit(TenantContext ctx, String action, Object id) {
        auditService.recordWrite(ctx, action, "Incident", String.valueOf(id), null, Map.of("id", String.valueOf(id)));
        readModel.appendAudit(ctx, action, "Incident", String.valueOf(id));
    }
}

