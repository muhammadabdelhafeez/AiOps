package org.kfh.aiops.commandcenter.inventory;

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
public class InventoryService {

    private final CommandCenterReadModel readModel;
    private final CountryAccessGuard countryGuard;
    private final AuditService auditService;

    public InventoryService(CommandCenterReadModel readModel, CountryAccessGuard countryGuard, AuditService auditService) {
        this.readModel = readModel;
        this.countryGuard = countryGuard;
        this.auditService = auditService;
    }

    public PageResponse<Map<String, Object>> list(TenantContext ctx, String country, String environment, int page, int size) {
        ctx.requirePermission("INVENTORY_READ");
        return PageResponse.of(readModel.inventory(UiQuerySupport.country(ctx, countryGuard, country), UiQuerySupport.environment(ctx, environment)), page, size);
    }

    public Map<String, Object> get(TenantContext ctx, UUID id) {
        ctx.requirePermission("INVENTORY_READ");
        return require(ctx, id);
    }

    public Map<String, Object> create(TenantContext ctx, UiWriteRequest request) {
        ctx.requirePermission("INVENTORY_WRITE");
        var created = readModel.createInventory(ctx, UiQuerySupport.name(request, "New resource"), UiQuerySupport.fields(request));
        audit(ctx, "RESOURCE_CREATED", created.get("id"));
        return created;
    }

    public Map<String, Object> update(TenantContext ctx, UUID id, UiWriteRequest request) {
        ctx.requirePermission("INVENTORY_WRITE");
        require(ctx, id);
        var updated = readModel.updateInventory(id, UiQuerySupport.fields(request));
        audit(ctx, "RESOURCE_UPDATED", id);
        return updated;
    }

    public void delete(TenantContext ctx, UUID id) {
        ctx.requirePermission("INVENTORY_WRITE");
        require(ctx, id);
        readModel.deleteInventory(id);
        audit(ctx, "RESOURCE_DELETED", id);
    }

    public Object dependencies(TenantContext ctx, UUID id) {
        var resource = require(ctx, id);
        return Map.of("resourceId", id.toString(), "upstream", readModel.inventory(ctx.countryCode(), ctx.environment()).stream()
                .filter(row -> !id.toString().equals(row.get("id"))).limit(2).toList(), "degraded", resource.get("health"));
    }

    public Object alerts(TenantContext ctx, UUID id) {
        require(ctx, id);
        return readModel.alerts(ctx.countryCode(), ctx.environment()).stream()
                .filter(row -> id.toString().equals(String.valueOf(row.get("resourceId"))))
                .toList();
    }

    private Map<String, Object> require(TenantContext ctx, UUID id) {
        var row = readModel.findInventory(id);
        if (row.isEmpty()) {
            throw new NotFoundException("Inventory resource not found");
        }
        countryGuard.requireAccess(ctx, String.valueOf(row.get("countryCode")));
        return row;
    }

    private void audit(TenantContext ctx, String action, Object id) {
        auditService.recordWrite(ctx, action, "Resource", String.valueOf(id), null, Map.of("id", String.valueOf(id)));
        readModel.appendAudit(ctx, action, "Resource", String.valueOf(id));
    }
}

