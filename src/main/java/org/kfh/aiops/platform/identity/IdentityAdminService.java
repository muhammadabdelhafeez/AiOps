package org.kfh.aiops.platform.identity;

import java.util.List;
import java.util.Locale;
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
public class IdentityAdminService {

    private final CommandCenterReadModel readModel;
    private final AuditService auditService;
    private final CountryAccessGuard countryAccessGuard;
    private final IdentityJdbcRepository identityJdbcRepository;

    public IdentityAdminService(CommandCenterReadModel readModel, AuditService auditService,
            CountryAccessGuard countryAccessGuard, IdentityJdbcRepository identityJdbcRepository) {
        this.readModel = readModel;
        this.auditService = auditService;
        this.countryAccessGuard = countryAccessGuard;
        this.identityJdbcRepository = identityJdbcRepository;
    }

    public PageResponse<Map<String, Object>> users(TenantContext ctx, String country, String environment, int page, int size) {
        ctx.requirePermission("IDENTITY_READ");
        var requestedCountry = country == null || country.isBlank() ? ctx.countryCode() : country;
        var requestedEnvironment = environment == null || environment.isBlank() ? ctx.environment() : environment;
        countryAccessGuard.requireAccess(ctx, requestedCountry);
        return PageResponse.of(identityJdbcRepository.users(ctx.tenantId(), requestedCountry, requestedEnvironment), page, size);
    }

    public Map<String, Object> user(TenantContext ctx, UUID id) {
        ctx.requirePermission("IDENTITY_READ");
        var row = identityJdbcRepository.findUser(ctx.tenantId(), id).orElseThrow(() -> new NotFoundException("User not found"));
        countryAccessGuard.requireAccess(ctx, String.valueOf(row.get("countryCode")));
        return row;
    }

    public Map<String, Object> createUser(TenantContext ctx, UiWriteRequest request) {
        ctx.requirePermission("IDENTITY_WRITE");
        var fields = UiQuerySupport.fields(request);
        var createContext = createContext(ctx, fields);
        countryAccessGuard.requireAccess(ctx, createContext.countryCode());
        var created = identityJdbcRepository.createUser(createContext, UiQuerySupport.name(request, "New user"), fields);
        audit(createContext, "USER_CREATED", created.get("id"));
        return created;
    }

    public Map<String, Object> updateUser(TenantContext ctx, UUID id, UiWriteRequest request) {
        var existing = user(ctx, id);
        ctx.requirePermission("IDENTITY_WRITE");
        var fields = UiQuerySupport.fields(request);
        var requestedCountry = requestedCountry(fields, existing);
        countryAccessGuard.requireAccess(ctx, requestedCountry);
        fields.put("countryCode", requestedCountry);
        canonicalizeRole(fields, requestedCountry);
        var updated = identityJdbcRepository.updateUser(ctx.tenantId(), id, UiQuerySupport.name(request, null), fields);
        audit(ctx, "USER_UPDATED", id);
        return updated;
    }

    public Map<String, Object> resetPassword(TenantContext ctx, UUID id, UiWriteRequest request) {
        user(ctx, id);
        ctx.requirePermission("IDENTITY_WRITE");
        var updated = identityJdbcRepository.updatePassword(ctx.tenantId(), id, UiQuerySupport.fields(request));
        audit(ctx, "USER_PASSWORD_RESET", id);
        return updated;
    }

    public void deleteUser(TenantContext ctx, UUID id) {
        user(ctx, id);
        ctx.requirePermission("IDENTITY_WRITE");
        identityJdbcRepository.deleteUser(ctx.tenantId(), id);
        audit(ctx, "USER_DELETED", id);
    }

    public Map<String, Object> toggleUser(TenantContext ctx, UUID id) {
        user(ctx, id);
        ctx.requirePermission("IDENTITY_WRITE");
        var updated = identityJdbcRepository.toggleUser(ctx.tenantId(), id);
        audit(ctx, "USER_TOGGLED", id);
        return updated;
    }

    public List<Map<String, Object>> userRoles(TenantContext ctx) {
        ctx.requirePermission("IDENTITY_READ");
        return identityJdbcRepository.roles(ctx.tenantId());
    }


