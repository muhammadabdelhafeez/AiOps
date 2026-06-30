package org.kfh.aiops.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AuditActivityRepositoryTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void shouldPersistAuditActivityWithoutSecretValues() {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var repository = new AuditActivityRepository(jdbcTemplate, new ObjectMapper());
        var json = ArgumentCaptor.forClass(String.class);
        var ctx = new TenantContext(TENANT_ID, USER_ID, "KW", "PROD", "corr-audit-db", Set.of("IDENTITY_WRITE"));

        repository.recordWrite(ctx, "SETTINGS_UPDATED", "Settings", "tenant", null,
                Map.of("dashboardRefreshSeconds", 20, "apiToken", "do-not-store", "nested", Map.of("password", "hidden")));

        verify(jdbcTemplate).update(contains("INSERT INTO public.tenants"), eq(TENANT_ID), eq("Tenant " + TENANT_ID));
        verify(jdbcTemplate).update(contains("INSERT INTO identity.audit_log"), any(UUID.class), eq(TENANT_ID), eq(USER_ID),
                eq("SETTINGS_UPDATED"), eq("Settings"), eq("tenant"), json.capture());
        assertThat(json.getValue())
                .contains("SETTINGS_UPDATED", "KW", "PROD", "CORR-AUDIT-DB", "dashboardRefreshSeconds")
                .doesNotContain("do-not-store", "apiToken", "password", "hidden");
    }

    @Test
    void shouldPersistReadableLoginDisplayMetadataWithoutSecretValues() {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var repository = new AuditActivityRepository(jdbcTemplate, new ObjectMapper());
        var json = ArgumentCaptor.forClass(String.class);
        var ctx = new TenantContext(TENANT_ID, USER_ID, "ALL", "PROD", "corr-login", Set.of("*"));

        repository.recordWrite(ctx, "LOGIN_SUCCEEDED", "Security", "AUTHENTICATION", null,
                Map.of("actorName", "KFH Global Admin", "actorUsername", "admin", "targetName", "Authentication",
                        "targetType", "Security", "message", "Login succeeded for KFH Global Admin", "apiToken", "do-not-store"));

        verify(jdbcTemplate).update(contains("INSERT INTO identity.audit_log"), any(UUID.class), eq(TENANT_ID), eq(USER_ID),
                eq("LOGIN_SUCCEEDED"), eq("Security"), eq("AUTHENTICATION"), json.capture());
        assertThat(json.getValue())
                .contains("KFH Global Admin", "admin", "Authentication", "Login succeeded for KFH Global Admin")
                .doesNotContain("apiToken", "do-not-store");
    }

    @Test
    void shouldDeriveReadableLoginDisplayForLegacyUuidLoginRows() throws SQLException {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var repository = new AuditActivityRepository(jdbcTemplate, new ObjectMapper());
        var rowMapper = new AtomicReference<RowMapper<Map<String, Object>>>();
        when(jdbcTemplate.query(anyString(), anyAuditRowMapper(), any(Object[].class))).thenAnswer(invocation -> {
            rowMapper.set(invocation.getArgument(1));
            return List.of();
        });

        repository.list(context("ALL", Set.of("AUDIT_READ", "COUNTRY_GLOBAL_VIEW")));

        verify(jdbcTemplate).query(anyString(), anyAuditRowMapper(), any(Object[].class));
        var mapper = rowMapper.get();
        assertThat(mapper).isNotNull();
        var rs = mockLegacyLoginResultSet();

        var row = mapper.mapRow(rs, 0);

        assertThat(row)
                .containsEntry("action", "LOGIN_SUCCEEDED")
                .containsEntry("entityId", "00000000-0000-4000-8000-000000000000")
                .containsEntry("actorName", "admin")
                .containsEntry("targetName", "Authentication")
                .containsEntry("targetId", "AUTHENTICATION")
                .containsEntry("result", "Success")
                .containsEntry("message", "Login succeeded for admin");
    }

    @Test
    void shouldFilterPersistedAuditListByCountryForCountryUser() {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var repository = new AuditActivityRepository(jdbcTemplate, new ObjectMapper());
        var sql = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(Object[].class);
        when(jdbcTemplate.query(anyString(), anyAuditRowMapper(), any(Object[].class))).thenReturn(List.of());

        repository.list(context("KW", Set.of("AUDIT_READ")));

        verify(jdbcTemplate).query(sql.capture(), anyAuditRowMapper(), args.capture());
        assertThat(sql.getValue()).contains("details->>'countryCode'");
        assertThat(args.getValue()).containsExactly(TENANT_ID, "PROD", "KW");
    }

    @Test
    void shouldNotApplyCountryPredicateForAllCountryAuditList() {
        var jdbcTemplate = mock(JdbcTemplate.class);
        var repository = new AuditActivityRepository(jdbcTemplate, new ObjectMapper());
        var sql = ArgumentCaptor.forClass(String.class);
        var args = ArgumentCaptor.forClass(Object[].class);
        when(jdbcTemplate.query(anyString(), anyAuditRowMapper(), any(Object[].class))).thenReturn(List.of());

        repository.list(context("ALL", Set.of("AUDIT_READ", "COUNTRY_GLOBAL_VIEW")));

        verify(jdbcTemplate).query(sql.capture(), anyAuditRowMapper(), args.capture());
        assertThat(sql.getValue()).doesNotContain("details->>'countryCode'");
        assertThat(args.getValue()).containsExactly(TENANT_ID, "PROD");
    }

    private static TenantContext context(String countryCode, Set<String> permissions) {
        return new TenantContext(TENANT_ID, USER_ID, countryCode, "PROD", "corr-audit-db", permissions);
    }

    private static ResultSet mockLegacyLoginResultSet() throws SQLException {
        var rs = mock(ResultSet.class);
        when(rs.getString("audit_id")).thenReturn("33333333-3333-3333-3333-333333333333");
        when(rs.getString("tenant_id")).thenReturn(TENANT_ID.toString());
        when(rs.getString("action")).thenReturn("LOGIN_SUCCEEDED");
        when(rs.getString("entity_type")).thenReturn("Security");
        when(rs.getString("entity_id")).thenReturn("00000000-0000-4000-8000-000000000000");
        when(rs.getString("details")).thenReturn("""
                {"tenantId":"11111111-1111-1111-1111-111111111111","userId":"00000000-0000-4000-8000-000000000000","countryCode":"ALL","environment":"PROD","correlationId":"AUTH-SUCCESS-LEGACY","category":"Security","result":"Success","severity":"Info","message":"LOGIN_SUCCEEDED on Security 00000000-0000-4000-8000-000000000000","afterState":{"username":"admin","source":"BOOTSTRAP"}}
                """);
        return rs;
    }

    @SuppressWarnings("unchecked")
    private static RowMapper<Map<String, Object>> anyAuditRowMapper() {
        return (RowMapper<Map<String, Object>>) any(RowMapper.class);
    }
}

