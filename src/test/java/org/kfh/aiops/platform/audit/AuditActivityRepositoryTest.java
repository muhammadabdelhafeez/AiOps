package org.kfh.aiops.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.kfh.aiops.platform.tenant.TenantContext;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

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
        verify(jdbcTemplate).update(contains("INSERT INTO identity.audit_log"), any(UUID.class), eq(TENANT_ID),
                eq("SETTINGS_UPDATED"), eq("Settings"), eq("tenant"), json.capture());
        assertThat(json.getValue())
                .contains("SETTINGS_UPDATED", "KW", "PROD", "CORR-AUDIT-DB", "dashboardRefreshSeconds")
                .doesNotContain("do-not-store", "apiToken", "password", "hidden");
    }
}

