package org.kfh.aiops.platform.config;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {

    private final Map<String, Object> settings = new ConcurrentHashMap<>();
    private final AuditService auditService;
    private final CommandCenterReadModel readModel;

    public SettingsService(AuditService auditService, CommandCenterReadModel readModel) {
        this.auditService = auditService;
        this.readModel = readModel;
        settings.put("dashboardRefreshSeconds", 30);
        settings.put("quietPeriodMinutes", 15);
        settings.put("aiMode", "EVIDENCE_PACK_ONLY");
    }

    public Map<String, Object> get(TenantContext ctx) {
        ctx.requirePermission("SETTINGS_READ");
        return Map.copyOf(settings);
    }

    public Map<String, Object> update(TenantContext ctx, Map<String, Object> request) {
        ctx.requirePermission("SETTINGS_WRITE");
        settings.putAll(request);
        auditService.recordWrite(ctx, "SETTINGS_UPDATED", "Settings", ctx.tenantId().toString(), null,
                Map.of("updatedKeys", safeKeys(request)));
        readModel.appendAudit(ctx, "SETTINGS_UPDATED", "Settings", ctx.tenantId().toString(), Map.of(
                "message", "Application settings updated",
                "updatedKeys", safeKeys(request),
                "details", Map.of("updatedKeys", safeKeys(request))));
        return Map.copyOf(settings);
    }

    public Map<String, Object> test(TenantContext ctx, String section, Map<String, Object> request) {
        ctx.requirePermission("SETTINGS_WRITE");
        auditService.recordWrite(ctx, "SETTINGS_TEST_REQUESTED", "Settings", section, null,
                Map.of("section", section, "submittedKeys", safeKeys(request)));
        readModel.appendAudit(ctx, "SETTINGS_TEST_REQUESTED", "Settings", section, Map.of(
                "message", "Settings test requested for " + section,
                "details", Map.of("section", section, "submittedKeys", safeKeys(request))));
        return Map.of("section", section, "status", "QUEUED", "correlationId", ctx.correlationId());
    }

    private static List<String> safeKeys(Map<String, Object> request) {
        if (request == null || request.isEmpty()) {
            return List.of();
        }
        return request.keySet().stream()
                .filter(key -> !isSecretLike(key))
                .sorted()
                .toList();
    }

    private static boolean isSecretLike(String key) {
        var normalized = key == null ? "" : key.toLowerCase();
        return normalized.contains("password") || normalized.contains("token") || normalized.contains("secret")
                || normalized.contains("apikey") || normalized.contains("api_key") || normalized.contains("credential");
    }
}

