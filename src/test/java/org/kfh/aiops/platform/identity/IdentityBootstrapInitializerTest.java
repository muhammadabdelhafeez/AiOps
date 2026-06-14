package org.kfh.aiops.platform.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.boot.DefaultApplicationArguments;

class IdentityBootstrapInitializerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    void shouldCreateBootstrapAdminWhenPasswordConfiguredAndUserMissing() {
        var repository = org.mockito.Mockito.mock(IdentityJdbcRepository.class);
        var auditService = org.mockito.Mockito.mock(AuditService.class);
        var readModel = new CommandCenterReadModel();
        var properties = properties("Strong-Password-123");
        when(repository.provisionBootstrapUser(any(TenantContext.class), eq("KFH Bootstrap Admin"), any()))
                .thenReturn(new IdentityJdbcRepository.BootstrapUserProvisionResult(
                        Map.of("id", UUID.randomUUID().toString()), true, true));

        new IdentityBootstrapInitializer(repository, properties, auditService, readModel)
                .run(new DefaultApplicationArguments());

        verify(repository).ensureTenant(TENANT_ID, "KFH Group");
        verify(repository).ensureDefaultRoles(TENANT_ID);
        verify(repository).provisionBootstrapUser(any(TenantContext.class), eq("KFH Bootstrap Admin"), any());
        verify(auditService).recordWrite(any(TenantContext.class), eq("IDENTITY_BOOTSTRAP_ADMIN_CREATED"),
                eq("Identity"), anyString(), eq(null), any());
        assertThat(readModel.audit(auditContext())).hasSize(1);
        assertThat(readModel.audit(auditContext()).getFirst())
                .containsEntry("action", "IDENTITY_BOOTSTRAP_ADMIN_CREATED")
                .containsEntry("entityType", "Identity");
    }

    @Test
    void shouldNotCreateBootstrapAdminWhenPasswordMissing() {
        var repository = org.mockito.Mockito.mock(IdentityJdbcRepository.class);
        var auditService = org.mockito.Mockito.mock(AuditService.class);
        var readModel = new CommandCenterReadModel();
        var properties = properties(" ");

        new IdentityBootstrapInitializer(repository, properties, auditService, readModel)
                .run(new DefaultApplicationArguments());

        verify(repository).ensureTenant(TENANT_ID, "KFH Group");
        verify(repository).ensureDefaultRoles(TENANT_ID);
        verify(repository, never()).provisionBootstrapUser(any(), anyString(), any());
        verify(auditService, never()).recordWrite(any(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void shouldUpdateBootstrapAdminWhenExistingPasswordDiffers() {
        var repository = org.mockito.Mockito.mock(IdentityJdbcRepository.class);
        var auditService = org.mockito.Mockito.mock(AuditService.class);
        var readModel = new CommandCenterReadModel();
        var properties = properties("Strong-Password-123");
        when(repository.provisionBootstrapUser(any(TenantContext.class), eq("KFH Bootstrap Admin"), any()))
                .thenReturn(new IdentityJdbcRepository.BootstrapUserProvisionResult(
                        Map.of("id", UUID.randomUUID().toString()), true, false));

        new IdentityBootstrapInitializer(repository, properties, auditService, readModel)
                .run(new DefaultApplicationArguments());

        verify(repository).ensureTenant(TENANT_ID, "KFH Group");
        verify(repository).ensureDefaultRoles(TENANT_ID);
        verify(repository).provisionBootstrapUser(any(TenantContext.class), eq("KFH Bootstrap Admin"), any());
        verify(auditService).recordWrite(any(TenantContext.class), eq("IDENTITY_BOOTSTRAP_ADMIN_UPDATED"),
                eq("Identity"), anyString(), eq(null), any());
        assertThat(readModel.audit(auditContext())).hasSize(1);
        assertThat(readModel.audit(auditContext()).getFirst())
                .containsEntry("action", "IDENTITY_BOOTSTRAP_ADMIN_UPDATED")
                .containsEntry("entityType", "Identity");
    }

    @Test
    void shouldNotAuditWhenBootstrapAdminAlreadyReady() {
        var repository = org.mockito.Mockito.mock(IdentityJdbcRepository.class);
        var auditService = org.mockito.Mockito.mock(AuditService.class);
        var readModel = new CommandCenterReadModel();
        var properties = properties("Strong-Password-123");
        when(repository.provisionBootstrapUser(any(TenantContext.class), eq("KFH Bootstrap Admin"), any()))
                .thenReturn(new IdentityJdbcRepository.BootstrapUserProvisionResult(
                        Map.of("id", UUID.randomUUID().toString()), false, false));

        new IdentityBootstrapInitializer(repository, properties, auditService, readModel)
                .run(new DefaultApplicationArguments());

        verify(repository).ensureTenant(TENANT_ID, "KFH Group");
        verify(repository).ensureDefaultRoles(TENANT_ID);
        verify(repository).provisionBootstrapUser(any(TenantContext.class), eq("KFH Bootstrap Admin"), any());
        verify(auditService, never()).recordWrite(any(), anyString(), anyString(), anyString(), any(), any());
        assertThat(readModel.audit(auditContext())).isEmpty();
    }

    private static TenantContext auditContext() {
        return new TenantContext(TENANT_ID, UUID.fromString("00000000-0000-4000-8000-000000000000"), "KW", "PROD",
                "test", Set.of("AUDIT_READ"));
    }

    private static IdentityBootstrapProperties properties(String password) {
        var properties = new IdentityBootstrapProperties();
        properties.setTenantId(TENANT_ID);
        properties.setTenantName("KFH Group");
        properties.setUsername("admin");
        properties.setPassword(password);
        properties.setDisplayName("KFH Bootstrap Admin");
        properties.setCountryCode("KW");
        properties.setEnvironment("PROD");
        properties.setRoleName("GLOBAL_ADMIN");
        return properties;
    }
}


