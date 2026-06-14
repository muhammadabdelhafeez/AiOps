package org.kfh.aiops.commandcenter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.commandcenter.dashboard.DashboardService;
import org.kfh.aiops.commandcenter.dto.UiWriteRequest;
import org.kfh.aiops.commandcenter.support.CommandCenterReadModel;
import org.kfh.aiops.platform.audit.AuditService;
import org.kfh.aiops.platform.country.CountryAccessGuard;
import org.kfh.aiops.platform.country.CountryRegistry;
import org.kfh.aiops.platform.exception.ForbiddenAccessException;
import org.kfh.aiops.platform.identity.IdentityAdminService;
import org.kfh.aiops.platform.identity.IdentityJdbcRepository;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.kfh.aiops.plugin.service.ConnectorService;

class CommandCenterBackendServiceTest {

    private final CommandCenterReadModel readModel = new CommandCenterReadModel();
    private final CountryAccessGuard guard = new CountryAccessGuard(new CountryRegistry(Set.of("KW", "BH", "EG")));
    private final AuditService audit = (ctx, action, entityType, entityId, beforeState, afterState) -> { };

    @Test
    void shouldReturnTenantScopedDashboardKpisWhenCountryAllowed() {
        var service = new DashboardService(readModel, guard);
        var kpis = service.kpis(ctx("KW", Set.of("DASHBOARD_READ")), "KW", "PROD");
        assertEquals("KW", kpis.get("countryCode"));
        assertEquals(1, kpis.get("openIncidents"));
    }

    @Test
    void shouldDenyDashboardWhenCrossCountryWithoutPermission() {
        var service = new DashboardService(readModel, guard);
        assertThrows(ForbiddenAccessException.class,
                () -> service.kpis(ctx("KW", Set.of("DASHBOARD_READ")), "BH", "PROD"));
    }

    @Test
    void shouldNotExposePlainConnectorSecretsWhenCreatingConnector() {
        var service = new ConnectorService(readModel, guard, audit);
        var request = new UiWriteRequest("BMC Helix", null, null, null, true,
                Map.of("pluginType", "BMC", "secretsPlain", Map.of("password", "dont-return")));
        var created = service.create(ctx("KW", Set.of("CONNECTOR_WRITE")), request);
        assertFalse(created.containsKey("secretsPlain"));
        assertEquals("configured", created.get("secretsMask"));
    }

    @Test
    void shouldListOnlyCurrentCountryUsersWhenCountryScopeSelected() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        when(repository.users(any(UUID.class), eq("KW"), eq("PROD")))
                .thenReturn(List.of(Map.of("countryCode", "KW", "username", "kw.operator")));
        when(repository.users(any(UUID.class), eq("BH"), eq("PROD")))
                .thenReturn(List.of(Map.of("countryCode", "BH", "username", "bh.operator")));

        var kwUsers = service.users(ctx("KW", Set.of("IDENTITY_READ")), "KW", "PROD", 0, 100);
        var bhUsers = service.users(ctx("BH", Set.of("IDENTITY_READ")), "BH", "PROD", 0, 100);

