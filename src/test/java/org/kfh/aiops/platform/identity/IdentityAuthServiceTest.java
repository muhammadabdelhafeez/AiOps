package org.kfh.aiops.platform.identity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.exception.ForbiddenAccessException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.mockito.ArgumentCaptor;

class IdentityAuthServiceTest {

    @Test
    void shouldReturnSignInResponseWhenCredentialsMatch() {
        var repository = org.mockito.Mockito.mock(IdentityJdbcRepository.class);
        var request = request();
        var response = response();
        when(repository.signIn(request)).thenReturn(Optional.of(response));
        var bootstrap = bootstrap(null);
        var readModel = new CommandCenterReadModel();

        var result = service(repository, bootstrap, readModel).signIn(request);

        org.junit.jupiter.api.Assertions.assertEquals(response, result);
        verify(repository, never()).signInFailureDiagnostics(request);
        org.assertj.core.api.Assertions.assertThat(readModel.audit(context(response))).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(readModel.audit(context(response)).getFirst())
                .containsEntry("action", "LOGIN_SUCCEEDED")
                .containsEntry("entityType", "Security")
                .containsEntry("entityId", "AUTHENTICATION")
                .containsEntry("actorName", "Operator")
                .containsEntry("targetName", "Authentication")
                .containsEntry("result", "Success");
    }

    @Test
    void shouldPersistReadableLoginAuditMetadataWhenCredentialsMatch() {
        var repository = org.mockito.Mockito.mock(IdentityJdbcRepository.class);
        var audit = org.mockito.Mockito.mock(AuditService.class);
        var request = request();
        var response = response();
        when(repository.signIn(request)).thenReturn(Optional.of(response));
        var bootstrap = bootstrap(null);
        var readModel = new CommandCenterReadModel();

        new IdentityAuthService(repository, bootstrap, properties(), audit, readModel).signIn(request);

        var afterState = ArgumentCaptor.forClass(Object.class);
        verify(audit).recordWrite(any(TenantContext.class), eq("LOGIN_SUCCEEDED"), eq("Security"),
                eq("AUTHENTICATION"), isNull(), afterState.capture());
        org.assertj.core.api.Assertions.assertThat(afterState.getValue())
                .isInstanceOf(Map.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("actorName", "Operator")
                .containsEntry("actorUsername", "operator")
                .containsEntry("targetName", "Authentication")
                .containsEntry("targetType", "Security")
                .containsEntry("message", "Login succeeded for Operator")
                .doesNotContainKeys("password", "passwordHash");
    }

    @Test
    void shouldCollectDiagnosticsWhenCredentialsRejected() {
        var repository = org.mockito.Mockito.mock(IdentityJdbcRepository.class);
        var request = request();
        when(repository.signIn(request)).thenReturn(Optional.empty());
        when(repository.signInFailureDiagnostics(request))
                .thenReturn(new IdentityJdbcRepository.SignInFailureDiagnostics(1, 1, 1, 1));
        var bootstrap = bootstrap(null);
        var readModel = new CommandCenterReadModel();
        var properties = properties();

        assertThrows(ForbiddenAccessException.class, () -> service(repository, bootstrap, properties, readModel).signIn(request));

        verify(repository).signInFailureDiagnostics(request);
        var auditContext = new TenantContext(properties.getTenantId(), UUID.randomUUID(), "KW", "PROD", "test", Set.of("AUDIT_READ"));
        org.assertj.core.api.Assertions.assertThat(readModel.audit(auditContext)).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(readModel.audit(auditContext).getFirst())
                .containsEntry("action", "LOGIN_FAILED")
                .containsEntry("entityType", "Security")
                .containsEntry("result", "Fail")
                .doesNotContainKeys("password", "passwordHash");
    }

    @Test
    void shouldPreferBootstrapAdminBeforeDatabaseLookup() {
        var request = request();
        var response = response();
        var bootstrap = bootstrap(response);
        var repository = org.mockito.Mockito.mock(IdentityJdbcRepository.class);
        var readModel = new CommandCenterReadModel();

        var result = service(repository, bootstrap, readModel).signIn(request);

        org.junit.jupiter.api.Assertions.assertEquals(response, result);
        verify(repository, never()).signIn(request);
        org.assertj.core.api.Assertions.assertThat(readModel.audit(context(response))).hasSize(1);
    }

    private static IdentityAuthService service(IdentityJdbcRepository repository, BootstrapInMemoryAuthenticator bootstrap,
            CommandCenterReadModel readModel) {
        return service(repository, bootstrap, properties(), readModel);
    }

    private static IdentityAuthService service(IdentityJdbcRepository repository, BootstrapInMemoryAuthenticator bootstrap,
            IdentityBootstrapProperties properties, CommandCenterReadModel readModel) {
        AuditService audit = (ctx, action, entityType, entityId, beforeState, afterState) -> { };
        return new IdentityAuthService(repository, bootstrap, properties, audit, readModel);
    }

    private static TenantContext context(IdentitySignInResponse response) {
        return new TenantContext(response.tenantId(), response.userId(), response.countryCode(), response.environment(),
                "test", Set.of("AUDIT_READ", "COUNTRY_GLOBAL_VIEW"));
    }

    private static IdentityBootstrapProperties properties() {
        var properties = new IdentityBootstrapProperties();
        properties.setTenantId(UUID.fromString("00000000-0000-4000-8000-000000000001"));
        return properties;
    }

    private static BootstrapInMemoryAuthenticator bootstrap(IdentitySignInResponse result) {
        var bootstrap = org.mockito.Mockito.mock(BootstrapInMemoryAuthenticator.class);
        when(bootstrap.authenticate(org.mockito.ArgumentMatchers.any(IdentitySignInRequest.class))).thenReturn(Optional.ofNullable(result));
        when(bootstrap.diagnostics(org.mockito.ArgumentMatchers.any(IdentitySignInRequest.class)))
                .thenReturn(new BootstrapInMemoryAuthenticator.BootstrapDiagnostics(true, true, false,
                        true, true, false, "ALL", "PROD", "GLOBAL_ADMIN", false));
        return bootstrap;
    }


    private static IdentitySignInRequest request() {
        return new IdentitySignInRequest("operator", "not-logged", "KW", "PROD");
    }

    private static IdentitySignInResponse response() {
        return new IdentitySignInResponse(UUID.randomUUID(), UUID.randomUUID(), "operator", "Operator",
                null, "KW", "KFH Kuwait", "KFH Group", "PROD", "GLOBAL_ADMIN",
                "KFH Global Admin", List.of("*"), Instant.now());
    }
}

