package org.kfh.aiops.platform.audit;

import java.util.Map;
import java.util.UUID;
import org.kfh.aiops.commandcenter.dto.PageResponse;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.exception.NotFoundException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {

    private final CommandCenterReadModel readModel;
    private final AuditActivityRepository auditActivityRepository;

    public AuditQueryService(CommandCenterReadModel readModel) {
        this(readModel, null);
    }

    @Autowired
    public AuditQueryService(CommandCenterReadModel readModel, AuditActivityRepository auditActivityRepository) {
        this.readModel = readModel;
        this.auditActivityRepository = auditActivityRepository;
    }

    public PageResponse<Map<String, Object>> list(TenantContext ctx, int page, int size) {
        ctx.requirePermission("AUDIT_READ");
        requireGlobalPermissionForAllCountryScope(ctx);
        var persisted = persistedAudit(ctx);
        if (!persisted.isEmpty()) {
            return PageResponse.of(persisted, page, size);
        }
        return PageResponse.of(readModel.audit(ctx), page, size);
    }

    public Map<String, Object> get(TenantContext ctx, UUID id) {
        ctx.requirePermission("AUDIT_READ");
        requireGlobalPermissionForAllCountryScope(ctx);
        if (auditActivityRepository != null) {
            var persisted = auditActivityRepository.find(ctx, id);
            if (persisted.isPresent()) {
                return persisted.get();
            }
        }
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

    private java.util.List<Map<String, Object>> persistedAudit(TenantContext ctx) {
        if (auditActivityRepository == null) {
            return java.util.List.of();
        }
        return auditActivityRepository.list(ctx);
    }
}