        assertEquals(1, kwUsers.totalElements());
        assertEquals(1, bhUsers.totalElements());
        assertEquals("KW", kwUsers.content().getFirst().get("countryCode"));
        assertEquals("BH", bhUsers.content().getFirst().get("countryCode"));
    }

    @Test
    void shouldDenyCrossCountryUserListingWithoutGlobalPermission() {
        var service = new IdentityAdminService(readModel, audit, guard, mock(IdentityJdbcRepository.class));
        assertThrows(ForbiddenAccessException.class,
                () -> service.users(ctx("KW", Set.of("IDENTITY_READ")), "BH", "PROD", 0, 20));
    }

    @Test
    void shouldIgnoreUserPayloadCountryWhenCreatingScopedUser() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        var created = Map.<String, Object>of("id", UUID.randomUUID(), "countryCode", "BH", "environment", "PROD");
        when(repository.createUser(any(TenantContext.class), anyString(), any())).thenReturn(created);

        service.createUser(ctx("BH", Set.of("IDENTITY_WRITE")),
                new UiWriteRequest("Scoped Operator", null, null, null, true,
                        Map.of("countryCode", "KW", "environment", "DEV")));

        verify(repository).createUser(any(TenantContext.class), eq("Scoped Operator"), any());
    }

    @Test
    void shouldNotExposePasswordFieldsWhenCreatingUser() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        when(repository.createUser(any(TenantContext.class), anyString(), any()))
                .thenReturn(Map.of("id", UUID.randomUUID(), "username", "secure.operator"));

        var created = service.createUser(ctx("KW", Set.of("IDENTITY_WRITE")),
                new UiWriteRequest("Secure Operator", null, null, null, true,
                        Map.of("username", "secure.operator", "password", "DoNotReturn123!",
                                "confirmPassword", "DoNotReturn123!")));

        assertFalse(created.containsKey("password"));
        assertFalse(created.containsKey("confirmPassword"));
        assertFalse(created.containsKey("passwordHash"));
    }

    @Test
    void shouldUpdateUserProfileWhenEditSubmitted() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        var userId = UUID.randomUUID();
        when(repository.findUser(any(UUID.class), eq(userId)))
                .thenReturn(Optional.of(Map.of("id", userId.toString(), "countryCode", "KW")));
        when(repository.updateUser(any(UUID.class), eq(userId), eq("Ahmed Salem"), any()))
                .thenReturn(Map.of("id", userId.toString(), "username", "ahmed", "countryCode", "KW", "status", "ACTIVE"));

        var updated = service.updateUser(ctx("KW", Set.of("IDENTITY_READ", "IDENTITY_WRITE")), userId,
                new UiWriteRequest("Ahmed Salem", null, null, "Active", true,
                        Map.of("username", "ahmed", "email", "92338@kfh.com", "roleIds", List.of("COUNTRY_ADMIN"))));

        assertEquals("ahmed", updated.get("username"));
        verify(repository).updateUser(any(UUID.class), eq(userId), eq("Ahmed Salem"), any());
    }

    @Test
    void shouldCreateAllCountriesUserWhenGlobalScopeAllowed() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        when(repository.createUser(any(TenantContext.class), anyString(), any()))
                .thenReturn(Map.of("id", UUID.randomUUID(), "countryCode", "ALL"));

        var created = service.createUser(
                ctx("KW", Set.of("IDENTITY_WRITE", "COUNTRY_GLOBAL_VIEW")),
                new UiWriteRequest("Global Operator", null, null, null, true,
                        Map.of("username", "global.operator", "countryCode", "ALL", "roleIds", List.of("ADMIN"))));

        assertEquals("ALL", created.get("countryCode"));
        verify(repository).createUser(argThat(context -> "ALL".equals(context.countryCode())), eq("Global Operator"),
                argThat(fields -> List.of("GLOBAL_ADMIN").equals(fields.get("roleIds"))));
    }

    @Test
    void shouldCreateCountryAdminWhenAdminRoleIsCountryScoped() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        when(repository.createUser(any(TenantContext.class), anyString(), any()))
                .thenReturn(Map.of("id", UUID.randomUUID(), "countryCode", "KW"));

        service.createUser(ctx("KW", Set.of("IDENTITY_WRITE")),
                new UiWriteRequest("Country Admin", null, null, null, true,
                        Map.of("username", "kw.admin", "roleIds", List.of("ADMIN"))));

        verify(repository).createUser(argThat(context -> "KW".equals(context.countryCode())), eq("Country Admin"),
                argThat(fields -> List.of("COUNTRY_ADMIN").equals(fields.get("roleIds"))));
    }

    @Test
    void shouldResetUserPasswordThroughIdentityRepository() {
        var repository = mock(IdentityJdbcRepository.class);
        var service = new IdentityAdminService(readModel, audit, guard, repository);
        var userId = UUID.randomUUID();
        when(repository.findUser(any(UUID.class), eq(userId)))
                .thenReturn(Optional.of(Map.of("id", userId.toString(), "countryCode", "KW")));
        when(repository.updatePassword(any(UUID.class), eq(userId), any()))
                .thenReturn(Map.of("id", userId.toString(), "countryCode", "KW"));

        var updated = service.resetPassword(ctx("KW", Set.of("IDENTITY_READ", "IDENTITY_WRITE")), userId,
                new UiWriteRequest("Country Admin", null, null, null, true,
                        Map.of("password", "New-Strong-Password-123")));

        assertEquals(userId.toString(), updated.get("id"));
        verify(repository).updatePassword(any(UUID.class), eq(userId),
                argThat(fields -> "New-Strong-Password-123".equals(fields.get("password"))));
    }

    @Test
    void shouldDenyAllCountriesUserCreationWithoutGlobalScope() {
        var service = new IdentityAdminService(readModel, audit, guard, mock(IdentityJdbcRepository.class));
        assertThrows(ForbiddenAccessException.class, () -> service.createUser(ctx("ALL", Set.of("IDENTITY_WRITE")),
                new UiWriteRequest("Blocked Global Operator", null, null, null, true,
                        Map.of("username", "blocked.global"))));
    }

    private static TenantContext ctx(String country, Set<String> permissions) {
        return new TenantContext(UUID.randomUUID(), UUID.randomUUID(), country, "PROD", "test-corr", permissions);
    }
}