    public Object userPermissions(TenantContext ctx, UUID userId) {
        user(ctx, userId);
        return Map.of("userId", userId.toString(), "permissions", List.of("DASHBOARD_READ", "INCIDENT_READ"));
    }

    public List<Map<String, Object>> roles(TenantContext ctx) {
        ctx.requirePermission("IDENTITY_READ");
        return identityJdbcRepository.roles(ctx.tenantId());
    }

    public Map<String, Object> role(TenantContext ctx, UUID id) {
        ctx.requirePermission("IDENTITY_READ");
        var row = readModel.findRole(id);
        if (row.isEmpty()) {
            throw new NotFoundException("Role not found");
        }
        return row;
    }

    public Map<String, Object> createRole(TenantContext ctx, UiWriteRequest request) {
        ctx.requirePermission("IDENTITY_WRITE");
        var created = readModel.createRole(ctx, UiQuerySupport.name(request, "New role"), UiQuerySupport.fields(request));
        audit(ctx, "ROLE_CREATED", created.get("id"));
        return created;
    }

    public Map<String, Object> updateRole(TenantContext ctx, UUID id, UiWriteRequest request) {
        role(ctx, id);
        ctx.requirePermission("IDENTITY_WRITE");
        var updated = readModel.updateRole(id, UiQuerySupport.fields(request));
        audit(ctx, "ROLE_UPDATED", id);
        return updated;
    }

    public void deleteRole(TenantContext ctx, UUID id) {
        role(ctx, id);
        ctx.requirePermission("IDENTITY_WRITE");
        readModel.deleteRole(id);
        audit(ctx, "ROLE_DELETED", id);
    }

    private void audit(TenantContext ctx, String action, Object id) {
        auditService.recordWrite(ctx, action, "Identity", String.valueOf(id), null, Map.of("id", String.valueOf(id)));
        readModel.appendAudit(ctx, action, "Identity", String.valueOf(id));
    }

    private static TenantContext createContext(TenantContext ctx, Map<String, Object> fields) {
        var requestedCountry = string(fields.get("countryCode"));
        if (!CountryAccessGuard.ALL_COUNTRIES_SCOPE.equalsIgnoreCase(requestedCountry)) {
            canonicalizeRole(fields, ctx.countryCode());
            return ctx;
        }
        canonicalizeRole(fields, CountryAccessGuard.ALL_COUNTRIES_SCOPE);
        var requestedEnvironment = string(fields.get("environment"));
        var environment = requestedEnvironment == null || requestedEnvironment.isBlank() ? ctx.environment() : requestedEnvironment.toUpperCase();
        return new TenantContext(ctx.tenantId(), ctx.userId(), CountryAccessGuard.ALL_COUNTRIES_SCOPE,
                environment, ctx.correlationId(), ctx.permissions());
    }

    private static void canonicalizeRole(Map<String, Object> fields, String countryCode) {
        var role = canonicalRole(firstRoleToken(fields), countryCode);
        fields.put("roleIds", List.of(role));
        fields.put("roles", List.of(role));
    }

    private static String requestedCountry(Map<String, Object> fields, Map<String, Object> existing) {
        var requested = string(fields.get("countryCode"));
        return requested == null || requested.isBlank()
                ? String.valueOf(existing.get("countryCode")).toUpperCase(Locale.ROOT)
                : requested.toUpperCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static String firstRoleToken(Map<String, Object> fields) {
        var roleIds = fields.get("roleIds");
        if (roleIds instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.getFirst());
        }
        var roles = fields.get("roles");
        if (roles instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.getFirst());
        }
        return string(fields.get("roleId"));
    }

    private static String canonicalRole(String roleToken, String countryCode) {
        var token = roleToken == null || roleToken.isBlank() ? "VIEWER" : roleToken.trim().toUpperCase(Locale.ROOT);
        return switch (token) {
            case "ADMIN", "COUNTRY_ADMIN", "GLOBAL_ADMIN" -> CountryAccessGuard.ALL_COUNTRIES_SCOPE.equalsIgnoreCase(countryCode)
                    ? "GLOBAL_ADMIN" : "COUNTRY_ADMIN";
            case "OPERATOR", "NOC_OPERATOR" -> "NOC_OPERATOR";
            default -> "VIEWER";
        };
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }
}

