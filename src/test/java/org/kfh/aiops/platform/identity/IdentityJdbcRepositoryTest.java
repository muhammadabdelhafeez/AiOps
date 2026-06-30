package org.kfh.aiops.platform.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.exception.ConflictException;
import org.kfh.aiops.platform.exception.ValidationException;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

class IdentityJdbcRepositoryTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Test
    void shouldCreateTenantWithUniqueFallbackNameWhenDefaultNameAlreadyExists() {
        var jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        var repository = new IdentityJdbcRepository(jdbcTemplate, org.mockito.Mockito.mock(PasswordEncoder.class));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TENANT_ID))).thenReturn(0);
        when(jdbcTemplate.update(contains("INSERT INTO public.tenants"), eq(TENANT_ID), eq("KFH Group")))
                .thenThrow(new DuplicateKeyException("duplicate tenant name"));

        repository.ensureTenant(TENANT_ID, "KFH Group");

        verify(jdbcTemplate).update(contains("INSERT INTO public.tenants"), eq(TENANT_ID), eq("KFH Group"));
        verify(jdbcTemplate).update(contains("INSERT INTO public.tenants"), eq(TENANT_ID),
                eq("KFH Group [" + TENANT_ID + "]"));
    }

    @Test
    void shouldNotInsertTenantWhenTenantAlreadyExists() {
        var jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        var repository = new IdentityJdbcRepository(jdbcTemplate, org.mockito.Mockito.mock(PasswordEncoder.class));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TENANT_ID))).thenReturn(1);

        repository.ensureTenant(TENANT_ID, "KFH Group");

        verify(jdbcTemplate, never()).update(contains("INSERT INTO public.tenants"), any(), any());
    }

    @Test
    void shouldEnsureDefaultRolesIncludeAuditReadForCountryAdmins() {
        var jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        var roleId = UUID.fromString("00000000-0000-4000-8000-000000000010");
        var repository = new IdentityJdbcRepository(jdbcTemplate, org.mockito.Mockito.mock(PasswordEncoder.class));
        when(jdbcTemplate.query(contains("SELECT role_id FROM identity.roles"), anyUuidRowMapper(), eq(TENANT_ID), anyString()))
                .thenReturn(java.util.List.of(roleId));

        repository.ensureDefaultRoles(TENANT_ID);

        verify(jdbcTemplate).update(contains("INSERT INTO identity.role_permissions"), eq(TENANT_ID), eq(roleId), eq("AUDIT_READ"));
    }

    @Test
    void shouldTryAllCountryIdentityWhenPhysicalCountrySignInHasNoExactMatch() {
        var jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        var repository = new IdentityJdbcRepository(jdbcTemplate, org.mockito.Mockito.mock(PasswordEncoder.class));
        when(jdbcTemplate.query(contains("WHERE lower(u.username)"), anySignInCandidateRowMapper(), eq("Admin"), eq("KW"), eq("PROD")))
                .thenReturn(java.util.List.of());
        when(jdbcTemplate.query(contains("WHERE lower(u.username)"), anySignInCandidateRowMapper(), eq("Admin"), eq("ALL"), eq("PROD")))
                .thenReturn(java.util.List.of());

        var result = repository.signIn(new IdentitySignInRequest("Admin", "not-logged", "KW", "PROD"));

        assertTrue(result.isEmpty());
        verify(jdbcTemplate).query(contains("WHERE lower(u.username)"), anySignInCandidateRowMapper(), eq("Admin"), eq("KW"), eq("PROD"));
        verify(jdbcTemplate).query(contains("WHERE lower(u.username)"), anySignInCandidateRowMapper(), eq("Admin"), eq("ALL"), eq("PROD"));
    }

    @Test
    void shouldEnsureTenantBeforeCreatingUser() {
        var jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        var passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        var repository = org.mockito.Mockito.spy(new IdentityJdbcRepository(jdbcTemplate, passwordEncoder));
        var userId = UUID.randomUUID();
        var createdUser = Map.<String, Object>of("id", userId.toString(), "username", "operator1");
        doNothing().when(repository).ensureTenant(TENANT_ID, "KFH Group");
        doNothing().when(repository).ensureDefaultRoles(TENANT_ID);
        doReturn(false).when(repository).userExists(TENANT_ID, "operator1", "KW", "PROD");
        doReturn(Optional.of(createdUser)).when(repository).findUser(eq(TENANT_ID), any(UUID.class));
        when(passwordEncoder.encode("Strong-Password-123")).thenReturn("encoded-password");

        var result = repository.createUser(context(), "NOC Operator", Map.of(
                "username", "operator1",
                "password", "Strong-Password-123",
                "roleId", "NOC_OPERATOR"));

        assertEquals(createdUser, result);
        var order = inOrder(repository);
        order.verify(repository).ensureTenant(TENANT_ID, "KFH Group");
        order.verify(repository).ensureDefaultRoles(TENANT_ID);
    }

    @Test
    void shouldRejectDuplicateUsernameWhenCreatingUser() {
        var repository = org.mockito.Mockito.spy(new IdentityJdbcRepository(
                org.mockito.Mockito.mock(JdbcTemplate.class),
                org.mockito.Mockito.mock(PasswordEncoder.class)));
        doNothing().when(repository).ensureTenant(TENANT_ID, "KFH Group");
        doNothing().when(repository).ensureDefaultRoles(TENANT_ID);
        doReturn(true).when(repository).userExists(TENANT_ID, "operator1", "KW", "PROD");

        assertThrows(ConflictException.class, () -> repository.createUser(context(), "NOC Operator", Map.of(
                "username", "operator1",
                "password", "Strong-Password-123")));

        verify(repository, never()).findUser(eq(TENANT_ID), any(UUID.class));
    }

    @Test
    void shouldValidatePasswordWhenCreatingUser() {
        var repository = org.mockito.Mockito.spy(new IdentityJdbcRepository(
                org.mockito.Mockito.mock(JdbcTemplate.class),
                org.mockito.Mockito.mock(PasswordEncoder.class)));
        doNothing().when(repository).ensureTenant(TENANT_ID, "KFH Group");
        doNothing().when(repository).ensureDefaultRoles(TENANT_ID);

        assertThrows(ValidationException.class, () -> repository.createUser(context(), "NOC Operator", Map.of(
                "username", "operator1")));

        verify(repository, never()).userExists(eq(TENANT_ID), anyString(), anyString(), anyString());
    }

    @Test
    void shouldHashPasswordWhenUpdatingUserProfilePassword() {
        var jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        var passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        var repository = org.mockito.Mockito.spy(new IdentityJdbcRepository(jdbcTemplate, passwordEncoder));
        var userId = UUID.randomUUID();
        var updatedUser = Map.<String, Object>of("id", userId.toString(), "username", "operator1");
        doReturn(Optional.of(updatedUser)).when(repository).findUser(TENANT_ID, userId);
        when(passwordEncoder.encode("New-Strong-Password-123")).thenReturn("encoded-new-password");

        var result = repository.updateUser(TENANT_ID, userId, "NOC Operator", Map.of(
                "username", "operator1",
                "password", "New-Strong-Password-123"));

        assertEquals(updatedUser, result);
        verify(passwordEncoder).encode("New-Strong-Password-123");
        verify(jdbcTemplate, times(1)).update(org.mockito.ArgumentMatchers.contains("password_hash = COALESCE"),
                eq("operator1"), eq("NOC Operator"), eq(null), eq(null), eq(null), eq("encoded-new-password"), eq(TENANT_ID), eq(userId));
    }

    @Test
    void shouldUpdateUserCountryWithoutHashingPasswordWhenPasswordNotSubmitted() {
        var jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        var passwordEncoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        var repository = org.mockito.Mockito.spy(new IdentityJdbcRepository(jdbcTemplate, passwordEncoder));
        var userId = UUID.randomUUID();
        var updatedUser = Map.<String, Object>of("id", userId.toString(), "username", "operator1", "countryCode", "BH");
        doReturn(Optional.of(updatedUser)).when(repository).findUser(TENANT_ID, userId);

        var result = repository.updateUser(TENANT_ID, userId, "NOC Operator", Map.of(
                "username", "operator1",
                "countryCode", "BH"));

        assertEquals(updatedUser, result);
        verify(passwordEncoder, never()).encode(anyString());
        verify(jdbcTemplate, times(1)).update(org.mockito.ArgumentMatchers.contains("country_code = COALESCE"),
                eq("operator1"), eq("NOC Operator"), eq(null), eq("BH"), eq(null), eq(null), eq(TENANT_ID), eq(userId));
    }

    @Test
    void shouldRequirePasswordWhenResettingUserPassword() {
        var repository = org.mockito.Mockito.spy(new IdentityJdbcRepository(
                org.mockito.Mockito.mock(JdbcTemplate.class),
                org.mockito.Mockito.mock(PasswordEncoder.class)));
        var userId = UUID.randomUUID();

        assertThrows(ValidationException.class, () -> repository.updatePassword(TENANT_ID, userId, Map.of()));

        verify(repository, never()).findUser(eq(TENANT_ID), eq(userId));
    }

    private static TenantContext context() {
        return new TenantContext(TENANT_ID, UUID.randomUUID(), "KW", "PROD", "corr-users-create-test", Set.of("IDENTITY_WRITE"));
    }

    private static RowMapper<UUID> anyUuidRowMapper() {
        return org.mockito.ArgumentMatchers.any();
    }

    private static RowMapper<?> anySignInCandidateRowMapper() {
        return org.mockito.ArgumentMatchers.any();
    }
}


