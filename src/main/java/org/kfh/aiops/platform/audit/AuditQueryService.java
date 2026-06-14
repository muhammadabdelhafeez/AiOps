package org.kfh.aiops.platform.audit;

import java.util.Map;
import java.util.UUID;
import org.kfh.aiops.commandcenter.dto.PageResponse;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.exception.NotFoundException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {

    private final CommandCenterReadModel readModel;

    public AuditQueryService(CommandCenterReadModel readModel) {
        this.readModel = readModel;
    }

    public PageResponse<Map<String, Object>> list(TenantContext ctx, int page, int size) {
        ctx.requirePermission("AUDIT_READ");
        requireGlobalPermissionForAllCountryScope(ctx);
        return PageResponse.of(readModel.audit(ctx), page, size);
    }

    public Map<String, Object> get(TenantContext ctx, UUID id) {
        ctx.requirePermission("AUDIT_READ");
        requireGlobalPermissionForAllCountryScope(ctx);
        var row = readModel.findAudit(ctx, id);
        if (row.isEmpty()) {
            throw new NotFoundException("Audit event not found");
        }
        return row;
    }

    private static void requireGlobalPermissionForAllCountryScope(TenantContext ctx) {
        if (CountryAccessGuard.ALL_COUNTRIES_SCOPE.equals(ctx.countryCode())) {
            ctx.requirePermission(CountryAccessGuard.GLOBAL_COUNTRY_VIEW);
        }
    }
}

