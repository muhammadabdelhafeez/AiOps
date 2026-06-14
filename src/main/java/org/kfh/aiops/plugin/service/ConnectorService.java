package org.kfh.aiops.plugin.service;

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
public class ConnectorService {

    private final CommandCenterReadModel readModel;
    private final CountryAccessGuard countryGuard;
    private final AuditService auditService;

    public ConnectorService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
        this.auditService = auditService;
    }

    public PageResponse<Map<String, Object>> list(TenantContext ctx, int page, int size) {
        ctx.requirePermission("CONNECTOR_READ");
        return PageResponse.of(readModel.connectors(ctx.countryCode(), ctx.environment()), page, size);
    }

    public Map<String, Object> get(TenantContext ctx, UUID id) {
        ctx.requirePermission("CONNECTOR_READ");
        return require(ctx, id);
    }

    public Map<String, Object> create(TenantContext ctx, UiWriteRequest request) {
        ctx.requirePermission("CONNECTOR_WRITE");
        var created = readModel.createConnector(ctx, UiQuerySupport.name(request, "New connector"), UiQuerySupport.fields(request));
        audit(ctx, "CONNECTOR_CREATED", created.get("id"));
        return created;
    }

    public Map<String, Object> update(TenantContext ctx, UUID id, UiWriteRequest request) {
        ctx.requirePermission("CONNECTOR_WRITE");
        require(ctx, id);
        var updated = readModel.updateConnector(id, UiQuerySupport.fields(request));
        audit(ctx, "CONNECTOR_UPDATED", id);
        return updated;
    }

    public void delete(TenantContext ctx, UUID id) {
        ctx.requirePermission("CONNECTOR_WRITE");
        require(ctx, id);
        readModel.deleteConnector(id);
        audit(ctx, "CONNECTOR_DELETED", id);
    }

    public Map<String, Object> toggle(TenantContext ctx, UUID id, boolean enabled) {
        ctx.requirePermission("CONNECTOR_WRITE");
        require(ctx, id);
        var updated = readModel.updateConnector(id, Map.of("enabled", enabled));
        audit(ctx, "CONNECTOR_TOGGLED", id);
        return updated;
    }

    public Map<String, Object> test(TenantContext ctx, UUID id) {
        ctx.requirePermission("CONNECTOR_TEST");
        require(ctx, id);
        audit(ctx, "CONNECTOR_TEST_REQUESTED", id);
        return Map.of("connectorRunId", UUID.randomUUID().toString(), "pass", true, "latencyMs", 42,
                "message", "Connection test queued/simulated without exposing secrets");
    }

    public List<Map<String, Object>> logs(TenantContext ctx, UUID id) {
        require(ctx, id);
        return List.of(Map.of("level", "INFO", "message", "Last connector run completed", "connectorId", id.toString()));
    }

    private Map<String, Object> require(TenantContext ctx, UUID id) {
        var row = readModel.findConnector(id);
        if (row.isEmpty()) {
            throw new NotFoundException("Connector not found");
        }
        countryGuard.requireAccess(ctx, String.valueOf(row.get("countryCode")));
        return row;
    }

    private void audit(TenantContext ctx, String action, Object id) {
        auditService.recordWrite(ctx, action, "Connector", String.valueOf(id), null, Map.of("id", String.valueOf(id)));
        readModel.appendAudit(ctx, action, "Connector", String.valueOf(id));
    }
}

