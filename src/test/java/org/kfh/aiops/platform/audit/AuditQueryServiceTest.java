package org.kfh.aiops.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.exception.ForbiddenAccessException;
import org.kfh.aiops.platform.exception.NotFoundException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.dao.DataAccessResourceFailureException;

class AuditQueryServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_TENANT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID USER_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void shouldNotReturnSeededDummyAuditRowsWhenNoApplicationActionOccurred() {
        var service = new AuditQueryService(new CommandCenterReadModel());

        var page = service.list(context(TENANT_ID, "KW"), 0, 100);

        assertThat(page.content()).isEmpty();
    }

    @Test
    void shouldReturnRealApplicationActivityWhenAuditIsAppended() {
        var readModel = new CommandCenterReadModel();
        var ctx = context(TENANT_ID, "KW");
        readModel.appendAudit(ctx, "CONNECTOR_TEST_REQUESTED", "Connector", "connector-1");
        var service = new AuditQueryService(readModel);

        var page = service.list(ctx, 0, 100);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst())
                .containsEntry("tenantId", TENANT_ID.toString())
                .containsEntry("countryCode", "KW")
                .containsEntry("environment", "PROD")
                .containsEntry("action", "CONNECTOR_TEST_REQUESTED")
                .containsEntry("entityType", "Connector")
                .containsEntry("result", "Success");
    }

    @Test
    void shouldReturnPersistedAuditActivityBeforeInMemoryFallback() {
        var readModel = new CommandCenterReadModel();
        var ctx = context(TENANT_ID, "KW");
        readModel.appendAudit(ctx, "MEMORY_ONLY", "Memory", "memory-1");
        var repository = mock(AuditActivityRepository.class);
        when(repository.list(ctx)).thenReturn(List.of(Map.of(
                "id", UUID.randomUUID().toString(),
                "tenantId", TENANT_ID.toString(),
                "countryCode", "KW",
                "environment", "PROD",
                "action", "DATABASE_AUDIT",
                "entityType", "Audit")));
        var service = new AuditQueryService(readModel, repository);

        var page = service.list(ctx, 0, 100);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst()).containsEntry("action", "DATABASE_AUDIT");
    }

    @Test
    void shouldFallbackToInMemoryAuditWhenPersistedListFails() {
        var readModel = new CommandCenterReadModel();
        var ctx = context(TENANT_ID, "KW");
        readModel.appendAudit(ctx, "MEMORY_FALLBACK", "Audit", "memory-1");
        var repository = mock(AuditActivityRepository.class);
        when(repository.list(ctx)).thenThrow(new DataAccessResourceFailureException("audit table unavailable"));
        var service = new AuditQueryService(readModel, repository);

        var page = service.list(ctx, 0, 100);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst()).containsEntry("action", "MEMORY_FALLBACK");
    }

    @Test
    void shouldFallbackToInMemoryAuditDetailWhenPersistedDetailFails() {
        var readModel = new CommandCenterReadModel();
        var ctx = context(TENANT_ID, "KW");
        readModel.appendAudit(ctx, "MEMORY_DETAIL", "Audit", "memory-1");
        var auditId = UUID.fromString(String.valueOf(readModel.audit(ctx).getFirst().get("id")));
        var repository = mock(AuditActivityRepository.class);
        when(repository.find(ctx, auditId)).thenThrow(new DataAccessResourceFailureException("audit table unavailable"));
        var service = new AuditQueryService(readModel, repository);

        var row = service.get(ctx, auditId);

        assertThat(row).containsEntry("action", "MEMORY_DETAIL");
    }

    @Test
    void shouldNotReturnAuditActivityAcrossTenantOrCountryScope() {
        var readModel = new CommandCenterReadModel();
        var kwContext = context(TENANT_ID, "KW");
        readModel.appendAudit(kwContext, "INCIDENT_UPDATED", "Incident", "incident-1");
        var auditId = UUID.fromString(String.valueOf(readModel.audit(kwContext).getFirst().get("id")));
        var service = new AuditQueryService(readModel);

        assertThat(service.list(context(OTHER_TENANT_ID, "KW"), 0, 100).content()).isEmpty();
        assertThat(service.list(context(TENANT_ID, "BH"), 0, 100).content()).isEmpty();
        assertThatThrownBy(() -> service.get(context(OTHER_TENANT_ID, "KW"), auditId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Audit event not found");
    }

    @Test
    void shouldReturnOnlyCountryAuditActivityForCountryAdminAuditRead() {
        var readModel = new CommandCenterReadModel();
        readModel.appendAudit(context(TENANT_ID, "KW"), "KW_INCIDENT_UPDATED", "Incident", "incident-kw");
        readModel.appendAudit(context(TENANT_ID, "BH"), "BH_SCHEDULE_RUN_REQUESTED", "Schedule", "schedule-bh");
        var service = new AuditQueryService(readModel);

        var page = service.list(context(TENANT_ID, "KW"), 0, 100);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst())
                .containsEntry("countryCode", "KW")
                .containsEntry("action", "KW_INCIDENT_UPDATED");
    }

    @Test
    void shouldRejectAllCountryAuditActivityWithoutGlobalCountryView() {
        var service = new AuditQueryService(new CommandCenterReadModel());

        assertThatThrownBy(() -> service.list(context(TENANT_ID, "ALL"), 0, 100))
                .isInstanceOf(ForbiddenAccessException.class)
                .hasMessageContaining("COUNTRY_GLOBAL_VIEW");
    }

    @Test
    void shouldReturnAllCountryAuditActivityForAllCountryScope() {
        var readModel = new CommandCenterReadModel();
        readModel.appendAudit(context(TENANT_ID, "KW"), "INCIDENT_UPDATED", "Incident", "incident-1");
        readModel.appendAudit(context(TENANT_ID, "BH"), "SCHEDULE_RUN_REQUESTED", "Schedule", "schedule-1");
        var service = new AuditQueryService(readModel);

        var page = service.list(globalContext(), 0, 100);

        assertThat(page.content()).extracting(row -> row.get("countryCode")).containsExactlyInAnyOrder("KW", "BH");
    }

    private static TenantContext context(UUID tenantId, String countryCode) {
        return new TenantContext(tenantId, USER_ID, countryCode, "PROD", "audit-test-correlation",
                Set.of("AUDIT_READ"));
    }

    private static TenantContext globalContext() {
        return new TenantContext(TENANT_ID, USER_ID, "ALL", "PROD", "audit-test-correlation",
                Set.of("AUDIT_READ", "COUNTRY_GLOBAL_VIEW"));
    }
}


