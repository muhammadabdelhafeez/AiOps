package org.kfh.aiops.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.tenant.TenantContext;

class SettingsServiceTest {

    @Test
    void shouldAppendAuditActivityWhenSettingsUpdated() {
        var readModel = new CommandCenterReadModel();
        var service = service(readModel);
        var ctx = context();

        service.update(ctx, Map.of("dashboardRefreshSeconds", 20, "apiToken", "do-not-show"));

        var rows = readModel.audit(ctx);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst())
                .containsEntry("action", "SETTINGS_UPDATED")
                .containsEntry("entityType", "Settings")
                .containsEntry("result", "Success")
                .doesNotContainKeys("apiToken", "password", "secret");
        assertThat(rows.getFirst().get("details").toString()).contains("dashboardRefreshSeconds").doesNotContain("do-not-show");
    }

    @Test
    void shouldAppendAuditActivityWhenSettingsTestRequested() {
        var readModel = new CommandCenterReadModel();
        var service = service(readModel);
        var ctx = context();

        service.test(ctx, "notifications", Map.of("webhookUrl", "https://example.invalid/hook"));

        assertThat(readModel.audit(ctx)).hasSize(1);
        assertThat(readModel.audit(ctx).getFirst())
                .containsEntry("action", "SETTINGS_TEST_REQUESTED")
                .containsEntry("entityType", "Settings")
                .containsEntry("entityId", "notifications");
    }

    private static SettingsService service(CommandCenterReadModel readModel) {
        AuditService audit = (ctx, action, entityType, entityId, beforeState, afterState) -> { };
        return new SettingsService(audit, readModel);
    }

    private static TenantContext context() {
        return new TenantContext(UUID.fromString("00000000-0000-4000-8000-000000000001"),
                UUID.fromString("00000000-0000-4000-8000-000000000101"), "KW", "PROD", "settings-test",
                Set.of("SETTINGS_READ", "SETTINGS_WRITE", "AUDIT_READ"));
    }
}

